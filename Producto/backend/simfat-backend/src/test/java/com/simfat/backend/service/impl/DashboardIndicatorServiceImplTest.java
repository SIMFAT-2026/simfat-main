package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.dto.DataFreshnessDTO;
import com.simfat.backend.dto.DataFreshnessStatus;
import com.simfat.backend.dto.IndicatorSeriesDTO;
import com.simfat.backend.model.DashboardRegionSnapshot;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.repository.DashboardRegionSnapshotRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.service.DashboardSnapshotService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardIndicatorServiceImplTest {

    @Mock
    private OpenEoIndicatorObservationRepository observationRepository;
    @Mock
    private DashboardRegionSnapshotRepository snapshotRepository;
    @Mock
    private DashboardSnapshotService snapshotService;

    private DashboardIndicatorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardIndicatorServiceImpl(
            observationRepository,
            snapshotRepository,
            snapshotService,
            new DashboardQueryCache()
        );
    }

    @Test
    void getSeries_usesDefaultLast30DaysWhenRangeIsMissing() {
        when(observationRepository.findByRegionIdAndIndicatorAndObservedAtBetweenOrderByObservedAtAsc(
            eq("region-1"),
            eq(IndicatorType.NDVI),
            any(),
            any()
        )).thenReturn(List.of());

        IndicatorSeriesDTO dto = service.getSeries("region-1", IndicatorType.NDVI, null, null, "day");

        assertNotNull(dto.getFrom());
        assertNotNull(dto.getTo());
        assertEquals(29, java.time.temporal.ChronoUnit.DAYS.between(dto.getFrom(), dto.getTo()));
        assertEquals("day", dto.getGranularity());
        verify(observationRepository).findByRegionIdAndIndicatorAndObservedAtBetweenOrderByObservedAtAsc(
            eq("region-1"),
            eq(IndicatorType.NDVI),
            any(),
            any()
        );
    }

    @Test
    void getDataFreshness_resolvesStatus() {
        DashboardRegionSnapshot snapshot = new DashboardRegionSnapshot();
        snapshot.setRegionId("region-1");
        snapshot.setComputedAt(LocalDateTime.now());
        snapshot.setDataFreshnessSeconds(120L);
        when(snapshotRepository.findByRegionId("region-1")).thenReturn(Optional.of(snapshot));

        DataFreshnessDTO fresh = service.getDataFreshness("region-1");
        assertEquals(DataFreshnessStatus.FRESH, fresh.getStatus());
        assertEquals(120L, fresh.getAgeSeconds());

        DashboardRegionSnapshot staleSnapshot = new DashboardRegionSnapshot();
        staleSnapshot.setRegionId("region-1");
        staleSnapshot.setComputedAt(LocalDateTime.now());
        staleSnapshot.setDataFreshnessSeconds(7200L);
        when(snapshotRepository.findByRegionId("region-2")).thenReturn(Optional.of(staleSnapshot));
        DataFreshnessDTO stale = service.getDataFreshness("region-2");
        assertEquals(DataFreshnessStatus.STALE, stale.getStatus());

        DashboardRegionSnapshot emptySnapshot = new DashboardRegionSnapshot();
        emptySnapshot.setRegionId("region-3");
        emptySnapshot.setComputedAt(LocalDateTime.now());
        emptySnapshot.setDataFreshnessSeconds(null);
        when(snapshotRepository.findByRegionId("region-3")).thenReturn(Optional.of(emptySnapshot));
        DataFreshnessDTO empty = service.getDataFreshness("region-3");
        assertEquals(DataFreshnessStatus.EMPTY, empty.getStatus());
    }
}
