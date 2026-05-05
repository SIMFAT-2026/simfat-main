package com.simfat.backend.service.impl;

import com.simfat.backend.dto.AlertsSummaryDTO;
import com.simfat.backend.dto.CriticalRegionDTO;
import com.simfat.backend.dto.DashboardSummaryDTO;
import com.simfat.backend.dto.LossTrendPointDTO;
import com.simfat.backend.model.AlertRule;
import com.simfat.backend.model.DashboardRegionSnapshot;
import com.simfat.backend.model.ForestLossRecord;
import com.simfat.backend.model.Region;
import com.simfat.backend.model.RiskLevel;
import com.simfat.backend.repository.DashboardRegionSnapshotRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.AlertRuleService;
import com.simfat.backend.service.DashboardService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final ForestLossRecordRepository forestLossRepository;
    private final HeatAlertEventRepository heatAlertRepository;
    private final RegionRepository regionRepository;
    private final DashboardRegionSnapshotRepository snapshotRepository;
    private final AlertRuleService alertRuleService;

    @Value("${app.alert.default-loss-threshold:1.0}")
    private Double defaultLossThreshold;

    @Value("${app.alert.default-heat-events-threshold:5}")
    private Integer defaultHeatEventsThreshold;

    public DashboardServiceImpl(
        ForestLossRecordRepository forestLossRepository,
        HeatAlertEventRepository heatAlertRepository,
        RegionRepository regionRepository,
        DashboardRegionSnapshotRepository snapshotRepository,
        AlertRuleService alertRuleService
    ) {
        this.forestLossRepository = forestLossRepository;
        this.heatAlertRepository = heatAlertRepository;
        this.regionRepository = regionRepository;
        this.snapshotRepository = snapshotRepository;
        this.alertRuleService = alertRuleService;
    }

    @Override
    public DashboardSummaryDTO getSummary() {
        List<ForestLossRecord> records = forestLossRepository.findAll();
        List<LossTrendPointDTO> trend = getLossTrend();

        DashboardSummaryDTO dto = new DashboardSummaryDTO();
        dto.setTotalHectareasPerdidas(records.stream()
            .map(ForestLossRecord::getHectareasPerdidas)
            .filter(value -> value != null)
            .reduce(0.0, Double::sum));
        dto.setRegionesCriticas(resolveCriticalRegionsCount());
        dto.setTotalAlertas(Math.toIntExact(heatAlertRepository.count()));
        dto.setAnioMayorPerdida(resolveYearWithHighestLoss(records));
        dto.setTendenciaGeneral(resolveGeneralTrend(trend));
        return dto;
    }

    @Override
    public List<CriticalRegionDTO> getCriticalRegions() {
        List<DashboardRegionSnapshot> snapshots = snapshotRepository.findAll();
        if (!snapshots.isEmpty()) {
            Map<String, String> regionNames = regionRepository.findAll().stream()
                .collect(Collectors.toMap(Region::getId, Region::getNombre));

            return snapshots.stream()
                .filter(snapshot -> Set.of("HIGH", "MEDIUM").contains(snapshot.getCriticality()))
                .map(snapshot -> {
                    CriticalRegionDTO dto = new CriticalRegionDTO();
                    dto.setRegionId(snapshot.getRegionId());
                    dto.setNombreRegion(regionNames.getOrDefault(snapshot.getRegionId(), snapshot.getRegionId()));
                    dto.setPorcentajePerdidaActual(snapshot.getForestLossCurrentPct());
                    dto.setEventosCalorRecientes(snapshot.getHeatAlerts7d());
                    dto.setEstadoCriticidad("HIGH".equals(snapshot.getCriticality()) ? "CRITICA" : "EN_RIESGO");
                    return dto;
                })
                .toList();
        }

        List<Region> regions = regionRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        return regions.stream()
            .map(region -> buildCriticalRegion(region, now))
            .filter(dto -> dto != null)
            .toList();
    }

    @Override
    public List<LossTrendPointDTO> getLossTrend() {
        Map<Integer, List<ForestLossRecord>> groupedByYear = forestLossRepository.findAllByOrderByAnioAsc().stream()
            .collect(Collectors.groupingBy(ForestLossRecord::getAnio));

        return groupedByYear.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                List<ForestLossRecord> records = entry.getValue();
                double totalHectareas = records.stream()
                    .map(ForestLossRecord::getHectareasPerdidas)
                    .filter(value -> value != null)
                    .reduce(0.0, Double::sum);
                double averagePercentage = records.stream()
                    .map(ForestLossRecord::getPorcentajePerdida)
                    .filter(value -> value != null)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

                LossTrendPointDTO point = new LossTrendPointDTO();
                point.setAnio(entry.getKey());
                point.setHectareasPerdidas(roundTwoDecimals(totalHectareas));
                point.setPorcentajePromedio(roundTwoDecimals(averagePercentage));
                return point;
            })
            .toList();
    }

    @Override
    public AlertsSummaryDTO getAlertsSummary() {
        AlertsSummaryDTO dto = new AlertsSummaryDTO();
        dto.setTotal(heatAlertRepository.count());
        dto.setBajo(valueOrZero(heatAlertRepository.countByNivelRiesgo(RiskLevel.BAJO)));
        dto.setMedio(valueOrZero(heatAlertRepository.countByNivelRiesgo(RiskLevel.MEDIO)));
        dto.setAlto(valueOrZero(heatAlertRepository.countByNivelRiesgo(RiskLevel.ALTO)));
        dto.setCritico(valueOrZero(heatAlertRepository.countByNivelRiesgo(RiskLevel.CRITICO)));
        return dto;
    }

    private CriticalRegionDTO buildCriticalRegion(Region region, LocalDateTime now) {
        List<ForestLossRecord> regionRecords = forestLossRepository.findByRegionId(region.getId());
        Double currentLoss = resolveCurrentLossPercentage(regionRecords);
        Long recentHeatEvents = valueOrZero(heatAlertRepository.countByRegionIdAndFechaEventoBetween(
            region.getId(),
            now.minusDays(7),
            now
        ));

        List<AlertRule> activeRules = alertRuleService.getActiveRulesForRegion(region.getId());
        boolean exceedsLoss = exceedsLossThreshold(currentLoss, activeRules);
        boolean exceedsHeat = exceedsHeatThreshold(recentHeatEvents, activeRules);

        if (!exceedsLoss && !exceedsHeat) {
            return null;
        }

        CriticalRegionDTO dto = new CriticalRegionDTO();
        dto.setRegionId(region.getId());
        dto.setNombreRegion(region.getNombre());
        dto.setPorcentajePerdidaActual(roundTwoDecimals(currentLoss));
        dto.setEventosCalorRecientes(recentHeatEvents);
        dto.setEstadoCriticidad(exceedsLoss && exceedsHeat ? "CRITICA" : "EN_RIESGO");
        return dto;
    }

    private boolean exceedsLossThreshold(Double currentLoss, List<AlertRule> activeRules) {
        if (currentLoss == null) {
            return false;
        }
        double threshold = activeRules.stream()
            .map(AlertRule::getUmbralPorcentajePerdida)
            .filter(value -> value != null && value > 0)
            .min(Double::compareTo)
            .orElse(defaultLossThreshold);
        return currentLoss >= threshold;
    }

    private boolean exceedsHeatThreshold(Long recentHeatEvents, List<AlertRule> activeRules) {
        int threshold = activeRules.stream()
            .map(AlertRule::getUmbralEventosCalor)
            .filter(value -> value != null && value > 0)
            .min(Integer::compareTo)
            .orElse(defaultHeatEventsThreshold);
        return recentHeatEvents >= threshold;
    }

    private Double resolveCurrentLossPercentage(List<ForestLossRecord> regionRecords) {
        if (regionRecords.isEmpty()) {
            return 0.0;
        }

        Integer latestYear = regionRecords.stream()
            .map(ForestLossRecord::getAnio)
            .max(Integer::compareTo)
            .orElse(null);

        if (latestYear == null) {
            return 0.0;
        }

        return regionRecords.stream()
            .filter(record -> latestYear.equals(record.getAnio()))
            .map(ForestLossRecord::getPorcentajePerdida)
            .filter(value -> value != null)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }

    private Integer resolveYearWithHighestLoss(List<ForestLossRecord> records) {
        return records.stream()
            .collect(Collectors.groupingBy(ForestLossRecord::getAnio,
                Collectors.summingDouble(record -> record.getHectareasPerdidas() != null ? record.getHectareasPerdidas() : 0.0)))
            .entrySet()
            .stream()
            .max(Comparator.comparingDouble(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private int resolveCriticalRegionsCount() {
        List<DashboardRegionSnapshot> snapshots = snapshotRepository.findAll();
        if (snapshots.isEmpty()) {
            return getCriticalRegions().size();
        }
        return (int) snapshots.stream()
            .filter(snapshot -> Set.of("HIGH", "MEDIUM").contains(snapshot.getCriticality()))
            .count();
    }

    private String resolveGeneralTrend(List<LossTrendPointDTO> trend) {
        if (trend.size() < 2) {
            return "SIN_DATOS";
        }

        LossTrendPointDTO previous = trend.get(trend.size() - 2);
        LossTrendPointDTO latest = trend.get(trend.size() - 1);

        if (latest.getHectareasPerdidas() > previous.getHectareasPerdidas()) {
            return "ALZA";
        }
        if (latest.getHectareasPerdidas() < previous.getHectareasPerdidas()) {
            return "BAJA";
        }
        return "ESTABLE";
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
