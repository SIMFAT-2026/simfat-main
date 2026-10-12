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
    void keysWithinTheCap_areAllRetained() {
        MapbiomasLayerCache cache = new MapbiomasLayerCache(64);

        for (int i = 0; i < 39; i++) {
            cache.computeIfAbsent("known-" + i, () -> Map.of("v", 1));
        }

        assertThat(cache.size()).isEqualTo(39);
    }
}
