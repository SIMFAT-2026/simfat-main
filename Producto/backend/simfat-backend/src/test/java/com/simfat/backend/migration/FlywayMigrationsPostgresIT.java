package com.simfat.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the real Flyway migrations against a real PostgreSQL, with a short lock_timeout so
 * a lock wait fails fast instead of hanging until a pooler's statement_timeout (the V7
 * production incident: ~2 minutes of waiting, then "canceling statement due to statement
 * timeout").
 *
 * <p>Opt-in: set {@code SIMFAT_PG_MIGRATION_URL} (and optionally
 * {@code SIMFAT_PG_MIGRATION_USER}/{@code SIMFAT_PG_MIGRATION_PASSWORD}) to a throwaway
 * PostgreSQL, e.g. {@code docker run -d -p 55432:5432 -e POSTGRES_PASSWORD=x postgres:17}
 * with {@code jdbc:postgresql://localhost:55432/postgres}. The test wipes the {@code public}
 * schema first. Skipped when the variable is absent, so the default suite (H2 + embedded
 * Mongo setup) is unaffected.
 */
class FlywayMigrationsPostgresIT {

    private static final String URL = System.getenv("SIMFAT_PG_MIGRATION_URL");
    private static final String USER = envOr("SIMFAT_PG_MIGRATION_USER", "postgres");
    private static final String PASSWORD = envOr("SIMFAT_PG_MIGRATION_PASSWORD", "x");

    @BeforeEach
    void resetSchema() throws SQLException {
        assumeTrue(URL != null && !URL.isBlank(), "SIMFAT_PG_MIGRATION_URL not set");
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
                Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA public CASCADE");
            s.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void allMigrationsApplyWithoutWaitingOnLocks() {
        Flyway upToV6 = flyway("15s").target(MigrationVersion.fromVersion("6")).load();
        upToV6.migrate();

        Flyway all = flyway("15s").load();
        var result = all.migrate();

        assertThat(result.success).isTrue();
        assertThat(all.info().current().getVersion().getVersion()).isEqualTo("7");
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(String lockTimeout) {
        return Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations("classpath:db/migration")
                .initSql("SET lock_timeout = '" + lockTimeout + "'; SET statement_timeout = '60s'");
    }

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
