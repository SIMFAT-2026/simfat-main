package com.simfat.backend.service.mapbiomas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * S1b3 follow-up: {@link MapbiomasSusceptibility#hasFlag} is the sanctioned way to read the
 * comma-joined {@code qualityFlag} field, so future consumers (S2a2) never hand-roll a comma
 * split or -- worse -- a substring {@code contains} check, which would wrongly match e.g.
 * {@code MAPBIOMAS_UNAVAILABLE} against {@code MAPBIOMAS_FUEL_UNAVAILABLE}.
 */
class MapbiomasSusceptibilityTest {

    private MapbiomasSusceptibility result(String qualityFlag) {
        return new MapbiomasSusceptibility(0.0, null, null, "v1", qualityFlag, null);
    }

    @Test
    void hasFlag_nullQualityFlag_returnsFalse() {
        assertFalse(result(null).hasFlag(MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE));
    }

    @Test
    void hasFlag_singleFlag_matchesExactly() {
        MapbiomasSusceptibility susceptibility = result(MapbiomasSusceptibility.MAPBIOMAS_FUEL_UNAVAILABLE);

        assertTrue(susceptibility.hasFlag(MapbiomasSusceptibility.MAPBIOMAS_FUEL_UNAVAILABLE));
    }

    @Test
    void hasFlag_combinedFlags_matchesEachToken() {
        MapbiomasSusceptibility susceptibility = result(
            MapbiomasSusceptibility.MAPBIOMAS_FUEL_UNAVAILABLE + ","
                + MapbiomasSusceptibility.MAPBIOMAS_BURNED_NORM_RAW_FALLBACK
        );

        assertTrue(susceptibility.hasFlag(MapbiomasSusceptibility.MAPBIOMAS_FUEL_UNAVAILABLE));
        assertTrue(susceptibility.hasFlag(MapbiomasSusceptibility.MAPBIOMAS_BURNED_NORM_RAW_FALLBACK));
    }

    @Test
    void hasFlag_doesNotSubstringMatchADifferentFlag() {
        // MAPBIOMAS_UNAVAILABLE is a substring of MAPBIOMAS_FUEL_UNAVAILABLE -- hasFlag must
        // require an exact token match after splitting on ",", not String#contains.
        MapbiomasSusceptibility susceptibility = result(MapbiomasSusceptibility.MAPBIOMAS_FUEL_UNAVAILABLE);

        assertFalse(susceptibility.hasFlag(MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE));
    }

    @Test
    void hasFlag_blankArgument_throwsIllegalArgumentException() {
        MapbiomasSusceptibility susceptibility = result(MapbiomasSusceptibility.MAPBIOMAS_FUEL_UNAVAILABLE);

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> susceptibility.hasFlag(" ")
        );
    }
}
