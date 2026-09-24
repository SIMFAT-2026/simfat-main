package com.simfat.backend.service.mapbiomas;

import java.util.Optional;

/**
 * Computes a per-comuna MapBiomas susceptibility score ({@code S_mapbiomas}, design D3) from
 * {@code comuna_mapbiomas_stats} (S1b2). Standalone collaborator, deliberately NOT wired into
 * {@code ComunaRiskServiceImpl} in this slice (S2a1) -- that blend/wiring, the {@code wM=0}
 * golden regression test, and the new {@code ComunaRiskSnapshot} fields are S2a2's scope.
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
}
