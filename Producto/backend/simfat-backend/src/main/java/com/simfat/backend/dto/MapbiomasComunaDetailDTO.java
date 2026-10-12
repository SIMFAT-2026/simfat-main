package com.simfat.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * Public, minimized per-comuna MapBiomas detail (slice S3-5, design D5 / MPE-3). Explicit
 * whitelist: no Mongo {@code _id}, no per-source URLs or checksums, no script version, no
 * class-weight table. This is public raster statistics (no PII); the minimization goal is a
 * stable contract and payload size.
 *
 * <p>{@code landCover.year}/{@code landCover.shares} follow the requested year; {@code series},
 * {@code fire} and {@code indices} never depend on it ({@code indices} always come from the
 * reference year, 2024).
 */
public record MapbiomasComunaDetailDTO(
    String comunaId,
    String nombreComuna,
    String regionId,
    String dataVersion,
    Attribution attribution,
    LandCover landCover,
    Fire fire,
    boolean fireHistoryAvailable,
    Indices indices
) {

    public record Attribution(String text, String license, String sourceUrl, String dataVersion, String downloadDate) {
    }

    /**
     * Land cover for the requested {@code year}. Semantics are aligned with the LANDCOVER layer:
     * {@code available=false} (with {@code shares=null}) when the comuna has no usable series,
     * the year lies outside the stored series, or no OBSERVED class (anything but 27) has a share
     * above zero in that year. In the last two cases {@code series} is still returned so the UI
     * can chart the history. If the stored series is corrupt (unknown class, short or non-numeric
     * key) everything but {@code available}/{@code year} is null instead of failing the request.
     *
     * <p>{@code shares} lists OBSERVED classes only: class 27 ("No observado") is never in it, as
     * in the layer's dominance rule. Its share for the year is reported solely through
     * {@code unobservedShare} (a fraction 0-1; null when the year is outside the series).
     */
    public record LandCover(
        boolean available,
        Integer year,
        Integer referenceYear,
        Integer minYear,
        Integer maxYear,
        List<ClassShare> shares,
        Double unobservedShare,
        Series series
    ) {
    }

    /** {@code share} is a FRACTION (0-1) of the comuna area for the requested year. */
    public record ClassShare(int code, String label, double share) {
    }

    /** Compact all-years series: {@code classes[code][i]} is basis points in {@code firstYear + i}. */
    public record Series(int firstYear, int lastYear, String unit, Map<String, String> labels,
                         Map<String, int[]> classes) {

        // int[] needs value semantics so that "series is year-independent" is checkable.
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Series other) || firstYear != other.firstYear || lastYear != other.lastYear
                || !java.util.Objects.equals(unit, other.unit) || !labels.equals(other.labels)
                || !classes.keySet().equals(other.classes.keySet())) {
                return false;
            }
            return classes.entrySet().stream()
                .allMatch(e -> java.util.Arrays.equals(e.getValue(), other.classes.get(e.getKey())));
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(firstYear, lastYear, unit, labels, classes.keySet());
        }
    }

    /** Fuego (annual 2013-2025). All fields except {@code available} are null when unavailable. */
    public record Fire(
        boolean available,
        Double coverageFraction,
        Map<String, Double> burnedHaByYear,
        Map<String, Double> burnedFractionByYear,
        Double burnedFraction,
        Integer yearLastFire,
        Integer yearsSinceLastFire,
        Double frequencyMean
    ) {
    }

    /**
     * Susceptibility components as already computed by the scoring service. {@code weight} is the
     * EFFECTIVE wM the user sees in the persisted risk snapshot (falling back to the configured
     * wM when the comuna has no snapshot); {@code enteredInScore} is true only when wM &gt; 0 and
     * the comuna's MapBiomas data is usable, so the UI can say honestly whether MapBiomas affects
     * the risk score.
     *
     * <p>{@code weightedComponent} = wEff * S_mapbiomas (rounded to 4 decimals) when it entered,
     * else 0. It is one term of the convex blend {@code (1 - wEff) * S_dyn + wEff * S_mb}, NOT
     * the net change to the score: the net effect is {@code wEff * (S_mb - S_dyn)}.
     *
     * <p>{@code sMapbiomas}, {@code fuelIndex} and {@code historyIndex} are null when the data is
     * unusable ({@code MAPBIOMAS_UNAVAILABLE} placeholder score, or {@code MAPBIOMAS_DATA_INVALID}
     * when the stored data failed validation), never a fabricated number.
     */
    public record Indices(
        Double fuelIndex,
        Double historyIndex,
        Double sMapbiomas,
        double weight,
        boolean enteredInScore,
        double weightedComponent,
        String qualityFlag,
        Double unobservedShare
    ) {
    }
}
