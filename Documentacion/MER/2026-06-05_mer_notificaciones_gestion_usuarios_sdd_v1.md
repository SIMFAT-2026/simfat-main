# MER Especifico — Notificaciones In-App y Gestion de Usuarios

Fecha: 2026-06-05
Sprint: CU09 / CU15 brecha
Estado: vigente

---

## 1. Entidades nuevas

### notifications (PostgreSQL — V6)

```
notifications
  id          VARCHAR(36) PK  (UUID)
  user_id     VARCHAR(36) NOT NULL  -- FK logica a app_users
  type        VARCHAR(60)  NOT NULL DEFAULT 'RISK_ALERT'
  title       VARCHAR(200) NOT NULL
  message     VARCHAR(500)
  region_id   VARCHAR(60)
  comuna_id   VARCHAR(60)
  alert_level VARCHAR(20)
  is_read     BOOLEAN NOT NULL DEFAULT FALSE
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

Indices: `idx_notifications_user_unread (user_id, is_read)`, `idx_notifications_created_at (created_at DESC)`.

---

## 2. Entidades modificadas (sin cambio de schema)

Las siguientes entidades no cambian su schema en BD pero si su comportamiento:

### VerificationEvent (PostgreSQL — sin cambio de schema)

Se incorpora la lectura de `findPendingIdentityResets()` — query nativa que retorna eventos `IDENTITY_RESET` sin eventos posteriores del mismo usuario.

Nuevo valor de `eventType` generado: `"ADMIN_STATUS_CHANGE"` (cuando admin modifica el estado).

### AppUser (PostgreSQL — sin cambio de schema)

Se agrega `findByComunaCode(String comunaCode)` al repositorio para identificar usuarios de la comuna afectada en el trigger de notificaciones.

---

## 3. Relaciones

```
app_users (1) ─────────────────── (N) notifications
  id                                    user_id (FK logica)

app_users (1) ─────────────────── (N) verification_events
  id                                    user_id FK

ComunaRiskSnapshot ──trigger──► notifications
  (MongoDB, no FK real — relacion logica via comunaId/regionId)

alert_rules (MongoDB) ──condiciona──► notifications
  (trigger solo si existe AlertRule activa para regionId)
```

---

## 4. Diagrama ASCII actualizado — solo tablas afectadas en este sprint

```
app_users
  id PK · email · full_name · comunaCode · ...
  │
  ├──── verification_events (1:N)
  │     id PK · user_id FK · event_type · old_status · new_status
  │     reviewed_by FK · notes · created_at
  │     NEW eventType: "ADMIN_STATUS_CHANGE"
  │     QUERY NEW: findPendingIdentityResets()
  │
  └──── notifications (1:N) [NUEVA — V6]
        id PK · user_id FK · type · title · message
        region_id · comuna_id · alert_level · is_read · created_at
```

---

## 5. Notas de integridad

- `notifications.user_id` es FK logica (sin constraint de BD) para evitar cascade delete de notificaciones historicas si un usuario se desactiva.
- `notifications.region_id` y `notifications.comuna_id` son referencias informativas a entidades MongoDB — no se puede establecer FK real entre PostgreSQL y MongoDB.
- El trigger de notificaciones opera en la capa de servicio, no en triggers de BD, para mantener la logica en Java donde esta el acceso a ambas bases de datos.
