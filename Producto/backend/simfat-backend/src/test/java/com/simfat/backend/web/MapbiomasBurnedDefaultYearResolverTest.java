package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Decision Q28 (engram {@code sdd/mapbiomas-integration/decisions-q28}): when the BURNED
 * {@code year} parameter is omitted, the map must default to the LATEST year that actually has
 * burned data in the loaded {@code dataVersion} -- never a fixed constant that can render an
 * empty map while the seed is still partial. Pure (no Spring, no Mongo): takes the already-loaded
 * stats list for the whole {@code dataVersion} (all regions, so switching regions never changes
 * the default) and returns the resolved year, computed only once per caller and then cached by
 * {@code TerritoryController}.
 */
class MapbiomasBurnedDefaultYearResolverTest {

    private static ComunaMapbiomasStats statsWithFire(
        String comunaId, boolean available, Map<String, Double> fractionByYear
    ) {
        ComunaMapbiomasStats stats = new ComunaMapbiomasStats();
        stats.setComunaId(comunaId);
        ComunaMapbiomasStats.Fire fire = new ComunaMapbiomasStats.Fire();
        fire.setAvailable(available);
        fire.setBurnedFractionByYear(fractionByYear);
        stats.setFire(fire);
        return stats;
    }

    private static Map<String, Double> years(String... yearValuePairs) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < yearValuePairs.length; i += 2) {
            map.put(yearValuePairs[i], Double.parseDouble(yearValuePairs[i + 1]));
        }
        return map;
    }

    @Test
    void returnsTheMaxQualifyingYearAcrossAllComunas() {
        List<ComunaMapbiomasStats> stats = List.of(
            statsWithFire("c1", true, years("2015", "0.01")),
            statsWithFire("c2", true, years("2017", "0.05", "2016", "0.02"))
        );

        assertThat(MapbiomasBurnedDefaultYearResolver.resolve(stats)).isEqualTo(2017);
    }

    @Test
    void ignoresComunasWhereFireIsNotAvailable() {
        List<ComunaMapbiomasStats> stats = List.of(
            statsWithFire("c1", false, years("2024", "0.9")),
            statsWithFire("c2", true, years("2015", "0.01"))
        );

        assertThat(MapbiomasBurnedDefaultYearResolver.resolve(stats)).isEqualTo(2015);
    }

    @Test
    void ignoresNullValuesEvenWhenAvailable() {
        Map<String, Double> byYear = new LinkedHashMap<>();
        byYear.put("2015", 0.01);
        byYear.put("2024", null); // present key, no computed value yet -- not "has data"
        List<ComunaMapbiomasStats> stats = List.of(statsWithFire("c1", true, byYear));

        assertThat(MapbiomasBurnedDefaultYearResolver.resolve(stats)).isEqualTo(2015);
    }

    @Test
    void returnsTheFallbackMaxYearWhenNoComunaHasData() {
        assertThat(MapbiomasBurnedDefaultYearResolver.resolve(List.of()))
            .isEqualTo(MapbiomasYear.BURNED_MAX_YEAR);

        List<ComunaMapbiomasStats> allUnavailable = List.of(
            statsWithFire("c1", false, years("2020", "0.5"))
        );
        assertThat(MapbiomasBurnedDefaultYearResolver.resolve(allUnavailable))
            .isEqualTo(MapbiomasYear.BURNED_MAX_YEAR);
    }

    @Test
    void ignoresKeysOutsideTheRangeOrNonNumericKeys() {
        Map<String, Double> byYear = new LinkedHashMap<>();
        byYear.put("2015", 0.01);
        byYear.put("1500", 0.9);   // outside BURNED range entirely
        byYear.put("9999", 0.9);   // outside BURNED range entirely
        byYear.put("abc", 0.9);    // non-numeric, defensive
        byYear.put("", 0.9);       // non-numeric, defensive
        List<ComunaMapbiomasStats> stats = List.of(statsWithFire("c1", true, byYear));

        assertThat(MapbiomasBurnedDefaultYearResolver.resolve(stats)).isEqualTo(2015);
    }

    @Test
    void nullFireOrNullByYearMapNeverThrows() {
        ComunaMapbiomasStats noFire = new ComunaMapbiomasStats();
        noFire.setComunaId("c1");
        ComunaMapbiomasStats availableButNoByYearMap = statsWithFire("c2", true, null);

        assertThat(MapbiomasBurnedDefaultYearResolver.resolve(List.of(noFire, availableButNoByYearMap)))
            .isEqualTo(MapbiomasYear.BURNED_MAX_YEAR);
    }
}
