package com.simfat.backend.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DashboardQueryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardQueryCache.class);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader) {
        Instant now = Instant.now();
        CacheEntry current = cache.get(key);
        if (current != null && current.expiresAt().isAfter(now)) {
            long hits = hitCount.incrementAndGet();
            LOGGER.info("dashboard_cache event=hit key={} hits={} misses={}", key, hits, missCount.get());
            return (T) current.value();
        }

        T loaded = loader.get();
        cache.put(key, new CacheEntry(loaded, now.plus(ttl)));
        long misses = missCount.incrementAndGet();
        LOGGER.info("dashboard_cache event=miss key={} hits={} misses={}", key, hitCount.get(), misses);
        return loaded;
    }

    public void invalidateByPrefix(String prefix) {
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }
}
