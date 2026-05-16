# Plan de Pruebas Semana 10 - DUOC

Fecha: 2026-05-15

## Objetivo

Validar estabilidad funcional, seguridad RBAC/JWT y disponibilidad de contratos API (incluyendo Swagger/OpenAPI).

## Alcance

- Backend API y seguridad.
- Frontend panel de accesos (flujo principal).
- Integraciones base de datos e infraestructura local.

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
