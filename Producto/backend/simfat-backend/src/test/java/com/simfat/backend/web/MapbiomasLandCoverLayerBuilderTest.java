package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure (no Spring, no Mongo) builder for the LANDCOVER value map (design D5, slice S3-0).
 * Fixtures use a 3-year series (1999-2001) so the per-year index arithmetic stays readable;
 * the real 1999-2024 seed is covered by {@code MapbiomasLandCoverRealSeedTest}.
 */
class MapbiomasLandCoverLayerBuilderTest {

    private static ComunaInfo comuna(String id) {
        ComunaInfo info = new ComunaInfo();
        info.setId(id);
        info.setRegionId("biobio");
        return info;
    }

    /** classes: code -> bp per year starting at 1999. */
    private static ComunaMapbiomasStats statsWithShares(String comunaId, Map<String, int[]> classes) {
        ComunaMapbiomasStats stats = new ComunaMapbiomasStats();
        stats.setComunaId(comunaId);
        ComunaMapbiomasStats.SharesByYear series = new ComunaMapbiomasStats.SharesByYear();
        series.setFirstYear(1999);
        series.setLastYear(2001);
        series.setUnit("bp");
        series.setClasses(classes);
        ComunaMapbiomasStats.LandCover landCover = new ComunaMapbiomasStats.LandCover();
        landCover.setSharesByYear(series);
        stats.setLandCover(landCover);
        return stats;
    }

    private static Map<String, Object> build(ComunaMapbiomasStats stats, int year) {
        return MapbiomasLandCoverLayerBuilder.build(
            List.of(comuna("c1")), stats == null ? Map.of() : Map.of("c1", stats),
            year, 1999, 2024, "test-version", "test-citation");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entry(Map<String, Object> layer) {
        return (Map<String, Object>) ((Map<String, Object>) layer.get("values")).get("c1");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> top(Map<String, Object> entry) {
        return (List<Map<String, Object>>) entry.get("top");
    }

    @Test
    void envelopeCarriesIndicatorFractionUnitYearBoundsVersionAndCitation() {
        Map<String, Object> layer = MapbiomasLandCoverLayerBuilder.build(
            List.of(), Map.of(), 2019, 1999, 2024, "test-version", "test-citation");

        assertThat(layer.get("indicator")).isEqualTo("LANDCOVER");
        assertThat(layer.get("unit")).isEqualTo("fraction");
        assertThat(layer.get("year")).isEqualTo(2019);
        assertThat(layer.get("minYear")).isEqualTo(1999);
        assertThat(layer.get("maxYear")).isEqualTo(2024);
        assertThat(layer.get("dataVersion")).isEqualTo("test-version");
        assertThat(layer.get("citation")).isEqualTo("test-citation");
        assertThat((Map<?, ?>) layer.get("values")).isEmpty();
    }

    @Test
    void sharesAreFractionsComputedExactlyFromBasisPoints() {
        Map<String, int[]> classes = new LinkedHashMap<>();
        classes.put("59", new int[] {3333, 0, 0});
        classes.put("12", new int[] {6667, 0, 0});
        Map<String, Object> e = entry(build(statsWithShares("c1", classes), 1999));

        assertThat(e.get("available")).isEqualTo(true);
        assertThat(e.get("dominantClass")).isEqualTo(12);
        assertThat(e.get("dominantLabel")).isEqualTo("Pastizal");
        assertThat(e.get("dominantShare")).isEqualTo(6667 / 10000.0);
        assertThat(top(e).get(1).get("share")).isEqualTo(3333 / 10000.0);
    }

    @Test
    void readsTheRequestedYearOnly() {
        Map<String, int[]> classes = new LinkedHashMap<>();
        classes.put("59", new int[] {10000, 0, 2000});
        classes.put("12", new int[] {0, 10000, 8000});

        assertThat(entry(build(statsWithShares("c1", classes), 1999)).get("dominantClass")).isEqualTo(59);
        assertThat(entry(build(statsWithShares("c1", classes), 2000)).get("dominantClass")).isEqualTo(12);
        assertThat(entry(build(statsWithShares("c1", classes), 2001)).get("dominantShare")).isEqualTo(0.8);
    }

    @Test
    void tieForDominance_goesToTheLowerClassCode() {
        Map<String, int[]> classes = new LinkedHashMap<>();
        // Insertion order deliberately puts the higher code first.
        classes.put("66", new int[] {5000, 0, 0});
        classes.put("9", new int[] {5000, 0, 0});
        Map<String, Object> e = entry(build(statsWithShares("c1", classes), 1999));

        assertThat(e.get("dominantClass")).isEqualTo(9);
        assertThat(top(e)).extracting(t -> t.get("code")).containsExactly(9, 66);
    }

    @Test
    void class27_isExcludedFromDominanceButReportedAsUnobservedShare() {
        Map<String, int[]> classes = new LinkedHashMap<>();
        classes.put("27", new int[] {7000, 0, 0});
        classes.put("60", new int[] {2000, 0, 0});
        classes.put("12", new int[] {1000, 0, 0});
        Map<String, Object> e = entry(build(statsWithShares("c1", classes), 1999));

        assertThat(e.get("dominantClass")).isEqualTo(60);
        assertThat(e.get("dominantShare")).isEqualTo(0.2);
        assertThat(e.get("unobservedShare")).isEqualTo(0.7);
        assertThat(top(e)).extracting(t -> t.get("code")).containsExactly(60, 12);
    }

    @Test
    void noUnobservedClass_reportsAZeroUnobservedShare() {
        Map<String, int[]> classes = Map.of("59", new int[] {10000, 0, 0});
        Map<String, Object> e = entry(build(statsWithShares("c1", classes), 1999));

        assertThat(e.get("available")).isEqualTo(true);
        assertThat(e.get("unobservedShare")).isEqualTo(0.0);
    }

    @Test
    void topHasUpToThreeObservedClasses_orderedByShareDescendingThenCode() {
        Map<String, int[]> classes = new LinkedHashMap<>();
        classes.put("18", new int[] {500, 0, 0});
        classes.put("9", new int[] {3000, 0, 0});
        classes.put("59", new int[] {3000, 0, 0});
        classes.put("12", new int[] {2000, 0, 0});
        classes.put("34", new int[] {0, 0, 0});
        classes.put("27", new int[] {1500, 0, 0});
        List<Map<String, Object>> top = top(entry(build(statsWithShares("c1", classes), 1999)));

        assertThat(top).hasSize(3);
        assertThat(top).extracting(t -> t.get("code")).containsExactly(9, 59, 12);
        assertThat(top).extracting(t -> t.get("label"))
            .containsExactly("Silvicultura", "Bosque Primario", "Pastizal");
        assertThat(top).extracting(t -> t.get("share")).containsExactly(0.3, 0.3, 0.2);
    }

    @Test
    void zeroShareClassesNeverAppearInTop() {
        Map<String, int[]> classes = new LinkedHashMap<>();
        classes.put("59", new int[] {10000, 0, 0});
        classes.put("12", new int[] {0, 0, 0});
        List<Map<String, Object>> top = top(entry(build(statsWithShares("c1", classes), 1999)));

        assertThat(top).extracting(t -> t.get("code")).containsExactly(59);
    }

    @Test
    void comunaWithoutAStatsDocument_isNoData_neverZeros() {
        Map<String, Object> e = entry(build(null, 2000));

        assertNoData(e);
    }

    @Test
    void comunaWithoutLandCover_isNoData_neverZeros() {
        ComunaMapbiomasStats stats = new ComunaMapbiomasStats();
        stats.setComunaId("c1");

        assertNoData(entry(build(stats, 2000)));
    }

    @Test
    void yearOutsideTheStoredSeries_isNoData() {
        Map<String, int[]> classes = Map.of("59", new int[] {10000, 10000, 10000});

        assertNoData(entry(build(statsWithShares("c1", classes), 2002)));
    }

    @Test
    void yearWithNoObservedClass_isNoData() {
        Map<String, int[]> classes = new LinkedHashMap<>();
        classes.put("27", new int[] {10000, 0, 0});
        classes.put("59", new int[] {0, 10000, 0});

        assertNoData(entry(build(statsWithShares("c1", classes), 1999)));
        // All-zero year: nothing at all was reported.
        assertNoData(entry(build(statsWithShares("c1", classes), 2001)));
    }

    @Test
    void unknownClassCode_failsLoudly() {
        Map<String, int[]> classes = Map.of("999", new int[] {10000, 0, 0});

        assertThatThrownBy(() -> build(statsWithShares("c1", classes), 1999))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");
    }

    @Test
    void nonNumericClassKey_failsLoudly() {
        Map<String, int[]> classes = Map.of("forest", new int[] {10000, 0, 0});

        assertThatThrownBy(() -> build(statsWithShares("c1", classes), 1999))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyComunaOfTheRegionGetsAnEntry() {
        Map<String, Object> layer = MapbiomasLandCoverLayerBuilder.build(
            List.of(comuna("c1"), comuna("c2")),
            Map.of("c1", statsWithShares("c1", Map.of("59", new int[] {10000, 0, 0}))),
            1999, 1999, 2024, "v", "c");

        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) layer.get("values");
        assertThat(values).containsOnlyKeys("c1", "c2");
    }

    private static void assertNoData(Map<String, Object> e) {
        assertThat(e.get("available")).isEqualTo(false);
        assertThat(e).containsKeys("dominantClass", "dominantLabel", "dominantShare", "top", "unobservedShare");
        assertThat(e.get("dominantClass")).isNull();
        assertThat(e.get("dominantLabel")).isNull();
        assertThat(e.get("dominantShare")).isNull();
        assertThat(e.get("top")).isNull();
        assertThat(e.get("unobservedShare")).isNull();
    }
}
