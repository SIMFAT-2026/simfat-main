package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.dto.OpenEoMeasurementIngestRequestDTO;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.model.Region;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.OpenEoJobRunRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.DashboardSnapshotService;
import com.simfat.backend.service.TerritoryRiskService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenEoIngestServiceImplTest {

    @Mock
    private OpenEoIndicatorObservationRepository observationRepository;
    @Mock
    private OpenEoJobRunRepository jobRunRepository;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DashboardSnapshotService snapshotService;
    @Mock
    private TerritoryRiskService territoryRiskService;

    private OpenEoIngestServiceImpl ingestService;

    @BeforeEach
    void setUp() {
        ingestService = new OpenEoIngestServiceImpl(
            observationRepository,
            jobRunRepository,
            regionRepository,
            snapshotService,
            territoryRiskService
        );
    }

    @Test
    void ingestMeasurement_recomputesDashboardAndTerritoryRisk_whenValueIsPersisted() {
        OpenEoMeasurementIngestRequestDTO request = baseRequest();
        request.setValue(0.42);

        Region region = new Region();
        region.setId("region-1");
        when(regionRepository.findById("region-1")).thenReturn(Optional.of(region));
        when(observationRepository.findByRegionIdAndIndicatorAndObservedAt(eq("region-1"), eq(IndicatorType.NDVI), any()))
            .thenReturn(Optional.empty());

        assertTrue(ingestService.ingestMeasurement(request).isObservationPersisted());

        verify(observationRepository).save(any(OpenEoIndicatorObservation.class));
        verify(snapshotService).recomputeSnapshot("region-1");
        verify(territoryRiskService).recomputeRiskByRegion("region-1");
        verify(jobRunRepository).save(any());
    }

    @Test
    void ingestMeasurement_doesNotRecomputeRisk_whenValueIsMissing() {
        OpenEoMeasurementIngestRequestDTO request = baseRequest();

        Region region = new Region();
        region.setId("region-1");
        when(regionRepository.findById("region-1")).thenReturn(Optional.of(region));

        ingestService.ingestMeasurement(request);

        verify(observationRepository, never()).save(any(OpenEoIndicatorObservation.class));
        verify(snapshotService, never()).recomputeSnapshot(any());
        verify(territoryRiskService, never()).recomputeRiskByRegion(any());
        verify(jobRunRepository).save(any());
    }

    private OpenEoMeasurementIngestRequestDTO baseRequest() {
        OpenEoMeasurementIngestRequestDTO request = new OpenEoMeasurementIngestRequestDTO();
        request.setRegionId("region-1");
        request.setIndicatorType("NDVI");
        request.setPeriodStart(LocalDate.parse("2026-04-01"));
        request.setPeriodEnd(LocalDate.parse("2026-04-10"));
        request.setFetchedAt(OffsetDateTime.parse("2026-04-15T10:00:00Z"));
        request.setMeasuredAt(OffsetDateTime.parse("2026-04-15T10:00:00Z"));
        return request;
    }
}
