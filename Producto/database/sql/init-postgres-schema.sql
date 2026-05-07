-- ==========================================
-- Simfat Backend - PostgreSQL schema (Auth)
-- Idempotent script
-- ==========================================

BEGIN;

CREATE TABLE IF NOT EXISTS app_users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(180) NOT NULL UNIQUE,
    full_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    roles VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    replaced_by_token_id VARCHAR(64) NULL,
    created_by_ip VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL
);

-- Indexes from current model/repository usage
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked_at ON refresh_tokens (revoked_at);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);

-- Recommended indexes for active-token lookups
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id_revoked_at ON refresh_tokens (user_id, revoked_at);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id_consumed_at ON password_reset_tokens (user_id, consumed_at);

-- Recommended referential integrity (logical FK in code -> physical FK in SQL)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_refresh_tokens_user'
          AND table_name = 'refresh_tokens'
    ) THEN
        ALTER TABLE refresh_tokens
            ADD CONSTRAINT fk_refresh_tokens_user
            FOREIGN KEY (user_id) REFERENCES app_users(id)
            ON DELETE CASCADE;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_password_reset_tokens_user'
          AND table_name = 'password_reset_tokens'
    ) THEN
        ALTER TABLE password_reset_tokens
            ADD CONSTRAINT fk_password_reset_tokens_user
            FOREIGN KEY (user_id) REFERENCES app_users(id)
            ON DELETE CASCADE;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_refresh_tokens_replaced_by_token_id'
          AND table_name = 'refresh_tokens'
    ) THEN
        ALTER TABLE refresh_tokens
            ADD CONSTRAINT fk_refresh_tokens_replaced_by_token_id
            FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens(token_id)
            ON DELETE SET NULL;
    END IF;
END
$$;

COMMIT;
