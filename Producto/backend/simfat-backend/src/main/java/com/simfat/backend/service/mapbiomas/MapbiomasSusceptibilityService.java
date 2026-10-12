package com.simfat.backend.service.mapbiomas;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Computes a per-comuna MapBiomas susceptibility score ({@code S_mapbiomas}, design D3) from
 * {@code comuna_mapbiomas_stats} (S1b2/S1b3). Wired into {@code ComunaRiskServiceImpl}'s score
 * blend by S2a2.
 */
public interface MapbiomasSusceptibilityService {

    /**
     * @param comunaId the comuna to score.
     * @return empty ONLY when no {@code comuna_mapbiomas_stats} document exists at all for
     *     this comuna at the pinned {@code dataVersion}. When a document exists but
     *     {@code landCover} or {@code fire} is individually unavailable, this returns a
     *     present, degraded result with an explicit {@link MapbiomasSusceptibility#qualityFlag}
     *     instead (decision Q26) -- never silently downgrades to empty.
     */
    Optional<MapbiomasSusceptibility> forComuna(String comunaId);

    /**
     * Bulk variant of {@link #forComuna}, for {@code ComunaRiskServiceImpl.recomputeAllComunas}
     * (NFR-7: "recompute for 86 comunas MUST NOT add per-comuna Mongo queries for stats beyond
     * one bulk read per run"). Reads {@code comuna_mapbiomas_stats} with a SINGLE query no
     * matter how many {@code comunaIds} are requested, instead of one lookup per comuna.
     *
     * @param comunaIds the comunas to score; order is not significant.
     * @return a map keyed by {@code comunaId}. A comuna with no stats document at all is
     *     ABSENT from the map (equivalent to {@link #forComuna} returning empty) -- callers
     *     MUST use {@code Map#get} returning {@code null} the same way they would treat
     *     {@code Optional.empty()} from {@link #forComuna}, never assume every requested id is
     *     present.
     */
    Map<String, MapbiomasSusceptibility> forComunas(Collection<String> comunaIds);
}
