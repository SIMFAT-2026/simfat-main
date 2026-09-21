package com.simfat.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaMapbiomasStats;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link MapbiomasSeedLoader#mapRawRecord}: the JSON-line -> entity
 * conversion, kept side-effect-free (no Mongo, no Spring context) so the mapping logic
 * itself is directly testable. Mongo persistence/idempotency is covered separately by
 * {@code MapbiomasSeedLoaderIntegrationTest}.
 */
class MapbiomasSeedLoaderMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ComunaInfo comunaInfo(String id, String nombre, String regionId) {
        ComunaInfo c = new ComunaInfo();
        c.setId(id);
        c.setNombre(nombre);
        c.setRegionId(regionId);
        return c;
    }

    @Test
    void mapRawRecord_fireOnlyPartialRecord_mapsToEntityWithNullLandCoverAndDeterministicId() throws Exception {
        String raw = "{"
            + "\"comunaId\":\"CHL.6.1.1_1\","
            + "\"landCover\":null,"
            + "\"landCoverReason\":\"LULC xlsx not processed in this S1b slice\","
            + "\"fire\":{"
            + "  \"available\":true,\"coverageFraction\":1.0,"
            + "  \"burnedHaByYear\":{\"2017\":0.0},"
            + "  \"burnedFractionByYear\":{\"2017\":0.0},"
            + "  \"frequencyMean\":0.0687,\"frequencyMax\":2,"
            + "  \"yearLastFire\":2016,\"yearsSinceLastFire\":1"
            + "},"
            + "\"provenance\":{\"sources\":[\"annual_burned_2017.tif\"],\"scope\":\"2017\",\"downloadDate\":\"2026-09-21\"},"
            + "\"computedAt\":\"2026-09-21T18:17:35.689908Z\","
            + "\"partial\":true"
            + "}";
        JsonNode node = objectMapper.readTree(raw);
        ComunaInfo info = comunaInfo("CHL.6.1.1_1", "Arauco", "biobio");

        ComunaMapbiomasStats result = MapbiomasSeedLoader.mapRawRecord(node, info, "fuego-col1@2017-partial", objectMapper);

        assertThat(result.getId()).isEqualTo("CHL.6.1.1_1|fuego-col1@2017-partial");
        assertThat(result.getComunaId()).isEqualTo("CHL.6.1.1_1");
        assertThat(result.getDataVersion()).isEqualTo("fuego-col1@2017-partial");
        assertThat(result.getRegionId()).isEqualTo("biobio");
        assertThat(result.getNombreComuna()).isEqualTo("Arauco");
        assertThat(result.getLandCover()).isNull();
        assertThat(result.getLandCoverReason()).isEqualTo("LULC xlsx not processed in this S1b slice");
        assertThat(result.isPartial()).isTrue();
        assertThat(result.getFire()).isNotNull();
        assertThat(result.getFire().getAvailable()).isTrue();
        assertThat(result.getFire().getYearLastFire()).isEqualTo(2016);
        assertThat(result.getFire().getBurnedHaByYear()).containsEntry("2017", 0.0);
        assertThat(result.getProvenance().getSources()).containsExactly("annual_burned_2017.tif");
        assertThat(result.getComputedAt()).isNotNull();
    }

    @Test
    void mapRawRecord_realSeedLineWithFireReasonField_doesNotThrowUnrecognizedProperty() throws Exception {
        // Exact line 1 of the real committed seed
        // (src/main/resources/seed/mapbiomas/comuna-mapbiomas-stats.fuego-col1-2017-partial.jsonl).
        // Every one of the 86 lines carries "fire.reason" (nullable string explaining why
        // fire.available is false; null when available=true), which the Fire model was
        // missing entirely -- with a plain, unconfigured ObjectMapper this made
        // treeToValue() throw UnrecognizedPropertyException for every single seed line
        // (see MapbiomasSeedLoaderIntegrationTest, which loads 0 of 86 documents before
        // this fix).
        String raw = "{\"comunaId\":\"CHL.6.1.1_1\",\"landCover\":null,\"fire\":{\"available\":true,"
            + "\"coverageFraction\":1.0,\"burnedHaByYear\":{\"2017\":0.0},"
            + "\"burnedFractionByYear\":{\"2017\":0.0},\"frequencyMean\":0.06870792202430369,"
            + "\"frequencyMax\":2,\"yearLastFire\":2016,\"yearsSinceLastFire\":1,\"reason\":null},"
            + "\"provenance\":{\"sources\":[\"mapbiomas_fire_chile_col1_annual_burned_2017.tif\"],"
            + "\"scope\":\"S1b real-data subset: fire year(s) 2017; frequency window 2013-2017\","
            + "\"downloadDate\":\"2026-09-21\"},"
            + "\"computedAt\":\"2026-09-21T18:17:35.689908+00:00\","
            + "\"landCoverReason\":\"LULC xlsx not processed in this S1b slice (fire-only real-data subset); "
            + "see sdd apply-progress for scope.\",\"partial\":true}";
        JsonNode node = objectMapper.readTree(raw);
        ComunaInfo info = comunaInfo("CHL.6.1.1_1", "Arauco", "biobio");

        ComunaMapbiomasStats result = MapbiomasSeedLoader.mapRawRecord(node, info, "fuego-col1@2017-partial", objectMapper);

        assertThat(result.getFire()).isNotNull();
        assertThat(result.getFire().getReason()).isNull();
        assertThat(result.getFire().getAvailable()).isTrue();
        assertThat(result.getFire().getYearLastFire()).isEqualTo(2016);
    }

    @Test
    void mapRawRecord_fireReasonPopulated_whenAvailableFalse() throws Exception {
        // Real semantics from mb_pipeline/fire_stats.py::build_fire_section: "reason" is
        // non-null exactly when "available" is false, explaining why (e.g. coverage below
        // threshold, or no fire years processed for the comuna).
        String raw = "{\"comunaId\":\"CHL.6.9.9_1\",\"landCover\":null,\"fire\":{\"available\":false,"
            + "\"coverageFraction\":0.42,\"burnedHaByYear\":{},\"burnedFractionByYear\":{},"
            + "\"frequencyMean\":null,\"frequencyMax\":null,\"yearLastFire\":null,\"yearsSinceLastFire\":null,"
            + "\"reason\":\"coverageFraction 0.4200 below threshold 0.8\"},"
            + "\"provenance\":{\"sources\":[],\"scope\":\"2017\",\"downloadDate\":\"2026-09-21\"},"
            + "\"computedAt\":\"2026-09-21T18:17:35.689908Z\",\"partial\":true}";
        JsonNode node = objectMapper.readTree(raw);
        ComunaInfo info = comunaInfo("CHL.6.9.9_1", "Ejemplo", "biobio");

        ComunaMapbiomasStats result = MapbiomasSeedLoader.mapRawRecord(node, info, "fuego-col1@2017-partial", objectMapper);

        assertThat(result.getFire().getAvailable()).isFalse();
        assertThat(result.getFire().getReason()).isEqualTo("coverageFraction 0.4200 below threshold 0.8");
    }

    @Test
    void mapRawRecord_differentComunaAndVersion_producesDifferentDeterministicId() throws Exception {
        String raw = "{"
            + "\"comunaId\":\"CHL.6.1.2_1\","
            + "\"landCover\":null,"
            + "\"landCoverReason\":null,"
            + "\"fire\":{\"available\":true,\"coverageFraction\":1.0,"
            + "  \"burnedHaByYear\":{\"2017\":33.5},\"burnedFractionByYear\":{\"2017\":0.0003},"
            + "  \"frequencyMean\":0.01,\"frequencyMax\":2,\"yearLastFire\":2017,\"yearsSinceLastFire\":0},"
            + "\"provenance\":{\"sources\":[],\"scope\":\"2017\",\"downloadDate\":\"2026-09-21\"},"
            + "\"computedAt\":\"2026-09-21T18:17:35.689908Z\","
            + "\"partial\":true"
            + "}";
        JsonNode node = objectMapper.readTree(raw);
        ComunaInfo info = comunaInfo("CHL.6.1.2_1", "Canete", "biobio");

        ComunaMapbiomasStats result = MapbiomasSeedLoader.mapRawRecord(node, info, "fuego-col1@2017-partial", objectMapper);

        assertThat(result.getId()).isEqualTo("CHL.6.1.2_1|fuego-col1@2017-partial")
            .isNotEqualTo("CHL.6.1.1_1|fuego-col1@2017-partial");
        assertThat(result.getFire().getYearLastFire()).isEqualTo(2017);
        assertThat(result.getFire().getBurnedHaByYear()).containsEntry("2017", 33.5);
    }

    // computeStatus() is the pure decision function behind loadSeed()'s final summary
    // log line. Before this fix, loadSeed() hardcoded "status=ok" unconditionally, which
    // would have made a total parse failure (e.g. from a future schema drift like the
    // fire.reason incident) indistinguishable from a healthy boot at INFO level. These
    // tests pin down the branching so the log/summary can never silently regress to
    // always-ok again.
    @Test
    void computeStatus_noErrorsAndSomeLoaded_isOk() {
        assertThat(MapbiomasSeedLoader.computeStatus(86, 0, 0)).isEqualTo("ok");
    }

    @Test
    void computeStatus_nothingProcessedAtAll_isOkByDesign() {
        // Genuinely empty resource (or fully disabled path never reaching this point in
        // practice) -- zero of everything is not itself a failure signal.
        assertThat(MapbiomasSeedLoader.computeStatus(0, 0, 0)).isEqualTo("ok");
    }

    @Test
    void computeStatus_someErrorsButSomeLoaded_isDegraded() {
        assertThat(MapbiomasSeedLoader.computeStatus(70, 5, 11)).isEqualTo("degraded");
    }

    @Test
    void computeStatus_allLinesErrorNothingLoaded_isFailed() {
        // The exact regression scenario the fire.reason incident nearly repeated: every
        // line fails to parse/map, loaded stays 0, but skipped==0 and errors==total.
        assertThat(MapbiomasSeedLoader.computeStatus(0, 0, 86)).isEqualTo("failed");
    }

    @Test
    void computeStatus_noErrorsButNothingLoadedBecauseAllSkipped_isEmptyResult() {
        // No line-level errors, but nothing was actually persisted (e.g. every comunaId
        // referenced by the seed is missing from comuna_info) -- also not a healthy "ok".
        assertThat(MapbiomasSeedLoader.computeStatus(0, 86, 0)).isEqualTo("empty_result");
    }
}
