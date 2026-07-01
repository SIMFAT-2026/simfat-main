package com.simfat.backend.service.impl;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.Region;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Single sanctioned entry point for reading FIRMS detections by geo-attribution
 * (Decision 6's coverage-gap fallback router), shared by {@link ComunaRiskServiceImpl}
 * and {@link TerritoryRiskServiceImpl} (post-review FIX 6 — findings C7 + C9).
 *
 * <p>Routing rule (FIX 1, finding C1+C5 — corrected to COMUNA granularity, not region):
 * a comuna is "covered" iff its OWN {@code geometry} field is non-null. A region can be
 * mostly covered (9/10 comunas with valid geometry) while one comuna's GADM polygon
 * failed validation (Decision 3) and has {@code geometry == null} — that comuna alone
 * must fall back to centroid attribution, never the whole region's coverage state.
 *
 * <p>Fallback candidate pool (FIX 2, finding C6): the centroid fallback's candidate pool
 * is sourced by {@code fuente} + recency window only, NEVER pre-filtered by the event's
 * persisted {@code regionId}. Decision 2 made FIRMS dedup region-independent — a physical
 * detection persists exactly once, owned by whichever region's cron leg synced it first,
 * with {@code regionId} set to that leg's region (a "whichever leg won the race"
 * artifact, not a geographic fact). Pre-filtering by persisted regionId would mean an
 * overlapping neighbor region's fallback could never even see a row a different leg
 * persisted, silently undercounting. Centroid distance — not persisted regionId — decides
 * true ownership for the uncovered path. This matches the pre-incident centroid logic's
 * original behavior from before Decision 2's dedup-key change (see design.md amendment to
 * Decision 6 for the git-archaeology confirming this).
 */
@Component
class FirmsAttributionRouter {

    private final ComunaInfoRepository comunaInfoRepository;
    private final HeatAlertEventRepository heatAlertEventRepository;
    private final RegionRepository regionRepository;

    FirmsAttributionRouter(
        ComunaInfoRepository comunaInfoRepository,
        HeatAlertEventRepository heatAlertEventRepository,
        RegionRepository regionRepository
    ) {
        this.comunaInfoRepository = comunaInfoRepository;
        this.heatAlertEventRepository = heatAlertEventRepository;
        this.regionRepository = regionRepository;
    }

    /** Comuna-level routing (used by {@link ComunaRiskServiceImpl#recomputeByComuna}). */
    List<HeatAlertEvent> resolveForComuna(ComunaInfo comuna, LocalDateTime since) {
        if (comuna.getGeometry() != null) {
            // Geometric source of truth: events whose persisted comunaId is this
            // comuna's id. Null-comunaId rows (offshore, or not yet backfilled) never
            // match by construction — preserves Invariant 4.
            return heatAlertEventRepository.findByComunaIdAndFechaEventoAfter(comuna.getId(), since).stream()
                .filter(this::isHighConfidenceFirmsWithCoords)
                .collect(Collectors.toList());
        }

        // RETAINED centroid fallback (Decision 6): this comuna's own geometry is missing
        // (coverage gap OR invalid/skipped GADM polygon, Decision 3) — keep the FIRMS
        // component functional instead of going silently blind. Candidate pool is
        // fuente+recency only (FIX 2) — never pre-filtered by persisted regionId.
        List<ComunaInfo> allComunasInRegion = comunaInfoRepository.findByRegionId(comuna.getRegionId());
        List<HeatAlertEvent> candidates = fallbackCandidatePool(since);
        return assignFocosToComuna(candidates, comuna.getId(), allComunasInRegion);
    }

    /** Region-level routing (used by {@link TerritoryRiskServiceImpl#recomputeRiskByRegion}). */
    List<HeatAlertEvent> resolveForRegion(String regionId, LocalDateTime since) {
        List<ComunaInfo> comunasInRegion = comunaInfoRepository.findByRegionId(regionId);

        List<ComunaInfo> covered = new ArrayList<>();
        List<ComunaInfo> uncovered = new ArrayList<>();
        for (ComunaInfo comuna : comunasInRegion) {
            if (comuna.getGeometry() != null) {
                covered.add(comuna);
            } else {
                uncovered.add(comuna);
            }
        }

        List<HeatAlertEvent> result = new ArrayList<>();

        // FIX 1 (finding C1): split coverage, not all-or-nothing. A region that is 9/10
        // covered gets precise geometric counts for the 9 covered comunas and
        // centroid-approximate counts for the 1 uncovered comuna — summed together —
        // instead of an all-or-nothing decision for the whole region.
        if (!covered.isEmpty()) {
            List<String> coveredComunaIds = covered.stream().map(ComunaInfo::getId).toList();
            result.addAll(
                heatAlertEventRepository.findByComunaIdInAndFechaEventoAfter(coveredComunaIds, since).stream()
                    .filter(this::isHighConfidenceFirmsWithCoords)
                    .toList()
            );
        }

        // No comunas at all in the region (e.g. a region not yet seeded with any comuna
        // rows) falls back to the legacy whole-region centroid path so the FIRMS
        // component degrades gracefully instead of silently returning nothing.
        if (!uncovered.isEmpty() || comunasInRegion.isEmpty()) {
            List<HeatAlertEvent> candidates = fallbackCandidatePool(since);
            List<Region> allRegions = regionRepository.findAll();
            result.addAll(
                candidates.stream()
                    .filter(e -> regionId.equals(findNearestRegionId(e.getLatitud(), e.getLongitud(), allRegions)))
                    .toList()
            );
        }

        return result;
    }

    // FIX 2: candidate pool for the centroid fallback — fuente + recency only, never
    // pre-filtered by the event's persisted regionId (see class Javadoc for rationale).
    private List<HeatAlertEvent> fallbackCandidatePool(LocalDateTime since) {
        return heatAlertEventRepository.findByFuenteAndFechaEventoAfter("NASA_FIRMS", since).stream()
            .filter(this::isHighConfidenceWithCoords)
            .toList();
    }

    private boolean isHighConfidenceFirmsWithCoords(HeatAlertEvent e) {
        return "NASA_FIRMS".equals(e.getFuente()) && isHighConfidenceWithCoords(e);
    }

    private boolean isHighConfidenceWithCoords(HeatAlertEvent e) {
        return e.getFirmsConfidence() != null && !"l".equals(e.getFirmsConfidence())
            && e.getLatitud() != null && e.getLongitud() != null;
    }

    // RETAINED centroid attribution (Decision 6) — comuna granularity. Unchanged math
    // from the pre-incident implementation; only the candidate pool sourcing changed
    // (FIX 2).
    private List<HeatAlertEvent> assignFocosToComuna(
        List<HeatAlertEvent> candidates,
        String targetComunaId,
        List<ComunaInfo> allComunas
    ) {
        return candidates.stream()
            .filter(foco -> targetComunaId.equals(findNearestComuna(foco.getLatitud(), foco.getLongitud(), allComunas)))
            .collect(Collectors.toList());
    }

    private String findNearestComuna(double lat, double lon, List<ComunaInfo> comunas) {
        String nearest = null;
        double minDist = Double.MAX_VALUE;
        for (ComunaInfo c : comunas) {
            if (c.getCenterLat() == null || c.getCenterLon() == null) continue;
            double dist = Math.pow(lat - c.getCenterLat(), 2) + Math.pow(lon - c.getCenterLon(), 2);
            if (dist < minDist) {
                minDist = dist;
                nearest = c.getId();
            }
        }
        return nearest;
    }

    // RETAINED centroid attribution (Decision 6) — region granularity. Unchanged math
    // from the pre-incident implementation; only the candidate pool sourcing changed
    // (FIX 2).
    private String findNearestRegionId(Double lat, Double lon, List<Region> regions) {
        if (lat == null || lon == null) {
            return null;
        }
        String nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Region region : regions) {
            List<Double> bbox = region.getAoiBbox();
            if (bbox == null || bbox.size() != 4) {
                continue;
            }
            double centerLat = (bbox.get(1) + bbox.get(3)) / 2;
            double centerLon = (bbox.get(0) + bbox.get(2)) / 2;
            double dist = Math.pow(lat - centerLat, 2) + Math.pow(lon - centerLon, 2);
            if (dist < minDist) {
                minDist = dist;
                nearest = region.getId();
            }
        }
        return nearest;
    }
}
