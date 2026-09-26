-- Enable Row Level Security on every backend-owned table in the public schema and
-- remove the Supabase Data API roles' privileges on them.
--
-- Why: Supabase exposes the public schema through PostgREST. A table without RLS can
-- be read and written by anyone holding the project's anon key; for user_roles that
-- would allow self-granted admin rights. The backend connects over JDBC as the table
-- owner, which bypasses RLS, so enabling RLS without policies does not affect it.
--
-- V1 tables and flyway_schema_history were hardened by hand on 2026-04-22
-- (Documentacion/Informes/seguridad-supabase-rls-2026-04-22.md). They are repeated here
-- so that any fresh environment gets the same state from the migrations alone. Every
-- statement is idempotent.
--
-- Precondition: the role that runs Flyway must own every listed table (true when all of
-- them were created by these migrations over the same datasource). Otherwise the first
-- ALTER fails with "must be owner", the migration rolls back and startup stops.
--
-- Deliberately untouched: service_role (full trust, used by the backend's Storage
-- client) and USAGE on schema public (anon/authenticated can still see the schema
-- but get "permission denied" on every table).

ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.role_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_verification ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verification_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_community_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_chat_room_access ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

-- anon and authenticated exist only on Supabase. Plain PostgreSQL (local Docker, CI)
-- does not have them, so the revokes run only where the roles exist.
DO $$
DECLARE
    tbl text;
    api_role text;
BEGIN
    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
            FOREACH tbl IN ARRAY ARRAY[
                'flyway_schema_history', 'app_users', 'refresh_tokens', 'password_reset_tokens',
                'roles', 'permissions', 'role_permissions', 'user_roles',
                'user_verification', 'verification_events',
                'user_community_profiles', 'community_chat_room_access', 'notifications'
            ] LOOP
                EXECUTE format('REVOKE ALL ON TABLE public.%I FROM %I', tbl, api_role);
            END LOOP;

            -- Tables created later by the backend owner must not be granted to the Data
            -- API roles by default; a table that needs Data API access grants it
            -- explicitly in its own migration.
            EXECUTE format(
                'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON TABLES FROM %I',
                current_user, api_role
            );
        END IF;
    END LOOP;
END
$$;

REVOKE ALL ON TABLE
    public.flyway_schema_history, public.app_users, public.refresh_tokens,
    public.password_reset_tokens, public.roles, public.permissions,
    public.role_permissions, public.user_roles, public.user_verification,
    public.verification_events, public.user_community_profiles,
    public.community_chat_room_access, public.notifications
FROM PUBLIC;
