package com.simfat.backend.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bounded, lazily-populated cache for MapBiomas-derived layer payloads (design D5 caching note,
 * task 2b2a.2). Backs {@code TerritoryController#burnedLayer}.
 *
 * <p><b>Defense in depth</b> (judgment-day finding #1, CRITICAL): the intended key space is
 * {@code dataVersion x indicator x year x regionId}, and callers are expected to normalize
 * {@code regionId} to one of the known regions before building a key -- so under normal
 * operation this cache never grows past a small, fixed number of entries. This class does NOT
 * rely on that discipline alone: it enforces a hard {@code maxEntries} cap independently, so even
 * if a future edit reintroduces an unnormalized, attacker-controlled key component, the cache can
 * never grow without bound. Once the cap is reached, additional keys are computed on every call
 * but never stored (never evicts an existing entry to make room) -- it degrades to "always
 * recompute" instead of leaking memory or amplifying Mongo load per distinct garbage input.
 */
public final class MapbiomasLayerCache {

    private final int maxEntries;
    private final Map<String, Map<String, Object>> entries = new LinkedHashMap<>();

    public MapbiomasLayerCache(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    /**
     * Returns the cached value for {@code key}, computing it via {@code compute} on a miss.
     * The computed value is stored only while the cache is below {@link #maxEntries}.
     */
    public synchronized Map<String, Object> computeIfAbsent(String key, Supplier<Map<String, Object>> compute) {
        Map<String, Object> cached = entries.get(key);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> computed = compute.get();
        if (entries.size() < maxEntries) {
            entries.put(key, computed);
        }
        return computed;
    }

    public synchronized int size() {
        return entries.size();
    }
}
