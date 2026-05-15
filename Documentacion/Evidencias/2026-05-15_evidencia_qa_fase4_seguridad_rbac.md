# Evidencia QA - Fase 4 Seguridad RBAC

- Fecha: 2026-05-15
- Rama: `sprint/fase0-rbac-permisos-jwt`
- Objetivo: cierre de auditoria y pruebas de autorizacion

## 1. Cambios implementados

1. Auditoria de acciones privilegiadas
- Se agrega `PrivilegedActionAuditFilter` para registrar operaciones mutantes (`POST/PUT/PATCH/DELETE`) ejecutadas con authorities privilegiadas.
- Se agrega `SecurityAuditService` para logging estructurado del evento (`userId`, metodo, path, status, authorities).

2. Robustez de errores de autorizacion
- Se agrega manejo explicito de `AuthorizationDeniedException` en `GlobalExceptionHandler` para responder `403` consistente.

3. Pruebas de seguridad RBAC
- Nuevo test de integracion: `SecurityAuthorizationIntegrationTest`.
- Casos validados:
  - sin autenticacion -> `403` en endpoint protegido;
  - autenticado sin permiso -> `403`;
  - autenticado con permiso -> `200`;
  - bearer token invalido -> `401`.

## 2. Ejecucion de pruebas

Comando ejecutado:
- `mvn -Dtest=SecurityAuthorizationIntegrationTest test`

Resultado:
- `BUILD SUCCESS`
- Tests run: 4
- Failures: 0
- Errors: 0

## 3. Hallazgos y correcciones

Hallazgo detectado durante la fase:
- `AuthorizationDeniedException` estaba cayendo en handler generico y devolvia `500`.

Correccion aplicada:
- Nuevo handler dedicado en `GlobalExceptionHandler` con respuesta `403` y mensaje `Acceso denegado`.

## 4. Estado de cierre

- Fase 4 de seguridad: completada en alcance actual.
- Sistema con trazabilidad base de acciones privilegiadas y validacion automatizada de autorizacion.
