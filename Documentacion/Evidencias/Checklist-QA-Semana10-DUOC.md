# Checklist QA Semana 10 - DUOC (Checkeable)

Fecha: 2026-05-15

## A. Seguridad y autorizacion

- [x] JWT invalido retorna `401` en endpoint protegido.
- [x] Usuario sin permiso retorna `403`.
- [x] Usuario con permiso correcto retorna `200`.
- [x] Handler de errores de seguridad retorna estructura API consistente.

## B. Swagger/OpenAPI

- [x] `/v3/api-docs` accesible sin autenticacion.
- [x] `/swagger-ui/index.html` accesible sin autenticacion.
- [x] Metadata OpenAPI contiene `title` y `version` esperados.

## C. UI Control de Accesos

- [x] Visualizacion de usuarios y roles efectivos.
- [x] Selector de perfil predefinido funcional.
- [x] Switch de usuario verificado funcional.
- [x] Bloque avanzado colapsable para ajustes finos.
- [x] Guardado de roles por usuario con feedback.

## D. Documentacion

- [x] UML casos de uso actualizado con roles ampliados.
- [x] UML clases de seguridad RBAC/JWT.
- [x] MER integrado actualizado.
- [x] Arquitectura tecnica actualizada.
- [x] Plan de pruebas y evidencias QA registradas.
- [x] Carta Gantt actualizada semana 10.
- [x] Evidencia de inyeccion de usuarios demo AIFBN (roles admin) documentada.
