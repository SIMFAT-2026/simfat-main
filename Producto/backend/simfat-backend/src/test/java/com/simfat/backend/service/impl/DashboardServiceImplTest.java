package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.simfat.backend.dto.DashboardSummaryDTO;
import com.simfat.backend.repository.ComunaRiskSnapshotRepository;
import com.simfat.backend.repository.DashboardRegionSnapshotRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.AlertRuleService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private ForestLossRecordRepository forestLossRepository;
    @Mock
    private HeatAlertEventRepository heatAlertRepository;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DashboardRegionSnapshotRepository snapshotRepository;
    @Mock
    private ComunaRiskSnapshotRepository comunaRiskSnapshotRepository;
    @Mock
    private AlertRuleService alertRuleService;

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(
            forestLossRepository,
            heatAlertRepository,
            regionRepository,
            snapshotRepository,
            comunaRiskSnapshotRepository,
            alertRuleService
        );
    }

    @Test
    void getSummary_totalAlertas_countsComunasWithHighOrCriticalAlertLevel_notHistoricHeatEvents() {
        // Regression test: totalAlertas previously counted ALL HeatAlertEvent
        // rows ever recorded (heatAlertRepository.count()), unrelated to the
        // current risk model. It must reflect distinct communes whose LATEST
        // ComunaRiskSnapshot is ALTO/CRITICO instead.
        when(forestLossRepository.findAll()).thenReturn(List.of());
        when(forestLossRepository.findAllByOrderByAnioAsc()).thenReturn(List.of());
        when(snapshotRepository.findAll()).thenReturn(List.of());
        when(regionRepository.findAll()).thenReturn(List.of());
        when(comunaRiskSnapshotRepository.countComunasWithHighOrCriticalAlertLevel()).thenReturn(7L);

        DashboardSummaryDTO summary = service.getSummary();

        assertEquals(7, summary.getTotalAlertas());
    }

    @Test
    void getSummary_totalAlertas_nullAggregation_defaultsToZero() {
        when(forestLossRepository.findAll()).thenReturn(List.of());
        when(forestLossRepository.findAllByOrderByAnioAsc()).thenReturn(List.of());
        when(snapshotRepository.findAll()).thenReturn(List.of());
        when(regionRepository.findAll()).thenReturn(List.of());
        when(comunaRiskSnapshotRepository.countComunasWithHighOrCriticalAlertLevel()).thenReturn(null);

        DashboardSummaryDTO summary = service.getSummary();

        assertEquals(0, summary.getTotalAlertas());
    }
}
