package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.TerritoryRiskSnapshot;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryRiskSnapshotRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Post-review FIX 1/2/6: the coverage-gap routing decision (Decision 6) was extracted
// into FirmsAttributionRouter (see FirmsAttributionRouterTest for the routing/fallback
// regression coverage, including the THE incident regression). This test class now mocks
// the router at the boundary and verifies only TerritoryRiskServiceImpl's own
// responsibility: scoring/escalation given whatever event list the router returns.
@ExtendWith(MockitoExtension.class)
class TerritoryRiskServiceImplTest {

    @Mock
    private RegionRepository regionRepository;
    @Mock
    private OpenEoIndicatorObservationRepository observationRepository;
    @Mock
    private TerritoryWeatherObservationRepository weatherRepository;
    @Mock
    private CitizenReportRepository citizenReportRepository;
    @Mock
    private TerritoryRiskSnapshotRepository snapshotRepository;
    @Mock
    private FirmsAttributionRouter firmsAttributionRouter;

    private TerritoryRiskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TerritoryRiskServiceImpl(
            regionRepository,
            observationRepository,
            weatherRepository,
            citizenReportRepository,
            snapshotRepository,
            firmsAttributionRouter
        );
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private HeatAlertEvent firmsEvent(LocalDateTime fecha, double frp) {
        HeatAlertEvent e = new HeatAlertEvent();
        e.setFechaEvento(fecha);
        e.setFuente("NASA_FIRMS");
        e.setFirmsConfidence("h");
        e.setFirmsFrp(frp);
        e.setLatitud(-37.5);
        e.setLongitud(-72.5);
        return e;
    }

    @Test
    void recomputeRiskByRegion_delegatesFirmsSelectionToRouter() {
        String regionId = "region-1";
        LocalDateTime notToday = LocalDateTime.now().minusHours(20);
        HeatAlertEvent e = firmsEvent(notToday, 30.0);
        when(firmsAttributionRouter.resolveForRegion(eq(regionId), any())).thenReturn(List.of(e));

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(regionId);

        assertEquals(1, snapshot.getFirmsCount());
    }

    @Test
    void recomputeRiskByRegion_routerReturnsEmpty_zeroFirmsCount() {
        String regionId = "region-1";
        when(firmsAttributionRouter.resolveForRegion(eq(regionId), any())).thenReturn(List.of());

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(regionId);

        assertEquals(0, snapshot.getFirmsCount());
    }

    @Test
    void recomputeRiskByRegion_countAtStandardizedThreshold_escalatesToCritico() {
        String regionId = "region-1";
        LocalDateTime notToday = LocalDateTime.now().minusHours(20);
        List<HeatAlertEvent> events = List.of(
            firmsEvent(notToday, 10.0),
            firmsEvent(notToday, 10.0),
            firmsEvent(notToday, 10.0),
            firmsEvent(notToday, 10.0)
        );
        when(firmsAttributionRouter.resolveForRegion(eq(regionId), any())).thenReturn(events);

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(regionId);

        // firmsCount == 4 == new FIRMS_COUNT_CRITICO. Under the old value (8) this would
        // NOT have escalated — proves the standardized threshold took effect.
        assertEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeRiskByRegion_frpAtStandardizedThreshold_escalatesToCritico() {
        String regionId = "region-1";
        LocalDateTime notToday = LocalDateTime.now().minusHours(20);
        List<HeatAlertEvent> events = List.of(firmsEvent(notToday, 60.0));
        when(firmsAttributionRouter.resolveForRegion(eq(regionId), any())).thenReturn(events);

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(regionId);

        // firmsFrpMean == 60.0 == new FIRMS_FRP_CRITICO. Under the old value (75.0) this
        // would NOT have escalated.
        assertEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeRiskByRegion_belowThreshold_doesNotEscalateToCritico() {
        String regionId = "region-1";
        LocalDateTime notToday = LocalDateTime.now().minusHours(20);
        List<HeatAlertEvent> events = List.of(
            firmsEvent(notToday, 50.0),
            firmsEvent(notToday, 50.0),
            firmsEvent(notToday, 50.0)
        );
        when(firmsAttributionRouter.resolveForRegion(eq(regionId), any())).thenReturn(events);

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(regionId);

        assertEquals("NORMAL", snapshot.getAlertLevel());
    }

    @Test
    void recomputeRiskByRegion_todaysDetection_alwaysCriticoRegardlessOfCount() {
        String regionId = "region-1";
        LocalDateTime today = LocalDateTime.now(java.time.ZoneOffset.UTC);
        List<HeatAlertEvent> events = List.of(firmsEvent(today, 5.0));
        when(firmsAttributionRouter.resolveForRegion(eq(regionId), any())).thenReturn(events);

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(regionId);

        assertEquals("CRITICO", snapshot.getAlertLevel());
    }
}
