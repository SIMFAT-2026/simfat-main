package com.simfat.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the 6 new MapBiomas fields added to {@link ComunaRiskSnapshot} by
 * S2a2 (design D3). {@code componentLoss} is a pre-existing, differently-meaning field
 * (forest loss, exposed by {@code TerritoryController}/{@code reportPrint.js}'s
 * {@code WLC_META}) and MUST NOT be reused for the MapBiomas contribution -- these are new,
 * separately-named, nullable fields so legacy documents (predating this change) deserialize
 * with {@code null} rather than colliding with an existing field's semantics.
 */
class ComunaRiskSnapshotMapbiomasFieldsTest {

    @Test
    void newFields_defaultToNull_forLegacyDocuments() {
        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();

        assertThat(snapshot.getComponentMapbiomas()).isNull();
        assertThat(snapshot.getMapbiomasFuelIndex()).isNull();
        assertThat(snapshot.getMapbiomasHistoryIndex()).isNull();
        assertThat(snapshot.getMapbiomasWeight()).isNull();
        assertThat(snapshot.getMapbiomasDataVersion()).isNull();
        assertThat(snapshot.getMapbiomasQualityFlag()).isNull();
        // componentLoss is untouched by this change -- still its own, unrelated field.
        assertThat(snapshot.getComponentLoss()).isNull();
    }

    @Test
    void newFields_roundTripThroughSettersAndGetters() {
        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();

        snapshot.setComponentMapbiomas(0.2415);
        snapshot.setMapbiomasFuelIndex(0.41);
        snapshot.setMapbiomasHistoryIndex(0.18);
        snapshot.setMapbiomasWeight(0.35);
        snapshot.setMapbiomasDataVersion("fuego-col1@2017-partial");
        snapshot.setMapbiomasQualityFlag("MAPBIOMAS_FUEL_UNAVAILABLE");

        assertThat(snapshot.getComponentMapbiomas()).isEqualTo(0.2415);
        assertThat(snapshot.getMapbiomasFuelIndex()).isEqualTo(0.41);
        assertThat(snapshot.getMapbiomasHistoryIndex()).isEqualTo(0.18);
        assertThat(snapshot.getMapbiomasWeight()).isEqualTo(0.35);
        assertThat(snapshot.getMapbiomasDataVersion()).isEqualTo("fuego-col1@2017-partial");
        assertThat(snapshot.getMapbiomasQualityFlag()).isEqualTo("MAPBIOMAS_FUEL_UNAVAILABLE");
        // componentLoss is untouched by this change -- still independently settable.
        snapshot.setComponentLoss(1.5);
        assertThat(snapshot.getComponentLoss()).isEqualTo(1.5);
    }
}
