package com.simfat.backend.service.impl;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.Region;
import com.simfat.backend.model.RiskLevel;
import com.simfat.backend.repository.ComunaInfoRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
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
    private final ComunaInfoRepository comunaInfoRepository;

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
        ComunaInfoRepository comunaInfoRepository
    ) {
        this.heatAlertEventRepository = heatAlertEventRepository;
        this.regionRepository = regionRepository;
        this.comunaInfoRepository = comunaInfoRepository;
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
        String url = baseUrl + "/area/csv/" + mapKey + "/" + firmsSource + "/" + area + "/" + dayRange;

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

            List<HeatAlertEvent> toSave = parseCsvResponse(response.body(), regionId);
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

    private List<HeatAlertEvent> parseCsvResponse(String body, String regionId) {
        List<HeatAlertEvent> toSave = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return toSave;
        }

        String[] lines = body.strip().split("\\r?\\n");
        if (lines.length < 2) {
            return toSave;
        }

        String[] headers = lines[0].split(",");
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            col.put(headers[i].trim().toLowerCase(), i);
        }

        Integer latIdx = col.get("latitude");
        Integer lonIdx = col.get("longitude");
        Integer confidenceIdx = col.get("confidence");
        Integer frpIdx = col.get("frp");
        Integer satelliteIdx = col.get("satellite");
        Integer acqDateIdx = col.get("acq_date");
        Integer acqTimeIdx = col.get("acq_time");

        if (latIdx == null || lonIdx == null) {
            LOGGER.warn("firms_api status=unexpected_format regionId={} header={}", regionId, lines[0]);
            return toSave;
        }

        for (int i = 1; i < lines.length; i++) {
            String[] fields = lines[i].split(",");
            if (fields.length < headers.length) {
                continue;
            }

            String confidence = confidenceIdx != null ? fields[confidenceIdx].trim().toLowerCase() : "";
            if ("l".equals(confidence)) {
                continue;
            }

            double lat = parseDouble(fields[latIdx]);
            double lon = parseDouble(fields[lonIdx]);
            double frp = frpIdx != null ? parseDouble(fields[frpIdx]) : 0.0;
            String satellite = satelliteIdx != null ? fields[satelliteIdx].trim() : null;
            String acqDate = acqDateIdx != null ? fields[acqDateIdx].trim() : "";
            String acqTime = acqTimeIdx != null ? fields[acqTimeIdx].trim() : "0000";
            LocalDateTime fechaEvento = parseAcqDateTime(acqDate, acqTime);

            // Decision 2: region-independent identity dedup. The same physical VIIRS
            // pixel can fall inside two overlapping region bboxes (e.g. Biobio/Nuble),
            // so the second leg to see it must short-circuit instead of re-inserting it
            // under a different regionId.
            if (heatAlertEventRepository.existsByLatitudAndLongitudAndFechaEventoAndFuente(
                lat, lon, fechaEvento, SOURCE)) {
                continue;
            }

            RiskLevel nivelRiesgo = "h".equals(confidence) ? RiskLevel.ALTO : RiskLevel.MEDIO;

            // Decision 4: geometric comuna attribution at insert time — the single
            // expression also reused by BackfillComunaIdRunner. Offshore / no-polygon
            // match (or region not yet covered by geometry) resolves to null.
            String comunaId = comunaInfoRepository
                .findOneByGeometryIntersects(new GeoJsonPoint(lon, lat))
                .map(ComunaInfo::getId)
                .orElse(null);

            HeatAlertEvent event = new HeatAlertEvent();
            event.setRegionId(regionId);
            event.setComunaId(comunaId);
            event.setLatitud(lat);
            event.setLongitud(lon);
            event.setNivelRiesgo(nivelRiesgo);
            event.setFuente(SOURCE);
            event.setFechaEvento(fechaEvento);
            event.setDescripcion("Foco activo VIIRS. FRP=" + frp + " MW. Confidence=" + confidence);
            event.setFirmsConfidence(confidence);
            event.setFirmsFrp(frp);
            event.setFirmsSatellite(satellite);
            event.setFirmsSource(firmsSource);

            toSave.add(event);
        }

        return toSave;
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private LocalDateTime parseAcqDateTime(String acqDate, String acqTime) {
        try {
            // NASA's area CSV does not always zero-pad acq_time (e.g. "5" for 00:05),
            // so substring(0,2)/substring(2,4) on the raw value can throw for early
            // UTC hours. Pad to HHMM before slicing.
            String paddedTime = String.format("%04d", Integer.parseInt(acqTime.trim()));
            int hour = Integer.parseInt(paddedTime.substring(0, 2));
            int minute = Integer.parseInt(paddedTime.substring(2, 4));
            String[] parts = acqDate.split("-");
            return LocalDateTime.of(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                hour, minute
            );
        } catch (Exception ex) {
            LOGGER.warn("firms_api status=acq_datetime_parse_failed acqDate={} acqTime={} error={}", acqDate, acqTime, ex.getMessage());
            return LocalDateTime.now();
        }
    }
}
