package com.simfat.backend.web;

/**
 * Attribution texts required by the MapBiomas terms of use
 * (https://chile.mapbiomas.org/terminos-de-uso/). Single source of truth, used as the default of
 * {@code mapbiomas.citation.landcover} / {@code mapbiomas.citation.fire} (env overrides
 * {@code MAPBIOMAS_CITATION_LANDCOVER} / {@code MAPBIOMAS_CITATION_FIRE}). Kept in Java rather than
 * application.properties because the test profile ships its own properties file and because
 * Spring reads .properties as ISO-8859-1 (accents would need escapes).
 */
public final class MapbiomasCitations {

    public static final String LANDCOVER =
        "MapBiomas \u2013 Colecci\u00f3n 2 de la Serie Anual de Mapas de Cobertura y Uso del Suelo de Chile, "
            + "consultada el 21 de septiembre de 2026 a trav\u00e9s del enlace: "
            + "https://chile.mapbiomas.org/wp-content/uploads/sites/7/2026/08/statistics_lulc_chile_col2_political_level_234.xlsx";

    public static final String FIRE =
        "MapBiomas \u2013 Colecci\u00f3n 1 de la Serie Anual de Mapas de \u00c1rea Quemada de Chile "
            + "(Fuego, 2013\u20132025), consultada el 30 de septiembre de 2026 a trav\u00e9s del enlace: "
            + "https://storage.googleapis.com/mapbiomas-public/initiatives/chile/fire/collection1/";

    private MapbiomasCitations() {
    }
}
