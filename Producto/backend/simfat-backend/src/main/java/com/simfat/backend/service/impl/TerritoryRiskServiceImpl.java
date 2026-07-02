package com.simfat.backend.service.impl;

import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.model.Region;
import com.simfat.backend.model.TerritoryRiskSnapshot;
import com.simfat.backend.model.TerritoryWeatherObservation;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryRiskSnapshotRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.TerritoryRiskService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TerritoryRiskServiceImpl implements TerritoryRiskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerritoryRiskServiceImpl.class);

    // WLC weights (SDD v1 — sujetos a calibracion con datos historicos)
    private static final double W_FWI = 0.38;
    private static final double W_NDMI = 0.22;
    private static final double W_FIRMS = 0.18;
    private static final double W_NDVI = 0.08;
    private static final double W_REPORTS = 0.04;

    // Rangos de normalizacion min-max de arranque (CFFDRS + literatura)
    private static final double FWI_MAX = 50.0;
    private static final double NDMI_DRY = -0.4;
    private static final double NDMI_WET = 0.4;
    private static final double NDVI_MIN = 0.1;
    private static final double NDVI_MAX = 0.8;
    private static final double REPORTS_MAX = 5.0;

    // Umbrales de alerta (SDD v1)
    private static final double SCORE_PREVENTIVO = 0.50;
    private static final double SCORE_ALTO = 0.70;
    private static final double SCORE_CRITICO = 0.85;
    private static final double FWI_PREVENTIVO = 20.0;
    private static final double FWI_ALTO = 30.0;
    private static final double FWI_CRITICO = 45.0;
    // Matches the frontend's "today" vs "recent" FIRMS bucket (TerritoryMapPanel.jsx
    // santiagoDateKey) so the alert override and the map legend agree.
    private static final ZoneId SANTIAGO_ZONE = ZoneId.of("America/Santiago");

    private final OpenEoIndicatorObservationRepository observationRepository;
    private final TerritoryWeatherObservationRepository weatherRepository;
    private final CitizenReportRepository citizenReportRepository;
    private final TerritoryRiskSnapshotRepository snapshotRepository;
    private final RegionRepository regionRepository;
    private final FirmsAttributionRouter firmsAttributionRouter;

    public TerritoryRiskServiceImpl(
        RegionRepository regionRepository,
        OpenEoIndicatorObservationRepository observationRepository,
        TerritoryWeatherObservationRepository weatherRepository,
        CitizenReportRepository citizenReportRepository,
        TerritoryRiskSnapshotRepository snapshotRepository,
        FirmsAttributionRouter firmsAttributionRouter
    ) {
        this.regionRepository = regionRepository;
        this.observationRepository = observationRepository;
        this.weatherRepository = weatherRepository;
        this.citizenReportRepository = citizenReportRepository;
        this.snapshotRepository = snapshotRepository;
        this.firmsAttributionRouter = firmsAttributionRouter;
    }

    @Scheduled(cron = "${territory.risk.recompute.cron:0 0 1 * * *}")
    @Override
    public void recomputeRiskForAllRegions() {
        List<Region> regions = regionRepository.findAll();
        for (Region region : regions) {
            try {
                recomputeRiskByRegion(region.getId());
            } catch (Exception ex) {
                LOGGER.warn("risk_recompute status=error regionId={} error={}", region.getId(), ex.getMessage());
            }
        }
    }

    @Override
    public TerritoryRiskSnapshot recomputeRiskByRegion(String regionId) {
        LocalDateTime now = LocalDateTime.now();
        int availableComponents = 0;

        // FWI
        Double fwiRaw = null;
        double fwiNorm = 0.0;
        Optional<TerritoryWeatherObservation> latestWeather = weatherRepository.findTopByRegionIdOrderByObservedAtDesc(regionId);
        if (latestWeather.isPresent() && latestWeather.get().getFwi() != null) {
            fwiRaw = latestWeather.get().getFwi();
            fwiNorm = normalize(fwiRaw, 0.0, FWI_MAX);
            availableComponents++;
        }

        // NDMI
        Double ndmiRaw = null;
        double ndmiNorm = 0.0;
        Optional<OpenEoIndicatorObservation> latestNdmi = observationRepository
            .findTopByRegionIdAndIndicatorOrderByIngestedAtDesc(regionId, IndicatorType.NDMI);
        if (latestNdmi.isPresent() && latestNdmi.get().getValue() != null) {
            ndmiRaw = latestNdmi.get().getValue();
            // Invertido: más seco = mayor riesgo
            ndmiNorm = normalize(NDMI_WET - ndmiRaw, 0.0, NDMI_WET - NDMI_DRY);
            availableComponents++;
        }

        // NDVI
        Double ndviRaw = null;
        double ndviNorm = 0.0;
        Optional<OpenEoIndicatorObservation> latestNdvi = observationRepository
            .findTopByRegionIdAndIndicatorOrderByIngestedAtDesc(regionId, IndicatorType.NDVI);
        if (latestNdvi.isPresent() && latestNdvi.get().getValue() != null) {
            ndviRaw = latestNdvi.get().getValue();
            // Invertido: menos vegetacion = mas riesgo
            ndviNorm = normalize(NDVI_MAX - ndviRaw, 0.0, NDVI_MAX - NDVI_MIN);
            availableComponents++;
        }

        // FIRMS (focos activos ultimas 48h) — Decision 6 coverage-gap fallback, routed via
        // the shared FirmsAttributionRouter (FIX 1/2/6, post-review): per-region routing
        // now SPLITS coverage (covered comunas read geometrically, uncovered comunas fall
        // back to centroid, both summed) instead of an all-or-nothing region decision, and
        // the fallback's candidate pool is never pre-filtered by persisted regionId.
        LocalDateTime firms48hAgo = now.minusHours(48);
        List<HeatAlertEvent> firmsEvents = firmsAttributionRouter.resolveForRegion(regionId, firms48hAgo);

        int firmsCountFiltered = firmsEvents.size();
        boolean hasTodayFirms = firmsEvents.stream().anyMatch(e -> isToday(e.getFechaEvento()));
        double firmsFrpMean = firmsEvents.stream()
            .filter(e -> e.getFirmsFrp() != null)
            .mapToDouble(HeatAlertEvent::getFirmsFrp)
            .average()
            .orElse(0.0);

        double firmsNorm = 0.0;
        if (firmsCountFiltered > 0) {
            double countNorm = normalize(firmsCountFiltered, 0, FirmsScoringConstants.FIRMS_MAX_COUNT);
            double frpNorm = normalize(firmsFrpMean, 0, FirmsScoringConstants.FIRMS_MAX_FRP);
            firmsNorm = countNorm * 0.6 + frpNorm * 0.4;
            availableComponents++;
        }

        // Community reports (ultimos 7 dias)
        LocalDateTime reports7dAgo = now.minusDays(7);
        long reportsCount = citizenReportRepository.findByRegionId(regionId).stream()
            .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(reports7dAgo))
            .count();

        double reportsNorm = normalize(reportsCount, 0, REPORTS_MAX);
        if (reportsCount > 0) {
            availableComponents++;
        }

        // Score compuesto WLC
        double score = fwiNorm * W_FWI
            + ndmiNorm * W_NDMI
            + firmsNorm * W_FIRMS
            + ndviNorm * W_NDVI
            + reportsNorm * W_REPORTS;

        score = Math.min(1.0, Math.max(0.0, score));

        String qualityFlag = availableComponents >= 4 ? "OK" : availableComponents >= 2 ? "PARTIAL" : "MINIMAL";
        AlertResolution alert = resolveAlert(score, fwiRaw, firmsCountFiltered, firmsFrpMean, hasTodayFirms);

        TerritoryRiskSnapshot snapshot = new TerritoryRiskSnapshot();
        snapshot.setRegionId(regionId);
        snapshot.setComputedAt(now);
        snapshot.setScoreComposite(round4(score));
        snapshot.setAlertLevel(alert.level());
        snapshot.setAlertDriver(alert.driver());
        snapshot.setQualityFlag(qualityFlag);
        snapshot.setComponentFwi(round4(fwiNorm * W_FWI));
        snapshot.setComponentNdmi(round4(ndmiNorm * W_NDMI));
        snapshot.setComponentFirms(round4(firmsNorm * W_FIRMS));
        snapshot.setComponentLoss(null);
        snapshot.setComponentNdvi(round4(ndviNorm * W_NDVI));
        snapshot.setComponentReports(round4(reportsNorm * W_REPORTS));
        snapshot.setFwiRaw(fwiRaw);
        snapshot.setNdmiRaw(ndmiRaw);
        snapshot.setNdviRaw(ndviRaw);
        snapshot.setFirmsCount(firmsCountFiltered);
        snapshot.setFirmsFrpMean(round4(firmsFrpMean));
        snapshot.setLossRateRaw(null);
        snapshot.setReportsCount((int) reportsCount);

        snapshotRepository.save(snapshot);

        LOGGER.info(
            "risk_recompute regionId={} score={} alertLevel={} alertDriver={} quality={} components={}",
            regionId, score, alert.level(), alert.driver(), qualityFlag, availableComponents
        );

        return snapshot;
    }

    @Override
    public TerritoryRiskSnapshot getLatestSnapshot(String regionId) {
        return snapshotRepository.findTopByRegionIdOrderByComputedAtDesc(regionId).orElse(null);
    }

    private record AlertResolution(String level, String driver) {}

    // Returns both the resolved alert level and the primary driver that triggered it.
    // Conditions are evaluated in priority order; the first match wins and becomes
    // the single named driver stored in the snapshot for PDF/audit reporting.
    private AlertResolution resolveAlert(double score, Double fwiRaw, int firmsHighConfidenceCount, double firmsFrpMean, boolean hasTodayFirms) {
        // CRITICO — priority 1: active fire today (most urgent signal; an ongoing event)
        if (hasTodayFirms) {
            return new AlertResolution("CRITICO", "FIRMS_HOY");
        }
        // CRITICO — priority 2: FWI in extreme range (meteorological driver)
        if (fwiRaw != null && fwiRaw >= FWI_CRITICO) {
            return new AlertResolution("CRITICO", "FWI");
        }
        // CRITICO — priority 3: composite score threshold
        if (score >= SCORE_CRITICO) {
            return new AlertResolution("CRITICO", "SCORE_WLC");
        }
        // CRITICO — priority 4: recent FIRMS cluster (not today, but dense or intense enough)
        if (firmsHighConfidenceCount >= FirmsScoringConstants.FIRMS_COUNT_CRITICO) {
            return new AlertResolution("CRITICO", "FIRMS_COUNT");
        }
        if (firmsFrpMean >= FirmsScoringConstants.FIRMS_FRP_CRITICO) {
            return new AlertResolution("CRITICO", "FIRMS_FRP");
        }
        if (fwiRaw != null && fwiRaw >= FWI_ALTO) {
            return new AlertResolution("ALTO", "FWI");
        }
        if (score >= SCORE_ALTO) {
            return new AlertResolution("ALTO", "SCORE_WLC");
        }
        if (fwiRaw != null && fwiRaw >= FWI_PREVENTIVO) {
            return new AlertResolution("PREVENTIVO", "FWI");
        }
        if (score >= SCORE_PREVENTIVO) {
            return new AlertResolution("PREVENTIVO", "SCORE_WLC");
        }
        return new AlertResolution("NORMAL", "SCORE_WLC");
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
        if (max <= min) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, (value - min) / (max - min)));
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
