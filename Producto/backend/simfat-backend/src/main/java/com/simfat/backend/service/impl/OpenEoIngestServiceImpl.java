package com.simfat.backend.service.impl;

import com.simfat.backend.dto.OpenEoMeasurementIngestRequestDTO;
import com.simfat.backend.dto.OpenEoMeasurementIngestResponseDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.model.OpenEoJobRun;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.OpenEoJobRunRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.DashboardSnapshotService;
import com.simfat.backend.service.OpenEoIngestService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OpenEoIngestServiceImpl implements OpenEoIngestService {

    private static final String SOURCE_DEFAULT = "openeo-service";
    private static final String STATUS_FINISHED = "finished";
    private static final String STATUS_NO_DATA = "no_data";

    private final OpenEoIndicatorObservationRepository observationRepository;
    private final OpenEoJobRunRepository jobRunRepository;
    private final RegionRepository regionRepository;
    private final DashboardSnapshotService snapshotService;

    public OpenEoIngestServiceImpl(
        OpenEoIndicatorObservationRepository observationRepository,
        OpenEoJobRunRepository jobRunRepository,
        RegionRepository regionRepository,
        DashboardSnapshotService snapshotService
    ) {
        this.observationRepository = observationRepository;
        this.jobRunRepository = jobRunRepository;
        this.regionRepository = regionRepository;
        this.snapshotService = snapshotService;
    }

    @Override
    public OpenEoMeasurementIngestResponseDTO ingestMeasurement(OpenEoMeasurementIngestRequestDTO request) {
        regionRepository.findById(request.getRegionId())
            .orElseThrow(() -> new ResourceNotFoundException("No existe la region para ingesta: " + request.getRegionId()));

        IndicatorType indicator = IndicatorType.from(request.getIndicatorType());
        if (request.getPeriodEnd().isBefore(request.getPeriodStart())) {
            throw new BadRequestException("periodEnd no puede ser menor a periodStart");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime measuredAt = toUtcLocalDateTime(
            request.getMeasuredAt() != null ? request.getMeasuredAt() : request.getFetchedAt(),
            now
        );
        String source = nonBlankOrDefault(request.getSource(), SOURCE_DEFAULT);
        String status = request.getValue() != null ? STATUS_FINISHED : STATUS_NO_DATA;
        String jobId = UUID.randomUUID().toString();

        boolean observationPersisted = false;
        if (request.getValue() != null) {
            OpenEoIndicatorObservation observation = observationRepository
                .findByRegionIdAndIndicatorAndObservedAt(request.getRegionId(), indicator, measuredAt)
                .orElseGet(OpenEoIndicatorObservation::new);

            observation.setRegionId(request.getRegionId());
            observation.setIndicator(indicator);
            observation.setObservedAt(measuredAt);
            observation.setValue(request.getValue());
            observation.setUnit(request.getUnit());
            observation.setAoi(null);
            observation.setQuality(nonBlankOrDefault(request.getQuality(), "measured"));
            observation.setSource(source);
            observation.setIngestedAt(now);
            observationRepository.save(observation);

            snapshotService.recomputeSnapshot(request.getRegionId());
            observationPersisted = true;
        }

        OpenEoJobRun jobRun = new OpenEoJobRun();
        jobRun.setJobId(jobId);
        jobRun.setRegionId(request.getRegionId());
        jobRun.setIndicator(indicator);
        jobRun.setPeriodStart(request.getPeriodStart().atStartOfDay());
        jobRun.setPeriodEnd(request.getPeriodEnd().atTime(23, 59, 59));
        jobRun.setStatus(status);
        jobRun.setRequestedAt(toUtcLocalDateTime(request.getFetchedAt(), now));
        jobRun.setUpdatedAt(now);
        jobRun.setFinishedAt(now);
        jobRun.setSource(source);
        if (request.getValue() == null) {
            jobRun.setErrorCode("no_value");
            jobRun.setErrorMessage("openeo-service respondio sin value");
        }
        jobRunRepository.save(jobRun);

        OpenEoMeasurementIngestResponseDTO response = new OpenEoMeasurementIngestResponseDTO();
        response.setSynced(true);
        response.setStatus(status);
        response.setJobId(jobId);
        response.setObservationPersisted(observationPersisted);
        return response;
    }

    private LocalDateTime toUtcLocalDateTime(OffsetDateTime value, LocalDateTime fallback) {
        if (value == null) {
            return fallback;
        }
        return value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private String nonBlankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
