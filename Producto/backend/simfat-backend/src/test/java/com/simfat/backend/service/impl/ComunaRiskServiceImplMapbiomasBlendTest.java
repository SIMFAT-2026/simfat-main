package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.simfat.backend.service.mapbiomas.MapbiomasSusceptibility;
import com.simfat.backend.service.mapbiomas.MapbiomasSusceptibilityService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * S2a2: the MapBiomas blend wired into {@code ComunaRiskServiceImpl} (design D3). The
 * "award Consistencia" wM=0 exact-equivalence regression itself lives in
 * {@link ComunaRiskServiceImplMapbiomasGoldenTest} (updated to the post-blend 10-arg
 * constructor with {@code mapbiomasService.forComuna(...)} stubbed to
 * {@code Optional.empty()}) -- this class covers the NEW behaviour the blend introduces:
 * the formula itself, missing/unavailable-data handling, the component-sum invariant,
 * override preservation across weights, threshold externalization and the NFR-7 bulk-read
 * wiring in {@code recomputeAllComunas}.
 */
@ExtendWith(MockitoExtension.class)
class ComunaRiskServiceImplMapbiomasBlendTest {

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
    @Mock
    private MapbiomasSusceptibilityService mapbiomasService;

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
            firmsAttributionRouter,
            mapbiomasService
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
            when(weatherRepository.findTopByRegionIdOrderByObservedAtDesc(comuna.getId())).thenReturn(Optional.empty());
            return;
        }
        TerritoryWeatherObservation obs = new TerritoryWeatherObservation();
        obs.setFwi(fwiRaw);
        when(weatherRepository.findTopByRegionIdOrderByObservedAtDesc(comuna.getId())).thenReturn(Optional.of(obs));
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

    // Mirrors ComunaRiskServiceImplMapbiomasGoldenTest#stubCopernicus -- duplicated here
    // deliberately (same rationale as #round4 below) so this class does not reach into the
    // golden test's private fixtures.
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

    // Mirrors ComunaRiskServiceImpl's private round4 -- duplicated here deliberately so the
    // test computes its own expected value independently of the production rounding call site.
    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    @Test
    void blend_appliesFormulaAfterModeResolves() {
        ComunaInfo comuna = comunaInfo("comuna-blend");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        stubWeather(comuna, 15.0); // fwiNorm=0.3 -> cFwi=0.156, below FWI_PREVENTIVO(20)
        stubFirms(comuna, List.of());
        stubReports(comuna, 0);
        stubNoCopernicus(comuna);
        // sDyn = 0.156 (STANDARD, fwi only)
        when(mapbiomasService.forComuna(comuna.getId())).thenReturn(Optional.of(
            new MapbiomasSusceptibility(0.9, 0.8, 0.6, "fuego-col1@2017-partial", null, 0.0)
        ));
        service.setMapbiomasWeight(0.35);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        double sDyn = 0.156;
        double expectedScore = round4((1 - 0.35) * sDyn + 0.35 * 0.9);
        assertEquals(expectedScore, snapshot.getScoreComposite());
        assertEquals(round4(0.35 * 0.9), snapshot.getComponentMapbiomas());
        assertEquals(0.35, snapshot.getMapbiomasWeight());
        assertEquals(0.8, snapshot.getMapbiomasFuelIndex());
        assertEquals(0.6, snapshot.getMapbiomasHistoryIndex());
        assertEquals("fuego-col1@2017-partial", snapshot.getMapbiomasDataVersion());
    }

    @Test
    void missingStatsRow_wEffZero_scoreUnchanged_noException() {
        ComunaInfo comuna = comunaInfo("comuna-nodata");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        stubWeather(comuna, 15.0);
        stubFirms(comuna, List.of());
        stubReports(comuna, 0);
        stubNoCopernicus(comuna);
        when(mapbiomasService.forComuna(comuna.getId())).thenReturn(Optional.empty());
        service.setMapbiomasWeight(0.35);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertEquals(round4(0.156), snapshot.getScoreComposite());
        assertEquals(0.0, snapshot.getMapbiomasWeight());
        assertNull(snapshot.getMapbiomasDataVersion());
    }

    @Test
    void mapbiomasUnavailableFlag_wEffZero_evenWithPresentResult() {
        ComunaInfo comuna = comunaInfo("comuna-flagged-unavailable");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        stubWeather(comuna, 15.0);
        stubFirms(comuna, List.of());
        stubReports(comuna, 0);
        stubNoCopernicus(comuna);
        when(mapbiomasService.forComuna(comuna.getId())).thenReturn(Optional.of(
            new MapbiomasSusceptibility(0.0, null, null, "fuego-col1@2017-partial", MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE, null)
        ));
        service.setMapbiomasWeight(0.35);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertEquals(round4(0.156), snapshot.getScoreComposite());
        assertEquals(0.0, snapshot.getMapbiomasWeight());
        assertEquals(MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE, snapshot.getMapbiomasQualityFlag());
    }

    @Test
    void componentSumInvariant_matchesScoreComposite_whenNotClamped() {
        ComunaInfo comuna = comunaInfo("comuna-sum-invariant");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        stubWeather(comuna, 15.0);
        stubFirms(comuna, List.of());
        stubReports(comuna, 0);
        stubNoCopernicus(comuna);
        when(mapbiomasService.forComuna(comuna.getId())).thenReturn(Optional.of(
            new MapbiomasSusceptibility(0.9, 0.8, 0.6, "fuego-col1@2017-partial", null, 0.0)
        ));
        service.setMapbiomasWeight(0.35);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        double sum = snapshot.getComponentFwi() + snapshot.getComponentFirms() + snapshot.getComponentReports()
            + snapshot.getComponentMapbiomas();
        assertEquals(snapshot.getScoreComposite(), sum, 1e-4);
    }

    /**
     * ENHANCED-mode fixtures for {@link
     * #componentSumInvariant_matchesScoreComposite_enhancedModeNotClamped}, chosen (via a small
     * offline search over the production formula) specifically to hit the rounding boundary
     * where rounding {@code cNdmi}/{@code cNdvi} to 4 decimals BEFORE scaling by {@code (1 -
     * wEff)} (the pre-fix code) diverges from rounding the raw product AFTER scaling (the
     * fixed code) by more than the {@code 1e-4} sum-invariant tolerance. All three use
     * pre-registered {@code wM} grid values (0.10/0.25/0.35).
     */
    private static Stream<EnhancedRoundingCase> enhancedRoundingBoundaryCases() {
        return Stream.of(
            new EnhancedRoundingCase(0.0, -0.398, 0.113, 0.10),
            new EnhancedRoundingCase(0.0, -0.386, 0.105, 0.25),
            new EnhancedRoundingCase(0.0, -0.338, 0.126, 0.35)
        );
    }

    private record EnhancedRoundingCase(double fwiRaw, double ndmi, double ndvi, double wM) {}

    @ParameterizedTest
    @MethodSource("enhancedRoundingBoundaryCases")
    void componentSumInvariant_matchesScoreComposite_enhancedModeNotClamped(EnhancedRoundingCase fixture) {
        ComunaInfo comuna = comunaInfo("comuna-sum-invariant-enh-" + fixture.wM());
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        stubWeather(comuna, fixture.fwiRaw());
        stubFirms(comuna, List.of());
        stubReports(comuna, 0);
        stubCopernicus(comuna, fixture.ndmi(), fixture.ndvi());
        when(mapbiomasService.forComuna(comuna.getId())).thenReturn(Optional.of(
            new MapbiomasSusceptibility(0.9, 0.8, 0.6, "fuego-col1@2017-partial", null, 0.0)
        ));
        service.setMapbiomasWeight(fixture.wM());

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        double sum = snapshot.getComponentFwi() + snapshot.getComponentFirms() + snapshot.getComponentReports()
            + snapshot.getComponentNdmi() + snapshot.getComponentNdvi() + snapshot.getComponentMapbiomas();
        assertEquals(snapshot.getScoreComposite(), sum, 1e-4,
            "wM=" + fixture.wM() + " fwiRaw=" + fixture.fwiRaw() + " ndmi=" + fixture.ndmi() + " ndvi=" + fixture.ndvi());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.35, 0.50})
    void overridesUnchanged_todayFirms_alwaysCriticoRegardlessOfWeight(double wM) {
        ComunaInfo comuna = comunaInfo("comuna-today-firms");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        LocalDateTime today = LocalDateTime.now(ZoneOffset.UTC);
        stubWeather(comuna, null);
        stubFirms(comuna, List.of(firmsEvent(today, 1.0)));
        stubReports(comuna, 0);
        stubNoCopernicus(comuna);
        when(mapbiomasService.forComuna(comuna.getId())).thenReturn(Optional.of(
            new MapbiomasSusceptibility(0.0, 0.0, 0.0, "fuego-col1@2017-partial", null, 0.0)
        ));
        service.setMapbiomasWeight(wM);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertEquals("CRITICO", snapshot.getAlertLevel());
    }

    @Test
    void thresholds_areExternalizedProperties_notDeadCode() {
        ComunaInfo comuna = comunaInfo("comuna-threshold-override");
        when(comunaRepository.findById(comuna.getId())).thenReturn(Optional.of(comuna));
        stubWeather(comuna, 15.0); // cFwi=0.156, no override (fwiRaw<20)
        stubFirms(comuna, List.of());
        stubReports(comuna, 0);
        stubNoCopernicus(comuna);
        when(mapbiomasService.forComuna(comuna.getId())).thenReturn(Optional.empty());
        // Lower SCORE_PREVENTIVO below 0.156 so the (otherwise NORMAL) score now qualifies.
        service.setScorePreventivo(0.10);

        ComunaRiskSnapshot snapshot = service.recomputeByComuna(comuna.getId());

        assertEquals("PREVENTIVO", snapshot.getAlertLevel());
    }

    @Test
    void recomputeAllComunas_usesBulkForComunas_neverPerComunaLookup() {
        ComunaInfo c1 = comunaInfo("comuna-1");
        ComunaInfo c2 = comunaInfo("comuna-2");
        when(comunaRepository.findAll()).thenReturn(List.of(c1, c2));
        when(comunaRepository.findById("comuna-1")).thenReturn(Optional.of(c1));
        when(comunaRepository.findById("comuna-2")).thenReturn(Optional.of(c2));
        stubWeather(c1, null);
        stubWeather(c2, null);
        stubFirms(c1, List.of());
        stubFirms(c2, List.of());
        stubReports(c1, 0);
        stubReports(c2, 0);
        stubNoCopernicus(c1);
        stubNoCopernicus(c2);
        when(mapbiomasService.forComunas(anyCollection())).thenReturn(Map.of());

        service.recomputeAllComunas();

        verify(mapbiomasService, times(1)).forComunas(anyCollection());
        verify(mapbiomasService, never()).forComuna(any());
    }
}
