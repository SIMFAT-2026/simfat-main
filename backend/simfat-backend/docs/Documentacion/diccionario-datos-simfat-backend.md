# Diccionario de Datos - SIMFAT Backend

- Proyecto: SIMFAT Backend
- Fecha: 2026-04-21
- Version: 1.0
- Alcance: estructuras persistentes activas del backend (PostgreSQL + MongoDB)
- Fuente tecnica: entidades en `src/main/java/com/simfat/backend/model` y migracion `V1__create_auth_tables.sql`

## 1) Convenciones

- PK: llave primaria.
- UK: llave unica.
- NN: no nulo.
- FK logica: relacion usada por la aplicacion, pero no forzada con `FOREIGN KEY` en la BD.
- En MongoDB, los campos se listan con su nombre real (camelCase) tal como se persisten por Spring Data.

## 2) Modelo Relacional (PostgreSQL)

### 2.1 Tabla `app_users`

Descripcion: usuarios del sistema para autenticacion y autorizacion.

| Campo | Tipo SQL | Nulo | Restricciones | Descripcion |
|---|---|---|---|---|
| id | VARCHAR(36) | No | PK | Identificador UUID en texto. |
| email | VARCHAR(180) | No | UK | Correo de acceso del usuario. |
| full_name | VARCHAR(120) | No |  | Nombre completo. |
| password_hash | VARCHAR(100) | No |  | Hash BCrypt de la contrasena. |
| enabled | BOOLEAN | No | Default `TRUE` | Estado habilitado del usuario. |
| roles | VARCHAR(255) | No |  | Roles serializados en CSV (`ADMIN,USER`). |
| created_at | TIMESTAMPTZ | No |  | Fecha de creacion (UTC). |
| updated_at | TIMESTAMPTZ | No |  | Ultima actualizacion (UTC). |

Notas:
- `created_at` y `updated_at` se completan/actualizan desde la aplicacion (`@PrePersist`, `@PreUpdate`).
- `roles` usa conversion `Set<UserRole> <-> String`.

### 2.2 Tabla `refresh_tokens`

Descripcion: tokens de refresco para sesiones JWT con rotacion y revocacion.

| Campo | Tipo SQL | Nulo | Restricciones | Descripcion |
|---|---|---|---|---|
| id | VARCHAR(36) | No | PK | Identificador UUID en texto. |
| token_id | VARCHAR(64) | No | UK | Identificador publico del refresh token. |
| user_id | VARCHAR(36) | No | FK logica -> `app_users.id` | Usuario propietario del token. |
| token_hash | VARCHAR(128) | No | UK | Hash del refresh token (no se guarda token plano). |
| issued_at | TIMESTAMPTZ | No |  | Fecha de emision (UTC). |
| expires_at | TIMESTAMPTZ | No |  | Fecha de expiracion (UTC). |
| revoked_at | TIMESTAMPTZ | Si |  | Fecha de revocacion (si aplica). |
| replaced_by_token_id | VARCHAR(64) | Si |  | Token que reemplaza al actual en rotacion. |
| created_by_ip | VARCHAR(64) | Si |  | IP origen de emision. |
| user_agent | VARCHAR(512) | Si |  | User-Agent reportado por cliente. |

Indices:
- `idx_refresh_tokens_user_id` (`user_id`)
- `idx_refresh_tokens_revoked_at` (`revoked_at`)
- `idx_refresh_tokens_expires_at` (`expires_at`)

### 2.3 Tabla `password_reset_tokens`

Descripcion: tokens para flujo de recuperacion y cambio de contrasena.

| Campo | Tipo SQL | Nulo | Restricciones | Descripcion |
|---|---|---|---|---|
| id | VARCHAR(36) | No | PK | Identificador UUID en texto. |
| token_hash | VARCHAR(128) | No | UK | Hash del token de recuperacion. |
| user_id | VARCHAR(36) | No | FK logica -> `app_users.id` | Usuario al que pertenece el token. |
| created_at | TIMESTAMPTZ | No |  | Fecha de creacion del token (UTC). |
| expires_at | TIMESTAMPTZ | No |  | Fecha de expiracion del token (UTC). |
| consumed_at | TIMESTAMPTZ | Si |  | Fecha de consumo exitoso del token. |

Indices:
- `idx_password_reset_tokens_user_id` (`user_id`)
- `idx_password_reset_tokens_expires_at` (`expires_at`)

## 3) Modelo Documental (MongoDB)

### 3.1 Coleccion `regions`

Descripcion: catalogo de regiones monitoreadas.

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripcion |
|---|---|---|---|---|
| id | ObjectId/String | Si (Mongo) | PK natural Mongo (`_id`) | Identificador de region. |
| nombre | String | Si | max 120, no vacio | Nombre de region. |
| codigo | String | Si | max 20, no vacio | Codigo funcional (`CL-15`, etc.). |
| zona | String | Si | max 50, no vacio | Macro zona (`NORTE`, `CENTRO`, etc.). |
| hectareasBosqueReferencia | Double | No | > 0 cuando se informa | Superficie de referencia de bosque. |
| aoiBbox | Array<Double> | No | idealmente 4 coords | Bounding box AOI `[west,south,east,north]`. |

Indices definidos:
- Solo indice por defecto de `_id`.

### 3.2 Coleccion `forest_loss_records`

Descripcion: historico de perdida anual de bosque por region.

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripcion |
|---|---|---|---|---|
| id | ObjectId/String | Si (Mongo) | PK natural Mongo (`_id`) | Identificador del registro. |
| regionId | String | Si | no vacio | Referencia logica a `regions.id`. |
| anio | Int32 | Si | >= 1900 | Ano del dato. |
| hectareasPerdidas | Double | Si | >= 0 | Hectareas perdidas en el ano. |
| porcentajePerdida | Double | No | >= 0 | Porcentaje de perdida respecto de referencia. |
| fuente | String | Si | max 120, no vacio | Fuente del dato. |
| fechaRegistro | Date | Si |  | Fecha/hora de registro del dato. |

Indices definidos:
- Solo indice por defecto de `_id`.

### 3.3 Coleccion `heat_alert_events`

Descripcion: eventos de alerta de calor asociados a regiones.

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripcion |
|---|---|---|---|---|
| id | ObjectId/String | Si (Mongo) | PK natural Mongo (`_id`) | Identificador del evento. |
| regionId | String | Si | no vacio | Referencia logica a `regions.id`. |
| fechaEvento | Date | Si |  | Fecha/hora del evento. |
| nivelRiesgo | String (enum) | Si | `BAJO`, `MEDIO`, `ALTO`, `CRITICO` | Nivel de severidad. |
| latitud | Double | Si | -90 a 90 | Coordenada geografica. |
| longitud | Double | Si | -180 a 180 | Coordenada geografica. |
| fuente | String | Si | max 120, no vacio | Fuente del evento (ej. NASA FIRMS). |
| descripcion | String | No | max 500 | Observacion textual del evento. |

Indices definidos:
- Solo indice por defecto de `_id`.

### 3.4 Coleccion `alert_rules`

Descripcion: reglas de negocio para disparo de alertas.

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripcion |
|---|---|---|---|---|
| id | ObjectId/String | Si (Mongo) | PK natural Mongo (`_id`) | Identificador de regla. |
| nombre | String | Si | max 120, no vacio | Nombre de la regla. |
| regionId | String | No |  | Si es `null`, la regla aplica globalmente. |
| umbralPorcentajePerdida | Double | Si | >= 0 | Umbral de perdida para activar alerta. |
| umbralEventosCalor | Int32 | Si | >= 0 | Umbral de eventos de calor para activar alerta. |
| activa | Boolean | Si |  | Estado de activacion de regla. |

Indices definidos:
- Solo indice por defecto de `_id`.

### 3.5 Coleccion `openeo_job_runs`

Descripcion: trazabilidad de ejecuciones/sincronizaciones de jobs hacia openEO.

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripcion |
|---|---|---|---|---|
| id | ObjectId/String | Si (Mongo) | PK natural Mongo (`_id`) | Identificador interno del run. |
| jobId | String | No* | Unico | ID externo/interno de job. |
| regionId | String | No* |  | Region objetivo del proceso. |
| indicator | String (enum) | No* | `NDVI`, `NDMI` | Indicador solicitado. |
| periodStart | Date | No |  | Inicio de ventana solicitada. |
| periodEnd | Date | No |  | Fin de ventana solicitada. |
| status | String | No* |  | Estado del job (`queued`, `success`, etc.). |
| requestedAt | Date | No |  | Fecha/hora de solicitud. |
| updatedAt | Date | No |  | Ultima actualizacion de estado. |
| finishedAt | Date | No |  | Fecha/hora de finalizacion. |
| errorCode | String | No |  | Codigo de error si falla. |
| errorMessage | String | No |  | Mensaje de error si falla. |
| source | String | No |  | Origen de la ejecucion. |


* No posee anotacion `@NotNull`, pero en la practica se usa como dato operativo clave.

Indices definidos:
- `jobId` unico.
- `regionId`.
- `indicator`.
- `status`.
- Compuesto `idx_status_updatedAt`: `{ status: 1, updatedAt: -1 }`.

### 3.6 Coleccion `openeo_indicator_observations`

Descripcion: observaciones historicas de indicadores (NDVI/NDMI) por region.

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripcion |
|---|---|---|---|---|
| id | ObjectId/String | Si (Mongo) | PK natural Mongo (`_id`) | Identificador de observacion. |
| regionId | String | No* |  | Region observada. |
| indicator | String (enum) | No* | `NDVI`, `NDMI` | Indicador observado. |
| observedAt | Date | No* |  | Fecha/hora efectiva de observacion. |
| value | Double | No |  | Valor medido del indicador. |
| unit | String | No |  | Unidad reportada por origen. |
| aoi | String | No |  | AOI usada para el calculo. |
| quality | String | No |  | Calidad de dato (ej. `measured`, `estimated`). |
| source | String | No |  | Fuente del dato. |
| ingestedAt | Date | No |  | Fecha/hora de ingestion en backend. |


* No posee anotacion `@NotNull`, pero participa en indice unico compuesto y consultas principales.

Indices definidos:
- Compuesto para lectura temporal: `{ regionId: 1, indicator: 1, observedAt: -1 }`.
- Compuesto unico: `{ regionId: 1, indicator: 1, observedAt: 1 }`.

### 3.7 Coleccion `dashboard_region_snapshots`

Descripcion: snapshot agregado por region para responder dashboard rapido.

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripcion |
|---|---|---|---|---|
| id | ObjectId/String | Si (Mongo) | PK natural Mongo (`_id`) | Identificador del snapshot. |
| regionId | String | No* | Unico | Region del snapshot actual. |
| latestNdvi | Double | No |  | Ultimo NDVI calculado. |
| latestNdmi | Double | No |  | Ultimo NDMI calculado. |
| ndviTrend30d | Double | No |  | Tendencia NDVI 30 dias. |
| ndmiTrend30d | Double | No |  | Tendencia NDMI 30 dias. |
| heatAlerts7d | Int64 | No |  | Total alertas calor ultimos 7 dias. |
| forestLossCurrentPct | Double | No |  | Perdida actual estimada (%). |
| criticality | String | No |  | Nivel de criticidad agregado. |
| computedAt | Date | No |  | Fecha/hora de computo de snapshot. |
| dataFreshnessSeconds | Int64 | No |  | Antiguedad de datos en segundos. |


* No posee anotacion `@NotNull`, pero en operacion normal se usa siempre.

Indices definidos:
- `regionId` unico.
- Compuesto `idx_computedAt_desc`: `{ computedAt: -1 }`.

## 4) Catalogo de Enums

### 4.1 `UserRole` (PostgreSQL, campo `app_users.roles`)

- `USER`
- `ADMIN`

Persistencia:
- Se almacena como texto CSV ordenado alfabeticamente (ejemplo: `ADMIN,USER`).

### 4.2 `RiskLevel` (MongoDB, campo `heat_alert_events.nivelRiesgo`)

- `BAJO`
- `MEDIO`
- `ALTO`
- `CRITICO`

### 4.3 `IndicatorType` (MongoDB, campos `openeo_job_runs.indicator`, `openeo_indicator_observations.indicator`)

- `NDVI`
- `NDMI`

## 5) Relaciones de Datos (logicas)

| Origen | Campo | Destino | Cardinalidad | Tipo |
|---|---|---|---|---|
| refresh_tokens | user_id | app_users.id | N:1 | FK logica |
| password_reset_tokens | user_id | app_users.id | N:1 | FK logica |
| forest_loss_records | regionId | regions.id | N:1 | FK logica |
| heat_alert_events | regionId | regions.id | N:1 | FK logica |
| alert_rules | regionId | regions.id | N:1 (opcional) | FK logica |
| openeo_job_runs | regionId | regions.id | N:1 | FK logica |
| openeo_indicator_observations | regionId | regions.id | N:1 | FK logica |
| dashboard_region_snapshots | regionId | regions.id | 1:1 efectivo (por indice unico) | FK logica |

## 6) Observaciones Tecnicas para Defensa

- La integridad referencial entre tablas auth (`user_id`) y entre colecciones Mongo (`regionId`) es controlada por aplicacion; actualmente no hay llaves foraneas fisicas.
- En PostgreSQL, `spring.jpa.hibernate.ddl-auto=validate`: la estructura productiva depende de migraciones Flyway.
- En MongoDB hay indices explicitos para los flujos openEO y snapshots; para colecciones funcionales (`forest_loss_records`, `heat_alert_events`, `regions`, `alert_rules`) solo existe indice por `_id`.
- Este diccionario representa el estado del repositorio a fecha 2026-04-21.
