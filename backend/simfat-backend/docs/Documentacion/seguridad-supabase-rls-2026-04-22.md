# Hardening Supabase RLS - SIMFAT Backend

- Fecha: 2026-04-22
- Version: 1.0
- Contexto: alerta de seguridad en Supabase por tablas publicas sin Row-Level Security (RLS).

## Hallazgo recibido

Security Advisor reporto el issue:

- `rls_disabled_in_public`
- Riesgo: lectura/escritura/eliminacion no autorizada desde clientes con URL del proyecto.

Tablas afectadas:

- `public.flyway_schema_history`
- `public.app_users`
- `public.refresh_tokens`
- `public.password_reset_tokens`

## Mitigacion aplicada

1. Activacion de RLS en las 4 tablas.
2. Revocacion de privilegios para `anon`, `authenticated` y `PUBLIC`.

SQL aplicado:

```sql
begin;

alter table public.flyway_schema_history enable row level security;
alter table public.app_users enable row level security;
alter table public.refresh_tokens enable row level security;
alter table public.password_reset_tokens enable row level security;

revoke all on table public.flyway_schema_history from anon, authenticated, PUBLIC;
revoke all on table public.app_users from anon, authenticated, PUBLIC;
revoke all on table public.refresh_tokens from anon, authenticated, PUBLIC;
revoke all on table public.password_reset_tokens from anon, authenticated, PUBLIC;

commit;
```

## Validacion operativa

- No se observaron errores visibles posteriores a la mitigacion.
- Flujo funcional reportado por equipo: operaciones CRUD desde aplicacion operativas.

## Recomendaciones de continuidad

1. Revisar Security Advisor de Supabase semanalmente.
2. Aplicar regla preventiva: toda tabla nueva en `public` debe nacer con RLS habilitado.
3. Mantener acceso a tablas de autenticacion solo via backend confiable.
