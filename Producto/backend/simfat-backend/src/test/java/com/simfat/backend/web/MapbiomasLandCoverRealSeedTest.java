package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaMapbiomasStats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the LANDCOVER builder over the REAL committed seed (86 comunas, 1999-2024), no Mongo.
 * Proves every class code in the real data is covered by {@link MapbiomasLandCoverLegend} (an
 * unknown code would throw) and pins one real sample comuna.
 */
class MapbiomasLandCoverRealSeedTest {

    private static final Path SEED_PATH = Path.of(
        "src/main/resources/seed/mapbiomas/comuna-mapbiomas-stats.lulc-col2+fuego-col1-2013-2025.jsonl");
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final List<ComunaInfo> COMUNAS = new ArrayList<>();
    private static final Map<String, ComunaMapbiomasStats> BY_ID = new LinkedHashMap<>();

    @BeforeAll
    static void loadSeed() throws IOException {
        for (String line : Files.readAllLines(SEED_PATH)) {
            JsonNode raw = MAPPER.readTree(line);
            ComunaMapbiomasStats stats = new ComunaMapbiomasStats();
            stats.setComunaId(raw.path("comunaId").asText());
            stats.setLandCover(
                MAPPER.treeToValue(raw.path("landCover"), ComunaMapbiomasStats.LandCover.class));
            BY_ID.put(stats.getComunaId(), stats);
            ComunaInfo info = new ComunaInfo();
            info.setId(stats.getComunaId());
            COMUNAS.add(info);
        }
    }

    private static Map<String, Object> layer(int year) {
        return MapbiomasLandCoverLayerBuilder.build(COMUNAS, BY_ID, year,
            MapbiomasYear.LANDCOVER_MIN_YEAR, MapbiomasYear.LANDCOVER_MAX_YEAR, "seed", "citation");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> values(Map<String, Object> layer) {
        return (Map<String, Map<String, Object>>) layer.get("values");
    }

    @Test
    void sampleComuna_arauco2024_payload() {
        Map<String, Object> entry = values(layer(2024)).get("CHL.6.1.1_1");

        assertThat(entry.get("available")).isEqualTo(true);
        assertThat(entry.get("dominantClass")).isEqualTo(9);
        assertThat(entry.get("dominantLabel")).isEqualTo("Silvicultura");
        assertThat(entry.get("dominantShare")).isEqualTo(0.6045);
        assertThat(entry.get("unobservedShare")).isEqualTo(0.0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) entry.get("top");
        assertThat(top).extracting(t -> t.get("code")).containsExactly(9, 15, 60);
        assertThat(top).extracting(t -> t.get("label"))
            .containsExactly("Silvicultura", "Pastura", "Bosque Secundario");
        assertThat(top).extracting(t -> t.get("share")).containsExactly(0.6045, 0.1463, 0.0799);
    }

    @Test
    void everyYearOfTheRealSeed_buildsWithoutAnUnknownClassAndWithConsistentFractions() {
        for (int year = MapbiomasYear.LANDCOVER_MIN_YEAR; year <= MapbiomasYear.LANDCOVER_MAX_YEAR; year++) {
            Map<String, Map<String, Object>> values = values(layer(year));
            assertThat(values).hasSize(86);
            for (Map<String, Object> entry : values.values()) {
                assertThat(entry.get("available")).as("year %d", year).isEqualTo(true);
                double dominant = (Double) entry.get("dominantShare");
                double unobserved = (Double) entry.get("unobservedShare");
                assertThat(dominant).isBetween(0.0, 1.0);
                assertThat(unobserved).isBetween(0.0, 1.0);
                assertThat(dominant + unobserved).isLessThanOrEqualTo(1.0 + 1e-9);
            }
        }
    }

    @Test
    void latestYearWithLandCoverData_isTheSeedLastYear() {
        assertThat(MapbiomasLandCoverDefaultYearResolver.resolve(new ArrayList<>(BY_ID.values())))
            .isEqualTo(2024);
    }
}
