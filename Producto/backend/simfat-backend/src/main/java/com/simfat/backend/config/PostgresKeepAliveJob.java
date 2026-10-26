package com.simfat.backend.config;

import com.simfat.backend.repository.AppRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs a cheap read-only query against Postgres on a schedule so the Supabase free project is not
 * paused for inactivity. The public map and every other scheduled job only touch Mongo, so without
 * this a week without logins produces no Postgres activity at all.
 */
@Component
public class PostgresKeepAliveJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresKeepAliveJob.class);

    private final AppRoleRepository appRoleRepository;
    private final boolean enabled;

    public PostgresKeepAliveJob(
        AppRoleRepository appRoleRepository,
        @Value("${postgres.keepalive.enabled:true}") boolean enabled
    ) {
        this.appRoleRepository = appRoleRepository;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${postgres.keepalive.cron:0 15 */12 * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            long roles = appRoleRepository.count();
            LOGGER.info("postgres_keepalive status=ok roles={}", roles);
        } catch (Exception ex) {
            LOGGER.warn("postgres_keepalive status=failed error={}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}
