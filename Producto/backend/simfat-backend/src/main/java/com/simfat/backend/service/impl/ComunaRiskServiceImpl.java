package com.simfat.backend.service.impl;

import com.simfat.backend.integration.openeo.OpenEoIndicatorLatestRequest;
import com.simfat.backend.integration.openeo.OpenEoIndicatorLatestResponse;
import com.simfat.backend.integration.openeo.OpenEoServiceClient;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.model.TerritoryWeatherObservation;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ComunaRiskSnapshotRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.ComunaRiskService;
import com.simfat.backend.service.NotificationService;
import com.simfat.backend.service.OpenWeatherFwiService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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

    // Ventana de validez de observaciones Copernicus (revisita Sentinel-2 ~5 días en Chile)
    private static final long COPERNICUS_STALENESS_DAYS = 6;

    // Normalización
    private static final double FWI_MAX = 50.0;
    private static final double FIRMS_MAX_COUNT = 5.0;
    private static final double FIRMS_MAX_FRP = 80.0;
    private static final double REPORTS_MAX = 3.0;
    private static final double NDMI_DRY = -0.4;
    private static final double NDMI_WET = 0.4;
    private static final double NDVI_MIN = 0.1;
    private static final double NDVI_MAX = 0.8;

    // Umbrales de alerta
    private static final double SCORE_PREVENTIVO = 0.50;
    private static final double SCORE_ALTO = 0.70;
    private static final double SCORE_CRITICO = 0.85;
    private static final double FWI_PREVENTIVO = 20.0;
    private static final double FWI_CRITICO = 45.0;
    // A single low-intensity FIRMS detection (e.g. a stray VIIRS pixel) should not by
    // itself force CRITICO — it already feeds the weighted score via firmsNorm. Only
    // escalate directly when detections cluster or one is genuinely intense.
    private static final int FIRMS_COUNT_CRITICO = 4;
    private static final double FIRMS_FRP_CRITICO = 60.0;

    private static final double COPERNICUS_COMUNA_PAD_DEG = 0.15;

    // Matches the frontend's "today" vs "recent" FIRMS bucket (TerritoryMapPanel.jsx
    // santiagoDateKey) so the alert override and the map legend agree on what counts
    // as "happening today".
    private static final ZoneId SANTIAGO_ZONE = ZoneId.of("America/Santiago");

    private final ComunaInfoRepository comunaRepository;
    private final ComunaRiskSnapshotRepository snapshotRepository;
    private final TerritoryWeatherObservationRepository weatherRepository;
    private final HeatAlertEventRepository heatAlertRepository;
    private final CitizenReportRepository citizenReportRepository;
    private final OpenWeatherFwiService fwiService;
    private final OpenEoIndicatorObservationRepository openEoObsRepository;
    private final NotificationService notificationService;
    private final OpenEoServiceClient openEoServiceClient;

    public ComunaRiskServiceImpl(
        ComunaInfoRepository comunaRepository,
        ComunaRiskSnapshotRepository snapshotRepository,
        TerritoryWeatherObservationRepository weatherRepository,
        HeatAlertEventRepository heatAlertRepository,
        CitizenReportRepository citizenReportRepository,
        OpenWeatherFwiService fwiService,
        OpenEoIndicatorObservationRepository openEoObsRepository,
        NotificationService notificationService,
        OpenEoServiceClient openEoServiceClient
    ) {
        this.comunaRepository = comunaRepository;
        this.snapshotRepository = snapshotRepository;
        this.weatherRepository = weatherRepository;
        this.heatAlertRepository = heatAlertRepository;
        this.citizenReportRepository = citizenReportRepository;
        this.fwiService = fwiService;
        this.openEoObsRepository = openEoObsRepository;
        this.notificationService = notificationService;
        this.openEoServiceClient = openEoServiceClient;
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

        // --- FIRMS (atribuido via comunaId persistido en sync, ya no por centroide) ---
        List<HeatAlertEvent> comunaFocos = heatAlertRepository.findByComunaIdAndFechaEventoAfter(comunaId, firms48h)
            .stream()
            .filter(e -> "NASA_FIRMS".equals(e.getFuente()))
            .filter(e -> e.getFirmsConfidence() != null && !"l".equals(e.getFirmsConfidence()))
            .filter(e -> e.getLatitud() != null && e.getLongitud() != null)
            .collect(Collectors.toList());
        int firmsCount = comunaFocos.size();
        boolean hasTodayFoco = comunaFocos.stream().anyMatch(e -> isToday(e.getFechaEvento()));
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

        // --- Modo ENHANCED: activar si score >= umbral y hay observacion Copernicus fresca ---
        String mode = "STANDARD";
        String qualityFlag = null;
        double scoreComposite = scoreStandard;
        double cFwi = fwiNorm * W_FWI_STD;
        double cFirms = firmsNorm * W_FIRMS_STD;
        double cReports = reportsNorm * W_REPORTS_STD;
        Double cNdmi = null;
        Double cNdvi = null;
        Double ndmiRawVal = null;
        Double ndviRawVal = null;
        String openeoObsId = null;

        {
            LocalDateTime cutoff = now.minusDays(COPERNICUS_STALENESS_DAYS);
            Optional<OpenEoIndicatorObservation> ndmiObs = openEoObsRepository
                .findTopByRegionIdAndIndicatorOrderByObservedAtDesc(comuna.getRegionId(), IndicatorType.NDMI);
            Optional<OpenEoIndicatorObservation> ndviObs = openEoObsRepository
                .findTopByRegionIdAndIndicatorOrderByObservedAtDesc(comuna.getRegionId(), IndicatorType.NDVI);

            boolean ndmiOk = ndmiObs.isPresent() && ndmiObs.get().getValue() != null
                && ndmiObs.get().getObservedAt() != null && ndmiObs.get().getObservedAt().isAfter(cutoff);
            boolean ndviOk = ndviObs.isPresent() && ndviObs.get().getValue() != null
                && ndviObs.get().getObservedAt() != null && ndviObs.get().getObservedAt().isAfter(cutoff);

            if (ndmiOk && ndviOk) {
                double ndmi = ndmiObs.get().getValue();
                double ndvi = ndviObs.get().getValue();
                // NDMI: cuanto mas seco (bajo) mayor riesgo — se invierte
                double ndmiNorm = 1.0 - normalize(ndmi, NDMI_DRY, NDMI_WET);
                // NDVI: mayor densidad = mayor combustible disponible
                double ndviNorm = normalize(ndvi, NDVI_MIN, NDVI_MAX);
                double scoreEnhanced = fwiNorm * W_FWI_ENH
                    + ndmiNorm * W_NDMI_ENH
                    + firmsNorm * W_FIRMS_ENH
                    + ndviNorm * W_NDVI_ENH
                    + reportsNorm * W_REPORTS_ENH;
                mode = "ENHANCED";
                scoreComposite = clamp(scoreEnhanced);
                cFwi = fwiNorm * W_FWI_ENH;
                cFirms = firmsNorm * W_FIRMS_ENH;
                cReports = reportsNorm * W_REPORTS_ENH;
                cNdmi = round4(ndmiNorm * W_NDMI_ENH);
                cNdvi = round4(ndviNorm * W_NDVI_ENH);
                ndmiRawVal = ndmi;
                ndviRawVal = ndvi;
                openeoObsId = ndmiObs.get().getId();
            } else {
                qualityFlag = "COPERNICUS_UNAVAILABLE";
            }
        }

        String alertLevel = resolveAlertLevel(scoreComposite, fwiRaw, firmsCount, firmsFrpMean, hasTodayFoco);

        // Nivel anterior para deduplicacion de notificaciones (antes de guardar el nuevo snapshot)
        String previousAlertLevel = snapshotRepository.findTopByComunaIdOrderByComputedAtDesc(comunaId)
            .map(ComunaRiskSnapshot::getAlertLevel)
            .orElse("NORMAL");

        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();
        snapshot.setComunaId(comunaId);
        snapshot.setRegionId(comuna.getRegionId());
        snapshot.setNombreComuna(comuna.getNombre());
        snapshot.setComputedAt(now);
        snapshot.setScoreComposite(round4(scoreComposite));
        snapshot.setAlertLevel(alertLevel);
        snapshot.setMode(mode);
        snapshot.setQualityFlag(qualityFlag);
        snapshot.setComponentFwi(round4(cFwi));
        snapshot.setComponentFirms(round4(cFirms));
        snapshot.setComponentReports(round4(cReports));
        snapshot.setComponentNdmi(cNdmi);
        snapshot.setComponentNdvi(cNdvi);
        snapshot.setComponentLoss(null);
        snapshot.setNdmiRaw(ndmiRawVal);
        snapshot.setNdviRaw(ndviRawVal);
        snapshot.setOpeneoObservationId(openeoObsId);
        snapshot.setFwiRaw(fwiRaw);
        snapshot.setFirmsCount(firmsCount);
        snapshot.setFirmsFrpMean(round4(firmsFrpMean));
        snapshot.setReportsCount((int) reportsCount);

        snapshotRepository.save(snapshot);
        notificationService.triggerComunaRiskAlert(snapshot, previousAlertLevel);
        return snapshot;
    }

    @Override
    public List<ComunaRiskSnapshot> getSnapshotHistory(String gadmGid, int days) {
        LocalDateTime after = LocalDateTime.now().minusDays(days);
        return snapshotRepository.findByComunaIdAndComputedAtAfterOrderByComputedAtDesc(gadmGid, after);
    }

    @Override
    public Map<String, ComunaRiskSnapshot> getLatestSnapshotsByRegion(String regionId) {
        // Single query sorted desc; first occurrence of each comunaId is the latest snapshot.
        Map<String, ComunaRiskSnapshot> result = new LinkedHashMap<>();
        for (ComunaRiskSnapshot snap : snapshotRepository.findByRegionIdOrderByComputedAtDesc(regionId)) {
            result.putIfAbsent(snap.getComunaId(), snap);
        }
        return result;
    }

    @Override
    public List<ComunaRiskSnapshot> getLatestSnapshotsListByRegion(String regionId) {
        return getLatestSnapshotsByRegion(regionId).values().stream().toList();
    }

    @Override
    public ComunaRiskSnapshot syncCopernicusAndRecompute(String comunaId) {
        ComunaInfo comuna = comunaRepository.findById(comunaId)
            .orElseThrow(() -> new IllegalArgumentException("Comuna no encontrada: " + comunaId));

        if (comuna.getCenterLat() == null || comuna.getCenterLon() == null) {
            LOGGER.warn("copernicus_comuna_sync status=skip reason=no_centroid comunaId={}", comunaId);
            return recomputeByComuna(comunaId);
        }

        double west = comuna.getCenterLon() - COPERNICUS_COMUNA_PAD_DEG;
        double south = comuna.getCenterLat() - COPERNICUS_COMUNA_PAD_DEG;
        double east = comuna.getCenterLon() + COPERNICUS_COMUNA_PAD_DEG;
        double north = comuna.getCenterLat() + COPERNICUS_COMUNA_PAD_DEG;
        LocalDate today = LocalDate.now();
        String periodStart = today.minusDays(30).toString();
        String periodEnd = today.toString();

        for (IndicatorType indicator : List.of(IndicatorType.NDVI, IndicatorType.NDMI)) {
            try {
                OpenEoIndicatorLatestRequest req = new OpenEoIndicatorLatestRequest();
                req.setRegionId(comunaId);
                req.setAoi(new OpenEoIndicatorLatestRequest.AoiRequest("bbox", List.of(west, south, east, north)));
                req.setPeriodStart(periodStart);
                req.setPeriodEnd(periodEnd);

                OpenEoIndicatorLatestResponse resp = openEoServiceClient.fetchLatestIndicator(indicator, req);

                if (resp.getValue() != null && comuna.getRegionId() != null) {
                    OpenEoIndicatorObservation obs = new OpenEoIndicatorObservation();
                    obs.setRegionId(comuna.getRegionId());
                    obs.setIndicator(indicator);
                    obs.setObservedAt(resp.getMeasuredAt() != null ? resp.getMeasuredAt() : LocalDateTime.now());
                    obs.setValue(resp.getValue());
                    obs.setSource("openeo-service-manual");
                    obs.setQuality(resp.getQuality() != null ? resp.getQuality() : "measured");
                    obs.setIngestedAt(LocalDateTime.now());
                    openEoObsRepository.save(obs);
                    LOGGER.info("copernicus_comuna_sync status=ok comunaId={} indicator={} value={}", comunaId, indicator, resp.getValue());
                }
            } catch (Exception ex) {
                LOGGER.warn("copernicus_comuna_sync status=error comunaId={} indicator={} error={}", comunaId, indicator, ex.getMessage());
            }
        }

        return recomputeByComuna(comunaId);
    }

    private String resolveAlertLevel(double score, Double fwiRaw, int firmsCount, double firmsFrpMean, boolean hasTodayFirms) {
        // A FIRMS detection from TODAY is an active fire happening right now — always CRITICO.
        if (hasTodayFirms || (fwiRaw != null && fwiRaw >= FWI_CRITICO) || score >= SCORE_CRITICO) {
            return "CRITICO";
        }
        // Detections that are only "recent" (not today) still escalate to CRITICO if they
        // cluster (FIRMS_COUNT_CRITICO) or are individually intense (FIRMS_FRP_CRITICO) —
        // otherwise they just feed the weighted score via firmsNorm.
        if (firmsCount >= FIRMS_COUNT_CRITICO || firmsFrpMean >= FIRMS_FRP_CRITICO) {
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

    private boolean isToday(LocalDateTime fechaEventoUtc) {
        if (fechaEventoUtc == null) {
            return false;
        }
        LocalDate eventDate = fechaEventoUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(SANTIAGO_ZONE).toLocalDate();
        LocalDate todaySantiago = LocalDateTime.now(SANTIAGO_ZONE).toLocalDate();
        return eventDate.equals(todaySantiago);
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
