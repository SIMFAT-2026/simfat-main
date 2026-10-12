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

        ComunaMapbiomasStats result = MapbiomasSeedLoader.mapRawRecord(node, info, "lulc-col2+fuego-col1@2017-partial", objectMapper);

        assertThat(result.getId()).isEqualTo("CHL.6.1.1_1|lulc-col2+fuego-col1@2017-partial");
        assertThat(result.getComunaId()).isEqualTo("CHL.6.1.1_1");
        assertThat(result.getDataVersion()).isEqualTo("lulc-col2+fuego-col1@2017-partial");
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
        // (src/main/resources/seed/mapbiomas/comuna-mapbiomas-stats.lulc-col2+fuego-col1-2017-partial.jsonl).
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

        ComunaMapbiomasStats result = MapbiomasSeedLoader.mapRawRecord(node, info, "lulc-col2+fuego-col1@2017-partial", objectMapper);

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

        ComunaMapbiomasStats result = MapbiomasSeedLoader.mapRawRecord(node, info, "lulc-col2+fuego-col1@2017-partial", objectMapper);

        assertThat(result.getFire().getAvailable()).isFalse();
        assertThat(result.getFire().getReason()).isEqualTo("coverageFraction 0.4200 below threshold 0.8");
    }

    @Test
    void mapRawRecord_burnedFractionPctPresent_mapsToFireField() throws Exception {
        // S1b3: design D1/D3's burnedFractionPct (empirical percentile rank across the 86
        // comunas), added to the pipeline output (see mb_pipeline/build_stats.py
        // add_burned_fraction_pct). Jackson's automatic field mapping (treeToValue) must pick
        // it up with no loader code change once the Fire model gains the field.
        String raw = "{\"comunaId\":\"CHL.6.1.1_1\",\"landCover\":null,\"fire\":{\"available\":true,"
            + "\"coverageFraction\":1.0,\"burnedHaByYear\":{\"2017\":0.0},"
            + "\"burnedFractionByYear\":{\"2017\":0.0},\"frequencyMean\":0.0687,"
            + "\"frequencyMax\":2,\"yearLastFire\":2016,\"yearsSinceLastFire\":1,\"reason\":null,"
            + "\"burnedFractionPct\":0.0},"
            + "\"provenance\":{\"sources\":[],\"scope\":\"2017\",\"downloadDate\":\"2026-09-24\"},"
            + "\"computedAt\":\"2026-09-24T01:24:03.953445Z\",\"partial\":true}";
        JsonNode node = objectMapper.readTree(raw);
        ComunaInfo info = comunaInfo("CHL.6.1.1_1", "Arauco", "biobio");

        ComunaMapbiomasStats result = MapbiomasSeedLoader.mapRawRecord(node, info, "lulc-col2+fuego-col1@2017-partial", objectMapper);

        assertThat(result.getFire().getBurnedFractionPct()).isEqualTo(0.0);
    }

    @Test
    void mapRawRecord_burnedFractionPctAbsent_mapsToNullNotZero() throws Exception {
        // A comuna with fire.available=false has no rank (excluded from
        // percentile_rank_burned_fraction) -- the field is simply absent from that JSON line,
        // and must map to null, never a silent 0.0.
        String raw = "{\"comunaId\":\"CHL.6.9.9_1\",\"landCover\":null,\"fire\":{\"available\":false,"
            + "\"coverageFraction\":0.42,\"burnedHaByYear\":{},\"burnedFractionByYear\":{},"
            + "\"frequencyMean\":null,\"frequencyMax\":null,\"yearLastFire\":null,\"yearsSinceLastFire\":null,"
            + "\"reason\":\"coverageFraction 0.4200 below threshold 0.8\"},"
            + "\"provenance\":{\"sources\":[],\"scope\":\"2017\",\"downloadDate\":\"2026-09-24\"},"
            + "\"computedAt\":\"2026-09-24T01:24:03.953445Z\",\"partial\":true}";
        JsonNode node = objectMapper.readTree(raw);
        ComunaInfo info = comunaInfo("CHL.6.9.9_1", "Ejemplo", "biobio");

        ComunaMapbiomasStats result = MapbiomasSeedLoader.mapRawRecord(node, info, "lulc-col2+fuego-col1@2017-partial", objectMapper);

        assertThat(result.getFire().getBurnedFractionPct()).isNull();
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

        ComunaMapbiomasStats result = MapbiomasSeedLoader.mapRawRecord(node, info, "lulc-col2+fuego-col1@2017-partial", objectMapper);

        assertThat(result.getId()).isEqualTo("CHL.6.1.2_1|lulc-col2+fuego-col1@2017-partial")
            .isNotEqualTo("CHL.6.1.1_1|lulc-col2+fuego-col1@2017-partial");
        assertThat(result.getFire().getYearLastFire()).isEqualTo(2017);
        assertThat(result.getFire().getBurnedHaByYear()).containsEntry("2017", 33.5);
    }

    @Test
    void mapRawRecord_realLandCoverWithSharesByYear_mapsAllThreeLandCoverFields() throws Exception {
        // S1b4: the real merged seed has a non-null landCover with sharesByClass,
        // sharesByClassMean5y and the new per-year bp series (26 years, 1999-2024).
        String raw = "{\"comunaId\":\"CHL.6.1.1_1\","
            + "\"landCover\":{\"referenceYear\":2024,"
            + "\"sharesByClass\":{\"59\":0.4,\"60\":0.6},"
            + "\"sharesByClassMean5y\":{\"59\":0.5,\"60\":0.5},"
            + "\"sharesByYear\":{\"firstYear\":1999,\"lastYear\":2024,\"unit\":\"bp\","
            + "\"classes\":{\"59\":[" + zeros(25) + ",4000],\"60\":[" + zeros(25) + ",6000]}}},"
            + "\"fire\":{\"available\":false},"
            + "\"provenance\":{\"sources\":[],\"scope\":\"s\",\"downloadDate\":\"2026-09-28\"},"
            + "\"computedAt\":\"2026-09-28T00:00:00Z\",\"partial\":true}";
        JsonNode node = objectMapper.readTree(raw);
        ComunaInfo info = comunaInfo("CHL.6.1.1_1", "Arauco", "biobio");

        ComunaMapbiomasStats result =
            MapbiomasSeedLoader.mapRawRecord(node, info, "lulc-col2+fuego-col1@2017-partial", objectMapper);

        assertThat(result.getLandCover()).isNotNull();
        assertThat(result.getLandCover().getReferenceYear()).isEqualTo(2024);
        assertThat(result.getLandCover().getSharesByClass()).containsEntry("59", 0.4);
        assertThat(result.getLandCover().getSharesByClassMean5y()).containsEntry("59", 0.5);
        assertThat(result.getLandCover().getSharesByYear()).isNotNull();
        assertThat(result.getLandCover().getSharesByYear().getFirstYear()).isEqualTo(1999);
        assertThat(result.getLandCover().getSharesByYear().getLastYear()).isEqualTo(2024);
        assertThat(result.getLandCover().getSharesByYear().getUnit()).isEqualTo("bp");
        assertThat(result.getLandCover().getSharesByYear().getClasses().get("59")).hasSize(26);
        assertThat(result.getLandCover().getSharesByYear().getClasses().get("59")[25]).isEqualTo(4000);
    }

    @Test
    void mapRawRecord_sharesByYearArrayLengthMismatchesFirstLastYear_throws() throws Exception {
        // Design D1: "classes.*.length == lastYear - firstYear + 1" is a HARD gate -- a
        // malformed seed (25 years instead of the declared 26) must be rejected loudly,
        // not silently loaded with an inconsistent series.
        String raw = "{\"comunaId\":\"CHL.6.1.1_1\","
            + "\"landCover\":{\"referenceYear\":2024,\"sharesByClass\":{\"59\":1.0},"
            + "\"sharesByClassMean5y\":{\"59\":1.0},"
            + "\"sharesByYear\":{\"firstYear\":1999,\"lastYear\":2024,\"unit\":\"bp\","
            + "\"classes\":{\"59\":[" + zeros(24) + "]}}},"
            + "\"fire\":{\"available\":false},"
            + "\"provenance\":{\"sources\":[],\"scope\":\"s\",\"downloadDate\":\"2026-09-28\"},"
            + "\"computedAt\":\"2026-09-28T00:00:00Z\",\"partial\":true}";
        JsonNode node = objectMapper.readTree(raw);
        ComunaInfo info = comunaInfo("CHL.6.1.1_1", "Arauco", "biobio");

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> MapbiomasSeedLoader.mapRawRecord(node, info, "lulc-col2+fuego-col1@2017-partial", objectMapper)
        );
    }

    @Test
    void mapRawRecord_sharesByClassDriftsFromSharesByYearReferenceYear_throws() throws Exception {
        // Design MCS-1d: sharesByClass must equal sharesByYear[referenceYear]/10000 within
        // 1e-4 -- the two representations of the reference year must never drift apart.
        // Here sharesByClass says 0.9 but the 2024 (last) bp slot says 4000 (0.4): a real
        // drift, not a rounding artifact, must be rejected.
        String raw = "{\"comunaId\":\"CHL.6.1.1_1\","
            + "\"landCover\":{\"referenceYear\":2024,\"sharesByClass\":{\"59\":0.9},"
            + "\"sharesByClassMean5y\":{\"59\":0.9},"
            + "\"sharesByYear\":{\"firstYear\":1999,\"lastYear\":2024,\"unit\":\"bp\","
            + "\"classes\":{\"59\":[" + zeros(25) + ",4000]}}},"
            + "\"fire\":{\"available\":false},"
            + "\"provenance\":{\"sources\":[],\"scope\":\"s\",\"downloadDate\":\"2026-09-28\"},"
            + "\"computedAt\":\"2026-09-28T00:00:00Z\",\"partial\":true}";
        JsonNode node = objectMapper.readTree(raw);
        ComunaInfo info = comunaInfo("CHL.6.1.1_1", "Arauco", "biobio");

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> MapbiomasSeedLoader.mapRawRecord(node, info, "lulc-col2+fuego-col1@2017-partial", objectMapper)
        );
    }

    private static String zeros(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append('0');
        }
        return sb.toString();
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
