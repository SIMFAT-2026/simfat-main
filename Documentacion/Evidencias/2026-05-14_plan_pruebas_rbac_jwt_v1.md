# Plan de Pruebas - Capa RBAC + JWT (Sprint Seguridad)

- Fecha: 2026-05-14
- Version: 1.0
- Alcance: preparacion QA para Fase 1-4 (Fase 0 documental)

## 1. Objetivo

Validar que la capa RBAC cumpla control de acceso por minimo privilegio, sin regresiones en autenticacion JWT ni degradacion operativa del backend.

## 2. Tipos de prueba

1. Unitarias
- Resolucion de authorities por rol.
- Evaluacion de permisos por endpoint.
- Conversion de estados de verificacion.

2. Integracion backend
- Login/refresh/logout con usuarios de distinto perfil.
- Respuestas esperadas 200/401/403 segun permiso.
- Revocacion/sesion tras cambio de rol.

3. Regresion funcional
- Endpoints publicos mantienen acceso esperado.
- Endpoints de escritura administrativa restringidos.

4. Seguridad
- Intentos de acceso con token invalido/expirado.
- Escalada horizontal/vertical de privilegios.
- Confirmacion de no bypass en endpoints internos.

## 3. Matriz de casos minimos (base)

- Caso RBAC-01: `ROLE_PUBLIC` no puede crear reglas (`POST /api/rules`) -> 401/403.
- Caso RBAC-02: `ROLE_COMMUNITY_USER` puede leer comunidad -> 200.
- Caso RBAC-03: `ROLE_VERIFIED_USER` puede crear reporte ciudadano -> 200.
- Caso RBAC-04: `ROLE_MODERATOR` puede cambiar estado de reporte -> 200.
- Caso RBAC-05: `ROLE_ADMIN` puede gestionar recursos comunitarios -> 200.
- Caso RBAC-06: `ROLE_SUPER_ADMIN` acceso total administrativo -> 200.
- Caso RBAC-07: token expirado en endpoint protegido -> 401.
- Caso RBAC-08: usuario activo sin permiso explicito -> 403.

## 4. Criterios de aceptacion

- 100% de casos criticos de autorizacion en estado aprobado.
- 0 bypass de privilegios en endpoints de escritura.
- 0 regresiones en flujo auth principal.

## 5. Evidencias requeridas

- Logs de ejecucion de pruebas.
- Capturas de respuestas API por rol.
- Checklist QA actualizado.
- Informe de defectos y cierre.
