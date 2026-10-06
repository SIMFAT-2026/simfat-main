package com.simfat.backend.web;

import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.List;
import java.util.Map;

/**
 * Pure (no Spring, no Mongo) computation of the LANDCOVER omitted-{@code year} default: the Q28
 * rule ("latest year that actually has data in the loaded dataVersion") applied to land cover.
 * Mirrors {@link MapbiomasBurnedDefaultYearResolver}.
 *
 * <p>A year Y has land-cover data when at least one comuna has a non-zero observed share (any
 * class other than 27, "No observado") at index {@code Y - firstYear} of its
 * {@code sharesByYear} series. Years outside
 * {@code [MapbiomasYear.LANDCOVER_MIN_YEAR, MapbiomasYear.LANDCOVER_MAX_YEAR]} are ignored, so the
 * result always lies within the range {@link MapbiomasYear} accepts. With the current seed this is
 * 2024.
 *
 * <p>Falls back to {@link MapbiomasYear#LANDCOVER_MAX_YEAR} when no comuna qualifies. The caller
 * (the controller) computes this at most once per dataVersion over the WHOLE dataVersion, never
 * per region, so switching regions never shifts the default.
 */
public final class MapbiomasLandCoverDefaultYearResolver {

    private MapbiomasLandCoverDefaultYearResolver() {
    }

    public static int resolve(List<ComunaMapbiomasStats> stats) {
        int best = -1;

        for (ComunaMapbiomasStats stat : stats) {
            if (stat == null || stat.getLandCover() == null) {
                continue;
            }
            ComunaMapbiomasStats.SharesByYear series = stat.getLandCover().getSharesByYear();
            if (series == null || series.getClasses() == null || series.getFirstYear() == null) {
                continue;
            }
            for (Map.Entry<String, int[]> e : series.getClasses().entrySet()) {
                if (String.valueOf(MapbiomasLandCoverLegend.UNOBSERVED_CODE).equals(e.getKey())
                    || e.getValue() == null) {
                    continue;
                }
                int[] bps = e.getValue();
                for (int i = bps.length - 1; i >= 0; i--) {
                    int year = series.getFirstYear() + i;
                    if (year <= best) {
                        break;
                    }
                    if (bps[i] > 0
                        && year >= MapbiomasYear.LANDCOVER_MIN_YEAR
                        && year <= MapbiomasYear.LANDCOVER_MAX_YEAR) {
                        best = year;
                        break;
                    }
                }
            }
        }

        return best == -1 ? MapbiomasYear.LANDCOVER_MAX_YEAR : best;
    }
}
