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
 * ({@code bp / 10000}), an unknown class code fails loudly (no invented labels), and missing data
 * is {@code null} / {@code available=false}, never a fabricated zero.
 */
public final class MapbiomasComunaDetailBuilder {

    static final String LICENSE = "CC BY 4.0";
    static final String SOURCE_URL = "https://chile.mapbiomas.org/";
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
            landCover(stats.getLandCover(), year),
            fire(fire, fireAvailable),
            fireAvailable,
            indices(susceptibility, weight));
    }

    private static MapbiomasComunaDetailDTO.LandCover landCover(ComunaMapbiomasStats.LandCover landCover, int year) {
        ComunaMapbiomasStats.SharesByYear series = landCover == null ? null : landCover.getSharesByYear();
        if (series == null || series.getClasses() == null
            || series.getFirstYear() == null || series.getLastYear() == null) {
            return new MapbiomasComunaDetailDTO.LandCover(false, year, null, null, null, null, null);
        }

        // Sorted by numeric code so the payload is deterministic; every code is validated
        // (unknown code -> IllegalArgumentException) regardless of the requested year.
        Map<String, int[]> classes = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        series.getClasses().entrySet().stream()
            .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey())))
            .forEach(e -> {
                classes.put(e.getKey(), e.getValue());
                labels.put(e.getKey(), MapbiomasLandCoverLegend.label(Integer.parseInt(e.getKey())));
            });

        List<MapbiomasComunaDetailDTO.ClassShare> shares = new ArrayList<>();
        int index = year - series.getFirstYear();
        if (index >= 0 && year <= series.getLastYear()) {
            for (Map.Entry<String, int[]> e : classes.entrySet()) {
                int[] bps = e.getValue();
                if (bps == null || bps.length <= index) {
                    throw new IllegalArgumentException(
                        "Land-cover series for class " + e.getKey() + " is shorter than "
                            + series.getFirstYear() + "-" + series.getLastYear());
                }
                if (bps[index] > 0) {
                    int code = Integer.parseInt(e.getKey());
                    shares.add(new MapbiomasComunaDetailDTO.ClassShare(
                        code, labels.get(e.getKey()), bps[index] / BP_PER_UNIT));
                }
            }
            shares.sort(Comparator.comparingDouble(MapbiomasComunaDetailDTO.ClassShare::share).reversed()
                .thenComparingInt(MapbiomasComunaDetailDTO.ClassShare::code));
        }

        return new MapbiomasComunaDetailDTO.LandCover(
            true, year, landCover.getReferenceYear(), series.getFirstYear(), series.getLastYear(), shares,
            new MapbiomasComunaDetailDTO.Series(
                series.getFirstYear(), series.getLastYear(), series.getUnit(), labels, classes));
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
        // Mirrors ComunaRiskServiceImpl: an absent/UNAVAILABLE result never enters the blend.
        boolean entered = weight > 0.0 && !s.hasFlag(MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE);
        return new MapbiomasComunaDetailDTO.Indices(
            s.fuelIndex(), s.historyIndex(), s.score(), weight, entered,
            entered ? weight * s.score() : 0.0, s.qualityFlag(), s.unobservedShare());
    }
}
