package com.simfat.backend.web;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure (no Spring, no Mongo) builder for the LANDCOVER value map served by {@code /layers} and
 * {@code /public/layers} (design D5, slice S3-0). Mirrors {@link MapbiomasBurnedLayerBuilder}.
 *
 * <p>Source: {@code landCover.sharesByYear}, class-major basis points (0-10000), one entry per
 * year from {@code firstYear}. Shares are returned as FRACTIONS ({@code bp / 10000.0}), the
 * envelope {@code unit} is {@code "fraction"}.
 *
 * <p><b>Dominance rule (deterministic, documented)</b>: {@code dominantClass} is the argmax of
 * the year's shares over OBSERVED classes, i.e. EXCLUDING class 27 ("No observado"). A tie goes
 * to the LOWER class code. {@code top} lists up to 3 observed classes with a non-zero share,
 * ordered by share descending and then by code ascending (so {@code top[0]} is always the
 * dominant class). {@code unobservedShare} is class 27's share (0.0 when the class is absent),
 * returned so the UI can flag years where a comuna was largely unobserved.
 *
 * <p><b>No-data is never a fabricated zero</b>: a comuna with no stats document, no land cover,
 * a year outside the stored series, or a year in which no observed class has any share reads
 * {@code available=false} with every other field {@code null}.
 *
 * <p>An unknown class code (not in {@link MapbiomasLandCoverLegend}) fails loudly with
 * {@link IllegalArgumentException}; a label is never invented. Every class key of a comuna is
 * checked, whether or not it is non-zero in the requested year, so a bad seed cannot hide behind
 * a year in which the class happens to be empty.
 */
public final class MapbiomasLandCoverLayerBuilder {

    private static final double BP_PER_UNIT = 10000.0;
    private static final int TOP_SIZE = 3;

    private MapbiomasLandCoverLayerBuilder() {
    }

    public static Map<String, Object> build(
        List<ComunaInfo> comunas,
        Map<String, ComunaMapbiomasStats> statsByComunaId,
        int year,
        int minYear,
        int maxYear,
        String dataVersion,
        String citation
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ComunaInfo comuna : comunas) {
            values.put(comuna.getId(), buildEntry(statsByComunaId.get(comuna.getId()), year));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicator", "LANDCOVER");
        result.put("unit", "fraction");
        result.put("year", year);
        result.put("minYear", minYear);
        result.put("maxYear", maxYear);
        result.put("dataVersion", dataVersion);
        result.put("citation", citation);
        result.put("values", values);
        return result;
    }

    private record ClassShare(int code, int bp) {
    }

    private static Map<String, Object> buildEntry(ComunaMapbiomasStats stats, int year) {
        ComunaMapbiomasStats.SharesByYear series = stats == null || stats.getLandCover() == null
            ? null : stats.getLandCover().getSharesByYear();
        if (series == null || series.getClasses() == null
            || series.getFirstYear() == null || series.getLastYear() == null) {
            return noData();
        }

        // Validate every class key up front (fail loudly), independent of the requested year.
        Map<Integer, int[]> byCode = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : series.getClasses().entrySet()) {
            byCode.put(parseCode(e.getKey()), e.getValue());
        }

        if (year < series.getFirstYear() || year > series.getLastYear()) {
            return noData();
        }
        int index = year - series.getFirstYear();

        int unobservedBp = 0;
        List<ClassShare> observed = new ArrayList<>();
        for (Map.Entry<Integer, int[]> e : byCode.entrySet()) {
            int[] bps = e.getValue();
            if (bps == null || bps.length <= index) {
                throw new IllegalArgumentException(
                    "Land-cover series for class " + e.getKey() + " is shorter than "
                        + series.getFirstYear() + "-" + series.getLastYear());
            }
            int bp = bps[index];
            if (e.getKey() == MapbiomasLandCoverLegend.UNOBSERVED_CODE) {
                unobservedBp = bp;
            } else if (bp > 0) {
                observed.add(new ClassShare(e.getKey(), bp));
            }
        }

        if (observed.isEmpty()) {
            return noData();
        }

        observed.sort(Comparator.comparingInt(ClassShare::bp).reversed()
            .thenComparingInt(ClassShare::code));

        List<Map<String, Object>> top = new ArrayList<>();
        for (ClassShare share : observed.subList(0, Math.min(TOP_SIZE, observed.size()))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", share.code());
            item.put("label", MapbiomasLandCoverLegend.label(share.code()));
            item.put("share", share.bp() / BP_PER_UNIT);
            top.add(item);
        }

        ClassShare dominant = observed.get(0);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("available", true);
        entry.put("dominantClass", dominant.code());
        entry.put("dominantLabel", MapbiomasLandCoverLegend.label(dominant.code()));
        entry.put("dominantShare", dominant.bp() / BP_PER_UNIT);
        entry.put("top", top);
        entry.put("unobservedShare", unobservedBp / BP_PER_UNIT);
        return entry;
    }

    private static int parseCode(String rawKey) {
        int code;
        try {
            code = Integer.parseInt(rawKey);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Non-numeric MapBiomas land-cover class key: " + rawKey, ex);
        }
        MapbiomasLandCoverLegend.label(code); // throws for an unknown code
        return code;
    }

    private static Map<String, Object> noData() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("available", false);
        entry.put("dominantClass", null);
        entry.put("dominantLabel", null);
        entry.put("dominantShare", null);
        entry.put("top", null);
        entry.put("unobservedShare", null);
        return entry;
    }
}
