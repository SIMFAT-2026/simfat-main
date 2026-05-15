# MER Integrado Semana 10 - PostgreSQL + MongoDB + RBAC

Fecha: 2026-05-15

## Entidades relacionales principales (PostgreSQL)

- `app_users`
- `roles`
- `permissions`
- `user_roles`
- `role_permissions`
- `user_verification`
- `verification_events`

## Relacional (resumen)

```mermaid
erDiagram
  APP_USERS ||--o{ USER_ROLES : has
  ROLES ||--o{ USER_ROLES : assigned
  ROLES ||--o{ ROLE_PERMISSIONS : grants
  PERMISSIONS ||--o{ ROLE_PERMISSIONS : maps
  APP_USERS ||--o| USER_VERIFICATION : verification
  APP_USERS ||--o{ VERIFICATION_EVENTS : audit
```

## Colecciones/documentos operativos (MongoDB)

- `heat_alert_events`
- `forest_loss_records`
- `community_board_posts`
- `community_resources`
- `citizen_reports`
- `open_eo_job_runs`
- `open_eo_indicator_observations`

## Decisiones

- RBAC y verificacion se gobiernan en PostgreSQL para integridad y joins de seguridad.
- Datos operativos y de series/telemetria se mantienen en MongoDB por flexibilidad y volumen.
- Las claves de usuario (`user_id`) permiten trazabilidad cruzada entre ambos motores.
