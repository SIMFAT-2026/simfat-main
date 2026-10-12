package com.simfat.backend.web;

import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.List;
import java.util.Map;

/**
 * Pure (no Spring, no Mongo) computation of the BURNED omitted-{@code year} default (Decision
 * Q28, engram {@code sdd/mapbiomas-integration/decisions-q28}). Given the stats already loaded
 * for a {@code dataVersion} (the WHOLE dataVersion, across every region -- never filtered to one
 * region, so switching regions never shifts the default), returns the LATEST year Y in
 * {@code [MapbiomasYear.BURNED_MIN_YEAR, MapbiomasYear.BURNED_MAX_YEAR]} such that at least one
 * comuna has {@code fire.available == true} AND a non-null {@code burnedFractionByYear[Y]}.
 *
 * <p>Defensive by construction: keys outside the BURNED range and non-numeric keys in
 * {@code burnedFractionByYear} are ignored rather than thrown on, since that map's keys are
 * seed-pipeline-controlled strings, not request input -- a malformed or stale key must never
 * crash the default computation.
 *
 * <p>Falls back to {@link MapbiomasYear#BURNED_MAX_YEAR} when no comuna qualifies (today's
 * behaviour, preserved for an empty/entirely-unavailable dataVersion).
 *
 * <p>{@code TerritoryController} calls this AT MOST ONCE PER LOADED {@code dataVersion} and
 * caches the result -- this class itself does no caching and no I/O, it is a pure function of
 * the stats list it is given.
 */
public final class MapbiomasBurnedDefaultYearResolver {

    private MapbiomasBurnedDefaultYearResolver() {
    }

    public static int resolve(List<ComunaMapbiomasStats> stats) {
        int best = -1;

        for (ComunaMapbiomasStats stat : stats) {
            if (stat == null) {
                continue;
            }
            ComunaMapbiomasStats.Fire fire = stat.getFire();
            if (fire == null || !Boolean.TRUE.equals(fire.getAvailable())) {
                continue;
            }
            Map<String, Double> byYear = fire.getBurnedFractionByYear();
            if (byYear == null) {
                continue;
            }
            for (Map.Entry<String, Double> entry : byYear.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                Integer year = parseYearOrNull(entry.getKey());
                if (year == null) {
                    continue;
                }
                if (year < MapbiomasYear.BURNED_MIN_YEAR || year > MapbiomasYear.BURNED_MAX_YEAR) {
                    continue;
                }
                if (year > best) {
                    best = year;
                }
            }
        }

        return best == -1 ? MapbiomasYear.BURNED_MAX_YEAR : best;
    }

    private static Integer parseYearOrNull(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(rawKey);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
