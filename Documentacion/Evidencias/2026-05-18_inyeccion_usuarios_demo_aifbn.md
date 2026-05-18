# Evidencia Tecnica - Inyeccion de Usuarios Demo AIFBN

Fecha: 2026-05-18  
Ambiente objetivo: backend online (staging/produccion segun disponibilidad operativa)

## 1. Objetivo

Disponer usuarios de prueba con nomenclatura simple para validacion funcional de acceso y autorizacion en reunion con AIFBN.

Usuarios solicitados:

- `jennifer@aifbn.cl`
- `pablo@aifbn.cl`

Rol requerido:

- `ROLE_ADMIN` (RBAC efectivo)

## 2. Estrategia aplicada

1. Alta inicial de usuarios via API de autenticacion para asegurar `password_hash` compatible.
2. Ajuste de privilegios en PostgreSQL para dejar:
   - `app_users.roles = ADMIN` (compatibilidad legacy)
   - asignacion en `user_roles` hacia `ROLE_ADMIN` (RBAC actual)

## 3. Credenciales operativas de demo

- Password comun de demostracion: `AifbnDemo2026!`

Nota de seguridad:
- Credenciales de demo para ambiente de prueba/presentacion.
- Se recomienda rotacion posterior al ciclo de evaluacion.

## 4. Comandos de referencia (documentales)

Registro por API:

```powershell
POST /api/auth/register
{
  "email": "jennifer@aifbn.cl",
  "fullName": "Jennifer AIFBN",
  "password": "AifbnDemo2026!"
}
```

```powershell
POST /api/auth/register
{
  "email": "pablo@aifbn.cl",
  "fullName": "Pablo AIFBN",
  "password": "AifbnDemo2026!"
}
```

Ajuste RBAC en SQL:

```sql
WITH target_users AS (
  SELECT id,email FROM app_users WHERE email IN ('jennifer@aifbn.cl','pablo@aifbn.cl')
), admin_role AS (
  SELECT id FROM roles WHERE code='ROLE_ADMIN'
)
UPDATE app_users u
SET roles='ADMIN', updated_at=NOW()
FROM target_users t
WHERE u.id=t.id;

WITH target_users AS (
  SELECT id,email FROM app_users WHERE email IN ('jennifer@aifbn.cl','pablo@aifbn.cl')
), admin_role AS (
  SELECT id FROM roles WHERE code='ROLE_ADMIN'
)
INSERT INTO user_roles (user_id, role_id, assigned_by, assigned_at)
SELECT t.id, r.id, t.id, NOW()
FROM target_users t CROSS JOIN admin_role r
ON CONFLICT (user_id, role_id) DO NOTHING;
```

## 5. Resultado validado

Consulta de verificacion esperada:

```sql
SELECT u.email, u.roles AS legacy_roles, array_agg(ro.code ORDER BY ro.code) AS assigned_rbac_roles
FROM app_users u
LEFT JOIN user_roles ur ON ur.user_id=u.id
LEFT JOIN roles ro ON ro.id=ur.role_id
WHERE u.email IN ('jennifer@aifbn.cl','pablo@aifbn.cl')
GROUP BY u.email, u.roles
ORDER BY u.email;
```

Estado esperado:

- `jennifer@aifbn.cl | ADMIN | {ROLE_ADMIN}`
- `pablo@aifbn.cl | ADMIN | {ROLE_ADMIN}`

## 6. Uso en QA/demo

- Estos usuarios se usan para:
  - login operativo
  - validacion de panel `Control de Accesos`
  - pruebas de endpoints administrativos protegidos
