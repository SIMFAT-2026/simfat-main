# Checklist QA — Notificaciones In-App y Gestion de Usuarios

Fecha: 2026-06-05
Sprint: CU09 / CU15 brecha
Estado: pendiente de ejecucion en entorno de produccion (Railway + Vercel)

---

## Backend — CU09 Notificaciones

| # | Verificacion | Estado |
|---|---|---|
| 1 | Migracion V6 ejecutada sin errores en Supabase (tabla notifications creada con indices) | [ ] |
| 2 | `GET /api/notifications/unread` devuelve 200 con envelope `{ notifications: [], unreadCount: 0 }` para usuario sin notificaciones | [ ] |
| 3 | `GET /api/notifications/unread` sin JWT devuelve 401 | [ ] |
| 4 | `PATCH /api/notifications/{id}/read` marca la notificacion y la devuelve con `read: true` | [ ] |
| 5 | `PATCH` con ID de notificacion de otro usuario devuelve 401 | [ ] |
| 6 | Trigger genera notificacion al escalar NORMAL→ALTO con AlertRule activa y usuario en la comuna | [ ] |
| 7 | Trigger no genera notificacion al mantenerse ALTO→ALTO (deduplicacion) | [ ] |
| 8 | Trigger no genera notificacion sin AlertRule activa para la region | [ ] |
| 9 | Trigger no genera notificacion en nivel PREVENTIVO o NORMAL | [ ] |
| 10 | `unreadCount` refleja el total real de no leidas (no solo las 10 del top) | [ ] |

---

## Backend — CU15 brecha

| # | Verificacion | Estado |
|---|---|---|
| 11 | `GET /api/admin/access/users/{id}/verification-events` devuelve lista ordenada desc | [ ] |
| 12 | El endpoint anterior devuelve 404 si el usuario no existe | [ ] |
| 13 | `PUT /api/admin/access/users/{id}/verification` cambia el status y crea VerificationEvent(ADMIN_STATUS_CHANGE) | [ ] |
| 14 | `PUT` con notes vacias devuelve 400 Bad Request | [ ] |
| 15 | `PUT` con newStatus invalido devuelve 400 Bad Request | [ ] |
| 16 | `GET /api/admin/access/users/pending-review` devuelve solo usuarios cuyo ultimo evento es IDENTITY_RESET | [ ] |
| 17 | Usuario que recibio ADMIN_STATUS_CHANGE ya no aparece en pending-review | [ ] |
| 18 | Los 3 endpoints nuevos devuelven 403 para usuario sin rol ADMIN | [ ] |

---

## Frontend — CU09 NotificationBell

| # | Verificacion | Estado |
|---|---|---|
| 19 | Badge no aparece cuando unreadCount = 0 | [ ] |
| 20 | Badge muestra el numero correcto de no leidas | [ ] |
| 21 | Dropdown se abre y cierra al hacer clic en la campana | [ ] |
| 22 | Dropdown se cierra al hacer clic fuera de el | [ ] |
| 23 | Clic en notificacion: badge disminuye, notificacion desaparece del dropdown, navega a /alerts | [ ] |
| 24 | Si no hay notificaciones, dropdown muestra mensaje "Sin notificaciones pendientes" | [ ] |
| 25 | Polling cada 30s actualiza el conteo sin recargar la pagina | [ ] |
| 26 | Error de red en polling no interrumpe la sesion ni muestra error al usuario | [ ] |

---

## Frontend — CU15 AccessControlPage

| # | Verificacion | Estado |
|---|---|---|
| 27 | Seccion "Verificaciones pendientes" visible para ROLE_ADMIN | [ ] |
| 28 | Seccion no visible o vacia si no hay pendientes | [ ] |
| 29 | Card muestra: nombre, email, estado actual, fecha del IDENTITY_RESET | [ ] |
| 30 | `<details>` expandible carga historial de eventos al abrirse (solo una vez) | [ ] |
| 31 | Tabla de historial muestra tipo, estados, notas y fecha | [ ] |
| 32 | Select de nuevo estado tiene opciones validas | [ ] |
| 33 | Boton "Guardar estado" deshabilitado durante el guardado | [ ] |
| 34 | Feedback de exito al guardar; usuario desaparece de la lista | [ ] |
| 35 | Error al intentar guardar sin seleccionar estado muestra feedback de error | [ ] |
| 36 | Error al intentar guardar sin notas muestra feedback de error | [ ] |

---

## Regresion — funcionalidad existente

| # | Verificacion | Estado |
|---|---|---|
| 37 | Roles y acceso de chat (funcionalidad anterior de AccessControlPage) siguen funcionando | [ ] |
| 38 | ComunaRiskServiceImpl sigue calculando y guardando snapshots correctamente | [ ] |
| 39 | Navbar renderiza correctamente en todas las rutas (NavLinks activos, logout) | [ ] |
| 40 | AlertsPage no fue afectada por los cambios | [ ] |
