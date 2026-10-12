package com.simfat.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure/structural logic on {@link ComunaMapbiomasStats}: the
 * deterministic upsert-key derivation used by MapbiomasSeedLoader (S1b2). Mongo
 * persistence/round-trip behavior (incl. null landCover) is covered separately by the
 * Mongo integration test for the loader.
 */
class ComunaMapbiomasStatsTest {

    @Test
    void buildId_concatenatesComunaIdAndDataVersionWithPipe() {
        String id = ComunaMapbiomasStats.buildId("CHL.6.1.1_1", "fuego-col1@2017-partial");

        assertThat(id).isEqualTo("CHL.6.1.1_1|fuego-col1@2017-partial");
    }

    @Test
    void buildId_differentDataVersionsForTheSameComunaProduceDifferentIds() {
        String v1 = ComunaMapbiomasStats.buildId("CHL.6.1.1_1", "fuego-col1@2017-partial");
        String v2 = ComunaMapbiomasStats.buildId("CHL.6.1.1_1", "lulc-col2+fuego-col1@2026-09-21");

        assertThat(v1).isNotEqualTo(v2);
        assertThat(v1).startsWith("CHL.6.1.1_1|");
        assertThat(v2).startsWith("CHL.6.1.1_1|");
    }
}
