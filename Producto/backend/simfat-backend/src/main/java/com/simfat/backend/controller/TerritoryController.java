package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.TerritoryBoundsResponseDTO;
import com.simfat.backend.dto.TerritoryLayersResponseDTO;
import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.service.ComunaRiskService;
import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.model.ForestLossRecord;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.model.TerritoryRiskSnapshot;
import com.simfat.backend.model.TerritoryWeatherObservation;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.NasaFirmsService;
import com.simfat.backend.service.OpenWeatherFwiService;
import com.simfat.backend.service.TerritoryRiskService;
import java.time.LocalDate;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/territory")
public class TerritoryController {

    private static final List<String> VALID_INDICATORS =
        List.of("NDVI", "NDMI", "LOSS", "ALERTS", "REPORTS", "FIRMS", "RISK_SCORE",
            "WIND", "HUMIDITY", "AIR_TEMP", "SOIL_TEMP");

    private final HeatAlertEventRepository heatAlertRepository;
    private final CitizenReportRepository citizenReportRepository;
    private final ForestLossRecordRepository forestLossRepository;
    private final OpenEoIndicatorObservationRepository observationRepository;
    private final RegionRepository regionRepository;
    private final TerritoryRiskService territoryRiskService;
    private final NasaFirmsService nasaFirmsService;
    private final OpenWeatherFwiService openWeatherFwiService;
    private final ComunaRiskService comunaRiskService;
    private final ComunaInfoRepository comunaInfoRepository;
    private final TerritoryWeatherObservationRepository weatherObservationRepository;

    public TerritoryController(
        HeatAlertEventRepository heatAlertRepository,
        CitizenReportRepository citizenReportRepository,
        ForestLossRecordRepository forestLossRepository,
        OpenEoIndicatorObservationRepository observationRepository,
        RegionRepository regionRepository,
        TerritoryRiskService territoryRiskService,
        NasaFirmsService nasaFirmsService,
        OpenWeatherFwiService openWeatherFwiService,
        ComunaRiskService comunaRiskService,
        ComunaInfoRepository comunaInfoRepository,
        TerritoryWeatherObservationRepository weatherObservationRepository
    ) {
        this.heatAlertRepository = heatAlertRepository;
        this.citizenReportRepository = citizenReportRepository;
        this.forestLossRepository = forestLossRepository;
        this.observationRepository = observationRepository;
        this.regionRepository = regionRepository;
        this.territoryRiskService = territoryRiskService;
        this.nasaFirmsService = nasaFirmsService;
        this.openWeatherFwiService = openWeatherFwiService;
        this.comunaRiskService = comunaRiskService;
        this.comunaInfoRepository = comunaInfoRepository;
        this.weatherObservationRepository = weatherObservationRepository;
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
        if (requestedIndicators.contains("FIRMS")) {
            layers.put("FIRMS", firmsLayer(regionId, fromDate, toDate));
        }
        if (requestedIndicators.contains("REPORTS")) {
            layers.put("REPORTS", reportsLayer(fromDate, toDate));
        }
        if (requestedIndicators.contains("RISK_SCORE")) {
            layers.put("RISK_SCORE", riskScoreLayer(regionId, geometry));
        }
        boolean needsClimateLayers = requestedIndicators.contains("WIND")
            || requestedIndicators.contains("HUMIDITY")
            || requestedIndicators.contains("AIR_TEMP")
            || requestedIndicators.contains("SOIL_TEMP");

        if (needsClimateLayers) {
            List<ComunaInfo> comunas = comunaInfoRepository.findByRegionId(regionId);
            Map<String, TerritoryWeatherObservation> latestByComuna = latestWeatherByComuna(comunas);

            if (requestedIndicators.contains("WIND")) {
                layers.put("WIND", climateValueMap(comunas, latestByComuna, "WIND", "km/h", TerritoryWeatherObservation::getWindMax));
            }
            if (requestedIndicators.contains("HUMIDITY")) {
                layers.put("HUMIDITY", climateValueMap(comunas, latestByComuna, "HUMIDITY", "%", TerritoryWeatherObservation::getHumidityMin));
            }
            if (requestedIndicators.contains("AIR_TEMP")) {
                layers.put("AIR_TEMP", climateValueMap(comunas, latestByComuna, "AIR_TEMP", "°C", TerritoryWeatherObservation::getTempMax));
            }
            if (requestedIndicators.contains("SOIL_TEMP")) {
                layers.put("SOIL_TEMP", climateValueMap(comunas, latestByComuna, "SOIL_TEMP", "°C", TerritoryWeatherObservation::getSoilTemp));
            }
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

    @GetMapping("/risk-score/{regionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRiskScore(@PathVariable String regionId) {
        TerritoryRiskSnapshot snapshot = territoryRiskService.getLatestSnapshot(regionId);
        if (snapshot == null) {
            return ResponseEntity.ok(ApiResponse.ok("Sin snapshot disponible para " + regionId, Map.of(
                "regionId", regionId,
                "alertLevel", "NORMAL",
                "scoreComposite", 0.0,
                "qualityFlag", "NO_DATA"
            )));
        }

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("fwi", Map.of("score", nullSafe(snapshot.getComponentFwi()), "rawValue", nullSafe(snapshot.getFwiRaw()), "weight", 0.38));
        components.put("ndmi", Map.of("score", nullSafe(snapshot.getComponentNdmi()), "rawValue", nullSafe(snapshot.getNdmiRaw()), "weight", 0.22));
        components.put("firms", Map.of("score", nullSafe(snapshot.getComponentFirms()), "focosCount", nullSafe(snapshot.getFirmsCount()), "frpMean", nullSafe(snapshot.getFirmsFrpMean()), "weight", 0.18));
        components.put("loss", Map.of("score", nullSafe(snapshot.getComponentLoss()), "lossRate", nullSafe(snapshot.getLossRateRaw()), "weight", 0.10));
        components.put("ndvi", Map.of("score", nullSafe(snapshot.getComponentNdvi()), "rawValue", nullSafe(snapshot.getNdviRaw()), "weight", 0.08));
        components.put("reports", Map.of("score", nullSafe(snapshot.getComponentReports()), "count", nullSafe(snapshot.getReportsCount()), "weight", 0.04));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("regionId", snapshot.getRegionId());
        data.put("computedAt", snapshot.getComputedAt());
        data.put("scoreComposite", snapshot.getScoreComposite());
        data.put("alertLevel", snapshot.getAlertLevel());
        data.put("qualityFlag", snapshot.getQualityFlag());
        data.put("components", components);
        data.put("fwiInputs", buildFwiInputs(regionId));

        return ResponseEntity.ok(ApiResponse.ok("Score de riesgo territorial obtenido correctamente", data));
    }

    @GetMapping(value = "/geojson/{regionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public org.springframework.http.ResponseEntity<String> getComunasGeoJson(@PathVariable String regionId) {
        String path = "geojson/comunas-" + regionId.toLowerCase(Locale.ROOT) + ".geojson";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            try (InputStream is = resource.getInputStream()) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return org.springframework.http.ResponseEntity.ok()
                    .header("Cache-Control", "max-age=86400")
                    .body(json);
            }
        } catch (Exception ex) {
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/risk-score/comunas/{regionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getComunalRiskScores(@PathVariable String regionId) {
        Map<String, ComunaRiskSnapshot> snapshots = comunaRiskService.getLatestSnapshotsByRegion(regionId);
        Map<String, Object> result = new LinkedHashMap<>();
        snapshots.forEach((comunaId, s) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("alertLevel", s.getAlertLevel());
            data.put("scoreComposite", s.getScoreComposite());
            data.put("mode", s.getMode());
            data.put("qualityFlag", s.getQualityFlag());
            data.put("nombreComuna", s.getNombreComuna());
            data.put("computedAt", s.getComputedAt());
            data.put("fwiRaw", s.getFwiRaw());
            data.put("firmsCount", s.getFirmsCount());
            data.put("firmsFrpMean", s.getFirmsFrpMean());
            data.put("ndmiRaw", s.getNdmiRaw());
            data.put("ndviRaw", s.getNdviRaw());
            Map<String, Object> components = new LinkedHashMap<>();
            components.put("fwi", s.getComponentFwi());
            components.put("firms", s.getComponentFirms());
            components.put("reports", s.getComponentReports());
            components.put("ndmi", s.getComponentNdmi());
            components.put("ndvi", s.getComponentNdvi());
            components.put("loss", s.getComponentLoss());
            data.put("components", components);
            data.put("fwiInputs", buildFwiInputs(comunaId));
            result.put(comunaId, data);
        });
        return ResponseEntity.ok(ApiResponse.ok("Scores comunales para " + regionId, result));
    }

    @GetMapping("/risk-score/comunas/{gadmGid}/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getComunaHistory(
        @PathVariable String gadmGid,
        @RequestParam(defaultValue = "7") int days
    ) {
        List<ComunaRiskSnapshot> history = comunaRiskService.getSnapshotHistory(gadmGid, days);
        List<Map<String, Object>> snapshots = history.stream().map(s -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("computedAt", s.getComputedAt());
            entry.put("scoreComposite", s.getScoreComposite());
            entry.put("alertLevel", s.getAlertLevel());
            entry.put("mode", s.getMode());
            return entry;
        }).toList();
        String nombre = history.isEmpty() ? gadmGid : history.get(0).getNombreComuna();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gadmGid", gadmGid);
        result.put("comunaNombre", nombre);
        result.put("snapshots", snapshots);
        return ResponseEntity.ok(ApiResponse.ok("Historial de riesgo para " + gadmGid, result));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerSync(@RequestParam String regionId) {
        // Buscar bbox de la región para FIRMS y FWI
        regionRepository.findById(regionId).ifPresent(region -> {
            java.util.List<Double> bbox = region.getAoiBbox();
            if (bbox != null && bbox.size() == 4) {
                nasaFirmsService.syncActiveFiresByRegion(regionId, bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3));
                double centerLat = (bbox.get(1) + bbox.get(3)) / 2.0;
                double centerLon = (bbox.get(0) + bbox.get(2)) / 2.0;
                openWeatherFwiService.syncFwiByRegion(regionId, centerLat, centerLon);
            }
        });

        TerritoryRiskSnapshot snapshot = territoryRiskService.recomputeRiskByRegion(regionId);

        // Recalcula scores comunales en background (no bloquea la respuesta)
        new Thread(comunaRiskService::recomputeAllComunas).start();

        return ResponseEntity.ok(ApiResponse.ok("Sync completo para " + regionId,
            Map.of("regionId", regionId, "alertLevel", snapshot.getAlertLevel(),
                   "scoreComposite", snapshot.getScoreComposite(), "qualityFlag", snapshot.getQualityFlag(),
                   "comunalSyncTriggered", true)));
    }

    private Map<String, Object> firmsLayer(String regionId, LocalDateTime from, LocalDateTime to) {
        List<Map<String, Object>> features = heatAlertRepository.findByRegionId(regionId)
            .stream()
            .filter(e -> "NASA_FIRMS".equals(e.getFuente()))
            .filter(e -> e.getFechaEvento() != null && !e.getFechaEvento().isBefore(from) && !e.getFechaEvento().isAfter(to))
            .filter(e -> e.getFirmsConfidence() != null && !"l".equals(e.getFirmsConfidence()))
            .map(e -> {
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("label", "Foco activo VIIRS");
                props.put("indicator", "FIRMS");
                props.put("confidence", e.getFirmsConfidence());
                props.put("frp", e.getFirmsFrp() == null ? 0.0 : e.getFirmsFrp());
                props.put("satellite", e.getFirmsSatellite());
                props.put("acquiredAt", e.getFechaEvento());
                return pointFeature(e.getId(), e.getLongitud(), e.getLatitud(), props);
            })
            .toList();

        return featureCollection(features);
    }

    private Map<String, Object> riskScoreLayer(String regionId, RegionGeometry geometry) {
        TerritoryRiskSnapshot snapshot = territoryRiskService.getLatestSnapshot(regionId);
        double score = snapshot != null && snapshot.getScoreComposite() != null ? snapshot.getScoreComposite() : 0.0;
        String alertLevel = snapshot != null && snapshot.getAlertLevel() != null ? snapshot.getAlertLevel() : "NORMAL";
        String computedAt = snapshot != null && snapshot.getComputedAt() != null ? snapshot.getComputedAt().toString() : null;

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("label", geometry.label);
        props.put("indicator", "RISK_SCORE");
        props.put("score", score);
        props.put("alertLevel", alertLevel);
        props.put("computedAt", computedAt);

        return featureCollection(List.of(pointFeature("risk-" + regionId, geometry.centerLng, geometry.centerLat, props)));
    }

    /**
     * Resuelve la ultima observacion meteorologica de cada comuna en una unica consulta
     * (en vez de una consulta por comuna), para evitar el patron N+1 al construir las
     * capas climaticas (WIND/HUMIDITY/AIR_TEMP/SOIL_TEMP) de /layers.
     */
    private Map<String, TerritoryWeatherObservation> latestWeatherByComuna(List<ComunaInfo> comunas) {
        List<String> comunaIds = comunas.stream().map(ComunaInfo::getId).collect(Collectors.toList());
        Map<String, TerritoryWeatherObservation> latestByComuna = new LinkedHashMap<>();

        for (TerritoryWeatherObservation obs : weatherObservationRepository.findByRegionIdInOrderByObservedAtDesc(comunaIds)) {
            latestByComuna.putIfAbsent(obs.getRegionId(), obs);
        }

        return latestByComuna;
    }

    /**
     * Construye un mapa de valores por comuna {comunaId: {value, unit, observedAt}} para una capa
     * climatica (WIND/HUMIDITY/AIR_TEMP/SOIL_TEMP), a partir de la ultima observacion meteorologica
     * persistida por comuna. Comunas sin observacion se omiten (no error) por requisito de spec.
     */
    private Map<String, Object> climateValueMap(
        List<ComunaInfo> comunas,
        Map<String, TerritoryWeatherObservation> latestByComuna,
        String indicator,
        String unit,
        Function<TerritoryWeatherObservation, Double> valueExtractor
    ) {
        Map<String, Object> values = new LinkedHashMap<>();

        for (ComunaInfo comuna : comunas) {
            TerritoryWeatherObservation obs = latestByComuna.get(comuna.getId());
            if (obs == null) {
                continue;
            }
            Double value = valueExtractor.apply(obs);
            if (value == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", value);
            entry.put("unit", unit);
            entry.put("observedAt", obs.getObservedAt());
            values.put(comuna.getId(), entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicator", indicator);
        result.put("unit", unit);
        result.put("values", values);
        return result;
    }

    /**
     * Expone los componentes Open-Meteo que alimentan el proxy FWI para un comuna/region,
     * a partir de la ultima observacion meteorologica persistida. Componentes faltantes
     * se exponen como null sin fallar el request.
     */
    private Map<String, Object> buildFwiInputs(String regionOrComunaId) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        Optional<TerritoryWeatherObservation> latest = weatherObservationRepository
            .findTopByRegionIdOrderByObservedAtDesc(regionOrComunaId);

        inputs.put("tempMax", latest.map(TerritoryWeatherObservation::getTempMax).orElse(null));
        inputs.put("humidityMin", latest.map(TerritoryWeatherObservation::getHumidityMin).orElse(null));
        inputs.put("windMax", latest.map(TerritoryWeatherObservation::getWindMax).orElse(null));
        inputs.put("precip", latest.map(TerritoryWeatherObservation::getPrecip).orElse(null));
        return inputs;
    }

    private List<String> parseIndicators(String rawIndicators) {
        if (rawIndicators == null || rawIndicators.isBlank()) {
            return List.of("NDVI", "NDMI", "LOSS", "ALERTS", "FIRMS", "REPORTS", "RISK_SCORE");
        }

        return List.of(rawIndicators.split(","))
            .stream()
            .map(item -> item == null ? "" : item.trim().toUpperCase(Locale.ROOT))
            .filter(VALID_INDICATORS::contains)
            .distinct()
            .toList();
    }

    private Object nullSafe(Object value) {
        return value == null ? 0 : value;
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
                return new RegionGeometry("araucania", "La Araucania", -38.7, -72.4, -39.8, -73.9, -37.8, -71.2, 9);
            }
            if ("nuble".equals(normalized)) {
                return new RegionGeometry("nuble", "Ñuble", -36.6, -71.9, -37.4, -72.8, -35.8, -71.0, 9);
            }
            return new RegionGeometry("biobio", "Biobio", -37.5, -72.5, -38.9, -74.1, -36.3, -71.0, 9);
        }
    }
}
