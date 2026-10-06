package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure (no Spring, no Mongo) builder for the BURNED value map (design D5, task 2b2a.2). Kept
 * directly unit-testable, mirroring {@code MapbiomasSusceptibilityServiceImpl}'s static-method
 * convention, since the controller-level MockMvc slice cannot be executed without a live Mongo
 * in this environment.
 */
class MapbiomasBurnedLayerBuilderTest {

    private static ComunaInfo comuna(String id) {
        ComunaInfo info = new ComunaInfo();
        info.setId(id);
        info.setRegionId("biobio");
        return info;
    }

    private static ComunaMapbiomasStats statsWithFire(
        String comunaId, boolean available, Map<String, Double> fractionByYear, Map<String, Double> haByYear
    ) {
        ComunaMapbiomasStats stats = new ComunaMapbiomasStats();
        stats.setComunaId(comunaId);
        stats.setDataVersion("test-version");
        ComunaMapbiomasStats.Fire fire = new ComunaMapbiomasStats.Fire();
        fire.setAvailable(available);
        fire.setBurnedFractionByYear(fractionByYear);
        fire.setBurnedHaByYear(haByYear);
        stats.setFire(fire);
        return stats;
    }

    @Test
    void burnedOnlyIn2019_yieldsPositiveFractionOnlyForThatYear_notCumulative() {
        Map<String, Double> fractionByYear = new LinkedHashMap<>();
        fractionByYear.put("2018", 0.0);
        fractionByYear.put("2019", 0.12);
        Map<String, Double> haByYear = new LinkedHashMap<>();
        haByYear.put("2018", 0.0);
        haByYear.put("2019", 340.0);
        ComunaMapbiomasStats stats = statsWithFire("c1", true, fractionByYear, haByYear);

        Map<String, Object> map2019 = MapbiomasBurnedLayerBuilder.build(
            List.of(comuna("c1")), Map.of("c1", stats), 2019, 2013, 2025, "test-version", "test-citation");
        Map<String, Object> map2018 = MapbiomasBurnedLayerBuilder.build(
            List.of(comuna("c1")), Map.of("c1", stats), 2018, 2013, 2025, "test-version", "test-citation");

        Map<String, Object> entry2019 = entryFor(map2019, "c1");
        Map<String, Object> entry2018 = entryFor(map2018, "c1");

        assertThat((Boolean) entry2019.get("available")).isTrue();
        assertThat((Double) entry2019.get("burnedFraction")).isEqualTo(0.12);
        assertThat((Double) entry2019.get("burnedHa")).isEqualTo(340.0);
        assertThat((Double) entry2018.get("burnedFraction")).isEqualTo(0.0);
    }

    @Test
    void fireUnavailable_isNoDataNeverZero_forEveryYear() {
        ComunaMapbiomasStats stats = statsWithFire("c2", false, null, null);

        Map<String, Object> map2013 = MapbiomasBurnedLayerBuilder.build(
            List.of(comuna("c2")), Map.of("c2", stats), 2013, 2013, 2025, "test-version", "test-citation");
        Map<String, Object> map2025 = MapbiomasBurnedLayerBuilder.build(
            List.of(comuna("c2")), Map.of("c2", stats), 2025, 2013, 2025, "test-version", "test-citation");

        for (Map<String, Object> map : List.of(map2013, map2025)) {
            Map<String, Object> entry = entryFor(map, "c2");
            assertThat((Boolean) entry.get("available")).isFalse();
            assertThat(entry.get("burnedFraction")).isNull();
            assertThat(entry.get("burnedHa")).isNull();
        }
    }

    @Test
    void noStatsDocumentAtAll_isTreatedAsNoData_neverThrows() {
        Map<String, Object> map = MapbiomasBurnedLayerBuilder.build(
            List.of(comuna("unknown")), Map.of(), 2020, 2013, 2025, "test-version", "test-citation");

        Map<String, Object> entry = entryFor(map, "unknown");
        assertThat((Boolean) entry.get("available")).isFalse();
    }

    @Test
    void availableButYearNotComputedInThePartialSeed_isNullNeverZero() {
        // Honest degrade for the REAL current seed (fuego-col1@2017-partial): fire.available may
        // be true while burnedFractionByYear only has a 2017 entry. A year outside that partial
        // map must read as "not computed", not as a fabricated 0.0.
        Map<String, Double> onlyTwentySeventeen = Map.of("2017", 0.03);
        ComunaMapbiomasStats stats = statsWithFire("c3", true, onlyTwentySeventeen, onlyTwentySeventeen);

        Map<String, Object> map2025 = MapbiomasBurnedLayerBuilder.build(
            List.of(comuna("c3")), Map.of("c3", stats), 2025, 2013, 2025, "test-version", "test-citation");

        Map<String, Object> entry = entryFor(map2025, "c3");
        assertThat((Boolean) entry.get("available")).isTrue();
        assertThat(entry.get("burnedFraction")).isNull();
        assertThat(entry.get("burnedHa")).isNull();
    }

    @Test
    void envelopeCarriesIndicatorUnitYearAndBounds() {
        Map<String, Object> map = MapbiomasBurnedLayerBuilder.build(
            List.of(), Map.of(), 2019, 2013, 2025, "test-version", "test-citation");

        assertThat(map.get("indicator")).isEqualTo("BURNED");
        // Q29/Q30: burnedFraction is a 0-1 FRACTION; the client renders it as a percentage.
        assertThat(map.get("unit")).isEqualTo("fraction");
        assertThat(map.get("year")).isEqualTo(2019);
        assertThat(map.get("minYear")).isEqualTo(2013);
        assertThat(map.get("maxYear")).isEqualTo(2025);
        assertThat(map.get("dataVersion")).isEqualTo("test-version");
    }

    @Test
    void envelopeCarriesTheConfiguredCitation() {
        // Judgment-day finding #2 (WARNING, fixed): design D5 requires a `citation` field on the
        // BURNED payload; it was missing entirely. Threaded through exactly like dataVersion.
        Map<String, Object> map = MapbiomasBurnedLayerBuilder.build(
            List.of(), Map.of(), 2019, 2013, 2025, "test-version",
            "MapBiomas Chile - Coleccion 2 de Cobertura y Uso del Suelo (1999-2024) y Coleccion 1 de Fuego (2013-2025). Licencia CC BY 4.0.");

        assertThat(map.get("citation")).isEqualTo(
            "MapBiomas Chile - Coleccion 2 de Cobertura y Uso del Suelo (1999-2024) y Coleccion 1 de Fuego (2013-2025). Licencia CC BY 4.0.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entryFor(Map<String, Object> layer, String comunaId) {
        Map<String, Object> values = (Map<String, Object>) layer.get("values");
        return (Map<String, Object>) values.get(comunaId);
    }
}
