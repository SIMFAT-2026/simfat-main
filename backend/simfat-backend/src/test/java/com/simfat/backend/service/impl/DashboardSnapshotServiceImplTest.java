package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.simfat.backend.model.DashboardRegionSnapshot;
import com.simfat.backend.model.ForestLossRecord;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.repository.DashboardRegionSnapshotRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardSnapshotServiceImplTest {

    @Mock
    private OpenEoIndicatorObservationRepository observationRepository;
    @Mock
    private DashboardRegionSnapshotRepository snapshotRepository;
    @Mock
    private HeatAlertEventRepository heatAlertRepository;
    @Mock
    private ForestLossRecordRepository forestLossRepository;

    private DashboardSnapshotServiceImpl snapshotService;

    @BeforeEach
    void setUp() {
        snapshotService = new DashboardSnapshotServiceImpl(
            observationRepository,
            snapshotRepository,
            heatAlertRepository,
            forestLossRepository
        );
    }

    @Test
    void recomputeSnapshot_calculatesLatestTrendAndFreshness() {
        String regionId = "region-abc";
        OpenEoIndicatorObservation ndviLatest = new OpenEoIndicatorObservation();
        ndviLatest.setObservedAt(LocalDateTime.now().minusMinutes(20));
        ndviLatest.setValue(0.45);

        OpenEoIndicatorObservation ndmiLatest = new OpenEoIndicatorObservation();
        ndmiLatest.setObservedAt(LocalDateTime.now().minusMinutes(10));
        ndmiLatest.setValue(0.22);

        OpenEoIndicatorObservation ndviOld = new OpenEoIndicatorObservation();
        ndviOld.setObservedAt(LocalDateTime.now().minusDays(29));
        ndviOld.setValue(0.31);

        OpenEoIndicatorObservation ndmiOld = new OpenEoIndicatorObservation();
        ndmiOld.setObservedAt(LocalDateTime.now().minusDays(28));
        ndmiOld.setValue(0.18);

        ForestLossRecord record = new ForestLossRecord();
        record.setAnio(2026);
        record.setPorcentajePerdida(0.9);

        when(observationRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(regionId, IndicatorType.NDVI))
            .thenReturn(Optional.of(ndviLatest));
        when(observationRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(regionId, IndicatorType.NDMI))
            .thenReturn(Optional.of(ndmiLatest));
        when(observationRepository.findByRegionIdAndIndicatorAndObservedAtBetweenOrderByObservedAtAsc(eq(regionId), eq(IndicatorType.NDVI), any(), any()))
            .thenReturn(List.of(ndviOld, ndviLatest));
        when(observationRepository.findByRegionIdAndIndicatorAndObservedAtBetweenOrderByObservedAtAsc(eq(regionId), eq(IndicatorType.NDMI), any(), any()))
            .thenReturn(List.of(ndmiOld, ndmiLatest));
        when(heatAlertRepository.countByRegionIdAndFechaEventoBetween(eq(regionId), any(), any())).thenReturn(3L);
        when(forestLossRepository.findByRegionId(regionId)).thenReturn(List.of(record));
        when(snapshotRepository.findByRegionId(regionId)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DashboardRegionSnapshot snapshot = snapshotService.recomputeSnapshot(regionId);

        assertEquals(regionId, snapshot.getRegionId());
        assertEquals(0.45, snapshot.getLatestNdvi());
        assertEquals(0.22, snapshot.getLatestNdmi());
        assertEquals(0.14, snapshot.getNdviTrend30d());
        assertEquals(0.04, snapshot.getNdmiTrend30d());
        assertEquals("LOW", snapshot.getCriticality());
        assertNotNull(snapshot.getDataFreshnessSeconds());
    }
}
