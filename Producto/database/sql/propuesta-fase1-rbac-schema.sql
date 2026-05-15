-- FASE 0 - PROPUESTA DE MIGRACION RBAC (NO EJECUTAR AUN)
-- Fecha: 2026-05-14
-- Estado: borrador para revision tecnica

-- 1) Catalogo de roles
CREATE TABLE IF NOT EXISTS roles (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255) NULL,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- 2) Catalogo de permisos
CREATE TABLE IF NOT EXISTS permissions (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    module VARCHAR(80) NOT NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- 3) Relacion rol-permiso
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

-- 4) Relacion usuario-rol
CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    assigned_by VARCHAR(36) NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 5) Verificacion de usuario
CREATE TABLE IF NOT EXISTS user_verification (
    user_id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(40) NOT NULL,
    email_verified_at TIMESTAMPTZ NULL,
    phone_verified_at TIMESTAMPTZ NULL,
    identity_verified_at TIMESTAMPTZ NULL,
    organization_name VARCHAR(120) NULL,
    organization_verified_at TIMESTAMPTZ NULL,
    trust_score NUMERIC(5,2) NULL,
    reputation_score INTEGER NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- 6) Auditoria de verificaciones
CREATE TABLE IF NOT EXISTS verification_events (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    old_status VARCHAR(40) NULL,
    new_status VARCHAR(40) NOT NULL,
    reviewed_by VARCHAR(36) NULL,
    notes VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Nota: FKs e indices se agregaran en Fase 1 tras validar estrategia de migracion legacy.
