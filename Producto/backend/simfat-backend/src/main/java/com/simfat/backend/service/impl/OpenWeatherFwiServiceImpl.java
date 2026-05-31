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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OpenWeatherFwiServiceImpl implements OpenWeatherFwiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenWeatherFwiServiceImpl.class);
    private static final String SOURCE = "openweathermap";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final TerritoryWeatherObservationRepository weatherRepository;
    private final RegionRepository regionRepository;
    private final ObjectMapper objectMapper;

    @Value("${openweather.api.key:}")
    private String apiKey;

    @Value("${openweather.api.base-url:https://api.openweathermap.org}")
    private String baseUrl;

    @Value("${openweather.sync.enabled:true}")
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

    @Scheduled(cron = "${openweather.sync.cron:0 30 */12 * * *}")
    @Override
    public void syncFwiForAllRegions() {
        if (!syncEnabled || apiKey == null || apiKey.isBlank()) {
            LOGGER.info("fwi_sync status=skipped reason={}", apiKey == null || apiKey.isBlank() ? "no_api_key" : "disabled");
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
        String url = baseUrl + "/data/3.0/onecall"
            + "?lat=" + lat
            + "&lon=" + lon
            + "&exclude=minutely,hourly,daily,alerts"
            + "&appid=" + apiKey;

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
            JsonNode current = root.path("current");
            JsonNode fwiNode = current.path("fire_weather_index");

            if (fwiNode.isMissingNode() || fwiNode.isNull()) {
                LOGGER.info("fwi_api status=no_fwi_data regionId={}", regionId);
                return false;
            }

            TerritoryWeatherObservation obs = new TerritoryWeatherObservation();
            obs.setRegionId(regionId);
            obs.setObservedAt(LocalDateTime.now());
            obs.setSource(SOURCE);
            obs.setLat(lat);
            obs.setLon(lon);
            obs.setFwi(getDoubleOrNull(fwiNode, "fwi"));
            obs.setFfmc(getDoubleOrNull(fwiNode, "ffmc"));
            obs.setDmc(getDoubleOrNull(fwiNode, "dmc"));
            obs.setDc(getDoubleOrNull(fwiNode, "dc"));
            obs.setIsi(getDoubleOrNull(fwiNode, "isi"));
            obs.setBui(getDoubleOrNull(fwiNode, "bui"));
            obs.setDsr(getDoubleOrNull(fwiNode, "dsr"));
            obs.setIngestedAt(LocalDateTime.now());

            weatherRepository.save(obs);
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

    private Double getDoubleOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asDouble();
    }
}
