# Handover de Migracion a Nuevo PC - SIMFAT

Fecha: 2026-05-25
Rama: `mig-david`
Objetivo: asegurar continuidad operativa y funcional al cambiar de equipo, sin perdida de contexto de arquitectura, avances ni decisiones de iteracion.

## 1) Estado actual resumido

- Backend desplegado y operativo en Railway.
- Frontend desplegado y operativo en Vercel.
- Persistencia activa en MongoDB Atlas + PostgreSQL/Supabase.
- Seguridad RBAC + JWT implementada y validada (roles y panel de control de accesos).
- Swagger activo (`/v3/api-docs`, `/swagger-ui/index.html`).

Documentos base recomendados para retomar:
- `Documentacion/Informes/README_documentacion_backend.md`
- `Documentacion/UML/Arquitectura-Integrada-Sistema-Semana10.md`
- `Documentacion/MER/MER-Integrado-RBAC-Semana10.md`
- `Documentacion/Informes/Matriz-Casos-de-Uso-Semana10-Roles-Actualizados.md`
- `Documentacion/Evidencias/2026-05-19_evidencia_cumplimiento_casos_uso_semana10.md`

## 2) Estructura de ramas vigente en remoto

- `main` (estable)
- `develop/simfat-web`
- `develop/simfat-backend`
- `develop/openeo-service`
- `sprint/fase0-rbac-permisos-jwt`
- `mig-david` (handover + specs de iteracion)

Nota: no existe una rama unica llamada `develop`; el esquema actual separa por frente.

## 3) Pasos de recuperacion en nuevo PC

1. Clonar repositorio:
   - `git clone https://github.com/SIMFAT-2026/simfat-main.git`
2. Entrar al proyecto y traer todo:
   - `git fetch --all --prune`
3. Listar ramas remotas/locales:
   - `git branch -a`
4. Cambiar a rama de trabajo inicial recomendada:
   - `git checkout mig-david`
5. Si se requiere trabajar por frente, crear tracking local:
   - `git checkout -b develop/simfat-web origin/develop/simfat-web`
   - `git checkout -b develop/simfat-backend origin/develop/simfat-backend`
   - `git checkout -b develop/openeo-service origin/develop/openeo-service`

## 4) Variables y accesos a validar al migrar

- GitHub auth (token/credential manager).
- Variables de entorno backend (`.env`, secretos Railway, URLs Atlas/Supabase).
- Variables frontend (`VITE_*`, endpoint API, config auth).
- Acceso a Railway, Vercel, Atlas, Supabase.

## 5) Check operativo post-migracion

1. `git status` sin cambios inesperados.
2. Backend levanta local y responde health/auth.
3. Frontend levanta local y autentica contra backend esperado.
4. Swagger accesible.
5. Endpoint protegido valida RBAC.
6. Documentacion de evidencia y matriz CU visibles y sincronizadas.

## 6) Trabajo en curso (continuidad iteracion)

La siguiente iteracion se esta definiendo en formato spec-driven y ya tiene borrador funcional/técnico en:
- `Documentacion/Informes/2026-05-25_spec_iteracion_riesgo_geo_y_comunidad_v1.md`

Incluye:
- Frente 1: metodologia de mapa de riesgo con NDVI/NDMI + deforestacion + reportes + viento + indice externo de peligro.
- Frente 2: evolucion de modulo comunitario con UX de bajo costo + chat MVP.

## 7) Riesgos de migracion y mitigacion

- Riesgo: perder configuracion local de secretos.
  - Mitigacion: checklist de variables y acceso a paneles cloud antes de mover.
- Riesgo: confundir estrategia de ramas por no existir `develop` unica.
  - Mitigacion: usar ramas `develop/*` por frente.
- Riesgo: retomar implementacion sin revisar decisiones recientes.
  - Mitigacion: comenzar por lectura de este handover + spec de iteracion v1.
