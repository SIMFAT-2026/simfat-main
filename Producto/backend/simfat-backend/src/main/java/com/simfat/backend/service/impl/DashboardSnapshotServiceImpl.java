package com.simfat.backend.service.impl;

import com.simfat.backend.model.DashboardRegionSnapshot;
import com.simfat.backend.model.ForestLossRecord;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.repository.DashboardRegionSnapshotRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.service.DashboardSnapshotService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DashboardSnapshotServiceImpl implements DashboardSnapshotService {

    private final OpenEoIndicatorObservationRepository observationRepository;
    private final DashboardRegionSnapshotRepository snapshotRepository;
    private final HeatAlertEventRepository heatAlertRepository;
    private final ForestLossRecordRepository forestLossRepository;

    public DashboardSnapshotServiceImpl(
        OpenEoIndicatorObservationRepository observationRepository,
        DashboardRegionSnapshotRepository snapshotRepository,
        HeatAlertEventRepository heatAlertRepository,
        ForestLossRecordRepository forestLossRepository
    ) {
        this.observationRepository = observationRepository;
        this.snapshotRepository = snapshotRepository;
        this.heatAlertRepository = heatAlertRepository;
        this.forestLossRepository = forestLossRepository;
    }

    @Override
    public DashboardRegionSnapshot recomputeSnapshot(String regionId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from30d = now.minusDays(30);

        OpenEoIndicatorObservation latestNdvi = observationRepository
            .findTopByRegionIdAndIndicatorOrderByObservedAtDesc(regionId, IndicatorType.NDVI)
            .orElse(null);
        OpenEoIndicatorObservation latestNdmi = observationRepository
            .findTopByRegionIdAndIndicatorOrderByObservedAtDesc(regionId, IndicatorType.NDMI)
            .orElse(null);

        List<OpenEoIndicatorObservation> ndviSeries = observationRepository
            .findByRegionIdAndIndicatorAndObservedAtBetweenOrderByObservedAtAsc(regionId, IndicatorType.NDVI, from30d, now);
        List<OpenEoIndicatorObservation> ndmiSeries = observationRepository
            .findByRegionIdAndIndicatorAndObservedAtBetweenOrderByObservedAtAsc(regionId, IndicatorType.NDMI, from30d, now);

        Long heatAlerts7d = heatAlertRepository.countByRegionIdAndFechaEventoBetween(regionId, now.minusDays(7), now);
        Double lossCurrentPct = resolveCurrentLossPercentage(regionId);
        Long freshnessSeconds = resolveFreshnessSeconds(now, latestNdvi, latestNdmi);

        DashboardRegionSnapshot snapshot = snapshotRepository.findByRegionId(regionId).orElseGet(DashboardRegionSnapshot::new);
        snapshot.setRegionId(regionId);
        snapshot.setLatestNdvi(latestNdvi != null ? latestNdvi.getValue() : null);
        snapshot.setLatestNdmi(latestNdmi != null ? latestNdmi.getValue() : null);
        snapshot.setNdviTrend30d(resolveTrend30d(ndviSeries));
        snapshot.setNdmiTrend30d(resolveTrend30d(ndmiSeries));
        snapshot.setHeatAlerts7d(heatAlerts7d != null ? heatAlerts7d : 0L);
        snapshot.setForestLossCurrentPct(lossCurrentPct);
        snapshot.setCriticality(resolveCriticality(snapshot));
        snapshot.setComputedAt(now);
        snapshot.setDataFreshnessSeconds(freshnessSeconds);
        return snapshotRepository.save(snapshot);
    }

    private Double resolveCurrentLossPercentage(String regionId) {
        List<ForestLossRecord> records = forestLossRepository.findByRegionId(regionId);
        if (records.isEmpty()) {
            return 0.0;
        }

        Integer latestYear = records.stream().map(ForestLossRecord::getAnio).max(Integer::compareTo).orElse(null);
        if (latestYear == null) {
            return 0.0;
        }

        double average = records.stream()
            .filter(record -> latestYear.equals(record.getAnio()))
            .map(ForestLossRecord::getPorcentajePerdida)
            .filter(value -> value != null)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);

        return roundTwoDecimals(average);
    }

    private Long resolveFreshnessSeconds(
        LocalDateTime now,
        OpenEoIndicatorObservation latestNdvi,
        OpenEoIndicatorObservation latestNdmi
    ) {
        LocalDateTime reference = null;
        if (latestNdvi != null) {
            reference = latestNdvi.getObservedAt();
        }
        if (latestNdmi != null && (reference == null || latestNdmi.getObservedAt().isAfter(reference))) {
            reference = latestNdmi.getObservedAt();
        }
        if (reference == null) {
            return null;
        }
        return ChronoUnit.SECONDS.between(reference, now);
    }

    private Double resolveTrend30d(List<OpenEoIndicatorObservation> series) {
        if (series.size() < 2) {
            return 0.0;
        }
        Double first = series.get(0).getValue();
        Double latest = series.get(series.size() - 1).getValue();
        if (first == null || latest == null) {
            return 0.0;
        }
        return roundTwoDecimals(latest - first);
    }

    private String resolveCriticality(DashboardRegionSnapshot snapshot) {
        boolean highByLoss = snapshot.getForestLossCurrentPct() != null && snapshot.getForestLossCurrentPct() >= 1.0;
        boolean highByHeat = snapshot.getHeatAlerts7d() != null && snapshot.getHeatAlerts7d() >= 5;
        boolean mediumByVegetation = (snapshot.getLatestNdvi() != null && snapshot.getLatestNdvi() < 0.35)
            || (snapshot.getLatestNdmi() != null && snapshot.getLatestNdmi() < 0.2);

        if (highByLoss || highByHeat) {
            return "HIGH";
        }
        if (mediumByVegetation) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
