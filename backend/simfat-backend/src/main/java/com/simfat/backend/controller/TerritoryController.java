package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.TerritoryBoundsResponseDTO;
import com.simfat.backend.dto.TerritoryLayersResponseDTO;
import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.model.ForestLossRecord;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/territory")
public class TerritoryController {

    private final HeatAlertEventRepository heatAlertRepository;
    private final CitizenReportRepository citizenReportRepository;
    private final ForestLossRecordRepository forestLossRepository;
    private final OpenEoIndicatorObservationRepository observationRepository;

    public TerritoryController(
        HeatAlertEventRepository heatAlertRepository,
        CitizenReportRepository citizenReportRepository,
        ForestLossRecordRepository forestLossRepository,
        OpenEoIndicatorObservationRepository observationRepository
    ) {
        this.heatAlertRepository = heatAlertRepository;
        this.citizenReportRepository = citizenReportRepository;
        this.forestLossRepository = forestLossRepository;
        this.observationRepository = observationRepository;
    }

    @GetMapping("/bounds")
    public ResponseEntity<ApiResponse<TerritoryBoundsResponseDTO>> getBounds(@RequestParam String regionId) {
        RegionGeometry geometry = RegionGeometry.fromRegion(regionId);

        TerritoryBoundsResponseDTO dto = new TerritoryBoundsResponseDTO();
        dto.setRegionId(regionId);
        dto.setCenter(List.of(geometry.centerLat, geometry.centerLng));
        dto.setBounds(List.of(
            List.of(geometry.minLat, geometry.minLng),
            List.of(geometry.maxLat, geometry.maxLng)
        ));
        dto.setZoom(geometry.zoom);

        return ResponseEntity.ok(ApiResponse.ok("Bounds territoriales obtenidos correctamente", dto));
    }

    @GetMapping("/layers")
    public ResponseEntity<ApiResponse<TerritoryLayersResponseDTO>> getLayers(
        @RequestParam String regionId,
        @RequestParam(required = false) String indicators,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDateTime fromDate = from != null ? from.atStartOfDay() : LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime toDate = to != null ? to.atTime(23, 59, 59) : LocalDate.now().atTime(23, 59, 59);
        RegionGeometry geometry = RegionGeometry.fromRegion(regionId);

        Map<String, Object> layers = new LinkedHashMap<>();
        List<String> requestedIndicators = parseIndicators(indicators);

        if (requestedIndicators.contains("NDVI")) {
            layers.put("NDVI", ndviLayer(regionId, geometry, fromDate, toDate));
        }
        if (requestedIndicators.contains("NDMI")) {
            layers.put("NDMI", ndmiLayer(regionId, geometry, fromDate, toDate));
        }
        if (requestedIndicators.contains("LOSS")) {
            layers.put("LOSS", lossLayer(regionId, geometry));
        }
        if (requestedIndicators.contains("ALERTS")) {
            layers.put("ALERTS", alertsLayer(fromDate, toDate));
        }
        if (requestedIndicators.contains("REPORTS")) {
            layers.put("REPORTS", reportsLayer(fromDate, toDate));
        }

        TerritoryLayersResponseDTO dto = new TerritoryLayersResponseDTO();
        dto.setRegionId(regionId);
        dto.setGeneratedAt(LocalDateTime.now());
        dto.setLayers(layers);

        return ResponseEntity.ok(ApiResponse.ok("Capas territoriales obtenidas correctamente", dto));
    }

    private Map<String, Object> ndviLayer(String regionId, RegionGeometry geometry, LocalDateTime from, LocalDateTime to) {
        List<OpenEoIndicatorObservation> observations = observationRepository
            .findByIndicatorAndObservedAtBetweenOrderByObservedAtDesc(IndicatorType.NDVI, from, to, PageRequest.of(0, 3));

        List<Map<String, Object>> features = new ArrayList<>();
        if (observations.isEmpty()) {
            features.add(pointFeature("ndvi-fallback-" + regionId, geometry.centerLng, geometry.centerLat, Map.of(
                "label", geometry.label,
                "indicator", "NDVI",
                "value", 0.0
            )));
        } else {
            for (int i = 0; i < observations.size(); i++) {
                OpenEoIndicatorObservation item = observations.get(i);
                features.add(pointFeature(
                    "ndvi-" + i,
                    geometry.centerLng + (i * 0.04),
                    geometry.centerLat + (i * 0.03),
                    Map.of(
                        "label", geometry.label,
                        "indicator", "NDVI",
                        "value", item.getValue() == null ? 0.0 : item.getValue()
                    )
                ));
            }
        }

        return featureCollection(features);
    }

    private Map<String, Object> ndmiLayer(String regionId, RegionGeometry geometry, LocalDateTime from, LocalDateTime to) {
        List<OpenEoIndicatorObservation> observations = observationRepository
            .findByIndicatorAndObservedAtBetweenOrderByObservedAtDesc(IndicatorType.NDMI, from, to, PageRequest.of(0, 3));

        List<Map<String, Object>> features = new ArrayList<>();
        if (observations.isEmpty()) {
            features.add(pointFeature("ndmi-fallback-" + regionId, geometry.centerLng, geometry.centerLat, Map.of(
                "label", geometry.label,
                "indicator", "NDMI",
                "value", 0.0
            )));
        } else {
            for (int i = 0; i < observations.size(); i++) {
                OpenEoIndicatorObservation item = observations.get(i);
                features.add(pointFeature(
                    "ndmi-" + i,
                    geometry.centerLng - (i * 0.04),
                    geometry.centerLat + (i * 0.02),
                    Map.of(
                        "label", geometry.label,
                        "indicator", "NDMI",
                        "value", item.getValue() == null ? 0.0 : item.getValue()
                    )
                ));
            }
        }

        return featureCollection(features);
    }

    private Map<String, Object> lossLayer(String regionId, RegionGeometry geometry) {
        List<ForestLossRecord> losses = forestLossRepository.findAllByOrderByAnioAsc();
        List<Map<String, Object>> features = new ArrayList<>();

        if (losses.isEmpty()) {
            features.add(pointFeature("loss-fallback-" + regionId, geometry.centerLng, geometry.centerLat, Map.of(
                "label", geometry.label,
                "indicator", "LOSS",
                "hectares", 0
            )));
        } else {
            ForestLossRecord latest = losses.get(losses.size() - 1);
            features.add(pointFeature("loss-latest-" + regionId, geometry.centerLng + 0.05, geometry.centerLat - 0.03, Map.of(
                "label", geometry.label,
                "indicator", "LOSS",
                "hectares", latest.getHectareasPerdidas() == null ? 0 : latest.getHectareasPerdidas()
            )));
        }

        return featureCollection(features);
    }

    private Map<String, Object> alertsLayer(LocalDateTime from, LocalDateTime to) {
        List<Map<String, Object>> features = heatAlertRepository.findAll()
            .stream()
            .filter(alert -> alert.getFechaEvento() != null && !alert.getFechaEvento().isBefore(from) && !alert.getFechaEvento().isAfter(to))
            .map(this::toAlertFeature)
            .toList();

        return featureCollection(features);
    }

    private Map<String, Object> reportsLayer(LocalDateTime from, LocalDateTime to) {
        List<Map<String, Object>> features = citizenReportRepository.findAll()
            .stream()
            .filter(item -> item.getCreatedAt() != null && !item.getCreatedAt().isBefore(from) && !item.getCreatedAt().isAfter(to))
            .map(this::toReportFeature)
            .toList();

        return featureCollection(features);
    }

    private Map<String, Object> toAlertFeature(HeatAlertEvent item) {
        return pointFeature(item.getId(), item.getLongitud(), item.getLatitud(), Map.of(
            "label", item.getDescripcion() == null || item.getDescripcion().isBlank() ? "Alerta de calor" : item.getDescripcion(),
            "indicator", "ALERTS",
            "level", item.getNivelRiesgo() == null ? "BAJO" : item.getNivelRiesgo().name()
        ));
    }

    private Map<String, Object> toReportFeature(CitizenReport item) {
        return pointFeature(item.getId(), item.getLongitude(), item.getLatitude(), Map.of(
            "label", item.getDescription() == null || item.getDescription().isBlank() ? "Reporte ciudadano" : item.getDescription(),
            "indicator", "REPORTS",
            "category", item.getCategory() == null ? "OTRO" : item.getCategory()
        ));
    }

    private List<String> parseIndicators(String rawIndicators) {
        if (rawIndicators == null || rawIndicators.isBlank()) {
            return List.of("NDVI", "NDMI", "LOSS", "ALERTS", "REPORTS");
        }

        return List.of(rawIndicators.split(","))
            .stream()
            .map(item -> item == null ? "" : item.trim().toUpperCase(Locale.ROOT))
            .filter(item -> List.of("NDVI", "NDMI", "LOSS", "ALERTS", "REPORTS").contains(item))
            .distinct()
            .toList();
    }

    private Map<String, Object> featureCollection(List<Map<String, Object>> features) {
        return Map.of(
            "type", "FeatureCollection",
            "features", features
        );
    }

    private Map<String, Object> pointFeature(String id, Double lng, Double lat, Map<String, Object> properties) {
        return Map.of(
            "type", "Feature",
            "id", id == null ? "feature" : id,
            "properties", properties,
            "geometry", Map.of(
                "type", "Point",
                "coordinates", List.of(lng == null ? 0.0 : lng, lat == null ? 0.0 : lat)
            )
        );
    }

    private record RegionGeometry(String id, String label, double centerLat, double centerLng, double minLat, double minLng, double maxLat, double maxLng, int zoom) {
        static RegionGeometry fromRegion(String regionId) {
            String normalized = regionId == null ? "" : regionId.trim().toLowerCase(Locale.ROOT);
            if ("araucania".equals(normalized)) {
                return new RegionGeometry("araucania", "La Araucania", -38.7, -72.4, -39.8, -73.9, -37.8, -71.2, 8);
            }
            return new RegionGeometry("biobio", "Biobio", -37.5, -72.5, -38.9, -74.1, -36.3, -71.0, 8);
        }
    }
}
