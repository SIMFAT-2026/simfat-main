CREATE TABLE IF NOT EXISTS roles (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255) NULL,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS permissions (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    module VARCHAR(80) NOT NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    assigned_by VARCHAR(36) NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_assigned_by FOREIGN KEY (assigned_by) REFERENCES app_users (id)
);

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
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_user_verification_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS verification_events (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    old_status VARCHAR(40) NULL,
    new_status VARCHAR(40) NOT NULL,
    reviewed_by VARCHAR(36) NULL,
    notes VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_verification_events_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_verification_events_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_users (id)
);

CREATE INDEX IF NOT EXISTS idx_roles_code ON roles (code);
CREATE INDEX IF NOT EXISTS idx_permissions_code ON permissions (code);
CREATE INDEX IF NOT EXISTS idx_permissions_module ON permissions (module);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles (role_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_assigned_by ON user_roles (assigned_by);
CREATE INDEX IF NOT EXISTS idx_user_verification_status ON user_verification (status);
CREATE INDEX IF NOT EXISTS idx_verification_events_user_id ON verification_events (user_id);
CREATE INDEX IF NOT EXISTS idx_verification_events_created_at ON verification_events (created_at);

-- Seed inicial de roles de sistema
INSERT INTO roles (id, code, name, description, is_system, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'ROLE_SUPER_ADMIN', 'Super Admin', 'Acceso total y administracion global', TRUE, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000002', 'ROLE_ADMIN', 'Admin', 'Gestion administrativa y operativa', TRUE, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000003', 'ROLE_MODERATOR', 'Moderator', 'Moderacion de contenido y reportes', TRUE, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000004', 'ROLE_VERIFIED_USER', 'Verified User', 'Usuario verificado para reportes avanzados', TRUE, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000005', 'ROLE_COMMUNITY_USER', 'Community User', 'Usuario comunitario con acceso limitado', TRUE, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- Seed inicial de permisos base
INSERT INTO permissions (id, code, name, module, description, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'PERM_REPORT_CREATE', 'Crear reporte ciudadano', 'citizen_reports', 'Permite crear reportes ciudadanos', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000102', 'PERM_REPORT_ATTACH_IMAGE', 'Adjuntar imagen en reporte', 'citizen_reports', 'Permite adjuntar imagenes a reportes', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000103', 'PERM_REPORT_MODERATE', 'Moderar reporte ciudadano', 'citizen_reports', 'Permite cambiar estado/eliminar reportes', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000104', 'PERM_COMMUNITY_RESOURCE_READ', 'Leer recursos comunitarios', 'community', 'Permite consultar recursos comunitarios', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000105', 'PERM_COMMUNITY_RESOURCE_MANAGE', 'Gestionar recursos comunitarios', 'community', 'Permite crear/eliminar recursos comunitarios', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000106', 'PERM_COMMUNITY_BOARD_MANAGE', 'Gestionar mural comunitario', 'community', 'Permite crear/eliminar avisos comunitarios', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000107', 'PERM_ALERT_RULE_MANAGE', 'Gestionar reglas de alerta', 'rules', 'Permite crear/editar/eliminar reglas', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000108', 'PERM_REGION_MANAGE', 'Gestionar regiones', 'regions', 'Permite crear/editar/eliminar regiones', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000109', 'PERM_DASHBOARD_SYNC_RUN', 'Ejecutar sync dashboard', 'dashboard', 'Permite disparar sincronizacion manual', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000110', 'PERM_USER_MANAGE', 'Gestionar usuarios', 'users', 'Permite administrar usuarios del sistema', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000111', 'PERM_VERIFICATION_APPROVE', 'Aprobar verificaciones', 'verification', 'Permite aprobar/rechazar verificaciones', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000112', 'PERM_MODERATOR_MANAGE', 'Gestionar moderadores', 'moderation', 'Permite asignar o remover moderadores', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000113', 'PERM_ADMIN_MANAGE', 'Gestionar administradores', 'administration', 'Permite administrar roles administrativos', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000114', 'PERM_INFRA_CONFIG', 'Configurar infraestructura', 'infrastructure', 'Permite ajustes de infraestructura', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- Mapeo base rol-permiso
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000104', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions WHERE role_id = '00000000-0000-0000-0000-000000000005' AND permission_id = '00000000-0000-0000-0000-000000000104'
);

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT '00000000-0000-0000-0000-000000000004', permission_id, NOW()
FROM (VALUES
    ('00000000-0000-0000-0000-000000000101'),
    ('00000000-0000-0000-0000-000000000102'),
    ('00000000-0000-0000-0000-000000000104')
) AS p(permission_id)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000004' AND rp.permission_id = p.permission_id
);

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT '00000000-0000-0000-0000-000000000003', permission_id, NOW()
FROM (VALUES
    ('00000000-0000-0000-0000-000000000103'),
    ('00000000-0000-0000-0000-000000000105'),
    ('00000000-0000-0000-0000-000000000106')
) AS p(permission_id)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000003' AND rp.permission_id = p.permission_id
);

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT '00000000-0000-0000-0000-000000000002', permission_id, NOW()
FROM (VALUES
    ('00000000-0000-0000-0000-000000000103'),
    ('00000000-0000-0000-0000-000000000105'),
    ('00000000-0000-0000-0000-000000000106'),
    ('00000000-0000-0000-0000-000000000107'),
    ('00000000-0000-0000-0000-000000000108'),
    ('00000000-0000-0000-0000-000000000109'),
    ('00000000-0000-0000-0000-000000000110'),
    ('00000000-0000-0000-0000-000000000111'),
    ('00000000-0000-0000-0000-000000000112')
) AS p(permission_id)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000002' AND rp.permission_id = p.permission_id
);

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT '00000000-0000-0000-0000-000000000001', p.id, NOW()
FROM permissions p
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000001' AND rp.permission_id = p.id
);
