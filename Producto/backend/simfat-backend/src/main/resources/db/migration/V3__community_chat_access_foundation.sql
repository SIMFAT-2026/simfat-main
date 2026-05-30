CREATE TABLE IF NOT EXISTS user_community_profiles (
    user_id VARCHAR(36) PRIMARY KEY,
    primary_region_id VARCHAR(80) NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_user_community_profiles_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS community_chat_room_access (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    region_id VARCHAR(80) NOT NULL,
    granted_by VARCHAR(36) NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_community_chat_room_access_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_chat_room_access_granted_by FOREIGN KEY (granted_by) REFERENCES app_users (id)
);

CREATE INDEX IF NOT EXISTS idx_user_community_profiles_region ON user_community_profiles (primary_region_id);
CREATE INDEX IF NOT EXISTS idx_community_chat_room_access_user ON community_chat_room_access (user_id);
CREATE INDEX IF NOT EXISTS idx_community_chat_room_access_region ON community_chat_room_access (region_id);
CREATE INDEX IF NOT EXISTS idx_community_chat_room_access_active ON community_chat_room_access (user_id, region_id, revoked_at);

INSERT INTO permissions (id, code, name, module, description, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000115', 'PERM_COMMUNITY_CHAT_READ', 'Leer chat comunitario', 'community_chat', 'Permite consultar salas y mensajes del chat comunitario', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000116', 'PERM_COMMUNITY_CHAT_SEND', 'Enviar mensajes comunitarios', 'community_chat', 'Permite enviar mensajes en salas comunitarias habilitadas', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000117', 'PERM_COMMUNITY_CHAT_MODERATE', 'Moderar chat comunitario', 'community_chat', 'Permite moderar mensajes del chat comunitario', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000118', 'PERM_COMMUNITY_CHAT_ACCESS_MANAGE', 'Gestionar acceso regional al chat', 'community_chat', 'Permite otorgar o revocar acceso a subsalas regionales', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT '00000000-0000-0000-0000-000000000005', permission_id, NOW()
FROM (VALUES
    ('00000000-0000-0000-0000-000000000115'),
    ('00000000-0000-0000-0000-000000000116')
) AS p(permission_id)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000005' AND rp.permission_id = p.permission_id
);

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT '00000000-0000-0000-0000-000000000004', permission_id, NOW()
FROM (VALUES
    ('00000000-0000-0000-0000-000000000115'),
    ('00000000-0000-0000-0000-000000000116')
) AS p(permission_id)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000004' AND rp.permission_id = p.permission_id
);

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT '00000000-0000-0000-0000-000000000003', permission_id, NOW()
FROM (VALUES
    ('00000000-0000-0000-0000-000000000115'),
    ('00000000-0000-0000-0000-000000000116'),
    ('00000000-0000-0000-0000-000000000117')
) AS p(permission_id)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000003' AND rp.permission_id = p.permission_id
);

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT '00000000-0000-0000-0000-000000000002', permission_id, NOW()
FROM (VALUES
    ('00000000-0000-0000-0000-000000000115'),
    ('00000000-0000-0000-0000-000000000116'),
    ('00000000-0000-0000-0000-000000000117'),
    ('00000000-0000-0000-0000-000000000118')
) AS p(permission_id)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000002' AND rp.permission_id = p.permission_id
);

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT '00000000-0000-0000-0000-000000000001', p.id, NOW()
FROM permissions p
WHERE p.module = 'community_chat'
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000001' AND rp.permission_id = p.id
);
