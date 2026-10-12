package com.simfat.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

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
 * schema first and refuses to run unless the host is localhost or 127.0.0.1. Skipped when the variable is absent, so the default suite (H2 + embedded
 * Mongo setup) is unaffected.
 */
class FlywayMigrationsPostgresIntegrationTest {

    private static final String URL = System.getenv("SIMFAT_PG_MIGRATION_URL");
    private static final String USER = envOr("SIMFAT_PG_MIGRATION_USER", "postgres");
    private static final String PASSWORD = envOr("SIMFAT_PG_MIGRATION_PASSWORD", "x");

    private static final List<String> TABLES = List.of(
            "app_users", "refresh_tokens", "password_reset_tokens", "roles", "permissions",
            "role_permissions", "user_roles", "user_verification", "verification_events",
            "user_community_profiles", "community_chat_room_access", "notifications");

    @BeforeEach
    void resetSchema() throws SQLException {
        assumeTrue(URL != null && !URL.isBlank(), "SIMFAT_PG_MIGRATION_URL not set");
        requireLocalHost();
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
                Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA public CASCADE");
            s.execute("CREATE SCHEMA public");
            // Mimic Supabase: anon/authenticated exist and get ALL on new public tables by default.
            // Roles are cluster-wide and intentionally left in place (throwaway local DB only).
            for (String role : List.of("anon", "authenticated")) {
                s.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + role
                        + "') THEN CREATE ROLE " + role + " NOLOGIN; END IF; END $$");
            }
            s.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated");
            s.execute("GRANT USAGE ON SCHEMA public TO anon, authenticated");
        }
    }

    @Test
    void allMigrationsApplyWithoutWaitingOnLocks() throws SQLException {
        Flyway upToV6 = flyway("15s").target(MigrationVersion.fromVersion("6")).load();
        upToV6.migrate();

        // Precondition: the Supabase-like default privileges really reached the V1-V6 tables.
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
                Statement s = c.createStatement()) {
            assertThat(scalar(s, "SELECT has_table_privilege('anon', 'public.app_users', 'SELECT')")).isTrue();
        }

        Flyway all = flyway("15s").load();
        var result = all.migrate();

        assertThat(result.success).isTrue();
        assertThat(all.info().current().getVersion().getVersion()).isEqualTo("7");

        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
                Statement s = c.createStatement()) {
            for (String table : TABLES) {
                assertThat(scalar(s, "SELECT relrowsecurity FROM pg_class WHERE oid = 'public." + table
                        + "'::regclass")).as("RLS enabled on %s", table).isTrue();
                for (String role : List.of("anon", "authenticated")) {
                    assertThat(scalar(s, "SELECT has_table_privilege('" + role + "', 'public." + table
                            + "', 'SELECT,INSERT,UPDATE,DELETE')"))
                            .as("%s must have no privileges on %s", role, table).isFalse();
                }
                assertThat(scalar(s, "SELECT COALESCE(bool_or(a.grantee = 0), false) FROM pg_class c, "
                        + "LATERAL aclexplode(COALESCE(c.relacl, acldefault('r', c.relowner))) a "
                        + "WHERE c.oid = 'public." + table + "'::regclass"))
                        .as("PUBLIC must have no privileges on %s", table).isFalse();
            }

            // Validates the ALTER DEFAULT PRIVILEGES revoke: a later table is closed to anon.
            s.execute("CREATE TABLE public.created_after_v7 (id int)");
            assertThat(scalar(s, "SELECT has_table_privilege('anon', 'public.created_after_v7', "
                    + "'SELECT,INSERT,UPDATE,DELETE')")).isFalse();
            assertThat(scalar(s, "SELECT has_table_privilege('authenticated', 'public.created_after_v7', "
                    + "'SELECT,INSERT,UPDATE,DELETE')")).isFalse();
        }
    }

    private static boolean scalar(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    /** Fails (never skips) when the target is not local, before anything destructive runs. */
    private static void requireLocalHost() {
        String host;
        try {
            host = URI.create(URL.substring("jdbc:".length())).getHost();
        } catch (RuntimeException e) {
            host = null;
        }
        if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
            fail("Refusing to run: SIMFAT_PG_MIGRATION_URL host must be localhost or 127.0.0.1 "
                    + "because the test drops the public schema (host was: " + host + ")");
        }
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
