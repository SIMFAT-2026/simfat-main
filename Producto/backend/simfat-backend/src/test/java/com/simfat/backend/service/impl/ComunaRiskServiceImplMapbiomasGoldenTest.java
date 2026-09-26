package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.simfat.backend.integration.openeo.OpenEoServiceClient;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import com.simfat.backend.model.TerritoryWeatherObservation;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ComunaRiskSnapshotRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.NotificationService;
import com.simfat.backend.service.OpenWeatherFwiService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Golden/characterization regression for the S2a2 MapBiomas blend (design D3, "wM = 0
 * exact-equivalence test, award Consistencia").
 *
 * <p><b>Provenance of the expected values (MANDATORY per the design decision).</b> Every
 * literal expected value below was captured by RUNNING this exact fixture set against the
 * pre-blend {@code ComunaRiskServiceImpl} (the implementation as it existed immediately
 * before the S2a2 commit that adds the MapBiomas blend), NOT hand-computed. The capture
 * procedure was: (1) write this test with placeholder expected values, (2) run
 * {@code mvn -Dtest=ComunaRiskServiceImplMapbiomasGoldenTest test} against the pre-blend
 * code, (3) copy the actual values reported in the JUnit failure output
 * ("expected: <placeholder> but was: <actual>") into the assertions below, (4) re-run to
 * confirm GREEN against the pre-blend code. This file is committed on its own, BEFORE any
 * production code change, so it captures "today's" behaviour.
 *
 * <p>After the blend lands, {@code wM = 0} (or an absent {@code MapbiomasSusceptibilityService}
 * result) MUST reproduce every value here bit-identically -- see
 * {@code ComunaRiskServiceImplMapbiomasBlendTest#wM0_reproducesGoldenValues_forEveryScenario}
 * which re-runs these exact fixtures through the post-blend constructor with
 * {@code mapbiomasService.forComuna(...)} stubbed to {@code Optional.empty()} and asserts
 * equality against the constants captured here.
 */
@ExtendWith(MockitoExtension.class)
class ComunaRiskServiceImplMapbiomasGoldenTest {

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

    private ComunaInfo comunaInfo(String id) {
        ComunaInfo c = new ComunaInfo();
        c.setId(id);
        c.setRegionId("region-" + id);
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

    private void stubWeather(ComunaInfo comuna, Double fwiRaw) {
        if (fwiRaw == null) {
            when(weatherRepository.findTopByRegionIdOrderByObservedAtDesc(comuna.getId()))
                .thenReturn(Optional.empty());
            return;
        }
        TerritoryWeatherObservation obs = new TerritoryWeatherObservation();
        obs.setFwi(fwiRaw);
        when(weatherRepository.findTopByRegionIdOrderByObservedAtDesc(comuna.getId()))
            .thenReturn(Optional.of(obs));
    }

    private void stubFirms(ComunaInfo comuna, List<HeatAlertEvent> events) {
        when(firmsAttributionRouter.resolveForComuna(eq(comuna), any())).thenReturn(events);
    }

    private void stubReports(ComunaInfo comuna, int count) {
        List<com.simfat.backend.model.CitizenReport> reports = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            com.simfat.backend.model.CitizenReport r = new com.simfat.backend.model.CitizenReport();
            r.setCreatedAt(LocalDateTime.now().minusHours(1));
            reports.add(r);
        }
        when(citizenReportRepository.findByRegionId(comuna.getRegionId())).thenReturn(reports);
    }

    private void stubNoCopernicus(ComunaInfo comuna) {
        when(openEoObsRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(comuna.getRegionId(), IndicatorType.NDMI))
            .thenReturn(Optional.empty());
        when(openEoObsRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(comuna.getRegionId(), IndicatorType.NDVI))
            .thenReturn(Optional.empty());
    }

    private void stubCopernicus(ComunaInfo comuna, double ndmi, double ndvi) {
        OpenEoIndicatorObservation ndmiObs = new OpenEoIndicatorObservation();
        ndmiObs.setId("ndmi-" + comuna.getId());
        ndmiObs.setValue(ndmi);
        ndmiObs.setObservedAt(LocalDateTime.now().minusDays(1));
        OpenEoIndicatorObservation ndviObs = new OpenEoIndicatorObservation();
        ndviObs.setValue(ndvi);
        ndviObs.setObservedAt(LocalDateTime.now().minusDays(1));
        when(openEoObsRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(comuna.getRegionId(), IndicatorType.NDMI))
            .thenReturn(Optional.of(ndmiObs));
        when(openEoObsRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(comuna.getRegionId(), IndicatorType.NDVI))
            .thenReturn(Optional.of(ndviObs));
    }

    private void assertGolden(
        ComunaRiskSnapshot snapshot,
        double scoreComposite,
        double componentFwi,
        double componentFirms,
        double componentReports,
        Double componentNdmi,
        Double componentNdvi,
        String alertLevel,
        String mode
    ) {
        assertAll(
            () -> assertEquals(scoreComposite, snapshot.getScoreComposite(), 0.0, "scoreComposite"),
            () -> assertEquals(componentFwi, snapshot.getComponentFwi(), 0.0, "componentFwi"),
            () -> assertEquals(componentFirms, snapshot.getComponentFirms(), 0.0, "componentFirms"),
            () -> assertEquals(componentReports, snapshot.getComponentReports(), 0.0, "componentReports"),
            () -> assertEquals(componentNdmi, snapshot.getComponentNdmi(), "componentNdmi"),
            () -> assertEquals(componentNdvi, snapshot.getComponentNdvi(), "componentNdvi"),
            () -> assertEquals(alertLevel, snapshot.getAlertLevel(), "alertLevel"),
            () -> assertEquals(mode, snapshot.getMode(), "mode")
        );
    }

    @Test
    void standard_normal_allZero() {
        ComunaInfo comuna = comunaInfo("comuna-std-normal");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        stubWeather(comuna, null);
        stubFirms(comuna, List.of());
        stubReports(comuna, 0);
        stubNoCopernicus(comuna);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertGolden(snapshot, 0.0, 0.0, 0.0, 0.0, null, null, "NORMAL", "STANDARD");
    }

    @Test
    void standard_preventivo_viaScore() {
        ComunaInfo comuna = comunaInfo("comuna-std-preventivo");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        // minusDays(3), not minusHours(20): isToday() treats the LocalDateTime as if it were
        // UTC and reinterprets it in America/Santiago, so a small hour offset can round-trip
        // back to "today" depending on the machine's default zone and wall-clock time at test
        // run (this machine's default zone IS America/Santiago). A 3-day margin is robust
        // regardless of when/where this runs.
        LocalDateTime notToday = LocalDateTime.now().minusDays(3);
        stubWeather(comuna, 15.0);
        stubFirms(comuna, List.of(firmsEvent(notToday, 50.0), firmsEvent(notToday, 50.0), firmsEvent(notToday, 50.0)));
        stubReports(comuna, 3);
        stubNoCopernicus(comuna);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertGolden(snapshot, 0.5073, 0.156, 0.2013, 0.15, null, null, "PREVENTIVO", "STANDARD");
    }

    @Test
    void standard_alto_viaFwiOverride() {
        ComunaInfo comuna = comunaInfo("comuna-std-alto");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        // minusDays(3), not minusHours(20): isToday() treats the LocalDateTime as if it were
        // UTC and reinterprets it in America/Santiago, so a small hour offset can round-trip
        // back to "today" depending on the machine's default zone and wall-clock time at test
        // run (this machine's default zone IS America/Santiago). A 3-day margin is robust
        // regardless of when/where this runs.
        LocalDateTime notToday = LocalDateTime.now().minusDays(3);
        stubWeather(comuna, 25.0);
        stubFirms(comuna, List.of(firmsEvent(notToday, 10.0)));
        stubReports(comuna, 1);
        stubNoCopernicus(comuna);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertGolden(snapshot, 0.3661, 0.26, 0.0561, 0.05, null, null, "ALTO", "STANDARD");
    }

    @Test
    void standard_critico_viaFwiRaw() {
        ComunaInfo comuna = comunaInfo("comuna-std-critico");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        // minusDays(3), not minusHours(20): isToday() treats the LocalDateTime as if it were
        // UTC and reinterprets it in America/Santiago, so a small hour offset can round-trip
        // back to "today" depending on the machine's default zone and wall-clock time at test
        // run (this machine's default zone IS America/Santiago). A 3-day margin is robust
        // regardless of when/where this runs.
        LocalDateTime notToday = LocalDateTime.now().minusDays(3);
        stubWeather(comuna, 48.0);
        stubFirms(comuna, List.of(firmsEvent(notToday, 10.0)));
        stubReports(comuna, 1);
        stubNoCopernicus(comuna);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertGolden(snapshot, 0.6053, 0.4992, 0.0561, 0.05, null, null, "CRITICO", "STANDARD");
    }

    @Test
    void enhanced_normal_allZero() {
        ComunaInfo comuna = comunaInfo("comuna-enh-normal");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        stubWeather(comuna, null);
        stubFirms(comuna, List.of());
        stubReports(comuna, 0);
        stubCopernicus(comuna, 0.4, 0.1);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertGolden(snapshot, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "NORMAL", "ENHANCED");
    }

    @Test
    void enhanced_alto_viaFwiOverride() {
        ComunaInfo comuna = comunaInfo("comuna-enh-alto");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        // minusDays(3), not minusHours(20): isToday() treats the LocalDateTime as if it were
        // UTC and reinterprets it in America/Santiago, so a small hour offset can round-trip
        // back to "today" depending on the machine's default zone and wall-clock time at test
        // run (this machine's default zone IS America/Santiago). A 3-day margin is robust
        // regardless of when/where this runs.
        LocalDateTime notToday = LocalDateTime.now().minusDays(3);
        stubWeather(comuna, 25.0);
        stubFirms(comuna, List.of(firmsEvent(notToday, 10.0)));
        stubReports(comuna, 1);
        stubCopernicus(comuna, -0.1, 0.4);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertGolden(snapshot, 0.4057, 0.19, 0.0306, 0.0133, 0.1375, 0.0343, "ALTO", "ENHANCED");
    }

    @Test
    void enhanced_critico_viaTodayFirms() {
        ComunaInfo comuna = comunaInfo("comuna-enh-critico");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        LocalDateTime today = LocalDateTime.now(ZoneOffset.UTC);
        stubWeather(comuna, null);
        stubFirms(comuna, List.of(firmsEvent(today, 5.0)));
        stubReports(comuna, 0);
        stubCopernicus(comuna, 0.4, 0.1);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertGolden(snapshot, 0.0261, 0.0, 0.0261, 0.0, 0.0, 0.0, "CRITICO", "ENHANCED");
    }
}
