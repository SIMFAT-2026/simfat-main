-- ==========================================
-- Simfat Backend - Test data seed (Auth)
-- Compatible with init-postgres-schema.sql
-- ==========================================

BEGIN;

-- Password hash placeholders (usar hash real de BCrypt en ambientes reales)
INSERT INTO app_users (id, email, full_name, password_hash, enabled, roles, created_at, updated_at)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'admin@simfat.cl', 'Admin SIMFAT', '$2a$10$adminplaceholderhash', TRUE, 'ROLE_ADMIN', NOW(), NOW()),
  ('22222222-2222-2222-2222-222222222222', 'usuario1@simfat.cl', 'Usuario General 1', '$2a$10$userplaceholderhash', TRUE, 'ROLE_USER', NOW(), NOW()),
  ('33333333-3333-3333-3333-333333333333', 'usuario2@simfat.cl', 'Usuario General 2', '$2a$10$userplaceholderhash2', TRUE, 'ROLE_USER', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

COMMIT;
