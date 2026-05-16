# Patrones de Diseno y Decisiones Tecnicas - Semana 10

Fecha: 2026-05-15

## Patrones de arquitectura

1. MVC en capa web (`Controller` + DTO + validaciones).
2. Service-Repository para separar logica de negocio y acceso a datos.
3. DTO Mapper para contrato estable API/cliente.
4. Filtro de seguridad con cadena Spring Security (`JwtAuthenticationFilter`).
5. RBAC data-driven (roles/permisos en BD, no hardcode).

## Patrones de diseno aplicados

- Strategy implicita para resolucion de autorizacion por permiso/rol.
- Template method en flujos comunes de API response y manejo de errores.
- Fail-safe defaults: denegacion por defecto cuando no hay autoridad valida.

## Decisiones clave

- JWT stateless para escalabilidad horizontal.
- RBAC desacoplado del token para permitir cambios de permisos sin relogin obligatorio (fuente de verdad en BD).
- Auditoria en acciones privilegiadas mutantes para trazabilidad.

## Deuda tecnica controlada

- Verificacion avanzada de usuario (identidad/documento y trust score) queda para siguiente incremento.
