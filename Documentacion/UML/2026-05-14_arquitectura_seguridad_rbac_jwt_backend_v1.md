# Arquitectura de Seguridad Backend - RBAC + JWT

- Fecha: 2026-05-14
- Version: 1.0
- Estado: Diseno Fase 0

## 1. Contexto actual

- Spring Security habilitado con filtro JWT.
- Autenticacion funcional para `/api/auth/*`.
- Autorizacion fina pendiente (muchos endpoints abiertos).

## 2. Arquitectura objetivo

1. `JwtAuthenticationFilter`
- Valida token.
- Resuelve identidad.
- Carga roles/permisos efectivos.

2. `Authorization layer`
- Politicas por endpoint y metodo.
- `@PreAuthorize` o reglas por request matcher.

3. `RBAC persistence`
- `roles`, `permissions`, `role_permissions`, `user_roles`.

4. `Verification layer`
- `user_verification` y eventos asociados.

## 3. Flujo objetivo resumido

1. Usuario autentica (login).
2. Backend emite JWT.
3. Request protegido entra por filtro JWT.
4. Se resuelven authorities efectivas.
5. Spring Security aplica regla minima necesaria.
6. Se permite o deniega con 200/401/403.

## 4. Principios aplicados

- Minimo privilegio.
- Defensa en profundidad (token + permiso + estado usuario).
- Auditoria de acciones criticas.
- Evolucion incremental con compatibilidad backward.

## 5. Decision de diseno

- Mantener JWT stateless.
- Evitar hardcode de permisos.
- Usar DB como fuente de verdad para roles/permisos.
