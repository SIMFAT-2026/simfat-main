package com.simfat.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.Region;
import com.simfat.backend.model.TerritoryWeatherObservation;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.OpenWeatherFwiService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OpenWeatherFwiServiceImpl implements OpenWeatherFwiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenWeatherFwiServiceImpl.class);
    private static final String SOURCE = "open-meteo";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final TerritoryWeatherObservationRepository weatherRepository;
    private final RegionRepository regionRepository;
    private final ObjectMapper objectMapper;

    @Value("${openmeteo.api.base-url:https://api.open-meteo.com}")
    private String baseUrl;

    @Value("${openmeteo.sync.enabled:true}")
    private boolean syncEnabled;

    public OpenWeatherFwiServiceImpl(
        TerritoryWeatherObservationRepository weatherRepository,
        RegionRepository regionRepository,
        ObjectMapper objectMapper
    ) {
        this.weatherRepository = weatherRepository;
        this.regionRepository = regionRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${openmeteo.sync.cron:0 30 */12 * * *}")
    @Override
    public void syncFwiForAllRegions() {
        if (!syncEnabled) {
            LOGGER.info("fwi_sync status=skipped reason=disabled");
            return;
        }

        List<Region> regions = regionRepository.findAll();
        for (Region region : regions) {
            List<Double> bbox = region.getAoiBbox();
            if (bbox == null || bbox.size() != 4) {
                LOGGER.warn("fwi_sync status=skipped_no_bbox regionId={}", region.getId());
                continue;
            }
            double centerLat = (bbox.get(1) + bbox.get(3)) / 2.0;
            double centerLon = (bbox.get(0) + bbox.get(2)) / 2.0;

            try {
                boolean saved = syncFwiByRegion(region.getId(), centerLat, centerLon);
                LOGGER.info("fwi_sync status={} regionId={}", saved ? "ok" : "no_data", region.getId());
            } catch (Exception ex) {
                LOGGER.warn("fwi_sync status=error regionId={} error={}", region.getId(), ex.getMessage());
            }
        }
    }

    @Override
    public boolean syncFwiByRegion(String regionId, double lat, double lon) {
        // Open-Meteo: variables meteorológicas para proxy FWI
        // (fire_danger_index no existe en Open-Meteo free tier)
        // windspeed_10m/winddirection_10m + past_hours=24 alimentan el slider
        // horario de viento (spec: wind-arrow-overlay); past_hours respalda las
        // horas ya transcurridas del día, no solo el pronóstico hacia adelante.
        String url = baseUrl + "/v1/forecast"
            + "?latitude=" + lat
            + "&longitude=" + lon
            + "&daily=temperature_2m_max,relative_humidity_2m_min,windspeed_10m_max,winddirection_10m_dominant,precipitation_sum"
            + "&hourly=soil_temperature_0cm,windspeed_10m,winddirection_10m"
            + "&past_hours=24"
            + "&forecast_days=1"
            + "&timezone=America%2FSantiago";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                LOGGER.warn("fwi_api status=http_error code={} regionId={}", response.statusCode(), regionId);
                return false;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode daily = root.path("daily");
            JsonNode hourly = root.path("hourly");

            Double tempMax = getFirstDouble(daily, "temperature_2m_max");
            Double rhMin = getFirstDouble(daily, "relative_humidity_2m_min");
            Double windMax = getFirstDouble(daily, "windspeed_10m_max");
            Double windDirection = getFirstDouble(daily, "winddirection_10m_dominant");
            Double precip = getFirstDouble(daily, "precipitation_sum");
            Double soilTemp = getDailyAggregateFromHourly(hourly, "soil_temperature_0cm");

            if (tempMax == null || rhMin == null || windMax == null || precip == null) {
                LOGGER.warn("fwi_api status=missing_fields regionId={}", regionId);
                return false;
            }

            double proxyFwi = computeProxyFwi(tempMax, rhMin, windMax, precip);

            TerritoryWeatherObservation obs = new TerritoryWeatherObservation();
            obs.setRegionId(regionId);
            obs.setObservedAt(LocalDateTime.now());
            obs.setSource(SOURCE);
            obs.setLat(lat);
            obs.setLon(lon);
            obs.setFwi(round2(proxyFwi));
            obs.setTempMax(tempMax);
            obs.setHumidityMin(rhMin);
            obs.setWindMax(windMax);
            obs.setWindDirection(windDirection);
            obs.setPrecip(precip);
            obs.setSoilTemp(soilTemp == null ? null : round2(soilTemp));
            obs.setHourlyTimestamps(getHourlyTimestamps(hourly));
            obs.setHourlyWindSpeed(getHourlyDoubles(hourly, "windspeed_10m"));
            obs.setHourlyWindDirection(getHourlyDoubles(hourly, "winddirection_10m"));
            obs.setIngestedAt(LocalDateTime.now());
            weatherRepository.save(obs);

            LOGGER.info("fwi_api status=ok regionId={} temp={} rh={} wind={} precip={} proxyFwi={}",
                regionId, tempMax, rhMin, windMax, precip, round2(proxyFwi));
            return true;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("fwi_api status=interrupted regionId={}", regionId);
            return false;
        } catch (Exception ex) {
            LOGGER.warn("fwi_api status=exception regionId={} error={}", regionId, ex.getMessage());
            return false;
        }
    }

    /**
     * Proxy FWI en escala 0-60 (similar a CFWI: <15 bajo, 15-30 moderado, 30-45 alto, >45 extremo).
     * Aproximación documentada para MVP basada en variables Open-Meteo disponibles.
     * Fuentes: relaciones meteorológicas del CFWI (temperatura-FFMC, humedad-FFMC, viento-ISI).
     */
    private double computeProxyFwi(double tempMax, double rhMin, double windMaxKmh, double precipMm) {
        // Factor de secado: temperatura alta eleva peligro
        double tempFactor = Math.max(0, Math.min(1.0, tempMax / 40.0));

        // Factor de sequedad: humedad mínima del día (peor caso)
        double drynessFactor = Math.max(0, (100.0 - rhMin) / 100.0);

        // Factor de viento: velocidad máxima del día
        double windFactor = Math.max(0, Math.min(1.0, windMaxKmh / 60.0));

        // Amortiguación por precipitación: 3mm+ reduce significativamente el riesgo
        double rainFactor = Math.max(0.0, 1.0 - precipMm / 3.0);

        // Compuesto: dryness domina (40%), temperatura (30%), viento (30%)
        double raw = 60.0 * (0.40 * drynessFactor + 0.30 * tempFactor + 0.30 * windFactor) * rainFactor;
        return Math.max(0, Math.min(60.0, raw));
    }

    private Double getFirstDouble(JsonNode daily, String field) {
        JsonNode arr = daily.path(field);
        if (arr.isMissingNode() || arr.isEmpty() || arr.get(0).isNull()) {
            return null;
        }
        return arr.get(0).asDouble();
    }

    /**
     * Open-Meteo no expone un agregado diario de temperatura de suelo en el tier gratuito;
     * se solicita la serie horaria (24 valores) y se promedia para obtener un valor diario.
     * Retorna null sin lanzar excepcion si el campo no esta disponible (graceful degradation).
     */
    private Double getDailyAggregateFromHourly(JsonNode hourly, String field) {
        JsonNode arr = hourly.path(field);
        if (arr.isMissingNode() || !arr.isArray() || arr.isEmpty()) {
            return null;
        }
        double sum = 0.0;
        int count = 0;
        for (JsonNode value : arr) {
            if (value.isNull()) {
                continue;
            }
            sum += value.asDouble();
            count++;
        }
        if (count == 0) {
            return null;
        }
        return sum / count;
    }

    private List<LocalDateTime> getHourlyTimestamps(JsonNode hourly) {
        JsonNode arr = hourly.path("time");
        if (arr.isMissingNode() || !arr.isArray()) {
            return null;
        }
        List<LocalDateTime> timestamps = new ArrayList<>();
        for (JsonNode value : arr) {
            timestamps.add(LocalDateTime.parse(value.asText()));
        }
        return timestamps;
    }

    private List<Double> getHourlyDoubles(JsonNode hourly, String field) {
        JsonNode arr = hourly.path(field);
        if (arr.isMissingNode() || !arr.isArray()) {
            return null;
        }
        List<Double> values = new ArrayList<>();
        for (JsonNode value : arr) {
            values.add(value.isNull() ? null : value.asDouble());
        }
        return values;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
