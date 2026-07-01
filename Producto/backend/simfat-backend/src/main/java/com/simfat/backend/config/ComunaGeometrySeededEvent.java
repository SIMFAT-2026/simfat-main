package com.simfat.backend.config;

import org.springframework.context.ApplicationEvent;

/**
 * Published by {@link MonitoredComunasConfig#ensureMonitoredComunas} after the comuna
 * geometry seed finishes (its {@code saveAll} call returns). FIX 4 (post-review, finding
 * C4): {@code @Order} only sequences listener INVOCATION on the publishing thread for
 * {@code ApplicationReadyEvent} — it does NOT wait for an {@code @Async} listener to
 * finish, and nothing previously guaranteed {@link BackfillComunaIdRunner}'s async thread
 * didn't start reading {@code ComunaInfo.geometry} while the synchronous seed was still
 * mid-upsert on first boot. This event makes the dependency explicit and race-free
 * regardless of future refactors to either listener's timing: the backfill listens for
 * THIS event instead of {@code ApplicationReadyEvent}.
 */
public class ComunaGeometrySeededEvent extends ApplicationEvent {

    public ComunaGeometrySeededEvent(Object source) {
        super(source);
    }
}
