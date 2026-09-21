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
}
