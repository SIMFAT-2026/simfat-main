package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapbiomasLandCoverDefaultYearResolverTest {

    private static ComunaMapbiomasStats stats(int firstYear, int lastYear, Map<String, int[]> classes) {
        ComunaMapbiomasStats.SharesByYear series = new ComunaMapbiomasStats.SharesByYear();
        series.setFirstYear(firstYear);
        series.setLastYear(lastYear);
        series.setClasses(classes);
        ComunaMapbiomasStats.LandCover landCover = new ComunaMapbiomasStats.LandCover();
        landCover.setSharesByYear(series);
        ComunaMapbiomasStats s = new ComunaMapbiomasStats();
        s.setLandCover(landCover);
        return s;
    }

    @Test
    void latestYearWithAnyObservedShare_isReturned() {
        // 1999..2001, data only up to 2000 (2001 is all zero).
        List<ComunaMapbiomasStats> list = List.of(
            stats(1999, 2001, Map.of("59", new int[] {5000, 4000, 0}, "27", new int[] {5000, 6000, 10000})));

        assertThat(MapbiomasLandCoverDefaultYearResolver.resolve(list)).isEqualTo(2000);
    }

    @Test
    void takesTheLatestAcrossComunas() {
        List<ComunaMapbiomasStats> list = List.of(
            stats(1999, 2001, Map.of("59", new int[] {10000, 0, 0})),
            stats(1999, 2001, Map.of("12", new int[] {0, 0, 10000})));

        assertThat(MapbiomasLandCoverDefaultYearResolver.resolve(list)).isEqualTo(2001);
    }

    @Test
    void yearsOutsideTheLandCoverRange_areIgnored() {
        // Series claims 2030, beyond LANDCOVER_MAX_YEAR (2024): the default must stay in range.
        List<ComunaMapbiomasStats> list = List.of(
            stats(2023, 2030, Map.of("59", new int[] {0, 0, 0, 0, 0, 0, 0, 10000})));

        assertThat(MapbiomasLandCoverDefaultYearResolver.resolve(list))
            .isEqualTo(MapbiomasYear.LANDCOVER_MAX_YEAR);
    }

    @Test
    void noData_fallsBackToTheMaxYear() {
        assertThat(MapbiomasLandCoverDefaultYearResolver.resolve(new ArrayList<>()))
            .isEqualTo(MapbiomasYear.LANDCOVER_MAX_YEAR);
        ComunaMapbiomasStats noLandCover = new ComunaMapbiomasStats();
        assertThat(MapbiomasLandCoverDefaultYearResolver.resolve(List.of(noLandCover)))
            .isEqualTo(MapbiomasYear.LANDCOVER_MAX_YEAR);
    }
}
