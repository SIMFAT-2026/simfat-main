package com.simfat.backend.service.impl;

import com.simfat.backend.config.OpenEoProperties;
import com.simfat.backend.dto.SyncRunResponseDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.OpenEoClientException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.integration.openeo.OpenEoIndicatorLatestRequest;
import com.simfat.backend.integration.openeo.OpenEoIndicatorLatestResponse;
import com.simfat.backend.integration.openeo.OpenEoServiceClient;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.model.OpenEoJobRun;
import com.simfat.backend.model.Region;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.OpenEoJobRunRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.DashboardSnapshotService;
import com.simfat.backend.service.OpenEoSyncService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OpenEoSyncServiceImpl implements OpenEoSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenEoSyncServiceImpl.class);

    private static final String SOURCE = "openeo-service";
    private static final String SOURCE_FALLBACK = "simfat-fallback";
    private static final String STATUS_FINISHED = "finished";
    private static final String STATUS_ERROR = "error";
    private static final String STATUS_NO_DATA = "no_data";
    private static final String STATUS_AOI_MISSING = "aoi_missing";

    private final OpenEoServiceClient openEoServiceClient;
    private final RegionRepository regionRepository;
    private final OpenEoJobRunRepository jobRunRepository;
    private final OpenEoIndicatorObservationRepository observationRepository;
    private final DashboardSnapshotService snapshotService;
    private final DashboardQueryCache dashboardQueryCache;
    private final OpenEoProperties openEoProperties;

    private final Map<String, BoundingBox> aoiBboxByRegionCode = new ConcurrentHashMap<>();
    private volatile String lastAoiMapRaw = null;

    private final java.util.Set<String> inFlightKeys = ConcurrentHashMap.newKeySet();
    private final AtomicLong totalSyncRuns = new AtomicLong(0);
    private final AtomicLong totalSyncSuccess = new AtomicLong(0);
    private final AtomicLong totalSyncErrors = new AtomicLong(0);

    public OpenEoSyncServiceImpl(
        OpenEoServiceClient openEoServiceClient,
        RegionRepository regionRepository,
        OpenEoJobRunRepository jobRunRepository,
        OpenEoIndicatorObservationRepository observationRepository,
        DashboardSnapshotService snapshotService,
        DashboardQueryCache dashboardQueryCache,
        OpenEoProperties openEoProperties
    ) {
        this.openEoServiceClient = openEoServiceClient;
        this.regionRepository = regionRepository;
        this.jobRunRepository = jobRunRepository;
        this.observationRepository = observationRepository;
        this.snapshotService = snapshotService;
        this.dashboardQueryCache = dashboardQueryCache;
        this.openEoProperties = openEoProperties;
    }

    @Scheduled(cron = "${openeo.sync.cron:0 */15 * * * *}")
    public void runScheduledSync() {
        if (!openEoProperties.getSync().isEnabled()) {
            return;
        }
        runSync(null, null, null);
    }

    @Override
    public SyncRunResponseDTO runSync(String regionId) {
        return runSync(regionId, null, null);
    }

    @Override
    public SyncRunResponseDTO runSync(String regionId, String from, String to) {
        LocalDateTime startedAt = LocalDateTime.now();
        totalSyncRuns.incrementAndGet();

        DateRange dateRange = resolveDateRange(from, to);
        List<Region> regions = resolveRegions(regionId);
        refreshAoiCacheIfNeeded();

        int acceptedCount = 0;
        int deduplicatedCount = 0;
        int errorCount = 0;

        for (Region region : regions) {
            for (IndicatorType indicator : IndicatorType.values()) {
                String lockKey = buildLockKey(region.getId(), indicator, dateRange.from(), dateRange.to());
                long flowStartNs = System.nanoTime();

                if (!inFlightKeys.add(lockKey)) {
                    deduplicatedCount++;
                    continue;
                }

                try {
                    LocalDateTime requestedAt = LocalDateTime.now();
                    if (shouldSkipByRecentObservation(region.getId(), indicator, dateRange, requestedAt)) {
                        deduplicatedCount++;
                        logSyncInfo(
                            region.getId(),
                            indicator,
                            SOURCE,
                            "cached_recent",
                            null,
                            durationMs(flowStartNs),
                            "skipped_recent_observation"
                        );
                        continue;
                    }

                    BoundingBox bbox = resolveBoundingBox(region);
                    if (bbox == null) {
                        OpenEoJobRun errorJobRun = buildErrorJobRun(
                            region,
                            indicator,
                            dateRange,
                            requestedAt,
                            STATUS_AOI_MISSING,
                            "aoi_missing",
                            "No hay AOI bbox configurado para regionCode=" + region.getCodigo(),
                            SOURCE
                        );
                        jobRunRepository.save(errorJobRun);
                        errorCount++;
                        totalSyncErrors.incrementAndGet();
                        logSyncWarn(region.getId(), indicator, SOURCE, null, null, durationMs(flowStartNs), STATUS_AOI_MISSING);
                        continue;
                    }

                    OpenEoIndicatorLatestRequest request = new OpenEoIndicatorLatestRequest();
                    request.setRegionId(region.getId());
                    request.setAoi(new OpenEoIndicatorLatestRequest.AoiRequest("bbox", bbox.coordinates()));
                    request.setPeriodStart(dateRange.from().toString());
                    request.setPeriodEnd(dateRange.to().toString());

                    OpenEoIndicatorLatestResponse latestResponse = openEoServiceClient.fetchLatestIndicator(indicator, request);
                    OpenEoIndicatorLatestResponse normalizedResponse = normalizeResponse(region, indicator, dateRange.to(), latestResponse);

                    if (normalizedResponse.getValue() == null) {
                        OpenEoJobRun noDataJobRun = buildErrorJobRun(
                            region,
                            indicator,
                            dateRange,
                            requestedAt,
                            STATUS_NO_DATA,
                            "no_value",
                            "openeo-service respondio sin value",
                            normalizedResponse.getSource() != null ? normalizedResponse.getSource() : SOURCE
                        );
                        jobRunRepository.save(noDataJobRun);
                        errorCount++;
                        totalSyncErrors.incrementAndGet();
                        logSyncWarn(
                            region.getId(),
                            indicator,
                            normalizedResponse.getSource(),
                            normalizedResponse.getQuality(),
                            null,
                            durationMs(flowStartNs),
                            STATUS_NO_DATA
                        );
                        continue;
                    }

                    upsertObservation(region.getId(), indicator, normalizedResponse, bbox);
                    snapshotService.recomputeSnapshot(region.getId());
                    invalidateDashboardCacheForRegion(region.getId());

                    OpenEoJobRun finishedJobRun = buildFinishedJobRun(region, indicator, dateRange, requestedAt, normalizedResponse);
                    jobRunRepository.save(finishedJobRun);
                    acceptedCount++;
                    totalSyncSuccess.incrementAndGet();

                    logSyncInfo(
                        region.getId(),
                        indicator,
                        normalizedResponse.getSource(),
                        normalizedResponse.getQuality(),
                        normalizedResponse.getValue(),
                        durationMs(flowStartNs),
                        STATUS_FINISHED
                    );
                } catch (OpenEoClientException ex) {
                    OpenEoJobRun integrationErrorJobRun = buildErrorJobRun(
                        region,
                        indicator,
                        dateRange,
                        LocalDateTime.now(),
                        STATUS_ERROR,
                        "integration_error",
                        ex.getMessage(),
                        SOURCE
                    );
                    jobRunRepository.save(integrationErrorJobRun);
                    errorCount++;
                    totalSyncErrors.incrementAndGet();
                    logSyncWarn(region.getId(), indicator, SOURCE, null, null, durationMs(flowStartNs), "integration_error");
                } catch (Exception ex) {
                    OpenEoJobRun genericErrorJobRun = buildErrorJobRun(
                        region,
                        indicator,
                        dateRange,
                        LocalDateTime.now(),
                        STATUS_ERROR,
                        "unexpected_error",
                        ex.getMessage(),
                        SOURCE
                    );
                    jobRunRepository.save(genericErrorJobRun);
                    errorCount++;
                    totalSyncErrors.incrementAndGet();
                    logSyncWarn(region.getId(), indicator, SOURCE, null, null, durationMs(flowStartNs), "unexpected_error");
                } finally {
                    inFlightKeys.remove(lockKey);
                }
            }
        }

        LOGGER.info(
            "dashboard_sync_metrics totalRuns={} success={} errors={} accepted={} deduplicated={} durationMs={}",
            totalSyncRuns.get(),
            totalSyncSuccess.get(),
            totalSyncErrors.get(),
            acceptedCount,
            deduplicatedCount,
            durationMs(startedAt)
        );

        SyncRunResponseDTO response = new SyncRunResponseDTO();
        response.setTriggeredAt(startedAt);
        response.setTotalRegions(regions.size());
        response.setTotalJobsAccepted(acceptedCount);
        response.setTotalDeduplicated(deduplicatedCount);
        response.setTotalErrors(errorCount);
        return response;
    }

    private List<Region> resolveRegions(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return regionRepository.findAll();
        }
        Region region = regionRepository.findById(regionId)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la region para sync: " + regionId));
        List<Region> regions = new ArrayList<>();
        regions.add(region);
        return regions;
    }

    private OpenEoIndicatorLatestResponse normalizeResponse(
        Region region,
        IndicatorType indicator,
        LocalDate periodEnd,
        OpenEoIndicatorLatestResponse response
    ) {
        if (response == null) {
            response = new OpenEoIndicatorLatestResponse();
        }

        if (response.getValue() != null) {
            if (response.getQuality() == null || response.getQuality().isBlank()) {
                response.setQuality("measured");
            }
            if (response.getSource() == null || response.getSource().isBlank()) {
                response.setSource(SOURCE);
            }
            return response;
        }

        if (!openEoProperties.getSync().isPlaceholderValueEnabled()) {
            return response;
        }

        // Testing/demo fallback only: keep disabled by default for real-data flows.
        response.setValue(buildPlaceholderValue(region.getId(), indicator, periodEnd));
        response.setQuality("estimated");
        response.setSource(SOURCE_FALLBACK);
        if (response.getMeasuredAt() == null) {
            response.setMeasuredAt(LocalDateTime.of(periodEnd, LocalTime.NOON));
        }
        return response;
    }

    private void upsertObservation(
        String regionId,
        IndicatorType indicator,
        OpenEoIndicatorLatestResponse response,
        BoundingBox bbox
    ) {
        LocalDateTime observedAt = response.getMeasuredAt() != null ? response.getMeasuredAt() : LocalDateTime.now();
        OpenEoIndicatorObservation observation = observationRepository
            .findByRegionIdAndIndicatorAndObservedAt(regionId, indicator, observedAt)
            .orElseGet(OpenEoIndicatorObservation::new);

        observation.setRegionId(regionId);
        observation.setIndicator(indicator);
        observation.setObservedAt(observedAt);
        observation.setValue(response.getValue());
        observation.setUnit(response.getUnit());
        observation.setAoi(bbox.toCompactString());
        observation.setQuality(response.getQuality() != null ? response.getQuality() : "measured");
        observation.setSource(response.getSource() != null ? response.getSource() : SOURCE);
        observation.setIngestedAt(LocalDateTime.now());
        observationRepository.save(observation);
    }

    private OpenEoJobRun buildFinishedJobRun(
        Region region,
        IndicatorType indicator,
        DateRange dateRange,
        LocalDateTime requestedAt,
        OpenEoIndicatorLatestResponse response
    ) {
        LocalDateTime now = LocalDateTime.now();

        OpenEoJobRun jobRun = new OpenEoJobRun();
        jobRun.setJobId(UUID.randomUUID().toString());
        jobRun.setRegionId(region.getId());
        jobRun.setIndicator(indicator);
        jobRun.setPeriodStart(dateRange.from().atStartOfDay());
        jobRun.setPeriodEnd(dateRange.to().atTime(23, 59, 59));
        jobRun.setStatus(STATUS_FINISHED);
        jobRun.setRequestedAt(requestedAt);
        jobRun.setUpdatedAt(now);
        jobRun.setFinishedAt(now);
        jobRun.setSource(response.getSource() != null ? response.getSource() : SOURCE);
        jobRun.setErrorCode(null);
        jobRun.setErrorMessage(null);
        return jobRun;
    }

    private OpenEoJobRun buildErrorJobRun(
        Region region,
        IndicatorType indicator,
        DateRange dateRange,
        LocalDateTime requestedAt,
        String status,
        String errorCode,
        String errorMessage,
        String source
    ) {
        LocalDateTime now = LocalDateTime.now();

        OpenEoJobRun jobRun = new OpenEoJobRun();
        jobRun.setJobId(UUID.randomUUID().toString());
        jobRun.setRegionId(region.getId());
        jobRun.setIndicator(indicator);
        jobRun.setPeriodStart(dateRange.from().atStartOfDay());
        jobRun.setPeriodEnd(dateRange.to().atTime(23, 59, 59));
        jobRun.setStatus(status);
        jobRun.setRequestedAt(requestedAt);
        jobRun.setUpdatedAt(now);
        jobRun.setFinishedAt(now);
        jobRun.setSource(source != null ? source : SOURCE);
        jobRun.setErrorCode(errorCode);
        jobRun.setErrorMessage(errorMessage);
        return jobRun;
    }

    private DateRange resolveDateRange(String from, String to) {
        LocalDate toDate = parseDateOrDefault(to, LocalDate.now(), "to");
        LocalDate fromDate = parseDateOrDefault(from, toDate.minusDays(29), "from");
        if (fromDate.isAfter(toDate)) {
            throw new BadRequestException("El rango de fechas es invalido: from no puede ser mayor a to");
        }
        return new DateRange(fromDate, toDate);
    }

    private LocalDate parseDateOrDefault(String rawValue, LocalDate fallback, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(rawValue);
        } catch (Exception ex) {
            throw new BadRequestException(fieldName + " invalido. Usa formato YYYY-MM-DD");
        }
    }

    private String buildLockKey(String regionId, IndicatorType indicator, LocalDate from, LocalDate to) {
        return regionId + "|" + indicator + "|" + from + "|" + to;
    }

    private boolean shouldSkipByRecentObservation(
        String regionId,
        IndicatorType indicator,
        DateRange dateRange,
        LocalDateTime requestedAt
    ) {
        int minIntervalMinutes = openEoProperties.getSync().getMinRequestIntervalMinutes();
        if (minIntervalMinutes <= 0) {
            return false;
        }

        LocalDateTime minAllowedIngestedAt = requestedAt.minus(minIntervalMinutes, ChronoUnit.MINUTES);
        LocalDateTime rangeStart = dateRange.from().atStartOfDay();
        LocalDateTime rangeEnd = dateRange.to().atTime(23, 59, 59);

        return observationRepository.findTopByRegionIdAndIndicatorOrderByIngestedAtDesc(regionId, indicator)
            .filter(obs -> obs.getIngestedAt() != null && !obs.getIngestedAt().isBefore(minAllowedIngestedAt))
            .filter(obs -> obs.getObservedAt() != null && !obs.getObservedAt().isBefore(rangeStart))
            .filter(obs -> obs.getObservedAt() != null && !obs.getObservedAt().isAfter(rangeEnd))
            .isPresent();
    }

    private long durationMs(long startedAtNs) {
        return (System.nanoTime() - startedAtNs) / 1_000_000L;
    }

    private long durationMs(LocalDateTime start) {
        return java.time.Duration.between(start, LocalDateTime.now()).toMillis();
    }

    private void logSyncInfo(
        String regionId,
        IndicatorType indicator,
        String source,
        String quality,
        Double value,
        long durationMs,
        String status
    ) {
        LOGGER.info(
            "dashboard_sync regionId={} indicator={} source={} quality={} value={} durationMs={} status={}",
            regionId,
            indicator,
            source,
            quality,
            value,
            durationMs,
            status
        );
    }

    private void logSyncWarn(
        String regionId,
        IndicatorType indicator,
        String source,
        String quality,
        Double value,
        long durationMs,
        String status
    ) {
        LOGGER.warn(
            "dashboard_sync regionId={} indicator={} source={} quality={} value={} durationMs={} status={}",
            regionId,
            indicator,
            source,
            quality,
            value,
            durationMs,
            status
        );
    }

    private double buildPlaceholderValue(String regionId, IndicatorType indicator, LocalDate periodEnd) {
        int base = Math.abs((regionId + "|" + indicator + "|" + periodEnd).hashCode());
        double normalized = (base % 1000) / 1000.0;

        if (IndicatorType.NDVI.equals(indicator)) {
            return roundThreeDecimals(0.35 + (normalized * 0.4));
        }
        return roundThreeDecimals(0.15 + (normalized * 0.35));
    }

    private double roundThreeDecimals(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private void refreshAoiCacheIfNeeded() {
        String raw = openEoProperties.getAoi() != null ? openEoProperties.getAoi().getBboxMap() : "";
        String normalized = raw != null ? raw.trim() : "";
        if (normalized.equals(lastAoiMapRaw)) {
            return;
        }

        aoiBboxByRegionCode.clear();
        if (!normalized.isBlank()) {
            String[] entries = normalized.split(";");
            for (String entry : entries) {
                String trimmed = entry.trim();
                if (trimmed.isBlank()) {
                    continue;
                }
                String[] pair = trimmed.split(":", 2);
                if (pair.length != 2) {
                    LOGGER.warn("openeo_aoi_config status=invalid_entry entry={}", trimmed);
                    continue;
                }

                String regionCode = pair[0].trim().toUpperCase();
                String[] coords = pair[1].trim().split(",");
                if (coords.length != 4) {
                    LOGGER.warn("openeo_aoi_config status=invalid_bbox regionCode={} raw={}", regionCode, pair[1]);
                    continue;
                }

                try {
                    double west = Double.parseDouble(coords[0].trim());
                    double south = Double.parseDouble(coords[1].trim());
                    double east = Double.parseDouble(coords[2].trim());
                    double north = Double.parseDouble(coords[3].trim());
                    aoiBboxByRegionCode.put(regionCode, new BoundingBox(west, south, east, north));
                } catch (NumberFormatException ex) {
                    LOGGER.warn("openeo_aoi_config status=parse_error regionCode={} raw={}", regionCode, pair[1]);
                }
            }
        }

        lastAoiMapRaw = normalized;
    }

    private BoundingBox resolveBoundingBox(Region region) {
        BoundingBox regionBbox = toBoundingBox(region.getAoiBbox());
        if (regionBbox != null) {
            return regionBbox;
        }

        if (region.getCodigo() == null || region.getCodigo().isBlank()) {
            return null;
        }
        return aoiBboxByRegionCode.get(region.getCodigo().trim().toUpperCase());
    }

    private BoundingBox toBoundingBox(List<Double> bbox) {
        if (bbox == null || bbox.size() != 4) {
            return null;
        }
        double west = bbox.get(0);
        double south = bbox.get(1);
        double east = bbox.get(2);
        double north = bbox.get(3);

        if (!Double.isFinite(west) || !Double.isFinite(south) || !Double.isFinite(east) || !Double.isFinite(north)) {
            return null;
        }
        if (west >= east || south >= north) {
            return null;
        }
        return new BoundingBox(west, south, east, north);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    private record BoundingBox(double west, double south, double east, double north) {
        List<Double> coordinates() {
            return List.of(west, south, east, north);
        }

        String toCompactString() {
            return west + "," + south + "," + east + "," + north;
        }
    }

    private void invalidateDashboardCacheForRegion(String regionId) {
        // Sync writes observations/snapshots; bust short-lived dashboard caches for fast UI consistency.
        dashboardQueryCache.invalidateByPrefix("latest|" + regionId + "|");
        dashboardQueryCache.invalidateByPrefix("series|" + regionId + "|");
        dashboardQueryCache.invalidateByPrefix("freshness|" + regionId);
        dashboardQueryCache.invalidateByPrefix("map|");
    }
}
