# Plan de Pruebas — Notificaciones In-App y Gestion de Usuarios

Fecha: 2026-06-05
Sprint: CU09 / CU15 brecha
Estado: definido

---

## 1. Objetivo

Verificar que el sistema de notificaciones in-app genera correctamente alertas al escalar el nivel de riesgo comunal, que los endpoints de lectura y marcado funcionan correctamente, y que los nuevos endpoints de administracion de verificacion permiten al admin gestionar el estado de usuarios con identidad pendiente de revision.

---

## 2. Alcance

| Modulo | Cubre |
|---|---|
| Backend Spring Boot | NotificationController, NotificationServiceImpl, trigger en ComunaRiskServiceImpl |
| Backend Spring Boot | AccessAdminController (3 endpoints nuevos), AccessAdminServiceImpl |
| Frontend React/Vite | NotificationBell, polling, dropdown, marcado como leido |
| Frontend React/Vite | AccessControlPage seccion pendientes, historial, formulario restauracion |
| BD PostgreSQL | Migracion V6 (notifications), indices |

---

## 3. Tipos de prueba

| Tipo | Descripcion |
|---|---|
| Prueba funcional | Verificar comportamiento esperado de cada endpoint con datos validos |
| Prueba de regla de negocio | Deduplicacion de notificaciones, condicion AlertRule, condicion escalada |
| Prueba de seguridad | JWT requerido; usuario no puede leer notificaciones de otro |
| Prueba de UI | Badge aparece, dropdown muestra notificaciones, clic navega a /alerts |
| Prueba admin | Pendientes cargados, historial expandido, restauracion guarda evento |

---

## 4. Casos de prueba

### CP-01: Trigger genera notificacion al escalar a ALTO

| Atributo | Detalle |
|---|---|
| ID | CP-01 |
| Nombre | ComunaRiskServiceImpl — trigger escala NORMAL→ALTO |
| Precondicion | Existe AlertRule activa para regionId de la comuna; existe usuario con comunaCode = comunaId; snapshot previo tiene alertLevel NORMAL |
| Pasos | 1. Ejecutar recomputeByComuna() con datos que resulten en alertLevel ALTO |
| Resultado esperado | 1 notificacion creada en tabla notifications para el usuario elegible |

### CP-02: Trigger NO genera notificacion si nivel no escala

| Atributo | Detalle |
|---|---|
| ID | CP-02 |
| Nombre | Deduplicacion — ALTO→ALTO no genera notificacion |
| Precondicion | Snapshot previo = ALTO; nuevo snapshot = ALTO |
| Pasos | 1. Ejecutar recomputeByComuna() |
| Resultado esperado | Sin nuevas notificaciones creadas |

### CP-03: Trigger NO genera notificacion sin AlertRule activa

| Atributo | Detalle |
|---|---|
| ID | CP-03 |
| Nombre | Sin AlertRule — no notificar |
| Precondicion | No existe AlertRule activa para la region; alertLevel = CRITICO |
| Pasos | 1. Ejecutar recomputeByComuna() |
| Resultado esperado | Sin notificaciones creadas |

### CP-04: GET /api/notifications/unread

| Atributo | Detalle |
|---|---|
| ID | CP-04 |
| Nombre | Obtener notificaciones no leidas del usuario autenticado |
| Precondicion | Existen 3 notificaciones no leidas para el usuario; JWT valido |
| Pasos | 1. GET /api/notifications/unread con Bearer token |
| Resultado esperado | 200 OK, `data.notifications` con hasta 10 items, `data.unreadCount >= 3` |

### CP-05: PATCH /api/notifications/{id}/read

| Atributo | Detalle |
|---|---|
| ID | CP-05 |
| Nombre | Marcar notificacion como leida |
| Precondicion | Notificacion no leida del usuario autenticado |
| Pasos | 1. PATCH /api/notifications/{id}/read con Bearer token |
| Resultado esperado | 200 OK, notificacion devuelta con `read: true` |

### CP-06: PATCH rechaza acceso a notificacion de otro usuario

| Atributo | Detalle |
|---|---|
| ID | CP-06 |
| Nombre | Seguridad — no leer notificacion ajena |
| Precondicion | Notificacion pertenece a usuario B; usuario A autenticado |
| Pasos | 1. PATCH /api/notifications/{idDeB}/read con token de A |
| Resultado esperado | 401 Unauthorized |

### CP-07: GET /api/admin/access/users/{id}/verification-events

| Atributo | Detalle |
|---|---|
| ID | CP-07 |
| Nombre | Obtener historial de verificacion de un usuario |
| Precondicion | Admin autenticado; usuario tiene 2 VerificationEvents |
| Pasos | 1. GET /api/admin/access/users/{id}/verification-events |
| Resultado esperado | 200 OK con lista de 2 eventos ordenados por fecha desc |

### CP-08: PUT /api/admin/access/users/{id}/verification — restaurar estado

| Atributo | Detalle |
|---|---|
| ID | CP-08 |
| Nombre | Admin restaura IDENTITY_VERIFIED con notas |
| Precondicion | Usuario tiene status EMAIL_VERIFIED post-IDENTITY_RESET; admin autenticado |
| Pasos | 1. PUT body `{ "newStatus": "IDENTITY_VERIFIED", "notes": "Revisado, nombre correcto" }` |
| Resultado esperado | 200 OK, usuario devuelto con `verificationStatus: "IDENTITY_VERIFIED"`; nuevo VerificationEvent type ADMIN_STATUS_CHANGE creado |

### CP-09: PUT rechaza notas vacias

| Atributo | Detalle |
|---|---|
| ID | CP-09 |
| Nombre | Validacion — notas obligatorias |
| Precondicion | Admin autenticado |
| Pasos | 1. PUT body `{ "newStatus": "IDENTITY_VERIFIED", "notes": "" }` |
| Resultado esperado | 400 Bad Request con mensaje de validacion |

### CP-10: GET /api/admin/access/users/pending-review

| Atributo | Detalle |
|---|---|
| ID | CP-10 |
| Nombre | Listar usuarios pendientes de revision |
| Precondicion | Existen 2 usuarios con ultimo evento IDENTITY_RESET; admin autenticado |
| Pasos | 1. GET /api/admin/access/users/pending-review |
| Resultado esperado | 200 OK, lista con 2 PendingReviewUserDTO, campo `lastEvent.eventType = "IDENTITY_RESET"` |

### CP-11: Badge de notificaciones en Navbar

| Atributo | Detalle |
|---|---|
| ID | CP-11 |
| Nombre | UI — badge aparece con conteo correcto |
| Precondicion | Usuario tiene 3 notificaciones no leidas |
| Pasos | 1. Abrir la aplicacion con sesion activa |
| Resultado esperado | Badge visible con numero 3 junto al icono de campana |

### CP-12: Dropdown se abre y muestra notificaciones

| Atributo | Detalle |
|---|---|
| ID | CP-12 |
| Nombre | UI — dropdown lista notificaciones |
| Precondicion | Usuario tiene notificaciones no leidas |
| Pasos | 1. Hacer clic en el icono de campana |
| Resultado esperado | Dropdown visible con lista de notificaciones (alertLevel, titulo, hora) |

### CP-13: Clic en notificacion navega a /alerts

| Atributo | Detalle |
|---|---|
| ID | CP-13 |
| Nombre | UI — navegacion desde notificacion |
| Precondicion | Notificacion tiene regionId |
| Pasos | 1. Clic en una notificacion del dropdown |
| Resultado esperado | Notificacion marcada como leida; navegacion a `/alerts?regionId=...` |

### CP-14: Seccion pendientes carga y muestra usuarios con IDENTITY_RESET

| Atributo | Detalle |
|---|---|
| ID | CP-14 |
| Nombre | UI — seccion verificaciones pendientes |
| Precondicion | Existen usuarios con IDENTITY_RESET sin revision |
| Pasos | 1. Abrir AccessControlPage como ROLE_ADMIN |
| Resultado esperado | Seccion "Verificaciones pendientes" visible con cards de usuarios |

### CP-15: Admin restaura estado desde UI y usuario desaparece de pendientes

| Atributo | Detalle |
|---|---|
| ID | CP-15 |
| Nombre | UI — flujo completo de restauracion |
| Precondicion | Usuario pendiente visible; admin autenticado |
| Pasos | 1. Seleccionar estado IDENTITY_VERIFIED; 2. Escribir notas; 3. Clic "Guardar estado" |
| Resultado esperado | Feedback de exito; usuario eliminado de la lista de pendientes |
