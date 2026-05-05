# Avance Auth + PostgreSQL (2026-04-15)

## Resumen ejecutivo

Se implemento autenticacion completa y segura en `simfat-backend`, con persistencia de auth en PostgreSQL y manteniendo MongoDB para dashboard/OpenEO.

## Entregables implementados

- Endpoints auth:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/forgot-password`
  - `POST /api/auth/reset-password`
  - `GET /api/auth/me`
  - `POST /api/auth/logout`
  - `POST /api/auth/refresh`
  - `POST /api/auth/dev/seed-users` (solo `dev/local`)
- JWT:
  - access token corto
  - refresh token rotatorio
  - revocacion de refresh tokens
- Seguridad:
  - hashing de passwords con BCrypt
  - rate limiting en login y forgot-password
  - turnstile opcional por feature flag
  - validaciones DTO robustas
  - respuestas compatibles con `ApiResponse<T>`
- Persistencia:
  - auth migrado a PostgreSQL (JPA + Flyway)
  - dashboard/OpenEO se mantiene en MongoDB
- Flyway:
  - migracion inicial `V1__create_auth_tables.sql`
  - tablas: `app_users`, `refresh_tokens`, `password_reset_tokens`
- CORS:
  - soporte para credenciales (`allowCredentials(true)`) en rutas `/api/**`

## Cambios de arquitectura

- Antes:
  - backend Spring Boot + MongoDB
- Ahora:
  - arquitectura hibrida:
    - PostgreSQL para autenticacion (requisito SQL evaluativo)
    - MongoDB para datos operacionales de dashboard/OpenEO

## Configuracion requerida

- Variables clave:
  - `POSTGRES_URI`
  - `POSTGRES_USER`
  - `POSTGRES_PASSWORD`
  - `MONGODB_URI`
  - `AUTH_JWT_SECRET` (>= 32 bytes)
  - `FRONTEND_URL` (origenes permitidos separados por coma)

## Verificaciones realizadas

- Compilacion:
  - `mvn -q -DskipTests compile`
- Pruebas auth:
  - registro/login/me/refresh/logout funcionando en local
  - preflight CORS correcto para origen de frontend

## Riesgos / notas operativas

- Si `AUTH_JWT_SECRET` no esta en entorno, el backend falla al iniciar.
- `.env` no siempre se carga automaticamente al ejecutar `mvn spring-boot:run`; asegurar export en la sesion o script de arranque.
- Para PostgreSQL 17.x se requiere dependencia `flyway-database-postgresql`.

## Proximos pasos sugeridos

1. Consolidar script de arranque local que cargue `.env` y ejecute backend.
2. Rotar credenciales compartidas en canales de chat (DB password y secretos).
3. Conectar envio real de correo para flujo `forgot/reset password`.
4. Agregar observabilidad de eventos auth (401, 429, refresh failures).

