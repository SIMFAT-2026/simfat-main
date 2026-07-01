package com.simfat.backend.service.impl;

/**
 * Shared FIRMS scoring constants (post-review FIX 7 — finding C8). Both
 * {@link ComunaRiskServiceImpl} and {@link TerritoryRiskServiceImpl} previously declared
 * identical literals independently; extracted here so the two services cannot drift apart
 * (Architectural Invariant 5 — both attribution paths, geometric and centroid fallback,
 * feed the SAME reconciled constants).
 */
final class FirmsScoringConstants {

    static final double FIRMS_MAX_COUNT = 5.0;
    static final double FIRMS_MAX_FRP = 80.0;
    static final int FIRMS_COUNT_CRITICO = 4;
    static final double FIRMS_FRP_CRITICO = 60.0;

    private FirmsScoringConstants() {
    }
}
