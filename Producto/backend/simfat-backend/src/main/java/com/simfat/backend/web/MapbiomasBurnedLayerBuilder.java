package com.simfat.backend.web;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure (no Spring, no Mongo) builder for the BURNED value map served by {@code /layers} and
 * {@code /public/layers} (design D5, task 2b2a.2). Kept as its own static-method class,
 * mirroring {@code MapbiomasSusceptibilityServiceImpl}'s convention, so the core logic stays
 * directly unit-testable without a live Mongo/Spring context.
 *
 * <p><b>Annual, never cumulative</b> (design D5 / spec TL-1g, MCS-10): for a requested year Y,
 * {@code burnedFraction} is {@code burnedFractionByYear[Y]} -- the burned fraction of THAT year
 * over mapped area -- never the 2013-2025 union.
 *
 * <p><b>No-data is never a fabricated zero</b> (spec TL-1j). Two distinct "no value" cases are
 * both surfaced as {@code available=false} or a {@code null} numeric field, never as a literal
 * {@code 0.0}:
 * <ul>
 *   <li>{@code fire.available=false} (no Fuego coverage for the comuna at all): every year reads
 *       {@code available=false}.</li>
 *   <li>{@code fire.available=true} but the requested year has no entry in
 *       {@code burnedFractionByYear}/{@code burnedHaByYear} -- the honest state of the REAL
 *       current seed ({@code fuego-col1@2017-partial}, which only populates a 2017 entry): the
 *       comuna reads {@code available=true} (coverage exists) with {@code burnedFraction=null}
 *       and {@code burnedHa=null} for that specific year (not computed yet), distinct from a
 *       real, computed {@code 0.0}.</li>
 * </ul>
 *
 * <p>A comuna with no {@code comuna_mapbiomas_stats} document at all is treated the same as
 * {@code available=false} -- there is no signal to report, never an exception.
 */
public final class MapbiomasBurnedLayerBuilder {

    private MapbiomasBurnedLayerBuilder() {
    }

    public static Map<String, Object> build(
        List<ComunaInfo> comunas,
        Map<String, ComunaMapbiomasStats> statsByComunaId,
        int year,
        int minYear,
        int maxYear,
        String dataVersion
    ) {
        String yearKey = Integer.toString(year);
        Map<String, Object> values = new LinkedHashMap<>();

        for (ComunaInfo comuna : comunas) {
            ComunaMapbiomasStats stats = statsByComunaId.get(comuna.getId());
            values.put(comuna.getId(), buildEntry(stats, yearKey));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicator", "BURNED");
        result.put("unit", "%");
        result.put("year", year);
        result.put("minYear", minYear);
        result.put("maxYear", maxYear);
        result.put("dataVersion", dataVersion);
        result.put("values", values);
        return result;
    }

    private static Map<String, Object> buildEntry(ComunaMapbiomasStats stats, String yearKey) {
        Map<String, Object> entry = new LinkedHashMap<>();
        ComunaMapbiomasStats.Fire fire = stats == null ? null : stats.getFire();
        boolean available = fire != null && Boolean.TRUE.equals(fire.getAvailable());

        entry.put("available", available);
        if (!available) {
            entry.put("burnedFraction", null);
            entry.put("burnedHa", null);
            return entry;
        }

        Map<String, Double> fractionByYear = fire.getBurnedFractionByYear();
        Map<String, Double> haByYear = fire.getBurnedHaByYear();
        entry.put("burnedFraction", fractionByYear == null ? null : fractionByYear.get(yearKey));
        entry.put("burnedHa", haByYear == null ? null : haByYear.get(yearKey));
        return entry;
    }
}
