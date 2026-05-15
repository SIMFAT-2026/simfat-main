# MER Actualizado - RBAC y Verificacion (Propuesta)

- Fecha: 2026-05-14
- Version: 1.0
- Estado: Diseno (Fase 0)

## 1. Objetivo

Actualizar el MER para soportar RBAC escalable, verificaciones de usuario y trazabilidad de permisos sin hardcode.

## 2. PostgreSQL (autenticacion/autorizacion)

### Nuevas entidades propuestas

1. `roles`
- `id` (PK)
- `code` (unique, ej. `ROLE_ADMIN`)
- `name`
- `description`
- `is_system`
- `created_at`, `updated_at`

2. `permissions`
- `id` (PK)
- `code` (unique, ej. `PERM_REPORT_MODERATE`)
- `name`
- `description`
- `module`
- `created_at`, `updated_at`

3. `role_permissions`
- `role_id` (FK -> roles)
- `permission_id` (FK -> permissions)
- PK compuesta (`role_id`, `permission_id`)

4. `user_roles`
- `user_id` (FK -> app_users)
- `role_id` (FK -> roles)
- `assigned_by` (FK -> app_users, nullable)
- `assigned_at`
- PK compuesta (`user_id`, `role_id`)

5. `user_verification`
- `user_id` (PK/FK -> app_users)
- `status` (`UNVERIFIED` ... `SUSPENDED`)
- `email_verified_at`
- `phone_verified_at`
- `identity_verified_at`
- `organization_name` (nullable)
- `organization_verified_at` (nullable)
- `trust_score` (nullable)
- `reputation_score` (nullable)
- `updated_at`

6. `verification_events` (auditoria)
- `id` (PK)
- `user_id` (FK -> app_users)
- `event_type`
- `old_status`
- `new_status`
- `reviewed_by` (FK -> app_users, nullable)
- `created_at`
- `notes`

### Ajuste en entidad existente

- `app_users.roles` (CSV legacy) queda en estado deprecado para migracion controlada a `user_roles`.

## 3. MongoDB (dominio operacional)

No se recomienda guardar autorizacion primaria en MongoDB.

Se propone agregar solo trazabilidad contextual en colecciones de dominio:
- `citizen_reports`: `createdByUserId`, `createdByRoleSnapshot`
- `community_board_posts`: `authorUserId`, `authorRoleSnapshot`
- `audit_events` (opcional): evento de moderacion y acciones criticas

## 4. Relaciones clave

- `app_users` 1..N `user_roles`
- `roles` 1..N `role_permissions`
- `permissions` 1..N `role_permissions`
- `app_users` 1..1 `user_verification`
- `app_users` 1..N `verification_events`

## 5. Beneficios del modelo

- Escalabilidad de permisos por modulo.
- Menor acoplamiento con codigo Java.
- Facil auditoria y evolucion a trust/reputacion.
- Compatibilidad con enfoque incremental.
