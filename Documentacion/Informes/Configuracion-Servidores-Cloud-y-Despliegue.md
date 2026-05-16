# Configuracion de Servidores Cloud y Despliegue - Semana 10

Fecha: 2026-05-15

## Topologia tecnica actual

- Frontend: React + Vite (local dev en `:4173`).
- Backend: Spring Boot (local dev en `:8081`).
- Servicio analitico: OpenEO service (local dev en `:8000`).
- PostgreSQL: Supabase (datos relacionales y seguridad RBAC).
- MongoDB: Atlas (datos operativos/series/eventos).

## Variables y configuracion base

- Variables por entorno en backend (`.env.example`, `.env.remote`).
- JWT secret, expiracion y refresh token por properties.
- CORS y rutas permitidas para frontend local.
- Swagger habilitado en:
  - `/v3/api-docs`
  - `/swagger-ui/index.html`

## Checklist de despliegue tecnico

- [ ] Configurar variables sensibles en cloud (no hardcode en repo).
- [ ] Verificar conectividad a Supabase y Atlas.
- [ ] Ejecutar migraciones SQL (RBAC + verification).
- [ ] Levantar backend con perfil de entorno.
- [ ] Verificar endpoint health y OpenAPI.
- [ ] Publicar frontend apuntando a API target.

## Riesgos operacionales

- DNS intermitente hacia Atlas puede afectar arranque local.
- Drift de permisos entre seeds y datos productivos si no hay control de migraciones.
