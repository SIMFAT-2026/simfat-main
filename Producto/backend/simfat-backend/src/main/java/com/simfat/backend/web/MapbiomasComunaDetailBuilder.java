package com.simfat.backend.web;

import com.simfat.backend.dto.MapbiomasComunaDetailDTO;
import com.simfat.backend.model.ComunaMapbiomasStats;
import com.simfat.backend.service.mapbiomas.MapbiomasSusceptibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure (no Spring, no Mongo) builder for the public comuna detail (slice S3-5, design D5).
 * It only SHAPES data: the susceptibility components arrive already computed by
 * {@code MapbiomasSusceptibilityService} and are never recomputed here.
 *
 * <p>Same conventions as {@link MapbiomasLandCoverLayerBuilder}: shares are fractions
 * ({@code bp / 10000}), class 27 stays out of {@code shares} (reported via
 * {@code unobservedShare}), and missing data is {@code null} / {@code available=false}, never a
 * fabricated zero. Unlike the layer, a corrupt series (unknown class, short series, non-numeric
 * key) does not fail the request: land cover degrades to {@code available=false}.
 */
public final class MapbiomasComunaDetailBuilder {

    static final String LICENSE = "CC BY 4.0";
    static final String SOURCE_URL = "https://chile.mapbiomas.org/";
    /** {@code qualityFlag} reported when the stored MapBiomas data failed validation. */
    public static final String DATA_INVALID_FLAG = "MAPBIOMAS_DATA_INVALID";
    private static final double BP_PER_UNIT = 10000.0;

    private MapbiomasComunaDetailBuilder() {
    }

    public static MapbiomasComunaDetailDTO build(
        ComunaMapbiomasStats stats,
        int year,
        Optional<MapbiomasSusceptibility> susceptibility,
        double weight,
        String citation
    ) {
        return build(stats, year, susceptibility, weight, citation, false);
    }

    /**
     * @param indicesInvalid true when computing the susceptibility failed validation (corrupt
     *     stored data): indices degrade to {@link #DATA_INVALID_FLAG} and the rest still renders.
     */
    public static MapbiomasComunaDetailDTO build(
        ComunaMapbiomasStats stats,
        int year,
        Optional<MapbiomasSusceptibility> susceptibility,
        double weight,
        String citation,
        boolean indicesInvalid
    ) {
        ComunaMapbiomasStats.Fire fire = stats.getFire();
        boolean fireAvailable = fire != null && Boolean.TRUE.equals(fire.getAvailable());
        String downloadDate = stats.getProvenance() == null ? null : stats.getProvenance().getDownloadDate();

        return new MapbiomasComunaDetailDTO(
            stats.getComunaId(),
            stats.getNombreComuna(),
            stats.getRegionId(),
            stats.getDataVersion(),
            new MapbiomasComunaDetailDTO.Attribution(
                citation, LICENSE, SOURCE_URL, stats.getDataVersion(), downloadDate),
            safeLandCover(stats.getLandCover(), year),
            fire(fire, fireAvailable),
            fireAvailable,
            indicesInvalid ? invalidIndices(weight) : indices(susceptibility, weight));
    }

    private static MapbiomasComunaDetailDTO.Indices invalidIndices(double weight) {
        return new MapbiomasComunaDetailDTO.Indices(null, null, null, weight, false, 0.0, DATA_INVALID_FLAG, null);
    }

    /** A corrupt series degrades land cover to unavailable instead of failing the whole response. */
    private static MapbiomasComunaDetailDTO.LandCover safeLandCover(
        ComunaMapbiomasStats.LandCover landCover, int year
    ) {
        try {
            return landCover(landCover, year);
        } catch (IllegalArgumentException ex) {
            return new MapbiomasComunaDetailDTO.LandCover(false, year, null, null, null, null, null, null);
        }
    }

    private static MapbiomasComunaDetailDTO.LandCover landCover(ComunaMapbiomasStats.LandCover landCover, int year) {
        ComunaMapbiomasStats.SharesByYear series = landCover == null ? null : landCover.getSharesByYear();
        if (series == null || series.getClasses() == null
            || series.getFirstYear() == null || series.getLastYear() == null) {
            return new MapbiomasComunaDetailDTO.LandCover(false, year, null, null, null, null, null, null);
        }

        // Sorted by numeric code so the payload is deterministic; every code is validated
        // (unknown/non-numeric code -> IllegalArgumentException) regardless of the requested year.
        Map<String, int[]> classes = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        series.getClasses().entrySet().stream()
            .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey())))
            .forEach(e -> {
                classes.put(e.getKey(), e.getValue());
                labels.put(e.getKey(), MapbiomasLandCoverLegend.label(Integer.parseInt(e.getKey())));
            });

        List<MapbiomasComunaDetailDTO.ClassShare> shares = new ArrayList<>();
        Double unobservedShare = null;
        int index = year - series.getFirstYear();
        if (index >= 0 && year <= series.getLastYear()) {
            int unobservedBp = 0;
            for (Map.Entry<String, int[]> e : classes.entrySet()) {
                int[] bps = e.getValue();
                if (bps == null || bps.length <= index) {
                    throw new IllegalArgumentException(
                        "Land-cover series for class " + e.getKey() + " is shorter than "
                            + series.getFirstYear() + "-" + series.getLastYear());
                }
                int code = Integer.parseInt(e.getKey());
                if (code == MapbiomasLandCoverLegend.UNOBSERVED_CODE) {
                    unobservedBp = bps[index];
                } else if (bps[index] > 0) {
                    shares.add(new MapbiomasComunaDetailDTO.ClassShare(
                        code, labels.get(e.getKey()), bps[index] / BP_PER_UNIT));
                }
            }
            unobservedShare = unobservedBp / BP_PER_UNIT;
            shares.sort(Comparator.comparingDouble(MapbiomasComunaDetailDTO.ClassShare::share).reversed()
                .thenComparingInt(MapbiomasComunaDetailDTO.ClassShare::code));
        }

        MapbiomasComunaDetailDTO.Series fullSeries = new MapbiomasComunaDetailDTO.Series(
            series.getFirstYear(), series.getLastYear(), series.getUnit(), labels, classes);
        // Same rule as the LANDCOVER layer: no observed share (or a year outside the series)
        // means "no data", never a fabricated empty distribution. The series is kept for charting.
        boolean available = !shares.isEmpty();
        return new MapbiomasComunaDetailDTO.LandCover(
            available, year, landCover.getReferenceYear(), series.getFirstYear(), series.getLastYear(),
            available ? shares : null, unobservedShare, fullSeries);
    }

    private static MapbiomasComunaDetailDTO.Fire fire(ComunaMapbiomasStats.Fire fire, boolean available) {
        if (!available) {
            return new MapbiomasComunaDetailDTO.Fire(false, null, null, null, null, null, null, null);
        }
        return new MapbiomasComunaDetailDTO.Fire(
            true, fire.getCoverageFraction(), fire.getBurnedHaByYear(), fire.getBurnedFractionByYear(),
            fire.getBurnedFraction(), fire.getYearLastFire(), fire.getYearsSinceLastFire(), fire.getFrequencyMean());
    }

    private static MapbiomasComunaDetailDTO.Indices indices(
        Optional<MapbiomasSusceptibility> susceptibility, double weight
    ) {
        if (susceptibility.isEmpty()) {
            return new MapbiomasComunaDetailDTO.Indices(null, null, null, weight, false, 0.0, null, null);
        }
        MapbiomasSusceptibility s = susceptibility.get();
        boolean unavailable = s.hasFlag(MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE);
        // Mirrors ComunaRiskServiceImpl: an absent/UNAVAILABLE result never enters the blend, and
        // its 0.0 score is a placeholder that must not be shown as a real S_mapbiomas.
        boolean entered = weight > 0.0 && !unavailable;
        return new MapbiomasComunaDetailDTO.Indices(
            unavailable ? null : s.fuelIndex(), unavailable ? null : s.historyIndex(),
            unavailable ? null : s.score(), weight, entered,
            entered ? round4(weight * s.score()) : 0.0, s.qualityFlag(), s.unobservedShare());
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
