package com.simfat.backend.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} so {@link BackfillComunaIdRunner} can run off the
 * ComunaGeometrySeededEvent thread (revised Decision 1 — never block boot/health-check
 * readiness on the FIRMS backfill).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    // Single-thread executor: the backfill must never compete with itself across boots
    // or run more than one instance concurrently.
    @Bean(name = "backfillExecutor")
    public Executor backfillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("firms-backfill-");
        executor.initialize();
        return executor;
    }
}
