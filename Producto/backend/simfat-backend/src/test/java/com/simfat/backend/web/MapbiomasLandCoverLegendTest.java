package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MapbiomasLandCoverLegendTest {

    @Test
    void officialSpanishLabels_forEveryClassInTheSeed() {
        assertThat(MapbiomasLandCoverLegend.label(59)).isEqualTo("Bosque Primario");
        assertThat(MapbiomasLandCoverLegend.label(60)).isEqualTo("Bosque Secundario");
        assertThat(MapbiomasLandCoverLegend.label(67)).isEqualTo("Bosque Achaparrado");
        assertThat(MapbiomasLandCoverLegend.label(11)).isEqualTo("Humedal");
        assertThat(MapbiomasLandCoverLegend.label(12)).isEqualTo("Pastizal");
        assertThat(MapbiomasLandCoverLegend.label(66)).isEqualTo("Matorral");
        assertThat(MapbiomasLandCoverLegend.label(29)).isEqualTo("Afloramiento Rocoso");
        assertThat(MapbiomasLandCoverLegend.label(9)).isEqualTo("Silvicultura");
        assertThat(MapbiomasLandCoverLegend.label(18)).isEqualTo("Agricultura");
        assertThat(MapbiomasLandCoverLegend.label(15)).isEqualTo("Pastura");
        assertThat(MapbiomasLandCoverLegend.label(24)).isEqualTo("Infraestructura");
        assertThat(MapbiomasLandCoverLegend.label(23)).isEqualTo("Arena, Playa y Duna");
        assertThat(MapbiomasLandCoverLegend.label(25)).isEqualTo("Otra área sin vegetación");
        assertThat(MapbiomasLandCoverLegend.label(33)).isEqualTo("Río, lago u océano");
        assertThat(MapbiomasLandCoverLegend.label(34)).isEqualTo("Hielo y nieve");
        assertThat(MapbiomasLandCoverLegend.label(27)).isEqualTo("No observado");
    }

    @Test
    void unobservedCodeIs27() {
        assertThat(MapbiomasLandCoverLegend.UNOBSERVED_CODE).isEqualTo(27);
    }

    @Test
    void unknownCode_failsLoudly_neverInventsALabel() {
        assertThatThrownBy(() -> MapbiomasLandCoverLegend.label(999))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");
    }
}
