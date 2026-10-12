package com.simfat.backend.web;

import java.util.Map;

/**
 * The ONE place that holds the official MapBiomas Chile Collection 2 land-cover class names
 * served by the LANDCOVER layer (slice S3-0).
 *
 * <p><b>Source</b>: the official MapBiomas Chile legend codes document,
 * {@code https://chile.mapbiomas.org/wp-content/uploads/sites/7/2026/08/Leyenda2025-LEYENDA-CODIGOS.pdf}
 * (linked from {@code https://chile.mapbiomas.org/descargas/informacion-de-las-leyendas/}),
 * 126,102 bytes, sha256 {@code c812298d69ff5dcdc6b55995d87fee5dada38d5c5b7263be90548351f308e8ae},
 * downloaded 2026-10-06. Third-party copies were deliberately ignored. The names are the
 * official Spanish ones, kept verbatim (accents included).
 *
 * <p>Only LEAF classes are listed: the seed aggregates leaf classes only (design D2), so parent
 * codes (1, 3, 10, 14, 22, 26) never reach a layer. Colours are a frontend concern and live in
 * the client palette, not here.
 *
 * <p>An unknown code FAILS LOUDLY ({@link IllegalArgumentException}): a label is never invented,
 * because an unlabelled class on a public map would be an unverifiable claim.
 */
public final class MapbiomasLandCoverLegend {

    /** "No observado": pixels the classifier could not observe. Never a dominant class. */
    public static final int UNOBSERVED_CODE = 27;

    private static final Map<Integer, String> LABELS = Map.ofEntries(
        Map.entry(59, "Bosque Primario"),
        Map.entry(60, "Bosque Secundario"),
        Map.entry(67, "Bosque Achaparrado"),
        Map.entry(11, "Humedal"),
        Map.entry(12, "Pastizal"),
        Map.entry(63, "Estepa"),
        Map.entry(66, "Matorral"),
        Map.entry(29, "Afloramiento Rocoso"),
        Map.entry(9, "Silvicultura"),
        Map.entry(18, "Agricultura"),
        Map.entry(15, "Pastura"),
        Map.entry(24, "Infraestructura"),
        Map.entry(23, "Arena, Playa y Duna"),
        Map.entry(61, "Salar"),
        Map.entry(25, "Otra área sin vegetación"),
        Map.entry(33, "Río, lago u océano"),
        Map.entry(34, "Hielo y nieve"),
        Map.entry(UNOBSERVED_CODE, "No observado")
    );

    private MapbiomasLandCoverLegend() {
    }

    /**
     * @throws IllegalArgumentException if {@code code} is not an official leaf class
     */
    public static String label(int code) {
        String label = LABELS.get(code);
        if (label == null) {
            throw new IllegalArgumentException(
                "Unknown MapBiomas land-cover class code: " + code
                    + " (add it to MapbiomasLandCoverLegend from the official legend, never guess a label)");
        }
        return label;
    }
}
