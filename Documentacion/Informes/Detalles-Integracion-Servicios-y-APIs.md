# Detalles de Integracion Servicios y APIs - Semana 10

Fecha: 2026-05-15

## Integraciones internas

1. Frontend <-> Backend REST
- Axios client + JWT bearer.
- Endpoints protegidos por rol/permiso.

2. Backend <-> PostgreSQL (Supabase)
- RBAC (`roles`, `permissions`, `user_roles`, `role_permissions`).
- Verificacion (`user_verification`, `verification_events`).

3. Backend <-> MongoDB Atlas
- Eventos y observaciones ambientales.
- Reportes ciudadanos y comunidad.

## Integraciones externas

1. OpenEO/NASA
- Sincronizacion de indicadores y jobs.
- Exposicion en dashboard y mapas.

2. Supabase Storage
- Carga y acceso de imagenes para reportes.

## Contratos de seguridad

- `401` para token invalido/no autenticado.
- `403` para autenticado sin permiso suficiente.
- Permisos de negocio `PERM_*` como autorizacion fina.

## Evolucion prevista

- Trust score y reputacion por usuario.
- Flujo de verificacion documental con estados avanzados.
- Auditoria persistente de cambios de permisos.
