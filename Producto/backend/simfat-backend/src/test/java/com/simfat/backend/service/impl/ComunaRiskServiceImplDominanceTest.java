package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * MRB-10 (SC-P2): the dominance boundary at which the MapBiomas blend weight {@code wM}
 * becomes the largest single component of the composite score, in BOTH modes. This is a pure
 * arithmetic property of the configured weights -- it does not need
 * {@code ComunaRiskServiceImpl}'s repositories/collaborators, so it lives in its own
 * Mockito-free test class rather than {@link ComunaRiskServiceImplMapbiomasBlendTest} (which
 * uses strict-stubbing Mockito fixtures that these tests would never touch).
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
 * <p>The weights below are read from {@link ComunaRiskServiceImpl}'s own package-private test
 * seams ({@link ComunaRiskServiceImpl#standardFwiWeight()} /
 * {@link ComunaRiskServiceImpl#enhancedFwiWeight()}), NOT duplicated as magic numbers, so this
 * test cannot silently drift from the production constants (STANDARD .52/.33/.15, ENHANCED
 * .38/.22/.18/.08/.04 per design D3).
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
class ComunaRiskServiceImplDominanceTest {

    private static boolean isMapbiomasDominant(double wM, double largestDynamicWeight) {
        return wM > (1.0 - wM) * largestDynamicWeight;
    }

    @Test
    void dominance_standardMode_boundaryBothSides() {
        double wFwiStd = ComunaRiskServiceImpl.standardFwiWeight();
        assertEquals(0.52, wFwiStd);
        double breakeven = wFwiStd / (1.0 + wFwiStd);

        assertFalse(isMapbiomasDominant(breakeven - 0.001, wFwiStd), "just below breakeven: FWI still dominant");
        assertTrue(isMapbiomasDominant(breakeven + 0.001, wFwiStd), "just above breakeven: MapBiomas now dominant");
        // Spec MRB-10a worked example, re-checked against the real weight.
        assertTrue(isMapbiomasDominant(0.35, wFwiStd));
        assertFalse(isMapbiomasDominant(0.25, wFwiStd));
    }

    @Test
    void dominance_enhancedMode_boundaryBothSides() {
        double wFwiEnh = ComunaRiskServiceImpl.enhancedFwiWeight();
        assertEquals(0.38, wFwiEnh);
        double breakeven = wFwiEnh / (1.0 + wFwiEnh);

        assertFalse(isMapbiomasDominant(breakeven - 0.001, wFwiEnh), "just below breakeven: FWI still dominant");
        assertTrue(isMapbiomasDominant(breakeven + 0.001, wFwiEnh), "just above breakeven: MapBiomas now dominant");
        // Spec MRB-10b worked boundary (~0.29), re-checked against the real weight.
        assertTrue(isMapbiomasDominant(0.29, wFwiEnh));
    }
}
