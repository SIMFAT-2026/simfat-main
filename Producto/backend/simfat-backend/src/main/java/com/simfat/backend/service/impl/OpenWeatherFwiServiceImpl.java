package com.simfat.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.Region;
import com.simfat.backend.model.TerritoryWeatherObservation;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.OpenWeatherFwiService;
import com.simfat.backend.service.fwi.ComunaFwiAdvanceResult;
import com.simfat.backend.service.fwi.ComunaFwiStateService;
import com.simfat.backend.service.fwi.FwiInputs;
import com.simfat.backend.service.fwi.FwiOutputs;
import com.simfat.backend.service.fwi.LegacyProxyFwiCalculator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    // Matches ComunaRiskServiceImpl's SANTIAGO_ZONE convention: "today" for the Van Wagner
    // noon lookup is always Chile local time, independent of the server's own timezone.
    private static final ZoneId SANTIAGO_ZONE = ZoneId.of("America/Santiago");

    private static final String FWI_METHOD_PROXY = "PROXY_V1";
    private static final String FWI_METHOD_VAN_WAGNER = "VAN_WAGNER";

    private final TerritoryWeatherObservationRepository weatherRepository;
    private final RegionRepository regionRepository;
    private final ObjectMapper objectMapper;
    private final ComunaInfoRepository comunaInfoRepository;
    private final ComunaFwiStateService comunaFwiStateService;

    @Value("${openmeteo.api.base-url:https://api.open-meteo.com}")
    private String baseUrl;

    @Value("${openmeteo.sync.enabled:true}")
    private boolean syncEnabled;

    // S1d2 (design D8 "Oct 6 cut date"): which FWI computation to use. Default PROXY_V1
    // keeps production behavior unchanged from this slice; VAN_WAGNER is fully wired here
    // but is a deliberate, later config-only flip, never bundled with a code change.
    // Gating mechanism (task 1d2.3 / CFW-8a): even when this flag is VAN_WAGNER, the real
    // chain is ONLY invoked when regionId is a real monitored comuna (comunaInfoRepository
    // .existsById), so the 16 non-target display-only Region entities (and the "biobio"/
    // "nuble"/"araucania" region-level slugs, which are also not comuna ids) always stay
    // on PROXY_V1, regardless of this flag. This covers all three current callers of
    // syncFwiByRegion — the cron loop below, ComunaRiskServiceImpl's per-comuna recompute,
    // and TerritoryController's admin-triggered /territory/sync — with no signature change.
    @Value("${territory.fwi.method:PROXY_V1}")
    private String fwiMethod;

    public OpenWeatherFwiServiceImpl(
        TerritoryWeatherObservationRepository weatherRepository,
        RegionRepository regionRepository,
        ObjectMapper objectMapper,
        ComunaInfoRepository comunaInfoRepository,
        ComunaFwiStateService comunaFwiStateService
    ) {
        this.weatherRepository = weatherRepository;
        this.regionRepository = regionRepository;
        this.objectMapper = objectMapper;
        this.comunaInfoRepository = comunaInfoRepository;
        this.comunaFwiStateService = comunaFwiStateService;
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
        // IMPORTANTE: forecast_days solo acota el bloque "daily"; cuando se
        // combina con past_hours, el bloque "hourly" ignora forecast_days y
        // cae al horizonte default (~16 días). forecast_hours es el parámetro
        // correcto para acotar el horizonte horario hacia adelante.
        String url = baseUrl + "/v1/forecast"
            + "?latitude=" + lat
            + "&longitude=" + lon
            + "&daily=temperature_2m_max,temperature_2m_min,relative_humidity_2m_min,windspeed_10m_max,winddirection_10m_dominant,precipitation_sum"
            // S1d2: temperature_2m/relative_humidity_2m/wind_speed_10m/precipitation are
            // ADDED to the existing hourly block (not replacing soil_temperature_0cm/
            // windspeed_10m/winddirection_10m/weather_code, which the proxy/tooltip/wind
            // slider paths already depend on) so the noon-of-today values can be derived
            // for the Van Wagner path without a second HTTP request.
            + "&hourly=soil_temperature_0cm,windspeed_10m,winddirection_10m,weather_code,"
            + "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation"
            + "&past_hours=24"
            + "&forecast_hours=24"
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
            Double tempMin = getFirstDouble(daily, "temperature_2m_min");
            Double rhMin = getFirstDouble(daily, "relative_humidity_2m_min");
            Double windMax = getFirstDouble(daily, "windspeed_10m_max");
            Double windDirection = getFirstDouble(daily, "winddirection_10m_dominant");
            Double precip = getFirstDouble(daily, "precipitation_sum");
            Double soilTemp = getDailyAggregateFromHourly(hourly, "soil_temperature_0cm");
            Integer weatherCode = getCurrentHourInt(hourly, "weather_code");

            if (tempMax == null || rhMin == null || windMax == null || precip == null) {
                LOGGER.warn("fwi_api status=missing_fields regionId={}", regionId);
                return false;
            }

            LocalDate targetDate = LocalDate.now(SANTIAGO_ZONE);
            FwiComputationResult fwiResult = computeFwi(regionId, tempMax, rhMin, windMax, precip, hourly, targetDate);

            TerritoryWeatherObservation obs = new TerritoryWeatherObservation();
            obs.setRegionId(regionId);
            obs.setObservedAt(LocalDateTime.now());
            obs.setSource(SOURCE);
            obs.setLat(lat);
            obs.setLon(lon);
            obs.setFwi(round2(fwiResult.fwi()));
            obs.setTempMax(tempMax);
            obs.setTempMin(tempMin);
            obs.setHumidityMin(rhMin);
            obs.setWindMax(windMax);
            obs.setWindDirection(windDirection);
            obs.setPrecip(precip);
            obs.setSoilTemp(soilTemp == null ? null : round2(soilTemp));
            obs.setWeatherCode(weatherCode);
            obs.setHourlyTimestamps(getHourlyTimestamps(hourly));
            obs.setHourlyWindSpeed(getHourlyDoubles(hourly, "windspeed_10m"));
            obs.setHourlyWindDirection(getHourlyDoubles(hourly, "winddirection_10m"));
            obs.setFwiMethod(fwiResult.method());
            if (fwiResult.ffmc() != null) {
                obs.setFfmc(round2(fwiResult.ffmc()));
                obs.setDmc(round2(fwiResult.dmc()));
                obs.setDc(round2(fwiResult.dc()));
                obs.setIsi(round2(fwiResult.isi()));
                obs.setBui(round2(fwiResult.bui()));
                obs.setDsr(round2(fwiResult.dsr()));
            }
            obs.setIngestedAt(LocalDateTime.now());
            weatherRepository.save(obs);

            LOGGER.info("fwi_api status=ok regionId={} temp={} rh={} wind={} precip={} fwiMethod={} fwi={}",
                regionId, tempMax, rhMin, windMax, precip, fwiResult.method(), round2(fwiResult.fwi()));
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
     * Result of the method-gated FWI computation: either the {@code PROXY_V1} branch
     * (only {@code fwi} populated, the five Van Wagner fields left {@code null}) or the
     * {@code VAN_WAGNER} branch (all seven fields populated from {@link FwiOutputs}).
     */
    private record FwiComputationResult(
            double fwi, String method, Double ffmc, Double dmc, Double dc, Double isi, Double bui, Double dsr) {

        static FwiComputationResult proxy(double fwi) {
            return new FwiComputationResult(fwi, FWI_METHOD_PROXY, null, null, null, null, null, null);
        }
    }

    /**
     * Decides and runs the FWI computation for one sync (task 1d2.2/1d2.3). Gated to the
     * Van Wagner chain ONLY when {@code territory.fwi.method=VAN_WAGNER} AND {@code
     * regionId} is a real monitored comuna (see the {@link #fwiMethod} field javadoc for
     * why this second condition exists). Falls back to the proxy, logging a warning, if
     * today's noon hourly inputs cannot be derived or the chain throws — this method never
     * fails the sync outright over the Van Wagner path.
     */
    private FwiComputationResult computeFwi(
            String regionId, double tempMax, double rhMin, double windMax, double precip,
            JsonNode hourly, LocalDate targetDate) {
        boolean useVanWagner = FWI_METHOD_VAN_WAGNER.equals(fwiMethod) && comunaInfoRepository.existsById(regionId);
        if (!useVanWagner) {
            return FwiComputationResult.proxy(LegacyProxyFwiCalculator.computeProxyFwi(tempMax, rhMin, windMax, precip));
        }

        FwiInputs noonInputs = deriveNoonInputs(hourly, precip, targetDate);
        if (noonInputs == null) {
            LOGGER.warn("fwi_van_wagner status=missing_noon_inputs regionId={} fallback=proxy", regionId);
            return FwiComputationResult.proxy(LegacyProxyFwiCalculator.computeProxyFwi(tempMax, rhMin, windMax, precip));
        }

        try {
            ComunaFwiAdvanceResult advanceResult = comunaFwiStateService.advance(regionId, targetDate, noonInputs);
            FwiOutputs outputs = advanceResult.outputs();
            return new FwiComputationResult(
                    outputs.fwi(), FWI_METHOD_VAN_WAGNER,
                    outputs.ffmc(), outputs.dmc(), outputs.dc(), outputs.isi(), outputs.bui(), outputs.dsr());
        } catch (Exception ex) {
            LOGGER.warn("fwi_van_wagner status=error regionId={} error={} fallback=proxy", regionId, ex.getMessage());
            return FwiComputationResult.proxy(LegacyProxyFwiCalculator.computeProxyFwi(tempMax, rhMin, windMax, precip));
        }
    }

    /**
     * Derives today's noon-local-time {@link FwiInputs} from the hourly Open-Meteo block,
     * or {@code null} if the noon timestamp or any of the three required fields is absent.
     *
     * <p><b>Documented simplification:</b> {@code precipMm} uses the already-fetched daily
     * {@code precipitation_sum} (calendar-day total) rather than a strict 24h-ending-at-noon
     * rolling window, which would require either a second API request or per-hour
     * accumulation logic. This is a deliberate MVP approximation for S1d2, not a hydrology-
     * perfect accumulation window.
     */
    private FwiInputs deriveNoonInputs(JsonNode hourly, Double dailyPrecipSum, LocalDate targetDate) {
        Integer noonIndex = findNoonIndex(hourly, targetDate);
        if (noonIndex == null) {
            return null;
        }
        Double tempC = getDoubleAt(hourly, "temperature_2m", noonIndex);
        Double rhPct = getDoubleAt(hourly, "relative_humidity_2m", noonIndex);
        Double windKmh = getDoubleAt(hourly, "wind_speed_10m", noonIndex);
        if (tempC == null || rhPct == null || windKmh == null) {
            return null;
        }
        double precipMm = dailyPrecipSum == null ? 0.0 : dailyPrecipSum;
        return new FwiInputs(tempC, rhPct, windKmh, precipMm, targetDate.getMonthValue());
    }

    private Integer findNoonIndex(JsonNode hourly, LocalDate targetDate) {
        JsonNode times = hourly.path("time");
        if (times.isMissingNode() || !times.isArray()) {
            return null;
        }
        String noonTimestamp = targetDate + "T12:00";
        for (int i = 0; i < times.size(); i++) {
            if (noonTimestamp.equals(times.get(i).asText())) {
                return i;
            }
        }
        return null;
    }

    private Double getDoubleAt(JsonNode hourly, String field, int index) {
        JsonNode arr = hourly.path(field);
        if (arr.isMissingNode() || !arr.isArray() || index >= arr.size() || arr.get(index).isNull()) {
            return null;
        }
        return arr.get(index).asDouble();
    }

    private Double getFirstDouble(JsonNode daily, String field) {
        JsonNode arr = daily.path(field);
        if (arr.isMissingNode() || arr.isEmpty() || arr.get(0).isNull()) {
            return null;
        }
        return arr.get(0).asDouble();
    }

    /**
     * Toma el valor horario mas cercano a "ahora" en lugar del agregado diario:
     * weather_code diario representa la condicion mas severa del dia entero, lo
     * que mostraria "lluvia" horas despues de que paro de llover. Se usa para
     * el widget de clima del tooltip comunal (no necesita serie completa, solo
     * el estado actual).
     */
    private Integer getCurrentHourInt(JsonNode hourly, String field) {
        JsonNode times = hourly.path("time");
        JsonNode values = hourly.path(field);
        if (times.isMissingNode() || !times.isArray() || values.isMissingNode() || !values.isArray()) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        int closestIndex = -1;
        long closestDiffMinutes = Long.MAX_VALUE;
        for (int i = 0; i < times.size(); i++) {
            try {
                LocalDateTime ts = LocalDateTime.parse(times.get(i).asText());
                long diff = Math.abs(java.time.Duration.between(now, ts).toMinutes());
                if (diff < closestDiffMinutes) {
                    closestDiffMinutes = diff;
                    closestIndex = i;
                }
            } catch (Exception ignored) {
                // timestamp malformado, se ignora ese punto
            }
        }

        if (closestIndex < 0 || closestIndex >= values.size() || values.get(closestIndex).isNull()) {
            return null;
        }
        return values.get(closestIndex).asInt();
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
