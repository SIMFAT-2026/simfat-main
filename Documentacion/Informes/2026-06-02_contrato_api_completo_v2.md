# Contrato API Completo — SIMFAT Backend v4

Fecha: 2026-06-05
Base URL: `https://<railway-backend>/`
Autenticación: Bearer JWT en header `Authorization`
Cambios v3→v4: seccion nueva `/api/notifications` (CU09); endpoints nuevos en `/api/admin/access` (CU15 brecha)

---

## Convenciones

- Todas las respuestas exitosas siguen el envelope `{ "success": true, "data": {...}, "message": "..." }`
- Errores: `{ "success": false, "error": "...", "status": 4xx|5xx }`
- Fechas: ISO 8601 UTC (`2026-06-02T01:00:00Z`)
- Roles requeridos se indican con `@`; si es público no se indica

---

## 1. Autenticación — `/api/auth`

### POST /api/auth/register
Registrar un nuevo usuario.

**Body:**
```json
{
  "email": "usuario@ejemplo.cl",
  "fullName": "Juan Pérez",
  "password": "min8chars"
}
```
**Response 201:**
```json
{ "success": true, "message": "Usuario registrado", "data": { "userId": "..." } }
```

### POST /api/auth/login
Iniciar sesión. Devuelve par de tokens.

**Body:**
```json
{ "email": "usuario@ejemplo.cl", "password": "..." }
```
**Response 200:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 900
  }
}
```

### POST /api/auth/refresh
Refrescar el access token usando el refresh token.

**Body:** `{ "refreshToken": "eyJ..." }`
**Response 200:** igual que login

### GET /api/auth/me
@Autenticado — Obtener perfil del usuario actual.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "id": "...",
    "email": "...",
    "fullName": "...",
    "roles": ["ROLE_VERIFIED_USER"],
    "permissions": ["PERM_REPORT_CREATE"],
    "primaryRegionId": "biobio",
    "verificationStatus": "VERIFIED"
  }
}
```

### POST /api/auth/logout
@Autenticado — Revocar refresh token actual.

### POST /api/auth/forgot-password
**Body:** `{ "email": "..." }` — Envía email de reseteo.

### POST /api/auth/reset-password
**Body:** `{ "token": "...", "newPassword": "..." }`

---

## 2. Cuenta y Perfil — `/api/account`

Todos los endpoints requieren JWT valido (cualquier usuario autenticado).

### GET /api/account/me
Obtener el perfil propio del usuario autenticado.

**Response 200:**
```json
{
  "success": true,
  "message": "Perfil obtenido correctamente",
  "data": {
    "id": "uuid",
    "email": "usuario@example.com",
    "fullName": "Maria Paz Lopez",
    "phone": "+56912345678",
    "regionCode": "biobio",
    "comunaCode": "CHL.6.3.2_1",
    "organizationName": "Brigada Biobio Norte",
    "verificationStatus": "FULLY_VERIFIED",
    "roles": ["ROLE_COMMUNITY_USER", "ROLE_VERIFIED_USER"],
    "createdAt": "2026-01-15T10:00:00Z"
  }
}
```

`phone`, `regionCode`, `comunaCode`, `organizationName` son null si no han sido completados.

### PATCH /api/account/me
Actualizar datos del perfil propio. Todos los campos son opcionales — solo se actualizan los presentes en el body.

**Body:**
```json
{
  "fullName": "Maria Paz Lopez Soto",
  "phone": "+56912345678",
  "regionCode": "biobio",
  "comunaCode": "CHL.6.3.2_1"
}
```

**Response 200:** igual que GET /me con los datos actualizados.

**Efectos colaterales:**
- Si `fullName` cambia y el usuario tenia `verificationStatus` = `IDENTITY_VERIFIED` o `FULLY_VERIFIED`, el status se degrada a `EMAIL_VERIFIED` y se registra un `VerificationEvent(type=IDENTITY_RESET)`.
- Si `regionCode` cambia, se hace upsert de `user_community_profiles.primary_region_id` con el mismo valor, otorgando acceso automatico a la sala de chat regional.

**Validaciones:**

| Campo | Constraint | Error |
|---|---|---|
| fullName | @Size(min=1, max=120) | 400 "El nombre no puede estar en blanco ni exceder 120 caracteres" |
| phone | @Size(max=20) | 400 "El telefono no puede exceder 20 caracteres" |
| regionCode | @Size(max=20) | 400 "Codigo de region invalido" |
| comunaCode | @Size(max=20) | 400 "Codigo de comuna invalido" |

### POST /api/account/change-password
Cambiar la contrasena del usuario autenticado. Requiere la contrasena actual. Al completar, revoca todos los refresh tokens activos del usuario.

**Body:**
```json
{
  "currentPassword": "Contrasena$Actual1",
  "newPassword": "NuevaContrasena$2",
  "confirmPassword": "NuevaContrasena$2"
}
```

**Politica de contrasena:** `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{12,72}$`
(minimo 12 chars, maximo 72, al menos 1 minuscula, 1 mayuscula, 1 numero, 1 simbolo)

**Response 200:**
```json
{
  "success": true,
  "message": "Contrasena actualizada. Por seguridad se cerraron todas las sesiones activas.",
  "data": null
}
```

**Errores:**

| Caso | HTTP | Mensaje |
|---|---|---|
| currentPassword incorrecta | 400 | "La contrasena actual no es correcta" |
| newPassword == currentPassword | 400 | "La nueva contrasena debe ser distinta a la actual" |
| newPassword != confirmPassword | 400 | "La confirmacion de contrasena no coincide" |
| newPassword debil | 400 | "La contrasena debe tener 12-72 caracteres, mayuscula, minuscula, numero y simbolo" |

**Nota de sesiones:** tras cambio exitoso, todos los refresh tokens con `revoked_at IS NULL` del usuario quedan revocados. El frontend redirige a `/login` a los 2.5 segundos.

---

## 3. Regiones — `/api/regions`

### GET /api/regions
Listar todas las regiones monitoreadas.

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "id": "biobio",
      "nombre": "Región del Biobío",
      "codigo": "BIOBIO",
      "zona": "SUR",
      "hectareasBosqueReferencia": 1500000.0,
      "aoiBbox": [-73.6, -38.5, -71.0, -36.7]
    }
  ]
}
```

### GET /api/regions/{id}
Obtener región por ID.

### POST /api/regions
@PERM_REGION_MANAGE — Crear región.

**Body:**
```json
{
  "nombre": "...",
  "codigo": "...",
  "zona": "SUR|CENTRO|NORTE",
  "hectareasBosqueReferencia": 100000,
  "aoiBbox": [-73.6, -38.5, -71.0, -36.7]
}
```

### PATCH /api/regions/{id}/aoi
@PERM_REGION_MANAGE — Actualizar bounding box del AOI.

**Body:** `{ "aoiBbox": [west, south, east, north] }`

---

## 3. Territorio — `/api/territory`

Módulo central de monitoreo territorial.

### GET /api/territory/bounds?regionId={id}
@Público — Obtener centro, bounds y zoom para el mapa.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "regionId": "biobio",
    "center": [-37.5, -72.3],
    "bounds": [[-38.5, -73.6], [-36.7, -71.0]],
    "zoom": 8
  }
}
```

### GET /api/territory/layers?regionId={id}&indicators={csv}&from={date}&to={date}
@ROLE_VERIFIED_USER — Obtener capas GeoJSON por región.

**Parámetros:**
- `indicators`: CSV de `NDVI,NDMI,LOSS,ALERTS,FIRMS,REPORTS,RISK_SCORE`
- `from`, `to`: fechas en formato `yyyy-MM-dd`

**Response 200:**
```json
{
  "success": true,
  "data": {
    "regionId": "biobio",
    "generatedAt": "2026-06-02T01:00:00Z",
    "layers": {
      "FIRMS": { "type": "FeatureCollection", "features": [...] },
      "RISK_SCORE": { "type": "FeatureCollection", "features": [...] }
    }
  }
}
```

**Feature FIRMS:**
```json
{
  "type": "Feature",
  "id": "...",
  "geometry": { "type": "Point", "coordinates": [lon, lat] },
  "properties": {
    "indicator": "FIRMS",
    "label": "Foco activo VIIRS",
    "confidence": "n|h",
    "frp": 18.4,
    "satellite": "N",
    "acquiredAt": "2026-06-02T00:00:00Z"
  }
}
```

### GET /api/territory/risk-score/{regionId}
@ROLE_VERIFIED_USER — Score de riesgo actual de una región.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "regionId": "biobio",
    "computedAt": "2026-06-02T01:00:00Z",
    "scoreComposite": 0.72,
    "alertLevel": "ALTO",
    "qualityFlag": null,
    "components": {
      "fwi":     { "score": 0.68, "rawValue": 32.4, "weight": 0.38 },
      "ndmi":    { "score": 0.71, "rawValue": -0.18, "weight": 0.22 },
      "firms":   { "score": 0.80, "focosCount": 3, "frpMean": 18.4, "weight": 0.18 },
      "loss":    { "score": 0.45, "lossRate": 0.12, "weight": 0.10 },
      "ndvi":    { "score": 0.30, "rawValue": 0.54, "weight": 0.08 },
      "reports": { "score": 0.10, "count": 1, "weight": 0.04 }
    }
  }
}
```

### GET /api/territory/risk-score/comunas/{regionId}
@ROLE_VERIFIED_USER — Scores actuales de todas las comunas de una región.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "CHL.6.2.1_1": {
      "alertLevel": "ALTO",
      "scoreComposite": 0.71,
      "mode": "ENHANCED",
      "qualityFlag": null,
      "nombreComuna": "Los Ángeles",
      "computedAt": "2026-06-02T01:00:00Z",
      "fwiRaw": 32.4,
      "firmsCount": 3,
      "firmsFrpMean": 18.4,
      "ndmiRaw": -0.18,
      "ndviRaw": 0.54,
      "components": {
        "fwi": 0.2584,
        "firms": 0.1440,
        "reports": 0.0040,
        "ndmi": 0.1562,
        "ndvi": 0.0240,
        "loss": 0.0
      }
    }
  }
}
```

**Notas:**
- `components` son contribuciones ponderadas (valor × peso), no scores normalizados
- Campos `ndmiRaw`, `ndviRaw`, `components.ndmi/ndvi/loss` son null en modo STANDARD
- `qualityFlag` = `"COPERNICUS_UNAVAILABLE"` si el score superó el umbral pero no hay dato satelital fresco

### GET /api/territory/risk-score/comunas/{gadmGid}/history?days={n}
@ROLE_VERIFIED_USER — Historial de snapshots de una comuna.

**Parámetros:** `days` (default 7, máximo recomendado 30)

**Response 200:**
```json
{
  "success": true,
  "data": {
    "gadmGid": "CHL.6.2.1_1",
    "comunaNombre": "Los Ángeles",
    "snapshots": [
      {
        "computedAt": "2026-06-02T01:00:00Z",
        "scoreComposite": 0.71,
        "alertLevel": "ALTO",
        "mode": "ENHANCED"
      },
      {
        "computedAt": "2026-06-01T13:00:00Z",
        "scoreComposite": 0.65,
        "alertLevel": "PREVENTIVO",
        "mode": "STANDARD"
      }
    ]
  }
}
```

### POST /api/territory/sync?regionId={id}
@ROLE_ADMIN — Disparar sync manual completo para una región.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "regionId": "biobio",
    "alertLevel": "ALTO",
    "scoreComposite": 0.71,
    "qualityFlag": null,
    "comunalSyncTriggered": true
  }
}
```

### GET /geojson/comunas-{regionId}.geojson
**Público** — Asset estático GeoJSON de polígonos GADM 4.1 para choropleth.

**Valores válidos de `regionId`:** `biobio`, `araucania`

**Response 200:** GeoJSON FeatureCollection. Cada feature incluye:
```json
{
  "type": "Feature",
  "properties": {
    "comunaId": "CHL.6.2.1_1",
    "nombre": "Los Ángeles",
    "provincia": "Biobío",
    "regionGadm": "Biobío",
    "centerLat": -37.47,
    "centerLon": -72.35
  },
  "geometry": { "type": "Polygon", "coordinates": [...] }
}
```

---

## 4. Alertas — `/api/alerts`

### GET /api/alerts
Listar alertas con filtros opcionales.

**Params:** `regionId`, `level` (BAJO|MEDIO|ALTO|CRITICO), `from`, `to` (yyyy-MM-dd)

### GET /api/alerts/map?regionId={id}&level={level}&from={date}&to={date}
Obtener alertas en formato optimizado para mapa.

### GET /api/alerts/region/{regionId}
Alertas de una región específica.

### POST /api/alerts
@PERM_REGION_MANAGE — Crear alerta manual.

**Body:**
```json
{
  "regionId": "biobio",
  "nivelRiesgo": "ALTO",
  "latitud": -37.47,
  "longitud": -72.35,
  "descripcion": "Foco detectado en zona norte"
}
```

---

## 5. Reglas de alerta — `/api/rules`

### GET /api/rules
Listar todas las reglas. Params: `regionId`

### POST /api/rules
@PERM_ALERT_RULE_MANAGE — Crear regla de alerta.

**Body:**
```json
{
  "nombre": "Umbral crítico Biobío",
  "regionId": "biobio",
  "umbralPorcentajePerdida": 2.5,
  "umbralEventosCalor": 3,
  "activa": true
}
```

---

## 6. Reportes ciudadanos — `/api/citizen-reports`

### GET /api/citizen-reports
Params: `regionId`, `status`, `category`

### POST /api/citizen-reports
@PERM_REPORT_CREATE — Crear reporte. `multipart/form-data`.

**Fields:**
- `regionId` (String)
- `category` (HUMO|FOCO|INCENDIO|OTRO)
- `description` (String)
- `latitude` (Double)
- `longitude` (Double)
- `photos[]` (File, opcional, múltiple)

### PATCH /api/citizen-reports/{id}/status
@PERM_REPORT_MODERATE

**Body:** `{ "status": "VALIDADO|DERIVADO|DESCARTADO" }`

---

## 7. Comunidad — `/api/community`

### GET /api/community/board?regionId={id}
Listar avisos del tablón comunitario.

### POST /api/community/board
@PERM_COMMUNITY_BOARD_MANAGE

**Body:**
```json
{
  "regionId": "biobio",
  "title": "Alerta preventiva zona costera",
  "message": "Se activa protocolo preventivo...",
  "priority": "ALTA"
}
```

### GET /api/community/resources?regionId={id}
### POST /api/community/resources
@PERM_COMMUNITY_RESOURCE_MANAGE

**Body:**
```json
{
  "regionId": "biobio",
  "title": "Protocolo evacuación",
  "category": "PROTOCOLO",
  "url": "https://...",
  "description": "..."
}
```

### GET /api/community/contacts?regionId={id}
### POST /api/community/contacts
@ROLE_ADMIN

**Body:**
```json
{
  "regionId": "biobio",
  "name": "CONAF Biobío",
  "organization": "CONAF",
  "phone": "+56 41 ...",
  "email": "biobio@conaf.cl",
  "protocol": "Llamar ante foco confirmado"
}
```

---

## 8. Dashboard — `/api/dashboard`

### GET /api/dashboard/summary
**Response 200:**
```json
{
  "success": true,
  "data": {
    "totalRegions": 2,
    "activeAlerts": 3,
    "criticalRegions": 1,
    "totalForestLossHa": 12500.0,
    "lastUpdated": "2026-06-02T01:00:00Z"
  }
}
```

### GET /api/dashboard/indicators/latest?regionId={id}&indicator={type}
Último valor de un indicador para una región.

**Indicadores:** `NDVI`, `NDMI`

**Response 200:**
```json
{
  "success": true,
  "data": {
    "regionId": "biobio",
    "indicator": "NDVI",
    "value": 0.54,
    "unit": "index",
    "observedAt": "2026-05-28T00:00:00Z"
  }
}
```

### GET /api/dashboard/indicators/series?regionId={id}&indicator={type}&from={date}&to={date}&granularity={day|week|month}
Serie temporal de un indicador.

### POST /api/dashboard/sync/run
@PERM_DASHBOARD_SYNC_RUN — Ejecutar sincronización completa manual.

---

## 9. Pérdida forestal — `/api/forest-loss`

### GET /api/forest-loss?regionId={id}
### GET /api/forest-loss/region/{regionId}
### GET /api/forest-loss/year/{year}
### POST /api/forest-loss
@PERM_REGION_MANAGE

**Body:**
```json
{
  "regionId": "biobio",
  "anio": 2023,
  "hectareasPerdidas": 1250.0,
  "porcentajePerdida": 0.83,
  "fuente": "CONAF"
}
```

---

## 10. Ingesta OpenEO — `/api/indicators`

### POST /api/indicators/measurements
Endpoint interno para que openeo-service ingeste resultados.

**Header:** `X-Ingest-Token: {OPENEO_INGEST_AUTH_TOKEN}`

**Body:**
```json
{
  "regionId": "biobio",
  "indicator": "NDVI",
  "value": 0.54,
  "unit": "index",
  "observedAt": "2026-05-28T00:00:00Z",
  "aoi": "-73.6,-38.5,-71.0,-36.7",
  "source": "copernicus_cdse"
}
```

---

## 11. openeo-service — Endpoints internos (FastAPI)

Base URL: `https://<railway-openeo>/`
Consumido únicamente por el backend Spring Boot.

### GET /health
Estado del servicio.

### GET /openeo/capabilities
Capacidades del servidor Copernicus CDSE configurado.

### GET /openeo/collections?limit={1-20}
Colecciones disponibles en CDSE (ej. `SENTINEL2_L2A`).

### POST /openeo/indicators/latest/{indicator}
Obtener último valor promedio de un indicador para un AOI.

**Path params:** `indicator` = `NDVI` | `NDMI`

**Body:**
```json
{
  "regionId": "biobio",
  "aoi": {
    "type": "bbox",
    "coordinates": [-73.6, -38.5, -71.0, -36.7]
  },
  "periodStart": "2026-05-20",
  "periodEnd": "2026-06-02"
}
```

**Response 200:**
```json
{
  "indicator": "NDVI",
  "value": 0.54,
  "unit": "index",
  "observedAt": "2026-05-28",
  "source": "copernicus_cdse",
  "aoi": [-73.6, -38.5, -71.0, -36.7]
}
```

---

## 12. Notificaciones in-app — `/api/notifications` [CU09 — nuevo]

### GET /api/notifications/unread
@Autenticado — Obtener notificaciones no leidas del usuario autenticado.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "notifications": [
      {
        "id": "uuid",
        "type": "RISK_ALERT",
        "title": "Alerta CRITICO — Los Angeles",
        "message": "Score de riesgo: 0.872. La comuna presenta nivel CRITICO.",
        "regionId": "biobio",
        "comunaId": "CHL.6.2.1_1",
        "alertLevel": "CRITICO",
        "read": false,
        "createdAt": "2026-06-05T01:15:00Z"
      }
    ],
    "unreadCount": 3
  }
}
```

### PATCH /api/notifications/{id}/read
@Autenticado — Marcar una notificacion como leida (solo el dueno puede marcarla).

**Response 200:** mismo formato que un item de la lista anterior, con `"read": true`.

**Errores:**
- `401` si el token es invalido o la notificacion pertenece a otro usuario.
- `404` si el id no existe.

---

## 12b. Control de acceso — endpoints nuevos `/api/admin/access` [CU15 brecha — nuevo]

### GET /api/admin/access/users/{userId}/verification-events
@ROLE_ADMIN — Historial de VerificationEvent de un usuario, ordenado por fecha desc.

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "eventType": "IDENTITY_RESET",
      "oldStatus": "IDENTITY_VERIFIED",
      "newStatus": "EMAIL_VERIFIED",
      "reviewedBy": null,
      "notes": "Nombre cambiado por el propio usuario",
      "createdAt": "2026-06-05T10:00:00Z"
    }
  ]
}
```

### PUT /api/admin/access/users/{userId}/verification
@ROLE_ADMIN — Cambiar estado de verificacion de un usuario. Notas obligatorias.

**Body:**
```json
{
  "newStatus": "IDENTITY_VERIFIED",
  "notes": "Revisado documento de identidad. Nombre correcto."
}
```

**Estados validos:** `EMAIL_VERIFIED`, `PHONE_VERIFIED`, `IDENTITY_VERIFIED`, `FULLY_VERIFIED`, `SUSPENDED`.

**Response 200:** AccessUserDTO con `verificationStatus` actualizado.

**Errores:**
- `400` si `newStatus` o `notes` estan vacios.
- `400` si `newStatus` no es un estado valido del enum.
- `404` si el usuario no existe.

### GET /api/admin/access/users/pending-review
@ROLE_ADMIN — Lista de usuarios cuyo ultimo VerificationEvent es `IDENTITY_RESET` sin evento posterior.

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "email": "usuario@ejemplo.cl",
      "fullName": "Juan Perez",
      "currentStatus": "EMAIL_VERIFIED",
      "lastEvent": {
        "id": "uuid",
        "eventType": "IDENTITY_RESET",
        "oldStatus": "IDENTITY_VERIFIED",
        "newStatus": "EMAIL_VERIFIED",
        "reviewedBy": null,
        "notes": "Nombre cambiado por el propio usuario",
        "createdAt": "2026-06-05T10:00:00Z"
      }
    }
  ]
}
```

---

## 13. Tabla de permisos por endpoint

| Endpoint | Público | V. User | Moderador | Admin | Super Admin |
|---|:---:|:---:|:---:|:---:|:---:|
| GET /auth/*, /api/regions, /api/alerts, /api/rules, /api/forest-loss | ✓ | ✓ | ✓ | ✓ | ✓ |
| GET /api/notifications/unread | — | ✓ | ✓ | ✓ | ✓ |
| PATCH /api/notifications/{id}/read | — | ✓ | ✓ | ✓ | ✓ |
| GET /api/account/me | — | ✓ | ✓ | ✓ | ✓ |
| PATCH /api/account/me | — | ✓ | ✓ | ✓ | ✓ |
| POST /api/account/change-password | — | ✓ | ✓ | ✓ | ✓ |
| GET /api/territory/* | — | ✓ | ✓ | ✓ | ✓ |
| GET /api/community/*, /api/citizen-reports | — | ✓ | ✓ | ✓ | ✓ |
| GET /api/dashboard/* | — | ✓ | ✓ | ✓ | ✓ |
| POST /api/citizen-reports | — | ✓ | ✓ | ✓ | ✓ |
| POST /api/community/board, /resources | — | — | ✓ | ✓ | ✓ |
| PATCH /api/citizen-reports/{id}/status | — | — | ✓ | ✓ | ✓ |
| POST /api/territory/sync | — | — | — | ✓ | ✓ |
| POST/PUT/DELETE /api/regions, /alerts, /rules, /api/forest-loss | — | — | — | ✓ | ✓ |
| POST /api/community/contacts | — | — | — | ✓ | ✓ |
| POST /api/dashboard/sync/run | — | — | — | ✓ | ✓ |
| GET /api/admin/access/users/{id}/verification-events | — | — | — | ✓ | ✓ |
| PUT /api/admin/access/users/{id}/verification | — | — | — | ✓ | ✓ |
| GET /api/admin/access/users/pending-review | — | — | — | ✓ | ✓ |
| Gestion de roles/permisos (/api/access) | — | — | — | — | ✓ |

**Nota:** "V. User" incluye cualquier usuario autenticado (ROLE_COMMUNITY_USER, ROLE_VERIFIED_USER o superior). Los endpoints de `/api/account` requieren unicamente estar autenticado, sin rol especifico.
