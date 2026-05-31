package com.simfat.backend.service.impl;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.TerritoryWeatherObservation;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ComunaRiskSnapshotRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.ComunaRiskService;
import com.simfat.backend.service.OpenWeatherFwiService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ComunaRiskServiceImpl implements ComunaRiskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComunaRiskServiceImpl.class);

    // Pesos modo STANDARD (sin Copernicus)
    private static final double W_FWI_STD = 0.52;
    private static final double W_FIRMS_STD = 0.33;
    private static final double W_REPORTS_STD = 0.15;

    // Pesos modo ENHANCED (con Copernicus NDVI/NDMI)
    private static final double W_FWI_ENH = 0.38;
    private static final double W_NDMI_ENH = 0.22;
    private static final double W_FIRMS_ENH = 0.18;
    private static final double W_NDVI_ENH = 0.08;
    private static final double W_REPORTS_ENH = 0.04;

    // Normalización
    private static final double FWI_MAX = 50.0;
    private static final double FIRMS_MAX_COUNT = 5.0;
    private static final double FIRMS_MAX_FRP = 80.0;
    private static final double REPORTS_MAX = 3.0;
    private static final double NDMI_DRY = -0.4;
    private static final double NDMI_WET = 0.4;
    private static final double NDVI_MIN = 0.1;
    private static final double NDVI_MAX = 0.8;

    // Umbral para activar Copernicus
    private static final double COPERNICUS_TRIGGER_THRESHOLD = 0.50;

    // Umbrales de alerta
    private static final double SCORE_PREVENTIVO = 0.50;
    private static final double SCORE_ALTO = 0.70;
    private static final double SCORE_CRITICO = 0.85;
    private static final double FWI_PREVENTIVO = 20.0;
    private static final double FWI_CRITICO = 45.0;

    private final ComunaInfoRepository comunaRepository;
    private final ComunaRiskSnapshotRepository snapshotRepository;
    private final TerritoryWeatherObservationRepository weatherRepository;
    private final HeatAlertEventRepository heatAlertRepository;
    private final CitizenReportRepository citizenReportRepository;
    private final OpenWeatherFwiService fwiService;

    public ComunaRiskServiceImpl(
        ComunaInfoRepository comunaRepository,
        ComunaRiskSnapshotRepository snapshotRepository,
        TerritoryWeatherObservationRepository weatherRepository,
        HeatAlertEventRepository heatAlertRepository,
        CitizenReportRepository citizenReportRepository,
        OpenWeatherFwiService fwiService
    ) {
        this.comunaRepository = comunaRepository;
        this.snapshotRepository = snapshotRepository;
        this.weatherRepository = weatherRepository;
        this.heatAlertRepository = heatAlertRepository;
        this.citizenReportRepository = citizenReportRepository;
        this.fwiService = fwiService;
    }

    @Scheduled(cron = "${territory.riesgo.comunal.cron:0 30 1,13 * * *}")
    @Override
    public void recomputeAllComunas() {
        List<ComunaInfo> comunas = comunaRepository.findAll();
        LOGGER.info("comuna_risk_recompute status=start total={}", comunas.size());
        int ok = 0, errors = 0;
        for (ComunaInfo comuna : comunas) {
            try {
                // Sync FWI fresco para el centroide de esta comuna
                fwiService.syncFwiByRegion(comuna.getId(), comuna.getCenterLat(), comuna.getCenterLon());
                recomputeByComuna(comuna.getId());
                ok++;
            } catch (Exception ex) {
                LOGGER.warn("comuna_risk_recompute status=error comunaId={} error={}", comuna.getId(), ex.getMessage());
                errors++;
            }
        }
        LOGGER.info("comuna_risk_recompute status=done ok={} errors={}", ok, errors);
    }

    @Override
    public ComunaRiskSnapshot recomputeByComuna(String comunaId) {
        ComunaInfo comuna = comunaRepository.findById(comunaId)
            .orElseThrow(() -> new IllegalArgumentException("Comuna no encontrada: " + comunaId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime firms48h = now.minusHours(48);
        LocalDateTime reports7d = now.minusDays(7);

        // --- FWI ---
        Double fwiRaw = null;
        double fwiNorm = 0.0;
        Optional<TerritoryWeatherObservation> latestFwi = weatherRepository
            .findTopByRegionIdOrderByObservedAtDesc(comunaId);
        if (latestFwi.isPresent() && latestFwi.get().getFwi() != null) {
            fwiRaw = latestFwi.get().getFwi();
            fwiNorm = normalize(fwiRaw, 0.0, FWI_MAX);
        }

        // --- FIRMS (distancia al centroide) ---
        List<HeatAlertEvent> regionFocos = heatAlertRepository.findByRegionId(comuna.getRegionId())
            .stream()
            .filter(e -> "NASA_FIRMS".equals(e.getFuente()))
            .filter(e -> e.getFechaEvento() != null && e.getFechaEvento().isAfter(firms48h))
            .filter(e -> e.getFirmsConfidence() != null && !"l".equals(e.getFirmsConfidence()))
            .filter(e -> e.getLatitud() != null && e.getLongitud() != null)
            .collect(Collectors.toList());

        List<HeatAlertEvent> comunaFocos = assignFocosToComuna(regionFocos, comunaId, comunaRepository.findByRegionId(comuna.getRegionId()));
        int firmsCount = comunaFocos.size();
        double firmsFrpMean = comunaFocos.stream()
            .filter(e -> e.getFirmsFrp() != null)
            .mapToDouble(HeatAlertEvent::getFirmsFrp)
            .average().orElse(0.0);
        double firmsNorm = 0.0;
        if (firmsCount > 0) {
            firmsNorm = normalize(firmsCount, 0, FIRMS_MAX_COUNT) * 0.6
                + normalize(firmsFrpMean, 0, FIRMS_MAX_FRP) * 0.4;
        }

        // --- Reportes ciudadanos ---
        long reportsCount = citizenReportRepository.findByRegionId(comuna.getRegionId())
            .stream()
            .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(reports7d))
            .count();
        double reportsNorm = normalize(reportsCount, 0, REPORTS_MAX);

        // --- Score STANDARD ---
        double scoreStandard = fwiNorm * W_FWI_STD + firmsNorm * W_FIRMS_STD + reportsNorm * W_REPORTS_STD;
        scoreStandard = clamp(scoreStandard);

        String alertLevel = resolveAlertLevel(scoreStandard, fwiRaw, firmsCount);
        String qualityFlag = "STANDARD";
        double scoreComposite = scoreStandard;

        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();
        snapshot.setComunaId(comunaId);
        snapshot.setRegionId(comuna.getRegionId());
        snapshot.setNombreComuna(comuna.getNombre());
        snapshot.setComputedAt(now);
        snapshot.setScoreComposite(round4(scoreComposite));
        snapshot.setAlertLevel(alertLevel);
        snapshot.setQualityFlag(qualityFlag);
        snapshot.setComponentFwi(round4(fwiNorm * W_FWI_STD));
        snapshot.setComponentFirms(round4(firmsNorm * W_FIRMS_STD));
        snapshot.setComponentReports(round4(reportsNorm * W_REPORTS_STD));
        snapshot.setFwiRaw(fwiRaw);
        snapshot.setFirmsCount(firmsCount);
        snapshot.setFirmsFrpMean(round4(firmsFrpMean));
        snapshot.setReportsCount((int) reportsCount);

        snapshotRepository.save(snapshot);
        return snapshot;
    }

    @Override
    public Map<String, ComunaRiskSnapshot> getLatestSnapshotsByRegion(String regionId) {
        List<ComunaInfo> comunas = comunaRepository.findByRegionId(regionId);
        Map<String, ComunaRiskSnapshot> result = new HashMap<>();
        for (ComunaInfo comuna : comunas) {
            snapshotRepository.findTopByComunaIdOrderByComputedAtDesc(comuna.getId())
                .ifPresent(s -> result.put(comuna.getId(), s));
        }
        return result;
    }

    @Override
    public List<ComunaRiskSnapshot> getLatestSnapshotsListByRegion(String regionId) {
        return getLatestSnapshotsByRegion(regionId).values().stream().toList();
    }

    private List<HeatAlertEvent> assignFocosToComuna(
        List<HeatAlertEvent> regionFocos,
        String targetComunaId,
        List<ComunaInfo> allComunas
    ) {
        return regionFocos.stream()
            .filter(foco -> {
                String nearest = findNearestComuna(foco.getLatitud(), foco.getLongitud(), allComunas);
                return targetComunaId.equals(nearest);
            })
            .collect(Collectors.toList());
    }

    private String findNearestComuna(double lat, double lon, List<ComunaInfo> comunas) {
        String nearest = null;
        double minDist = Double.MAX_VALUE;
        for (ComunaInfo c : comunas) {
            if (c.getCenterLat() == null || c.getCenterLon() == null) continue;
            double dist = Math.pow(lat - c.getCenterLat(), 2) + Math.pow(lon - c.getCenterLon(), 2);
            if (dist < minDist) {
                minDist = dist;
                nearest = c.getId();
            }
        }
        return nearest;
    }

    private String resolveAlertLevel(double score, Double fwiRaw, int firmsCount) {
        if (firmsCount > 0 || (fwiRaw != null && fwiRaw >= FWI_CRITICO) || score >= SCORE_CRITICO) {
            return "CRITICO";
        }
        if ((fwiRaw != null && fwiRaw >= FWI_PREVENTIVO) || score >= SCORE_ALTO) {
            return "ALTO";
        }
        if (score >= SCORE_PREVENTIVO) {
            return "PREVENTIVO";
        }
        return "NORMAL";
    }

    private double normalize(double value, double min, double max) {
        if (max <= min) return 0.0;
        return clamp((value - min) / (max - min));
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
