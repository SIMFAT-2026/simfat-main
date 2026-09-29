package com.simfat.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Judgment-day finding #1: {@code RegionGeometry.fromRegion} is the single normalization point
 * for {@code regionId} used by {@code TerritoryController#burnedLayer} (both the cache key and
 * the {@code comunaInfoRepository.findByRegionId} query). This pure, no-Spring test locks down
 * that garbage input can never produce a new canonical id -- it always collapses onto one of the
 * three known regions, matching how {@code MonitoredComunasConfig} seeds comuna {@code regionId}
 * values ({@code araucania}, {@code nuble}, {@code biobio}).
 */
class TerritoryControllerRegionNormalizationTest {

    @Test
    void knownRegionIds_resolveToThemselves() {
        assertThat(TerritoryController.RegionGeometry.fromRegion("araucania").id()).isEqualTo("araucania");
        assertThat(TerritoryController.RegionGeometry.fromRegion("nuble").id()).isEqualTo("nuble");
        assertThat(TerritoryController.RegionGeometry.fromRegion("biobio").id()).isEqualTo("biobio");
    }

    @Test
    void unknownOrGarbageRegionIds_fallBackToTheDefaultRegion() {
        String[] garbage = {"", "unknown", "'; DROP TABLE comunas;", "ARAUCANIA-EXTRA", "null", " ", "🔥"};
        for (String value : garbage) {
            assertThat(TerritoryController.RegionGeometry.fromRegion(value).id())
                .as("garbage regionId '%s' must fall back to the default region, never mint a new id", value)
                .isEqualTo("biobio");
        }
        assertThat(TerritoryController.RegionGeometry.fromRegion(null).id()).isEqualTo("biobio");
    }

    @Test
    void manyDistinctGarbageRegionIds_allCollapseOntoTheThreeKnownRegions() {
        Set<String> normalized = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            normalized.add(TerritoryController.RegionGeometry.fromRegion("garbage-" + i).id());
        }

        // None of the 500 distinct garbage inputs match "araucania" or "nuble" literally, so
        // every one of them must fall back to the same default -- never a distinct new id.
        assertThat(normalized).containsExactly("biobio");
    }
}
