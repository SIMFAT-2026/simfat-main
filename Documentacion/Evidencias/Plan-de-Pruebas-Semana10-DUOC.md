# Plan de Pruebas Semana 10 - DUOC

Fecha: 2026-05-15

## Objetivo

Validar estabilidad funcional, seguridad RBAC/JWT y disponibilidad de contratos API (incluyendo Swagger/OpenAPI).

## Alcance

- Backend API y seguridad.
- Frontend panel de accesos (flujo principal).
- Integraciones base de datos e infraestructura local.

## Ambiente de pruebas (detalle operativo)

### Equipo de ejecucion

- Host de pruebas: notebook personal de desarrollo (Windows 11).
- Runtime principal:
  - Java 23
  - Maven 3.9.x
  - Node.js 22.x + npm 10.x
  - Python 3.13.x
- Navegador de validacion funcional: Chrome/Edge (canal estable).

### Ambientes utilizados

1. Local (integracion tecnica)
- Frontend: `http://localhost:4173`
- Backend: `http://localhost:8081`
- OpenEO service: `http://localhost:8000`
- DB remotas usadas en pruebas locales:
  - PostgreSQL Supabase (`POSTGRES_URI`)
  - MongoDB Atlas (`MONGODB_URI`)

2. Staging (demo estable)
- Backend: `https://simfat-backend-staging.up.railway.app`
- Swagger: `https://simfat-backend-staging.up.railway.app/swagger-ui/index.html`
- OpenAPI: `https://simfat-backend-staging.up.railway.app/v3/api-docs`

3. Produccion (validacion de despliegue)
- Backend: `https://simfat-backend-production.up.railway.app`
- OpenEO service: `https://openeo-service-production-production.up.railway.app`
- Frontend Vercel (dominio productivo del proyecto): validado en linea.

### Criterio de uso por riesgo

- Para demostraciones y pruebas de continuidad operativa se prioriza staging cuando produccion presenta inestabilidad transitoria (ej. reinicios/OOM).
- Produccion se considera valida al pasar smoke test de login, `auth/me`, regiones, comunidad y panel de accesos.

## Tipos de prueba

1. Operativas
- Arranque de servicios (frontend, backend, openeo-service).
- Conexion backend con fuentes de datos.

2. Verificacion
- Endpoints protegidos retornan `401/403` segun regla.
- Endpoints publicos (OpenAPI/Swagger) disponibles.
- RBAC: asignacion y persistencia de roles por usuario.

3. Validacion
- Flujo UI de panel de accesos:
  - elegir perfil predefinido
  - activar switch verificado
  - guardar cambios
  - visualizar roles efectivos

## Casos de prueba criticos

- CP-SEC-001: POST `/api/rules` sin auth -> `403`.
- CP-SEC-002: POST `/api/rules` con rol sin permiso -> `403`.
- CP-SEC-003: POST `/api/rules` con `PERM_ALERT_RULE_MANAGE` -> `200`.
- CP-SEC-004: token bearer invalido -> `401`.
- CP-SWG-001: GET `/v3/api-docs` -> `200`, metadata esperada.
- CP-SWG-002: GET `/swagger-ui/index.html` -> `200`.
- CP-UI-001: panel accesos muestra perfiles compactos y guardado por usuario.

## Criterios de aceptacion

- 100% casos criticos de seguridad y Swagger en estado PASS.
- Sin errores bloqueantes en flujo UI de control de accesos.
- Evidencia registrada con fecha y comando de ejecucion.
