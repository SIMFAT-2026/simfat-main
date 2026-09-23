package com.simfat.backend.service.fwi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * S1e2b: the per-FWI-method alert threshold TABLE mechanism (task 1e.2, scope-reduced per
 * decision recorded in the S1e2a apply-progress "Next slice" note). This slice does NOT
 * attempt any quantile-matching calibration -- there is no real Van Wagner FWI history to
 * calibrate against yet, since S1e2a's spin-up runner has never been run against production
 * data. These tests only prove the TABLE mechanism itself: it loads real, committed,
 * per-method threshold data and, critically, lets a caller programmatically detect which
 * entries are real ({@code calibrated=true}) versus provisional placeholders
 * ({@code calibrated=false}).
 */
class FwiThresholdTableTest {

    private final FwiThresholdTable table = new FwiThresholdTable(new ObjectMapper());

    @Test
    void forMethod_proxyV1_returnsCalibratedProductionValues() {
        FwiThresholds thresholds = table.forMethod("PROXY_V1");

        // These MUST match ComunaRiskServiceImpl's existing FWI_PREVENTIVO/FWI_CRITICO/FWI_MAX
        // constants exactly -- this table is a config-form copy of already-shipped values, not
        // new numbers.
        assertEquals(20.0, thresholds.preventivo());
        assertEquals(45.0, thresholds.critico());
        assertEquals(50.0, thresholds.max());
        assertTrue(thresholds.calibrated(), "PROXY_V1 entries are real production values");
    }

    @Test
    void forMethod_vanWagner_returnsUncalibratedPlaceholderMarkedAsSuch() {
        FwiThresholds thresholds = table.forMethod("VAN_WAGNER");

        // Same numeric values as PROXY_V1 -- a safe, honest placeholder, NOT a claim that these
        // are the correct Van Wagner-scale values.
        assertEquals(20.0, thresholds.preventivo());
        assertEquals(45.0, thresholds.critico());
        assertEquals(50.0, thresholds.max());

        // The one assertion that matters most in this slice: a future consumer of this table
        // MUST be able to tell, programmatically, that these VAN_WAGNER numbers are not real yet.
        assertFalse(thresholds.calibrated(), "VAN_WAGNER thresholds are provisional, not calibrated");
        assertTrue(thresholds.source() != null && !thresholds.source().isBlank());
    }

    @Test
    void forMethod_unknownMethod_throwsClearException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> table.forMethod("NOT_A_REAL_METHOD")
        );

        assertTrue(ex.getMessage().contains("NOT_A_REAL_METHOD"));
    }

    @Test
    void constructor_missingResource_throwsClearException() {
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> new FwiThresholdTable(new ObjectMapper(), "fwi/does-not-exist.json")
        );

        assertTrue(ex.getMessage().contains("does-not-exist.json"));
    }
}
