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
import com.simfat.backend.repository.HeatAlertEventRepository;
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

/**
 * Regression coverage for the standardized FIRMS escalation constants
 * (FIRMS_MAX_COUNT=5, FIRMS_COUNT_CRITICO=4, FIRMS_FRP_CRITICO=60) and for
 * comuna-scoped queries reading the persisted comunaId instead of nearest-
 * centroid assignment.
 */
@ExtendWith(MockitoExtension.class)
class ComunaRiskServiceImplTest {

    @Mock
    private ComunaInfoRepository comunaRepository;
    @Mock
    private ComunaRiskSnapshotRepository snapshotRepository;
    @Mock
    private TerritoryWeatherObservationRepository weatherRepository;
    @Mock
    private HeatAlertEventRepository heatAlertRepository;
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

    private ComunaRiskServiceImpl service;

    private static final String COMUNA_A = "comuna-a";
    private static final String COMUNA_B = "comuna-b";
    private static final String REGION_ID = "region-1";

    @BeforeEach
    void setUp() {
        service = new ComunaRiskServiceImpl(
            comunaRepository,
            snapshotRepository,
            weatherRepository,
            heatAlertRepository,
            citizenReportRepository,
            fwiService,
            openEoObsRepository,
            notificationService,
            openEoServiceClient
        );

        ComunaInfo comunaA = new ComunaInfo();
        comunaA.setId(COMUNA_A);
        comunaA.setRegionId(REGION_ID);
        comunaA.setNombre("Comuna A");
        when(comunaRepository.findById(COMUNA_A)).thenReturn(Optional.of(comunaA));

        when(weatherRepository.findTopByRegionIdOrderByObservedAtDesc(any())).thenReturn(Optional.empty());
        when(openEoObsRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(citizenReportRepository.findByRegionId(any())).thenReturn(List.of());
        when(snapshotRepository.findTopByComunaIdOrderByComputedAtDesc(any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private HeatAlertEvent firmsEvent(double frp, LocalDateTime fechaEvento) {
        HeatAlertEvent event = new HeatAlertEvent();
        event.setFuente("NASA_FIRMS");
        event.setFirmsConfidence("h");
        event.setFirmsFrp(frp);
        event.setLatitud(-36.0);
        event.setLongitud(-72.0);
        event.setFechaEvento(fechaEvento);
        return event;
    }

    @Test
    void recomputeByComuna_countAtStandardizedThreshold_escalatesToCritico() {
        // FIRMS_COUNT_CRITICO=4, not today -> CRITICO via cluster count, regardless of FRP.
        LocalDateTime notToday = LocalDateTime.now().minusDays(2);
        List<HeatAlertEvent> events = List.of(
            firmsEvent(10.0, notToday),
            firmsEvent(10.0, notToday),
            firmsEvent(10.0, notToday),
            firmsEvent(10.0, notToday)
        );
        when(heatAlertRepository.findByComunaIdAndFechaEventoAfter(eq(COMUNA_A), any())).thenReturn(events);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(COMUNA_A);

        assertEquals("CRITICO", snapshot.getAlertLevel());
        assertEquals(4, snapshot.getFirmsCount());
    }

    @Test
    void recomputeByComuna_frpAtStandardizedThreshold_escalatesToCritico() {
        // FIRMS_FRP_CRITICO=60, not today, single detection -> CRITICO via FRP intensity.
        LocalDateTime notToday = LocalDateTime.now().minusDays(2);
        List<HeatAlertEvent> events = List.of(firmsEvent(60.0, notToday));
        when(heatAlertRepository.findByComunaIdAndFechaEventoAfter(eq(COMUNA_A), any())).thenReturn(events);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(COMUNA_A);

        assertEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeByComuna_belowThreshold_doesNotEscalateToCritico() {
        // firmsCount=3 (< 4), firmsFrpMean=50 (< 60), not today, no FWI -> NOT CRITICO.
        LocalDateTime notToday = LocalDateTime.now().minusDays(2);
        List<HeatAlertEvent> events = List.of(
            firmsEvent(50.0, notToday),
            firmsEvent(50.0, notToday),
            firmsEvent(50.0, notToday)
        );
        when(heatAlertRepository.findByComunaIdAndFechaEventoAfter(eq(COMUNA_A), any())).thenReturn(events);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(COMUNA_A);

        assertEquals(3, snapshot.getFirmsCount());
        org.junit.jupiter.api.Assertions.assertNotEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeByComuna_todaysDetection_alwaysCriticoRegardlessOfCount() {
        // isToday() treats fechaEvento as a UTC instant and converts to America/Santiago
        // before comparing calendar dates — fechaEvento must be expressed in UTC "now".
        LocalDateTime today = LocalDateTime.now(java.time.ZoneOffset.UTC);
        List<HeatAlertEvent> events = List.of(firmsEvent(5.0, today));
        when(heatAlertRepository.findByComunaIdAndFechaEventoAfter(eq(COMUNA_A), any())).thenReturn(events);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(COMUNA_A);

        assertEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeByComuna_queriesByPersistedComunaId_onlyOwnEventsCounted() {
        // Comuna-A's recompute must request events scoped to its own comunaId only.
        // The adjacent comuna's events are never returned by this mock for COMUNA_A's
        // query, proving no cross-comuna leakage via the comunaId-scoped repository call.
        LocalDateTime notToday = LocalDateTime.now().minusDays(2);
        when(heatAlertRepository.findByComunaIdAndFechaEventoAfter(eq(COMUNA_A), any()))
            .thenReturn(List.of(firmsEvent(10.0, notToday)));
        // Deliberately unused by COMUNA_A's recompute — proves no cross-comuna leakage.
        org.mockito.Mockito.lenient().when(heatAlertRepository.findByComunaIdAndFechaEventoAfter(eq(COMUNA_B), any()))
            .thenReturn(List.of(firmsEvent(10.0, notToday), firmsEvent(10.0, notToday)));

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(COMUNA_A);

        assertEquals(1, snapshot.getFirmsCount());
        org.mockito.Mockito.verify(heatAlertRepository, org.mockito.Mockito.never())
            .findByComunaIdAndFechaEventoAfter(eq(COMUNA_B), any());
    }
}
