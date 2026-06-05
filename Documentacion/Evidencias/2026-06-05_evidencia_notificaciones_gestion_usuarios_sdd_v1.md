# Evidencia de Implementacion — Notificaciones In-App y Gestion de Usuarios

Fecha: 2026-06-05
Sprint: CU09 / CU15 brecha
Estado: implementado — pendiente ejecucion de checklist en produccion

---

## 1. Archivos creados o modificados

### Backend

| Archivo | Hash / commit | Descripcion |
|---|---|---|
| `db/migration/V6__notifications.sql` | creado | Tabla notifications + indices en PostgreSQL |
| `model/Notification.java` | creado | Entidad JPA para notificaciones in-app |
| `repository/NotificationRepository.java` | creado | findByUserIdAndReadFalse + countByUserIdAndReadFalse |
| `dto/NotificationDTO.java` | creado | Record de transferencia de datos |
| `dto/UnreadNotificationsDTO.java` | creado | Wrapper con lista + conteo total |
| `service/NotificationService.java` | creado | Interfaz del servicio |
| `service/impl/NotificationServiceImpl.java` | creado | Implementacion con trigger y logica de deduplicacion |
| `controller/NotificationController.java` | creado | GET /unread + PATCH /{id}/read |
| `repository/AppUserRepository.java` | modificado | +findByComunaCode |
| `service/impl/ComunaRiskServiceImpl.java` | modificado | +NotificationService inyectado + trigger post-save |
| `dto/admin/VerificationEventDTO.java` | creado | Record para historial de eventos |
| `dto/admin/UpdateVerificationStatusRequestDTO.java` | creado | Body del PUT verification con validaciones |
| `dto/admin/PendingReviewUserDTO.java` | creado | Record para lista de pendientes |
| `repository/VerificationEventRepository.java` | modificado | +findPendingIdentityResets (query nativa) |
| `service/AccessAdminService.java` | modificado | +3 metodos nuevos en interfaz |
| `service/impl/AccessAdminServiceImpl.java` | modificado | +VerificationEventRepository + 3 implementaciones |
| `controller/AccessAdminController.java` | modificado | +3 endpoints nuevos |

### Frontend

| Archivo | Descripcion |
|---|---|
| `api/endpoints.js` | +notificationsUnread, +notifications, +adminAccessPendingReview |
| `services/notificationsService.js` | creado — getUnreadNotifications, markNotificationRead |
| `services/index.js` | +export notificationsService |
| `services/accessAdminService.js` | +getVerificationEvents, +updateVerificationStatus, +getPendingReview |
| `components/layout/NotificationBell.jsx` | creado — badge + dropdown + polling |
| `components/layout/Navbar.jsx` | +NotificationBell entre nav y user info |
| `pages/AccessControlPage.jsx` | +seccion pending-review + historial + formulario restauracion |

---

## 2. Decisiones tecnicas tomadas durante la implementacion

### No se creo endpoint dedicado de suscripcion

La asignacion de notificaciones es automatica via `comunaCode` en `AppUser`. No se requirio crear una tabla de suscripciones ni un flujo de opt-in.

### FK logica en notifications

`notifications.user_id` no tiene FK con CASCADE en BD para preservar el historial si un usuario se desactiva. La integridad se garantiza en la capa de servicio.

### Polling simple en lugar de WebSocket

El polling cada 30s es suficiente para el MVP. WebSocket/SSE quedaria para una fase posterior si la experiencia de tiempo real fuera requerida por AIFBN.

### Ruta pending-review vs ruta variable en Spring

`GET /api/admin/access/users/pending-review` tiene el segmento "pending-review" que podria colisionar con el `@PathVariable` de `/users/{userId}`. Spring resuelve esto correctamente porque los metodos de mapeo especificos tienen prioridad sobre los parametrizados — confirmado por el comportamiento estandar de Spring MVC.

---

## 3. Criterios de aceptacion cumplidos

| CU | Criterio | Estado |
|---|---|---|
| CU09 | El sistema genera notificacion cuando el score comunal escala a ALTO o CRITICO | implementado |
| CU09 | La notificacion se genera solo si hay AlertRule activa para la region | implementado |
| CU09 | No se genera notificacion si el nivel no escala respecto al anterior | implementado |
| CU09 | El usuario ve el badge de no leidas en la Navbar | implementado |
| CU09 | El usuario puede ver las ultimas 10 notificaciones en el dropdown | implementado |
| CU09 | El usuario puede marcar una notificacion como leida | implementado |
| CU09 | Clic en notificacion navega a la region afectada | implementado |
| CU15 | El admin puede ver el historial de VerificationEvents de cualquier usuario | implementado |
| CU15 | El admin puede cambiar el estado de verificacion con notas obligatorias | implementado |
| CU15 | El admin ve la lista de usuarios con IDENTITY_RESET pendiente de revision | implementado |
| CU15 | Al restaurar un estado, el usuario desaparece de la lista de pendientes | implementado |

---

## 4. Pendientes post-sprint

| Item | Prioridad | Motivo del diferimiento |
|---|---|---|
| Notificaciones globales para ROLE_ADMIN | Media | Requiere politica de suscripcion adicional |
| Email / push notifications | Baja | Fuera del alcance MVP — integracion con proveedor externo |
| Marcar todas como leidas | Baja | Optimizacion UX futura |
| WebSocket para notificaciones en tiempo real | Baja | Polling 30s es suficiente para el MVP |
