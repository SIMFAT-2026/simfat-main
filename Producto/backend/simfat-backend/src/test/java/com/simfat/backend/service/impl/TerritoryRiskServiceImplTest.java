package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.TerritoryRiskSnapshot;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryRiskSnapshotRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression coverage proving the standardized FIRMS escalation constants
 * (FIRMS_MAX_COUNT=5, FIRMS_COUNT_CRITICO=4, FIRMS_FRP_CRITICO=60) took
 * effect in TerritoryRiskServiceImpl, replacing the prior divergent
 * 10/8/75 thresholds — and that region totals are now derived from
 * persisted comunaId rather than nearest-region-centroid reassignment.
 */
@ExtendWith(MockitoExtension.class)
class TerritoryRiskServiceImplTest {

    @Mock
    private RegionRepository regionRepository;
    @Mock
    private ComunaInfoRepository comunaInfoRepository;
    @Mock
    private OpenEoIndicatorObservationRepository observationRepository;
    @Mock
    private TerritoryWeatherObservationRepository weatherRepository;
    @Mock
    private HeatAlertEventRepository heatAlertEventRepository;
    @Mock
    private CitizenReportRepository citizenReportRepository;
    @Mock
    private TerritoryRiskSnapshotRepository snapshotRepository;

    private TerritoryRiskServiceImpl service;

    private static final String REGION_ID = "region-biobio";
    private static final String COMUNA_A = "comuna-a";

    @BeforeEach
    void setUp() {
        service = new TerritoryRiskServiceImpl(
            regionRepository,
            comunaInfoRepository,
            observationRepository,
            weatherRepository,
            heatAlertEventRepository,
            citizenReportRepository,
            snapshotRepository
        );

        org.mockito.Mockito.lenient().when(weatherRepository.findTopByRegionIdOrderByObservedAtDesc(any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(observationRepository.findTopByRegionIdAndIndicatorOrderByIngestedAtDesc(any(), any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(citizenReportRepository.findByRegionId(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ComunaInfo comunaA = new ComunaInfo();
        comunaA.setId(COMUNA_A);
        comunaA.setRegionId(REGION_ID);
        org.mockito.Mockito.lenient().when(comunaInfoRepository.findByRegionId(REGION_ID)).thenReturn(List.of(comunaA));
    }

    private HeatAlertEvent firmsEvent(String comunaId, double frp, LocalDateTime fechaEvento) {
        HeatAlertEvent event = new HeatAlertEvent();
        event.setComunaId(comunaId);
        event.setFuente("NASA_FIRMS");
        event.setFirmsConfidence("h");
        event.setFirmsFrp(frp);
        event.setLatitud(-36.0);
        event.setLongitud(-72.0);
        event.setFechaEvento(fechaEvento);
        return event;
    }

    @Test
    void recomputeRiskByRegion_countAtStandardizedThreshold_escalatesToCritico() {
        // Was 8 under the old TerritoryRiskServiceImpl constants; proves the new
        // value (4) actually took effect — same input would NOT have escalated before.
        LocalDateTime notToday = LocalDateTime.now().minusDays(2);
        List<HeatAlertEvent> events = List.of(
            firmsEvent(COMUNA_A, 10.0, notToday),
            firmsEvent(COMUNA_A, 10.0, notToday),
            firmsEvent(COMUNA_A, 10.0, notToday),
            firmsEvent(COMUNA_A, 10.0, notToday)
        );
        when(heatAlertEventRepository.findByComunaIdInAndFechaEventoAfter(anyList(), any())).thenReturn(events);

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(REGION_ID);

        assertEquals("CRITICO", snapshot.getAlertLevel());
        assertEquals(4, snapshot.getFirmsCount());
    }

    @Test
    void recomputeRiskByRegion_frpAtStandardizedThreshold_escalatesToCritico() {
        // Was 75.0 under the old constants; proves the new value (60.0) took effect.
        LocalDateTime notToday = LocalDateTime.now().minusDays(2);
        List<HeatAlertEvent> events = List.of(firmsEvent(COMUNA_A, 60.0, notToday));
        when(heatAlertEventRepository.findByComunaIdInAndFechaEventoAfter(anyList(), any())).thenReturn(events);

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(REGION_ID);

        assertEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeRiskByRegion_belowThreshold_doesNotEscalateToCritico() {
        LocalDateTime notToday = LocalDateTime.now().minusDays(2);
        List<HeatAlertEvent> events = List.of(
            firmsEvent(COMUNA_A, 50.0, notToday),
            firmsEvent(COMUNA_A, 50.0, notToday),
            firmsEvent(COMUNA_A, 50.0, notToday)
        );
        when(heatAlertEventRepository.findByComunaIdInAndFechaEventoAfter(anyList(), any())).thenReturn(events);

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(REGION_ID);

        assertEquals(3, snapshot.getFirmsCount());
        assertNotEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeRiskByRegion_todaysDetection_alwaysCriticoRegardlessOfCount() {
        // isToday() treats fechaEvento as a UTC instant and converts to America/Santiago
        // before comparing calendar dates — fechaEvento must be expressed in UTC "now".
        LocalDateTime today = LocalDateTime.now(java.time.ZoneOffset.UTC);
        List<HeatAlertEvent> events = List.of(firmsEvent(COMUNA_A, 5.0, today));
        when(heatAlertEventRepository.findByComunaIdInAndFechaEventoAfter(anyList(), any())).thenReturn(events);

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(REGION_ID);

        assertEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeRiskByRegion_queriesByComunaIdsOfRegion_singleAttributionNoDoubleCount() {
        // Region total must equal the sum of its own comunas' attributed counts —
        // queried via findByComunaIdInAndFechaEventoAfter(regionComunaIds, ...),
        // never by nearest-region-centroid reassignment (removed findNearestRegionId).
        LocalDateTime notToday = LocalDateTime.now().minusDays(2);
        List<HeatAlertEvent> events = List.of(
            firmsEvent(COMUNA_A, 10.0, notToday),
            firmsEvent(COMUNA_A, 10.0, notToday)
        );
        when(heatAlertEventRepository.findByComunaIdInAndFechaEventoAfter(eq(List.of(COMUNA_A)), any()))
            .thenReturn(events);

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion(REGION_ID);

        assertEquals(2, snapshot.getFirmsCount());
    }

    @Test
    void recomputeRiskByRegion_noComunasInRegion_returnsZeroFirmsWithoutQuerying() {
        when(comunaInfoRepository.findByRegionId("region-empty")).thenReturn(List.of());

        TerritoryRiskSnapshot snapshot = service.recomputeRiskByRegion("region-empty");

        assertEquals(0, snapshot.getFirmsCount());
    }
}
