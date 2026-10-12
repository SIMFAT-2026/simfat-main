package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.simfat.backend.integration.openeo.OpenEoServiceClient;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaRiskSnapshot;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MRB-10 (SC-P2): the dominance boundary at which the MapBiomas blend weight {@code wM}
 * becomes the largest single component of the composite score, in BOTH modes.
 *
 * <p>This class has two halves:
 *
 * <ul>
 *   <li><b>Constant-drift guard</b> ({@code dominance_*BoundaryBothSides}): a pure arithmetic
 *       property of the configured weights, re-derived from {@link ComunaRiskServiceImpl}'s own
 *       package-private test seams ({@link ComunaRiskServiceImpl#standardFwiWeight()} / {@link
 *       ComunaRiskServiceImpl#enhancedFwiWeight()}) rather than duplicated magic numbers, so it
 *       flags a future change to the production weight constants without touching production
 *       code at all. It does NOT exercise {@code recomputeByComuna} -- see the note on each test
 *       below.</li>
 *   <li><b>End-to-end dominance assertions</b> ({@code dominance_*_endToEnd}): the same
 *       breakeven, but proven through the real production code path ({@code
 *       recomputeByComuna}), so this class also verifies the constant-drift guard's algebra
 *       actually matches what {@link ComunaRiskServiceImpl} persists, not just a test-local
 *       re-derivation of it.</li>
 * </ul>
 *
 * <p>Derivation (stated here per the S2a2 apply instructions): MapBiomas is the largest single
 * component exactly when its raw weight exceeds the largest dynamic component's raw weight
 * scaled by (1 - wM) -- comparing both components at their theoretical maximum contribution
 * (S_mapbiomas = fwiNorm = 1, the worst case for dominance):
 * <pre>
 *   wM &gt; (1 - wM) * W_FWI
 *   wM + wM * W_FWI &gt; W_FWI
 *   wM * (1 + W_FWI) &gt; W_FWI
 *   wM &gt; W_FWI / (1 + W_FWI)                      &lt;-- exact breakeven
 * </pre>
 *
 * <ul>
 *   <li>STANDARD: W_FWI_STD = 0.52 -&gt; breakeven = 0.52 / 1.52 = 0.342105... Design D3 states
 *       this rounded as "STANDARD threshold 0.34"; the spec's MRB-10a worked example
 *       (wM=0.35 -&gt; dominant, wM=0.25 -&gt; not dominant) sits cleanly on either side of the
 *       real breakeven.</li>
 *   <li>ENHANCED: W_FWI_ENH = 0.38 -&gt; breakeven = 0.38 / 1.38 = 0.275362... Design D3 states
 *       this rounded as "ENHANCED 0.28"; the spec's MRB-10b worked boundary ("wM &gt;= 0.29")
 *       is likewise past the real breakeven.</li>
 * </ul>
 *
 * <p><b>No discrepancy found</b> between the spec's/design's rounded figures and the exact
 * algebraic breakeven computed here from the actual production weights.
 */
@ExtendWith(MockitoExtension.class)
class ComunaRiskServiceImplDominanceTest {

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
    }

    // Only the *_endToEnd tests call recomputeByComuna and need these two stubs -- the pure
    // arithmetic constant-drift-guard tests never touch the mocks, so this is NOT in @BeforeEach
    // (Mockito's strict stubbing would flag it there as unnecessary on every arithmetic-only run).
    private void stubSnapshotPersistence() {
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.findTopByComunaIdOrderByComputedAtDesc(any())).thenReturn(Optional.empty());
    }

    // Mirrors ComunaRiskServiceImplMapbiomasBlendTest's fixture helpers -- duplicated here
    // deliberately (same rationale as that class's #round4 comment) so this end-to-end half
    // does not reach into another test class's private fixtures.
    private ComunaInfo comunaInfo(String id) {
        ComunaInfo c = new ComunaInfo();
        c.setId(id);
        c.setRegionId("region-" + id);
        c.setNombre(id);
        c.setCenterLat(-37.5);
        c.setCenterLon(-72.5);
        return c;
    }

    private void stubMaxFwiStandard(ComunaInfo comuna) {
        TerritoryWeatherObservation obs = new TerritoryWeatherObservation();
        obs.setFwi(50.0); // FWI_MAX -> fwiNorm = 1.0, the worst case for dominance.
        when(weatherRepository.findTopByRegionIdOrderByObservedAtDesc(comuna.getId())).thenReturn(Optional.of(obs));
        when(firmsAttributionRouter.resolveForComuna(eq(comuna), any())).thenReturn(List.of());
        when(citizenReportRepository.findByRegionId(comuna.getRegionId())).thenReturn(List.of());
        when(openEoObsRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(comuna.getRegionId(), IndicatorType.NDMI))
            .thenReturn(Optional.empty());
        when(openEoObsRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(comuna.getRegionId(), IndicatorType.NDVI))
            .thenReturn(Optional.empty());
    }

    private void stubMaxFwiEnhanced(ComunaInfo comuna) {
        TerritoryWeatherObservation obs = new TerritoryWeatherObservation();
        obs.setFwi(50.0); // FWI_MAX -> fwiNorm = 1.0, the worst case for dominance.
        when(weatherRepository.findTopByRegionIdOrderByObservedAtDesc(comuna.getId())).thenReturn(Optional.of(obs));
        when(firmsAttributionRouter.resolveForComuna(eq(comuna), any())).thenReturn(List.of());
        when(citizenReportRepository.findByRegionId(comuna.getRegionId())).thenReturn(List.of());
        // NDMI=0.4 (NDMI_WET) and NDVI=0.1 (NDVI_MIN) both normalize to 0 -- fresh Copernicus
        // is present (so mode=ENHANCED) but contributes nothing, isolating FWI as the largest
        // dynamic component exactly as the algebraic derivation assumes.
        OpenEoIndicatorObservation ndmiObs = new OpenEoIndicatorObservation();
        ndmiObs.setId("ndmi-" + comuna.getId());
        ndmiObs.setValue(0.4);
        ndmiObs.setObservedAt(LocalDateTime.now().minusDays(1));
        OpenEoIndicatorObservation ndviObs = new OpenEoIndicatorObservation();
        ndviObs.setValue(0.1);
        ndviObs.setObservedAt(LocalDateTime.now().minusDays(1));
        when(openEoObsRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(comuna.getRegionId(), IndicatorType.NDMI))
            .thenReturn(Optional.of(ndmiObs));
        when(openEoObsRepository.findTopByRegionIdAndIndicatorOrderByObservedAtDesc(comuna.getRegionId(), IndicatorType.NDVI))
            .thenReturn(Optional.of(ndviObs));
    }

    private void stubMapbiomasFullSusceptibility(ComunaInfo comuna) {
        when(mapbiomasService.forComuna(comuna.getId())).thenReturn(Optional.of(
            new MapbiomasSusceptibility(1.0, 0.9, 0.9, "fuego-col1@2017-partial", null, 0.0)
        ));
    }

    private static boolean isMapbiomasDominant(double wM, double largestDynamicWeight) {
        return wM > (1.0 - wM) * largestDynamicWeight;
    }

    // Renamed/relabeled per S2a2 apply instructions: this test never calls recomputeByComuna --
    // it re-derives wM > (1-wM)*W in a test-local method and is a guard against the production
    // weight CONSTANTS drifting, not a proof the production CODE implements the formula. See
    // dominance_standardMode_endToEnd below for that proof.
    @Test
    void dominance_standardMode_constantDriftGuard_boundaryBothSides() {
        double wFwiStd = ComunaRiskServiceImpl.standardFwiWeight();
        assertEquals(0.52, wFwiStd);
        double breakeven = wFwiStd / (1.0 + wFwiStd);

        assertFalse(isMapbiomasDominant(breakeven - 0.001, wFwiStd), "just below breakeven: FWI still dominant");
        assertTrue(isMapbiomasDominant(breakeven + 0.001, wFwiStd), "just above breakeven: MapBiomas now dominant");
        // Spec MRB-10a worked example, re-checked against the real weight.
        assertTrue(isMapbiomasDominant(0.35, wFwiStd));
        assertFalse(isMapbiomasDominant(0.25, wFwiStd));
    }

    // Renamed/relabeled per S2a2 apply instructions -- see the Javadoc note on
    // #dominance_standardMode_constantDriftGuard_boundaryBothSides above.
    @Test
    void dominance_enhancedMode_constantDriftGuard_boundaryBothSides() {
        double wFwiEnh = ComunaRiskServiceImpl.enhancedFwiWeight();
        assertEquals(0.38, wFwiEnh);
        double breakeven = wFwiEnh / (1.0 + wFwiEnh);

        assertFalse(isMapbiomasDominant(breakeven - 0.001, wFwiEnh), "just below breakeven: FWI still dominant");
        assertTrue(isMapbiomasDominant(breakeven + 0.001, wFwiEnh), "just above breakeven: MapBiomas now dominant");
        // Spec MRB-10b worked boundary (~0.29), re-checked against the real weight.
        assertTrue(isMapbiomasDominant(0.29, wFwiEnh));
    }

    /**
     * End-to-end proof of the STANDARD-mode breakeven THROUGH {@code recomputeByComuna}, not a
     * test-local re-derivation: fwiNorm=1.0 (FWI_MAX), zero FIRMS/reports (so FWI is the only,
     * and therefore largest, dynamic component) and S_mapbiomas=1.0 (MapBiomas at its own
     * maximum contribution) -- the exact worst-case-for-dominance scenario the algebra above
     * assumes.
     */
    @Test
    void dominance_standardMode_endToEnd() {
        stubSnapshotPersistence();
        ComunaInfo comunaDominant = comunaInfo("comuna-dominance-std-035");
        when(comunaRepository.findById(comunaDominant.getId())).thenReturn(Optional.of(comunaDominant));
        stubMaxFwiStandard(comunaDominant);
        stubMapbiomasFullSusceptibility(comunaDominant);
        service.setMapbiomasWeight(0.35);
        ComunaRiskSnapshot dominant = service.recomputeByComuna(comunaDominant.getId());
        assertEquals("STANDARD", dominant.getMode());
        assertTrue(dominant.getComponentMapbiomas() > dominant.getComponentFwi(),
            "wM=0.35 (above the 0.342 breakeven) must make MapBiomas the largest component");

        ComunaInfo comunaNotDominant = comunaInfo("comuna-dominance-std-025");
        when(comunaRepository.findById(comunaNotDominant.getId())).thenReturn(Optional.of(comunaNotDominant));
        stubMaxFwiStandard(comunaNotDominant);
        stubMapbiomasFullSusceptibility(comunaNotDominant);
        service.setMapbiomasWeight(0.25);
        ComunaRiskSnapshot notDominant = service.recomputeByComuna(comunaNotDominant.getId());
        assertEquals("STANDARD", notDominant.getMode());
        assertTrue(notDominant.getComponentMapbiomas() < notDominant.getComponentFwi(),
            "wM=0.25 (below the 0.342 breakeven) must leave FWI as the largest component");
    }

    /**
     * End-to-end proof of the ENHANCED-mode breakeven THROUGH {@code recomputeByComuna} -- same
     * rationale as {@link #dominance_standardMode_endToEnd}, with fresh Copernicus observations
     * present (NDMI=NDMI_WET, NDVI=NDVI_MIN, both normalizing to 0) so mode=ENHANCED while FWI
     * remains the only nonzero, and therefore largest, dynamic component.
     */
    @Test
    void dominance_enhancedMode_endToEnd() {
        stubSnapshotPersistence();
        ComunaInfo comunaDominant = comunaInfo("comuna-dominance-enh-029");
        when(comunaRepository.findById(comunaDominant.getId())).thenReturn(Optional.of(comunaDominant));
        stubMaxFwiEnhanced(comunaDominant);
        stubMapbiomasFullSusceptibility(comunaDominant);
        service.setMapbiomasWeight(0.29);
        ComunaRiskSnapshot dominant = service.recomputeByComuna(comunaDominant.getId());
        assertEquals("ENHANCED", dominant.getMode());
        assertTrue(dominant.getComponentMapbiomas() > dominant.getComponentFwi(),
            "wM=0.29 (above the 0.275 breakeven) must make MapBiomas the largest component");

        ComunaInfo comunaNotDominant = comunaInfo("comuna-dominance-enh-025");
        when(comunaRepository.findById(comunaNotDominant.getId())).thenReturn(Optional.of(comunaNotDominant));
        stubMaxFwiEnhanced(comunaNotDominant);
        stubMapbiomasFullSusceptibility(comunaNotDominant);
        service.setMapbiomasWeight(0.25);
        ComunaRiskSnapshot notDominant = service.recomputeByComuna(comunaNotDominant.getId());
        assertEquals("ENHANCED", notDominant.getMode());
        assertTrue(notDominant.getComponentMapbiomas() < notDominant.getComponentFwi(),
            "wM=0.25 (below the 0.275 breakeven) must leave FWI as the largest component");
    }
}
