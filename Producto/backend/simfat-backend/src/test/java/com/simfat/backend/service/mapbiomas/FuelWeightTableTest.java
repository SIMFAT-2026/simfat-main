package com.simfat.backend.service.mapbiomas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * S2a1: the committed fuel-weight table mechanism (design D3's {@code fuel-weights-v1.json}).
 * Mirrors {@code FwiThresholdTable}'s load-from-classpath idiom: fail loud on a
 * missing/malformed resource, fail loud (not silently zero) on an unknown class code, and
 * treat class 27 (unobserved) as excluded from the fuel weighted sum rather than an implicit
 * zero weight -- see the resource's own {@code provenance.notes} for why.
 */
class FuelWeightTableTest {

    private final FuelWeightTable table = new FuelWeightTable(new ObjectMapper());

    @Test
    void weightFor_knownClass_returnsLiteratureWeight() {
        // Class 9 (silviculture/plantations) carries the STRONGEST literature evidence
        // (Bowman 2018, McWethy 2018) and the highest weight in the table.
        assertEquals(1.00, table.weightFor("9"));
        assertEquals(0.80, table.weightFor("66"));
    }

    @Test
    void weightFor_unknownClass_throwsClearException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> table.weightFor("999")
        );

        assertTrue(ex.getMessage().contains("999"));
    }

    @Test
    void isExcluded_class27_isTrue() {
        assertTrue(table.isExcluded("27"));
    }

    @Test
    void isExcluded_knownFuelClass_isFalse() {
        assertEquals(false, table.isExcluded("9"));
    }

    @Test
    void constructor_missingResource_throwsClearException() {
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> new FuelWeightTable(new ObjectMapper(), "mapbiomas/does-not-exist.json")
        );

        assertTrue(ex.getMessage().contains("does-not-exist.json"));
    }

    @Test
    void realResource_coversEveryClassCodeObservedInS1a2RealXlsxData() {
        // Cross-check against the MapBiomas Land Cover Col 2 class codes actually present in
        // the real per-comuna xlsx (verified in S1a2): forest family, silviculture, shrubland,
        // grassland, steppe, pasture, agriculture, infrastructure, and the non-vegetated /
        // water / unobserved family. 27 is EXCLUDED by design, not weighted, so it must not
        // appear as a classWeights key.
        String[] weighted = {
            "9", "66", "12", "15", "3", "59", "60", "67", "18", "63", "11", "29", "24", "33", "34", "25", "23", "61"
        };
        for (String code : weighted) {
            // Must not throw -- every observed code has an explicit literature-derived weight.
            table.weightFor(code);
        }
        assertTrue(table.isExcluded("27"), "class 27 (unobserved) must be excluded, not weighted");
    }
}
