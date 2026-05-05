package com.simfat.backend.service.impl;

import com.simfat.backend.dto.DataFreshnessDTO;
import com.simfat.backend.dto.DataFreshnessStatus;
import com.simfat.backend.dto.IndicatorLatestDTO;
import com.simfat.backend.dto.IndicatorMapPointDTO;
import com.simfat.backend.dto.IndicatorMapResponseDTO;
import com.simfat.backend.dto.IndicatorSeriesDTO;
import com.simfat.backend.dto.IndicatorSeriesPointDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.model.DashboardRegionSnapshot;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.repository.DashboardRegionSnapshotRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.service.DashboardIndicatorService;
import com.simfat.backend.service.DashboardSnapshotService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class DashboardIndicatorServiceImpl implements DashboardIndicatorService {

    private final OpenEoIndicatorObservationRepository observationRepository;
    private final DashboardRegionSnapshotRepository snapshotRepository;
    private final DashboardSnapshotService snapshotService;
    private final DashboardQueryCache dashboardQueryCache;

    public DashboardIndicatorServiceImpl(
        OpenEoIndicatorObservationRepository observationRepository,
        DashboardRegionSnapshotRepository snapshotRepository,
        DashboardSnapshotService snapshotService,
        DashboardQueryCache dashboardQueryCache
    ) {
        this.observationRepository = observationRepository;
        this.snapshotRepository = snapshotRepository;
        this.snapshotService = snapshotService;
        this.dashboardQueryCache = dashboardQueryCache;
    }

    @Override
    public IndicatorLatestDTO getLatest(String regionId, IndicatorType indicator) {
        validateRegionId(regionId);
        String key = "latest|" + regionId + "|" + indicator;
        return dashboardQueryCache.getOrLoad(key, Duration.ofSeconds(45), () -> {
            OpenEoIndicatorObservation latest = observationRepository
                .findTopByRegionIdAndIndicatorOrderByObservedAtDesc(regionId, indicator)
                .orElse(null);

            IndicatorLatestDTO dto = new IndicatorLatestDTO();
            dto.setRegionId(regionId);
            dto.setIndicator(indicator.name());
            if (latest != null) {
                dto.setObservedAt(latest.getObservedAt());
                dto.setValue(latest.getValue());
                dto.setUnit(latest.getUnit());
                dto.setQuality(latest.getQuality());
                dto.setSource(latest.getSource());
            }
            dto.setCached(true);
            return dto;
        });
    }

    @Override
    public IndicatorSeriesDTO getSeries(String regionId, IndicatorType indicator, String from, String to, String granularity) {
        validateRegionId(regionId);
        LocalDate toDate = parseDateOrDefault(to, LocalDate.now(), "to");
        LocalDate fromDate = parseDateOrDefault(from, toDate.minusDays(29), "from");
        String normalizedGranularity = normalizeGranularity(granularity);
        validateRange(fromDate, toDate);

        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = LocalDateTime.of(toDate, LocalTime.MAX);
        List<OpenEoIndicatorObservation> observations = observationRepository
            .findByRegionIdAndIndicatorAndObservedAtBetweenOrderByObservedAtAsc(regionId, indicator, fromDateTime, toDateTime);

        Map<LocalDateTime, List<OpenEoIndicatorObservation>> grouped = new LinkedHashMap<>();
        for (OpenEoIndicatorObservation observation : observations) {
            LocalDateTime bucket = bucketStart(observation.getObservedAt(), normalizedGranularity);
            grouped.computeIfAbsent(bucket, key -> new ArrayList<>()).add(observation);
        }

        List<IndicatorSeriesPointDTO> points = grouped.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                double average = entry.getValue().stream()
                    .map(OpenEoIndicatorObservation::getValue)
                    .filter(value -> value != null)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

                IndicatorSeriesPointDTO point = new IndicatorSeriesPointDTO();
                point.setTs(entry.getKey());
                point.setValue(roundTwoDecimals(average));
                return point;
            })
            .toList();

        IndicatorSeriesDTO dto = new IndicatorSeriesDTO();
        dto.setRegionId(regionId);
        dto.setIndicator(indicator.name());
        dto.setFrom(fromDate);
        dto.setTo(toDate);
        dto.setGranularity(normalizedGranularity);
        dto.setPoints(points);
        return dto;
    }

    @Override
    public IndicatorMapResponseDTO getMap(IndicatorType indicator, String from, String to, Integer limit) {
        int resolvedLimit = normalizeLimit(limit);
        LocalDate fromDate = parseDateRequired(from, "from");
        LocalDate toDate = parseDateRequired(to, "to");
        validateRange(fromDate, toDate);

        String key = "map|" + indicator + "|" + fromDate + "|" + toDate + "|" + resolvedLimit;
        return dashboardQueryCache.getOrLoad(key, Duration.ofSeconds(90), () -> {
            List<OpenEoIndicatorObservation> observations = observationRepository.findByIndicatorAndObservedAtBetweenOrderByObservedAtDesc(
                indicator,
                fromDate.atStartOfDay(),
                LocalDateTime.of(toDate, LocalTime.MAX),
                PageRequest.of(0, resolvedLimit)
            );

            List<IndicatorMapPointDTO> items = observations.stream()
                .map(observation -> {
                    IndicatorMapPointDTO point = new IndicatorMapPointDTO();
                    point.setRegionId(observation.getRegionId());
                    point.setObservedAt(observation.getObservedAt());
                    point.setValue(observation.getValue());
                    point.setAoi(observation.getAoi());
                    point.setQuality(observation.getQuality());
                    return point;
                })
                .toList();

            IndicatorMapResponseDTO dto = new IndicatorMapResponseDTO();
            dto.setIndicator(indicator.name());
            dto.setFrom(fromDate);
            dto.setTo(toDate);
            dto.setLimit(resolvedLimit);
            dto.setItems(items);
            return dto;
        });
    }

    @Override
    public DataFreshnessDTO getDataFreshness(String regionId) {
        validateRegionId(regionId);
        String key = "freshness|" + regionId;
        return dashboardQueryCache.getOrLoad(key, Duration.ofSeconds(30), () -> {
            DashboardRegionSnapshot snapshot = snapshotRepository.findByRegionId(regionId)
                .orElseGet(() -> snapshotService.recomputeSnapshot(regionId));

            DataFreshnessDTO dto = new DataFreshnessDTO();
            dto.setRegionId(regionId);
            Long ageSeconds = snapshot.getDataFreshnessSeconds();
            dto.setAgeSeconds(ageSeconds);
            dto.setDataFreshnessSeconds(ageSeconds);
            dto.setComputedAt(snapshot.getComputedAt());
            dto.setLastUpdate(resolveLastUpdate(snapshot));

            DataFreshnessStatus status = resolveStatus(ageSeconds);
            dto.setStatus(status);
            dto.setStale(DataFreshnessStatus.STALE.equals(status) || DataFreshnessStatus.EMPTY.equals(status));
            return dto;
        });
    }

    private void validateRegionId(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            throw new BadRequestException("regionId es obligatorio");
        }
    }

    private LocalDate parseDateOrDefault(String value, LocalDate defaultValue, String fieldName) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw new BadRequestException(fieldName + " invalido. Usa formato YYYY-MM-DD");
        }
    }

    private LocalDate parseDateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " es obligatorio con formato YYYY-MM-DD");
        }
        return parseDateOrDefault(value, LocalDate.now(), fieldName);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("El rango de fechas es invalido: from no puede ser mayor a to");
        }
    }

    private int normalizeLimit(Integer requestedLimit) {
        int value = requestedLimit == null ? 200 : requestedLimit;
        if (value <= 0) {
            throw new BadRequestException("limit debe ser mayor a 0");
        }
        return Math.min(value, 500);
    }

    private String normalizeGranularity(String granularity) {
        if (granularity == null || granularity.isBlank()) {
            return "day";
        }
        String normalized = granularity.trim().toLowerCase();
        if (!"day".equals(normalized) && !"week".equals(normalized) && !"month".equals(normalized)) {
            throw new BadRequestException("granularity invalida. Valores permitidos: day, week, month");
        }
        return normalized;
    }

    private LocalDateTime bucketStart(LocalDateTime dateTime, String granularity) {
        if ("week".equals(granularity)) {
            LocalDate date = dateTime.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return date.atStartOfDay();
        }
        if ("month".equals(granularity)) {
            LocalDate date = dateTime.toLocalDate().withDayOfMonth(1);
            return date.atStartOfDay();
        }
        return dateTime.toLocalDate().atStartOfDay();
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private DataFreshnessStatus resolveStatus(Long ageSeconds) {
        if (ageSeconds == null) {
            return DataFreshnessStatus.EMPTY;
        }
        if (ageSeconds <= 3_600) {
            return DataFreshnessStatus.FRESH;
        }
        return DataFreshnessStatus.STALE;
    }

    private LocalDateTime resolveLastUpdate(DashboardRegionSnapshot snapshot) {
        if (snapshot.getComputedAt() == null || snapshot.getDataFreshnessSeconds() == null) {
            return null;
        }
        return snapshot.getComputedAt().minusSeconds(snapshot.getDataFreshnessSeconds());
    }
}
