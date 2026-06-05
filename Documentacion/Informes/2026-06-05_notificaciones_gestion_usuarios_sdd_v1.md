# Notificaciones In-App y Gestion de Usuarios — SDD v1

Fecha: 2026-06-05
Cambio SDD: `notificaciones-gestion-usuarios-v1`
Modulo propietario: Alertas / Administracion
Estado: **implementado**

---

## 1. Objetivo

Cerrar los casos de uso CU09 (Recibir notificaciones in-app de riesgo territorial) y la brecha restante de CU15 (Gestion de usuarios: historial de verificacion + cambio manual de estado + usuarios pendientes de revision de identidad).

Usuarios afectados:
- CU09: cualquier usuario autenticado cuya `comunaCode` coincida con una comuna que escale de nivel de alerta.
- CU15 brecha: administradores con `ROLE_ADMIN` o `ROLE_SUPER_ADMIN`.

---

## 2. Alcance MVP

### Entra en este sprint

| Area | Alcance | Estado |
|---|---|---|
| Migracion BD V6 | Tabla `notifications` en PostgreSQL | implementado |
| NotificationService | Trigger post-recomputo comunal: escala de nivel + AlertRule activa + usuario en comuna | implementado |
| GET /api/notifications/unread | Top-10 no leidas del usuario autenticado + conteo total | implementado |
| PATCH /api/notifications/{id}/read | Marcar como leida (solo el dueno) | implementado |
| NotificationBell (frontend) | Badge con conteo, dropdown con ultimas 10, polling 30s | implementado |
| GET /api/admin/access/users/{id}/verification-events | Historial de eventos para un usuario | implementado |
| PUT /api/admin/access/users/{id}/verification | Admin cambia estado de verificacion con notas obligatorias | implementado |
| GET /api/admin/access/users/pending-review | Usuarios cuyo ultimo VerificationEvent es IDENTITY_RESET sin evento posterior | implementado |
| AccessControlPage — seccion pendientes | Tabla de pendientes, historial expandible, formulario de restauracion | implementado |

### Queda diferido

| Item | Motivo |
|---|---|
| Email y push notifications | Fuera del alcance MVP — solo in-app por ahora |
| Notificaciones para ROLE_ADMIN (global) | D4 postergado — se implementara cuando se defina politica de suscripcion |
| Marcar-todas-como-leidas | Optimizacion UX futura |

---

## 3. Decisiones de diseno cerradas

### 3.1 Persistencia de notificaciones: PostgreSQL

Las notificaciones estan atadas a un `userId` que vive en PostgreSQL (`app_users`). Usar MongoDB habria requerido joins logicos entre bases; la tabla `notifications` en Postgres es la opcion natural.

### 3.2 Logica del trigger (CU09)

El trigger se ejecuta en `ComunaRiskServiceImpl.recomputeByComuna()` despues de guardar el snapshot. Las condiciones que deben cumplirse **todas** para crear notificaciones:

1. `alertLevel` resultante es `ALTO` o `CRITICO`.
2. El nivel es estrictamente mayor al nivel del snapshot anterior de la misma comuna (deduplicacion, evita spam si el nivel no escala).
3. Existe al menos una `AlertRule` con `activa=true` cuyo `regionId` coincide con el `regionId` de la comuna.
4. Existen usuarios con `AppUser.comunaCode == comunaId`.

Se crea un registro de `Notification` por cada usuario elegible.

**Ordenamiento de niveles para deduplicacion:**
`NORMAL=0 < PREVENTIVO=1 < ALTO=2 < CRITICO=3`

### 3.3 Notificaciones por usuario de la comuna afectada (D4)

Las notificaciones se generan para usuarios cuyo `AppUser.comunaCode` coincide con el `comunaId` del snapshot. Esto es coherente con el modelo de perfil existente y no requiere tablas adicionales de suscripcion.

### 3.4 AlertRule a nivel de region, no de comuna

`AlertRule` tiene `regionId` (no `comunaId`). La condicion del trigger verifica si existe una regla activa para la **region** que contiene la comuna. Esto es correcto dado el modelo actual — una regla cubre toda la region.

### 3.5 Deduplicacion via snapshot anterior (D3)

Se consulta `snapshotRepository.findTopByComunaIdOrderByComputedAtDesc(comunaId)` **antes** de guardar el nuevo snapshot para capturar el nivel previo. Si no hay snapshot previo se asume `"NORMAL"`.

### 3.6 Estado de verificacion: admin puede asignar cualquier valor del enum

El admin puede asignar: `EMAIL_VERIFIED`, `PHONE_VERIFIED`, `IDENTITY_VERIFIED`, `FULLY_VERIFIED`, `SUSPENDED`. El campo `notes` es obligatorio (validado con `@NotBlank`). Cada cambio genera un `VerificationEvent(eventType="ADMIN_STATUS_CHANGE")`.

### 3.7 Criterio de "pendiente de revision" (D5)

Un usuario esta pendiente si su ultimo `VerificationEvent` tiene `eventType='IDENTITY_RESET'` y no existe ningun evento posterior (de cualquier tipo) para ese usuario. Implementado con query nativa en `VerificationEventRepository.findPendingIdentityResets()`. Esto significa que cualquier cambio de identidad (auto-degradacion del sprint CU12/13/14) mantiene al usuario pendiente hasta que un admin registre un nuevo evento.

---

## 4. Modelo de datos

### 4.1 notifications (PostgreSQL — nueva en V6)

| Columna | Tipo | Constraint | Notas |
|---|---|---|---|
| id | VARCHAR(36) | PK | UUID |
| user_id | VARCHAR(36) | NOT NULL | FK logica a app_users |
| type | VARCHAR(60) | NOT NULL DEFAULT 'RISK_ALERT' | Tipo de notificacion |
| title | VARCHAR(200) | NOT NULL | |
| message | VARCHAR(500) | NULL | Descripcion corta |
| region_id | VARCHAR(60) | NULL | Para link a region en frontend |
| comuna_id | VARCHAR(60) | NULL | ID GADM de la comuna |
| alert_level | VARCHAR(20) | NULL | ALTO / CRITICO |
| is_read | BOOLEAN | NOT NULL DEFAULT FALSE | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

Indices: `(user_id, is_read)`, `(created_at DESC)`.

---

## 5. API expuesta

### CU09

| Metodo | Ruta | Auth | Descripcion |
|---|---|---|---|
| GET | /api/notifications/unread | JWT | Retorna top-10 no leidas + conteo total |
| PATCH | /api/notifications/{id}/read | JWT | Marca una notificacion como leida |

### CU15 (brecha)

| Metodo | Ruta | Auth | Descripcion |
|---|---|---|---|
| GET | /api/admin/access/users/{id}/verification-events | ADMIN | Historial de VerificationEvent para un usuario |
| PUT | /api/admin/access/users/{id}/verification | ADMIN | Cambiar estado de verificacion con notas |
| GET | /api/admin/access/users/pending-review | ADMIN | Usuarios con IDENTITY_RESET pendiente de revision |

---

## 6. Componentes frontend

### NotificationBell

- Ubicacion: `Navbar.jsx` (entre links de navegacion y nombre de usuario)
- Polling cada 30s a `GET /api/notifications/unread`
- Badge numerico visible si `unreadCount > 0`
- Dropdown con lista de notificaciones; clic en item → marca como leida + navega a `/alerts?regionId=...`

### AccessControlPage — seccion "Verificaciones pendientes"

- Aparece antes del catalogo de permisos
- Lista de cards: nombre, email, estado actual, fecha del IDENTITY_RESET
- `<details>` expandible con tabla de historial de `VerificationEvent`
- Formulario inline: select de nuevo estado + textarea de notas (ambos obligatorios)
- Al guardar: llama `PUT .../verification`, elimina al usuario de la lista de pendientes en estado local

---

## 7. Archivos creados o modificados

### Backend

| Archivo | Tipo |
|---|---|
| `db/migration/V6__notifications.sql` | Nuevo |
| `model/Notification.java` | Nuevo |
| `repository/NotificationRepository.java` | Nuevo |
| `dto/NotificationDTO.java` | Nuevo |
| `dto/UnreadNotificationsDTO.java` | Nuevo |
| `service/NotificationService.java` | Nuevo |
| `service/impl/NotificationServiceImpl.java` | Nuevo |
| `controller/NotificationController.java` | Nuevo |
| `repository/AppUserRepository.java` | Modificado — `findByComunaCode` |
| `service/impl/ComunaRiskServiceImpl.java` | Modificado — inyeccion NotificationService + trigger |
| `dto/admin/VerificationEventDTO.java` | Nuevo |
| `dto/admin/UpdateVerificationStatusRequestDTO.java` | Nuevo |
| `dto/admin/PendingReviewUserDTO.java` | Nuevo |
| `repository/VerificationEventRepository.java` | Modificado — `findPendingIdentityResets` |
| `service/AccessAdminService.java` | Modificado — 3 metodos nuevos |
| `service/impl/AccessAdminServiceImpl.java` | Modificado — implementacion + VerificationEventRepository |
| `controller/AccessAdminController.java` | Modificado — 3 endpoints nuevos |

### Frontend

| Archivo | Tipo |
|---|---|
| `api/endpoints.js` | Modificado — 3 endpoints nuevos |
| `services/notificationsService.js` | Nuevo |
| `services/index.js` | Modificado — export notificationsService |
| `services/accessAdminService.js` | Modificado — 3 funciones nuevas |
| `components/layout/NotificationBell.jsx` | Nuevo |
| `components/layout/Navbar.jsx` | Modificado — integra NotificationBell |
| `pages/AccessControlPage.jsx` | Modificado — seccion pending-review + funciones de verificacion |
