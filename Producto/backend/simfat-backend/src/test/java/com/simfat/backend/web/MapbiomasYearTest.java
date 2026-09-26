package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.web.MapbiomasYear.Indicator;
import org.junit.jupiter.api.Test;

/**
 * Design D5 / task 2b2a.1: bounded {@code year} parameter, one class holding both
 * MapBiomas indicator ranges (LANDCOVER 1999-2024, BURNED 2013-2025), strict
 * {@code [0-9]{1,4}} parsing (no lenient coercion), 400 on out-of-range or malformed input,
 * default = latest available year when the parameter is omitted (never on an empty string).
 */
class MapbiomasYearTest {

    @Test
    void landcoverBoundsAreInclusive() {
        assertThat(MapbiomasYear.resolve(Indicator.LANDCOVER, "1999").year()).isEqualTo(1999);
        assertThat(MapbiomasYear.resolve(Indicator.LANDCOVER, "2024").year()).isEqualTo(2024);
    }

    @Test
    void burnedBoundsAreInclusive() {
        assertThat(MapbiomasYear.resolve(Indicator.BURNED, "2013").year()).isEqualTo(2013);
        assertThat(MapbiomasYear.resolve(Indicator.BURNED, "2025").year()).isEqualTo(2025);
    }

    @Test
    void justOutsideLandcoverRangeIsRejected() {
        assertThatThrownBy(() -> MapbiomasYear.resolve(Indicator.LANDCOVER, "1998"))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> MapbiomasYear.resolve(Indicator.LANDCOVER, "2025"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void justOutsideBurnedRangeIsRejected() {
        assertThatThrownBy(() -> MapbiomasYear.resolve(Indicator.BURNED, "2012"))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> MapbiomasYear.resolve(Indicator.BURNED, "2026"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void malformedValuesAreRejectedWithoutAnUnhandledException() {
        String[] malformed = {"abc", "2020.5", "1e3", "-1", "20 20", " ", "99999999999"};
        for (String value : malformed) {
            assertThatThrownBy(() -> MapbiomasYear.resolve(Indicator.LANDCOVER, value))
                .as("value '%s' must be rejected as a 400, not propagate a raw parse exception", value)
                .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void emptyYearParameterIsRejected_distinctFromOmitted() {
        // year=  (present, empty string) -> 400, never silently treated as "omitted".
        assertThatThrownBy(() -> MapbiomasYear.resolve(Indicator.LANDCOVER, ""))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void omittedYearDefaultsToTheLatestAvailableYearPerIndicator() {
        MapbiomasYear landcover = MapbiomasYear.resolve(Indicator.LANDCOVER, null);
        assertThat(landcover.year()).isEqualTo(2024);
        assertThat(landcover.minYear()).isEqualTo(1999);
        assertThat(landcover.maxYear()).isEqualTo(2024);

        MapbiomasYear burned = MapbiomasYear.resolve(Indicator.BURNED, null);
        assertThat(burned.year()).isEqualTo(2025);
        assertThat(burned.minYear()).isEqualTo(2013);
        assertThat(burned.maxYear()).isEqualTo(2025);
    }

    @Test
    void rejectionMessageNamesTheFailingIndicator() {
        assertThatThrownBy(() -> MapbiomasYear.resolve(Indicator.LANDCOVER, "2025"))
            .hasMessageContaining("LANDCOVER");
        assertThatThrownBy(() -> MapbiomasYear.resolve(Indicator.BURNED, "2026"))
            .hasMessageContaining("BURNED");
    }

    @Test
    void integerOverflowIsRejectedBeforeParsing() {
        // Long enough to overflow int if it were ever passed to Integer.parseInt naively;
        // the strict [0-9]{1,4} pattern must reject it on length alone.
        assertThatThrownBy(() -> MapbiomasYear.resolve(Indicator.BURNED, "999999999999999999999"))
            .isInstanceOf(BadRequestException.class);
    }
}
