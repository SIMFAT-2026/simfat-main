package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Judgment-day finding #1 (defense in depth): {@link MapbiomasLayerCache} must stay bounded even
 * if a caller ever builds a key from an unnormalized, attacker-controlled value. Independent of
 * whether the caller normalizes correctly, the cache itself enforces a hard cap and never grows
 * past it -- entries beyond the cap are computed but not stored.
 */
class MapbiomasLayerCacheTest {

    @Test
    void distinctKeysBeyondTheCap_areComputedButNeverStored_sizeNeverExceedsTheCap() {
        MapbiomasLayerCache cache = new MapbiomasLayerCache(3);

        for (int i = 0; i < 500; i++) {
            String key = "garbage-" + i;
            Map<String, Object> result = cache.computeIfAbsent(key, () -> Map.of("v", 1));
            assertThat(result).isNotNull();
            assertThat(cache.size()).isLessThanOrEqualTo(3);
        }

        assertThat(cache.size()).isLessThanOrEqualTo(3);
    }

    @Test
    void sameKeyTwice_secondCallDoesNotRecompute() {
        MapbiomasLayerCache cache = new MapbiomasLayerCache(64);
        int[] computeCount = {0};

        Map<String, Object> first = cache.computeIfAbsent("k1", () -> {
            computeCount[0]++;
            return new HashMap<>(Map.of("value", "same-instance-marker"));
        });
        Map<String, Object> second = cache.computeIfAbsent("k1", () -> {
            computeCount[0]++;
            return new HashMap<>(Map.of("value", "should-not-be-used"));
        });

        assertThat(computeCount[0]).isEqualTo(1);
        assertThat(second).isSameAs(first);
    }

    @Test
    void defaultCap_isDeliberatelySizedForTheWholeClosedKeySpace() {
        // 3 regions x (26 LANDCOVER years + 13 BURNED years) = 117 bounded keys; the cap is 128.
        int years = (MapbiomasYear.LANDCOVER_MAX_YEAR - MapbiomasYear.LANDCOVER_MIN_YEAR + 1)
            + (MapbiomasYear.BURNED_MAX_YEAR - MapbiomasYear.BURNED_MIN_YEAR + 1);
        assertThat(MapbiomasLayerCache.KNOWN_REGIONS * years).isEqualTo(117);
        assertThat(MapbiomasLayerCache.BOUNDED_KEY_SPACE).isEqualTo(117);
        assertThat(MapbiomasLayerCache.DEFAULT_MAX_ENTRIES).isEqualTo(128);

        MapbiomasLayerCache cache = new MapbiomasLayerCache(MapbiomasLayerCache.DEFAULT_MAX_ENTRIES);
        for (int i = 0; i < MapbiomasLayerCache.BOUNDED_KEY_SPACE; i++) {
            cache.computeIfAbsent("known-" + i, () -> Map.of("v", 1));
        }
        assertThat(cache.size()).isEqualTo(117);

        // Garbage keys beyond the legitimate space still cannot grow it past the cap.
        for (int i = 0; i < 500; i++) {
            cache.computeIfAbsent("garbage-" + i, () -> Map.of("v", 1));
        }
        assertThat(cache.size()).isEqualTo(128);
    }

    @Test
    void boundedKeySpace_isDerivedFromTheYearRangesAndFitsInTheDefaultCap() {
        // Guard: adding a region or widening a year range must fail the build here instead of
        // silently pushing the legitimate key space past the cap (which would disable caching).
        int landCoverYears = MapbiomasYear.LANDCOVER_MAX_YEAR - MapbiomasYear.LANDCOVER_MIN_YEAR + 1;
        int burnedYears = MapbiomasYear.BURNED_MAX_YEAR - MapbiomasYear.BURNED_MIN_YEAR + 1;

        assertThat(MapbiomasLayerCache.BOUNDED_KEY_SPACE)
            .isEqualTo(MapbiomasLayerCache.KNOWN_REGIONS * (landCoverYears + burnedYears));
        assertThat(MapbiomasLayerCache.BOUNDED_KEY_SPACE)
            .isLessThanOrEqualTo(MapbiomasLayerCache.DEFAULT_MAX_ENTRIES);
    }

    @Test
    void keysWithinTheCap_areAllRetained() {
        MapbiomasLayerCache cache = new MapbiomasLayerCache(64);

        for (int i = 0; i < 39; i++) {
            cache.computeIfAbsent("known-" + i, () -> Map.of("v", 1));
        }

        assertThat(cache.size()).isEqualTo(39);
    }
}
