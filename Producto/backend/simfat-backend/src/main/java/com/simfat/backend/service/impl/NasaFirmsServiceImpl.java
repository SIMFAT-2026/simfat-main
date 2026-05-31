package com.simfat.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.Region;
import com.simfat.backend.model.RiskLevel;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.NasaFirmsService;
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
public class NasaFirmsServiceImpl implements NasaFirmsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NasaFirmsServiceImpl.class);
    private static final String SOURCE = "NASA_FIRMS";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final HeatAlertEventRepository heatAlertEventRepository;
    private final RegionRepository regionRepository;
    private final ObjectMapper objectMapper;

    @Value("${firms.api.map-key:}")
    private String mapKey;

    @Value("${firms.api.base-url:https://firms.modaps.eosdis.nasa.gov/api}")
    private String baseUrl;

    @Value("${firms.api.source:VIIRS_NOAA20_NRT}")
    private String firmsSource;

    @Value("${firms.api.day-range:2}")
    private int dayRange;

    @Value("${firms.sync.enabled:true}")
    private boolean syncEnabled;

    public NasaFirmsServiceImpl(
        HeatAlertEventRepository heatAlertEventRepository,
        RegionRepository regionRepository,
        ObjectMapper objectMapper
    ) {
        this.heatAlertEventRepository = heatAlertEventRepository;
        this.regionRepository = regionRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${firms.sync.cron:0 0 */12 * * *}")
    @Override
    public void syncActiveFiresForAllRegions() {
        if (!syncEnabled || mapKey == null || mapKey.isBlank()) {
            LOGGER.info("firms_sync status=skipped reason={}", mapKey == null || mapKey.isBlank() ? "no_map_key" : "disabled");
            return;
        }

        List<Region> regions = regionRepository.findAll();
        for (Region region : regions) {
            List<Double> bbox = region.getAoiBbox();
            if (bbox == null || bbox.size() != 4) {
                LOGGER.warn("firms_sync status=skipped_no_bbox regionId={}", region.getId());
                continue;
            }
            try {
                int saved = syncActiveFiresByRegion(region.getId(), bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3));
                LOGGER.info("firms_sync status=ok regionId={} saved={}", region.getId(), saved);
            } catch (Exception ex) {
                LOGGER.warn("firms_sync status=error regionId={} error={}", region.getId(), ex.getMessage());
            }
        }
    }

    @Override
    public int syncActiveFiresByRegion(String regionId, double west, double south, double east, double north) {
        String area = west + "," + south + "," + east + "," + north;
        String url = baseUrl + "/area/json/" + mapKey + "/" + firmsSource + "/" + area + "/" + dayRange;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                LOGGER.warn("firms_api status=http_error code={} regionId={}", response.statusCode(), regionId);
                return 0;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                return 0;
            }

            List<HeatAlertEvent> toSave = new ArrayList<>();
            for (JsonNode node : root) {
                String confidence = node.path("confidence").asText("").toLowerCase();
                if ("l".equals(confidence)) {
                    continue;
                }

                double lat = node.path("latitude").asDouble(0);
                double lon = node.path("longitude").asDouble(0);
                double frp = node.path("frp").asDouble(0);
                String satellite = node.path("satellite").asText(null);
                String acqDate = node.path("acq_date").asText("");
                String acqTime = node.path("acq_time").asText("0000");

                RiskLevel nivelRiesgo = "h".equals(confidence) ? RiskLevel.ALTO : RiskLevel.MEDIO;

                HeatAlertEvent event = new HeatAlertEvent();
                event.setRegionId(regionId);
                event.setLatitud(lat);
                event.setLongitud(lon);
                event.setNivelRiesgo(nivelRiesgo);
                event.setFuente(SOURCE);
                event.setFechaEvento(parseAcqDateTime(acqDate, acqTime));
                event.setDescripcion("Foco activo VIIRS. FRP=" + frp + " MW. Confidence=" + confidence);
                event.setFirmsConfidence(confidence);
                event.setFirmsFrp(frp);
                event.setFirmsSatellite(satellite);
                event.setFirmsSource(firmsSource);

                toSave.add(event);
            }

            heatAlertEventRepository.saveAll(toSave);
            return toSave.size();

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("firms_api status=interrupted regionId={}", regionId);
            return 0;
        } catch (Exception ex) {
            LOGGER.warn("firms_api status=exception regionId={} error={}", regionId, ex.getMessage());
            return 0;
        }
    }

    private LocalDateTime parseAcqDateTime(String acqDate, String acqTime) {
        try {
            int hour = Integer.parseInt(acqTime.substring(0, 2));
            int minute = Integer.parseInt(acqTime.substring(2, 4));
            String[] parts = acqDate.split("-");
            return LocalDateTime.of(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                hour, minute
            );
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }
}
