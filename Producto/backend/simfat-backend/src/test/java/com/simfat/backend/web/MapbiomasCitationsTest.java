package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Guards the exact citation format required by the MapBiomas terms of use (prize rules 5.9). */
class MapbiomasCitationsTest {

    @Test
    void landCoverCitationFollowsTheOfficialTemplate() {
        assertThat(MapbiomasCitations.LANDCOVER).isEqualTo(
            "MapBiomas – Colección 2 de la Serie Anual de Mapas de Cobertura y Uso del Suelo de Chile, "
                + "consultada el 21 de septiembre de 2026 a través del enlace: "
                + "https://chile.mapbiomas.org/wp-content/uploads/sites/7/2026/08/statistics_lulc_chile_col2_political_level_234.xlsx");
    }

    @Test
    void fireCitationFollowsTheOfficialTemplate() {
        assertThat(MapbiomasCitations.FIRE).isEqualTo(
            "MapBiomas – Colección 1 de la Serie Anual de Mapas de Área Quemada de Chile "
                + "(Fuego, 2013–2025), consultada el 30 de septiembre de 2026 a través del enlace: "
                + "https://storage.googleapis.com/mapbiomas-public/initiatives/chile/fire/collection1/");
    }
}
