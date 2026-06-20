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
3. Mantener acceso a tablas de autenticaci?n solo via backend confiable.

## Aviso Supabase Data API - 2026-05-27

Supabase anuncio un cambio de seguridad para la exposicion automatica de tablas del esquema `public` en la Data API.

### Impacto

- Desde el 2026-05-30, los proyectos nuevos no exponen tablas nuevas de `public` en PostgREST, GraphQL ni `supabase-js` por defecto.
- En proyectos existentes, el cambio se aplicara a tablas nuevas desde el 2026-10-30.
- Las tablas existentes mantienen el comportamiento actual hasta esa fecha.

### Decision SIMFAT

Por ahora no se modifican migraciones ni permisos existentes. Se documenta el cambio como riesgo preventivo para futuras tablas.

### Regla para futuras migraciones

Toda tabla nueva que deba ser accesible desde Supabase Data API debe incluir `GRANT` explicitos en la misma migracion, manteniendo RLS como control de acceso por fila.

Ejemplo para tabla accesible solo por usuarios autenticados:

```sql
grant usage on schema public to authenticated;
grant select, insert, update, delete on table public.mi_tabla to authenticated;
```

Ejemplo para tabla publica de solo lectura:

```sql
grant usage on schema public to anon, authenticated;
grant select on table public.mi_tabla to anon, authenticated;
```

Nota: `GRANT` y RLS son controles complementarios. `GRANT` permite que la Data API vea la tabla; RLS define que filas puede leer o modificar cada rol.
