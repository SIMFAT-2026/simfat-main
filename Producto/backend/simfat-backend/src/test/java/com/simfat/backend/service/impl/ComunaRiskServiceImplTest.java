package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.simfat.backend.integration.openeo.OpenEoServiceClient;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ComunaRiskSnapshotRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.NotificationService;
import com.simfat.backend.service.OpenWeatherFwiService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Post-review FIX 1/2/6: the coverage-gap routing decision (Decision 6, corrected to
// comuna granularity) was extracted into FirmsAttributionRouter (see
// FirmsAttributionRouterTest for the routing/fallback regression coverage, including THE
// incident regression). This test class now mocks the router at the boundary and verifies
// only ComunaRiskServiceImpl's own responsibility: scoring/escalation given whatever event
// list the router returns.
@ExtendWith(MockitoExtension.class)
class ComunaRiskServiceImplTest {

    @Mock
    private ComunaInfoRepository comunaRepository;
    @Mock
    private ComunaRiskSnapshotRepository snapshotRepository;
    @Mock
    private TerritoryWeatherObservationRepository weatherRepository;
    @Mock
    private CitizenReportRepository citizenReportRepository;
    @Mock
    private OpenWeatherFwiService fwiService;
    @Mock
    private OpenEoIndicatorObservationRepository openEoObsRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private OpenEoServiceClient openEoServiceClient;
    @Mock
    private FirmsAttributionRouter firmsAttributionRouter;

    private ComunaRiskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComunaRiskServiceImpl(
            comunaRepository,
            snapshotRepository,
            weatherRepository,
            citizenReportRepository,
            fwiService,
            openEoObsRepository,
            notificationService,
            openEoServiceClient,
            firmsAttributionRouter
        );
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.findTopByComunaIdOrderByComputedAtDesc(any())).thenReturn(Optional.empty());
    }

    private ComunaInfo comunaInfo(String id, String regionId) {
        ComunaInfo c = new ComunaInfo();
        c.setId(id);
        c.setRegionId(regionId);
        c.setNombre(id);
        c.setCenterLat(-37.5);
        c.setCenterLon(-72.5);
        return c;
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
    void recomputeByComuna_delegatesFirmsSelectionToRouter() {
        String comunaId = "comuna-A";
        ComunaInfo comuna = comunaInfo(comunaId, "region-A");
        when(comunaRepository.findById(comunaId)).thenReturn(Optional.of(comuna));

        LocalDateTime notToday = LocalDateTime.now().minusHours(20);
        HeatAlertEvent foco = firmsEvent(notToday, 30.0);
        when(firmsAttributionRouter.resolveForComuna(eq(comuna), any())).thenReturn(List.of(foco));

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comunaId);

        assertEquals(1, snapshot.getFirmsCount());
    }

    @Test
    void recomputeByComuna_routerReturnsEmpty_zeroFirmsCount() {
        String comunaId = "comuna-A";
        ComunaInfo comuna = comunaInfo(comunaId, "region-A");
        when(comunaRepository.findById(comunaId)).thenReturn(Optional.of(comuna));
        when(firmsAttributionRouter.resolveForComuna(eq(comuna), any())).thenReturn(List.of());

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comunaId);

        assertEquals(0, snapshot.getFirmsCount());
    }

    @Test
    void recomputeByComuna_countAtStandardizedThreshold_escalatesToCritico() {
        String comunaId = "comuna-A";
        ComunaInfo comuna = comunaInfo(comunaId, "region-A");
        when(comunaRepository.findById(comunaId)).thenReturn(Optional.of(comuna));

        LocalDateTime notToday = LocalDateTime.now().minusHours(20);
        List<HeatAlertEvent> events = List.of(
            firmsEvent(notToday, 10.0),
            firmsEvent(notToday, 10.0),
            firmsEvent(notToday, 10.0),
            firmsEvent(notToday, 10.0)
        );
        when(firmsAttributionRouter.resolveForComuna(eq(comuna), any())).thenReturn(events);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comunaId);

        assertEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeByComuna_frpAtStandardizedThreshold_escalatesToCritico() {
        String comunaId = "comuna-A";
        ComunaInfo comuna = comunaInfo(comunaId, "region-A");
        when(comunaRepository.findById(comunaId)).thenReturn(Optional.of(comuna));

        LocalDateTime notToday = LocalDateTime.now().minusHours(20);
        List<HeatAlertEvent> events = List.of(firmsEvent(notToday, 60.0));
        when(firmsAttributionRouter.resolveForComuna(eq(comuna), any())).thenReturn(events);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comunaId);

        assertEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeByComuna_belowThreshold_doesNotEscalateToCritico() {
        String comunaId = "comuna-A";
        ComunaInfo comuna = comunaInfo(comunaId, "region-A");
        when(comunaRepository.findById(comunaId)).thenReturn(Optional.of(comuna));

        LocalDateTime notToday = LocalDateTime.now().minusHours(20);
        List<HeatAlertEvent> events = List.of(
            firmsEvent(notToday, 50.0),
            firmsEvent(notToday, 50.0),
            firmsEvent(notToday, 50.0)
        );
        when(firmsAttributionRouter.resolveForComuna(eq(comuna), any())).thenReturn(events);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comunaId);

        assertEquals("NORMAL", snapshot.getAlertLevel());
    }

    @Test
    void recomputeByComuna_todaysDetection_alwaysCriticoRegardlessOfCount() {
        String comunaId = "comuna-A";
        ComunaInfo comuna = comunaInfo(comunaId, "region-A");
        when(comunaRepository.findById(comunaId)).thenReturn(Optional.of(comuna));

        LocalDateTime today = LocalDateTime.now(java.time.ZoneOffset.UTC);
        List<HeatAlertEvent> events = List.of(firmsEvent(today, 5.0));
        when(firmsAttributionRouter.resolveForComuna(eq(comuna), any())).thenReturn(events);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comunaId);

        assertEquals("CRITICO", snapshot.getAlertLevel());
    }
}
