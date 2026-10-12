# Diccionario de Datos - SIMFAT Backend

- Proyecto: SIMFAT Backend
- Fecha: 2026-04-21
- Version: 1.0
- Alcance: estructuras persistentes activas del backend (PostgreSQL + MongoDB)
- Fuente t?cnica: entidades en `src/main/java/com/simfat/backend/model` y migracion `V1__create_auth_tables.sql`

## 1) Convenciones

- PK: llave primaria.
- UK: llave unica.
- NN: no nulo.
- FK l?gica: relacion usada por la aplicacion, pero no forzada con `FOREIGN KEY` en la BD.
- En MongoDB, los campos se listan con su nombre real (camelCase) tal como se persisten por Spring Data.

## 2) Modelo Relacional (PostgreSQL)

### 2.1 Tabla `app_users`

Descripcion: usuarios del sistema para autenticaci?n y autorizaci?n.

| Campo | Tipo SQL | Nulo | Restricciones | Descripcion |
|---|---|---|---|---|
| id | VARCHAR(36) | No | PK | Identificador UUID en texto. |
| email | VARCHAR(180) | No | UK | Correo de acceso del usuario. |
| full_name | VARCHAR(120) | No |  | Nombre completo. |
| password_hash | VARCHAR(100) | No |  | Hash BCrypt de la contrase?a. |
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
| user_id | VARCHAR(36) | No | FK l?gica -> `app_users.id` | Usuario propietario del token. |
| token_hash | VARCHAR(128) | No | UK | Hash del refresh token (no se guarda token plano). |
| issued_at | TIMESTAMPTZ | No |  | Fecha de emision (UTC). |
| expires_at | TIMESTAMPTZ | No |  | Fecha de expiracion (UTC). |
| revoked_at | TIMESTAMPTZ | Si |  | Fecha de revocacion (s? aplica). |
| replaced_by_token_id | VARCHAR(64) | Si |  | Token que reemplaza al actual en rotacion. |
| created_by_ip | VARCHAR(64) | Si |  | IP origen de emision. |
| user_agent | VARCHAR(512) | Si |  | User-Agent reportado por cliente. |

Indices:
- `idx_refresh_tokens_user_id` (`user_id`)
- `idx_refresh_tokens_revoked_at` (`revoked_at`)
- `idx_refresh_tokens_expires_at` (`expires_at`)

### 2.3 Tabla `password_reset_tokens`

Descripcion: tokens para flujo de recuperacion y cambio de contrase?a.

| Campo | Tipo SQL | Nulo | Restricciones | Descripcion |
|---|---|---|---|---|
| id | VARCHAR(36) | No | PK | Identificador UUID en texto. |
| token_hash | VARCHAR(128) | No | UK | Hash del token de recuperacion. |
| user_id | VARCHAR(36) | No | FK l?gica -> `app_users.id` | Usuario al que pertenece el token. |
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
| c?digo | String | Si | max 20, no vacio | C?digo funcional (`CL-15`, etc.). |
| zona | String | Si | max 50, no vacio | Macro zona (`NORTE`, `CENTRO`, etc.). |
| hectareasBosqueReferencia | Double | No | > 0 cuando se informa | Superficie de referencia de bosque. |
| aoiBbox | Array<Double> | No | idealmente 4 coords | Bounding box AOI `[west,south,east,north]`. |

Indices definidos:
- Solo indice por defecto de `_id`.

### 3.2 Coleccion `forest_loss_records`

Descripcion: hist?rico de perdida anual de bosque por region.

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripcion |
|---|---|---|---|---|
| id | ObjectId/String | Si (Mongo) | PK natural Mongo (`_id`) | Identificador del registro. |
| regionId | String | Si | no vacio | Referencia l?gica a `regions.id`. |
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
| regionId | String | Si | no vacio | Referencia l?gica a `regions.id`. |
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
| errorCode | String | No |  | C?digo de error s? falla. |
| errorMessage | String | No |  | Mensaje de error s? falla. |
| source | String | No |  | Origen de la ejecuci?n. |


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


* No posee anotacion `@NotNull`, pero en operaci?n normal se usa siempre.

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
| refresh_tokens | user_id | app_users.id | N:1 | FK l?gica |
| password_reset_tokens | user_id | app_users.id | N:1 | FK l?gica |
| forest_loss_records | regionId | regions.id | N:1 | FK l?gica |
| heat_alert_events | regionId | regions.id | N:1 | FK l?gica |
| alert_rules | regionId | regions.id | N:1 (opcional) | FK l?gica |
| openeo_job_runs | regionId | regions.id | N:1 | FK l?gica |
| openeo_indicator_observations | regionId | regions.id | N:1 | FK l?gica |
| dashboard_region_snapshots | regionId | regions.id | 1:1 efectivo (por indice unico) | FK l?gica |

## 6) Observaciones Tecnicas para Defensa

- La integridad referencial entre tablas auth (`user_id`) y entre colecciones Mongo (`regionId`) es controlada por aplicacion; actualmente no hay llaves foraneas fisicas.
- En PostgreSQL, `spring.jpa.hibernate.ddl-auto=validate`: la estructura productiva depende de migraciones Flyway.
- En MongoDB hay indices explicitos para los flujos openEO y snapshots; para colecciones funcionales (`forest_loss_records`, `heat_alert_events`, `regions`, `alert_rules`) solo existe indice por `_id`.
- Este diccionario representa el estado del repositorio a fecha 2026-04-21.

---

## Actualización 2026-10-12: integración MapBiomas

- Fecha de actualización: 2026-10-12
- Alcance: colecciones y campos MongoDB agregados o modificados por la integración MapBiomas (commit de merge `767caa6`).
- Fuente técnica: `model/ComunaMapbiomasStats.java`, `model/ComunaFwiState.java`, `model/ComunaRiskSnapshot.java`, `model/TerritoryWeatherObservation.java` y `application.properties`.
- Detalle ampliado, diagrama ER y reglas de cálculo: `Documentacion/MER/2026-10-12_mer_integracion_mapbiomas_v1.md`.
- **PostgreSQL:** sin cambios. La última migración Flyway sigue siendo `V6__notifications.sql`.
- Las secciones 3 a 6 anteriores describen el estado al 2026-04-21 y no se modifican. Los nombres de colección de esta actualización son los reales del código (`@Document`).

### 3.8 Colección `comuna_mapbiomas_stats` (nueva)

Descripción: estadísticas MapBiomas por comuna (cobertura del suelo Colección 2, 1999-2024, y Fuego Colección 1, 2013-2025). Un documento por `(comunaId, dataVersion)`, cargado desde una semilla JSONL por `MapbiomasSeedLoader`. Con la semilla vigente hay 86 documentos (`dataVersion = lulc-col2+fuego-col1@2013-2025`).

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripción |
|---|---|---|---|---|
| id | String | Sí | PK natural Mongo (`_id`) con formato `{comunaId}\|{dataVersion}` | Llave determinística de upsert; evita duplicados al recargar. |
| comunaId | String | Sí | GADM GID_3 (ejemplo `CHL.13.1.1_1`) | Comuna a la que pertenecen las estadísticas. |
| regionId | String | Sí | `biobio`, `nuble` o `araucania` | Región, copiada desde `comunas.regionId` al cargar. |
| nombreComuna | String | Sí | | Nombre legible, copiado desde `comunas.nombre`. |
| dataVersion | String | Sí | | Versión de los datos (propiedad `mapbiomas.seed.data-version`). |
| landCover | Objeto | No | `null` si no hay cobertura | Cobertura del suelo (ver 3.8.1). |
| landCoverReason | String | No | | Motivo por el que `landCover` es `null`; no viene en la semilla vigente. |
| fire | Objeto | No | | Historial de fuego (ver 3.8.2). |
| fireReason | String | No | | Motivo por el que falta `fire`; no viene en la semilla vigente. |
| partial | Boolean | Sí | Por defecto `false` | `true` si alguna fuente es parcial; `false` en las 86 comunas vigentes. |
| provenance | Objeto | No | | Procedencia: `sources` (lista de archivos), `scope` (texto) y `downloadDate` (texto `AAAA-MM-DD`). |
| computedAt | Date (Instant UTC) | No | | Momento en que el pipeline generó el registro. |

Subdocumento `landCover` (3.8.1):

| Campo | Tipo (BSON esperado) | Descripción |
|---|---|---|
| referenceYear | Int32 | Año de referencia (2024 en la semilla vigente). |
| sharesByClass | Documento (clase a Double) | Fracción (0 a 1) de cada clase de cobertura en el año de referencia; suma aproximadamente 1,0. |
| sharesByClassMean5y | Documento (clase a Double) | Fracción promedio de 5 años. |
| sharesByYear.firstYear / lastYear | Int32 | Rango de la serie anual (1999 a 2024). |
| sharesByYear.unit | String | `bp` (puntos base, 0 a 10000). |
| sharesByYear.classes | Documento (clase a arreglo de Int32) | Serie por clase; el índice `i` es el año `firstYear + i`. Solo en puntos base; las demás proporciones son fracciones. |

Subdocumento `fire` (3.8.2):

| Campo | Tipo (BSON esperado) | Descripción |
|---|---|---|
| available | Boolean | `true` si hay datos de fuego utilizables. |
| coverageFraction | Double | Fracción de la comuna cubierta por la extensión del raster de Fuego. |
| burnedHaByYear | Documento (año a Double) | Hectáreas quemadas por año, 2013 a 2025. |
| burnedFractionByYear | Documento (año a Double) | Fracción de la superficie comunal quemada por año. |
| burnedFraction | Double | Fracción quemada acumulada (unión a nivel de píxel; no es la suma de las anuales). |
| burnedFractionPct | Double | Rango percentil empírico de `burnedFraction` entre las comunas; es el `burnedNorm` del score. |
| burnedFractionLog | Double | Variante `log1p` con recorte en p95. |
| frequencyMean | Double | Frecuencia media de quemas por píxel (2013-2025). |
| frequencyMax | Int32 | Frecuencia máxima en la comuna. |
| yearLastFire | Int32 | Último año con fuego. |
| yearsSinceLastFire | Int32 | Años desde el último fuego respecto de 2025. |
| reason | String | Solo no nulo cuando `available = false`. |

Indices definidos:
- Compuesto `idx_region_dataversion`: `{ regionId: 1, dataVersion: 1 }`.
- Compuesto `idx_comuna_dataversion_desc`: `{ comunaId: 1, dataVersion: -1 }`.

### 3.9 Colección `comuna_fwi_state` (nueva)

Descripción: estado vigente de la cadena FWI Van Wagner por comuna (un documento por comuna, sin historial). Solo se usa cuando `territory.fwi.method = VAN_WAGNER`.

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripción |
|---|---|---|---|---|
| id | String | Sí | PK natural Mongo (`_id`) = `comunaId` | Identificador de la comuna. |
| stateDate | Date (LocalDate) | Sí | | Día calendario hasta el cual se avanzó la cadena. |
| ffmc | Double | Sí | | Fine Fuel Moisture Code al cierre de `stateDate`. |
| dmc | Double | Sí | | Duff Moisture Code al cierre de `stateDate`. |
| dc | Double | Sí | | Drought Code al cierre de `stateDate`. |
| baseDate | Date (LocalDate) | Sí | Un día antes de `stateDate` | Fecha de la base desde la que se avanza. |
| baseFfmc / baseDmc / baseDc | Double | Sí | | Códigos al cierre de `baseDate`; permiten recalcular el mismo día sin acumular dos veces. |
| method | String | Sí | `VAN_WAGNER` | Método que produjo el estado. |
| qualityFlag | String | No | `null`, `FWI_WARMUP` o `FWI_RESTARTED` | `FWI_WARMUP`: arranque en frío; `FWI_RESTARTED`: reinicio por un hueco mayor que 3 días. |
| updatedAt | Date (LocalDateTime) | No | | Solo observabilidad. |

Indices definidos: ninguno adicional al de `_id`.

### 3.10 Colección `comuna_risk_snapshots` (campos nuevos)

Descripción: snapshot de riesgo por comuna y ciclo. Se listan solo los campos agregados por la integración; los campos previos (`comunaId`, `regionId`, `scoreComposite`, `alertLevel`, `mode`, `qualityFlag`, `component*`, `fwiRaw`, `firmsCount`, etc.) no cambian de significado. Los documentos anteriores leen `null` en estos campos.

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripción |
|---|---|---|---|---|
| alertDriver | String | No | `SCORE`, `FWI`, `FIRMS_TODAY`, `FIRMS_CLUSTER` o `FIRMS_FRP` | Regla que determinó `alertLevel`. |
| componentMapbiomas | Double | No | | `wEff × S_mapbiomas` redondeado a 4 decimales. |
| mapbiomasFuelIndex | Double | No | 0 a 1 | Índice de combustible; `null` si no hay cobertura. |
| mapbiomasHistoryIndex | Double | No | 0 a 1 | Índice de historial de fuego; `null` si no hay datos de fuego. |
| mapbiomasWeight | Double | No | 0 a 0,50 | Peso efectivo `wEff` de este snapshot (0 si faltan datos). |
| mapbiomasDataVersion | String | No | | `dataVersion` de la que salió el resultado. |
| mapbiomasQualityFlag | String | No | Lista separada por comas | `MAPBIOMAS_FUEL_UNAVAILABLE`, `MAPBIOMAS_FIRE_UNAVAILABLE`, `MAPBIOMAS_UNAVAILABLE`, `MAPBIOMAS_BURNED_NORM_RAW_FALLBACK`. |

Efecto sobre campos existentes: con `wEff > 0`, `scoreComposite = clamp((1 - wEff) × S_dyn + wEff × S_mapbiomas)` y los componentes `componentFwi`, `componentFirms`, `componentReports`, `componentNdmi` y `componentNdvi` se multiplican por `(1 - wEff)`. Con el valor por defecto (`wEff = 0`) los valores no cambian.

Indices definidos (sin cambios):
- Compuesto `idx_comuna_risk_computed_desc`: `{ comunaId: 1, computedAt: -1 }`.
- Compuesto `idx_region_computed_desc`: `{ regionId: 1, computedAt: -1 }`.

### 3.11 Colección `territory_weather_observations` (campo nuevo)

| Campo | Tipo (BSON esperado) | Requerido en app | Restricciones de dominio | Descripción |
|---|---|---|---|---|
| fwiMethod | String | No | `PROXY_V1` o `VAN_WAGNER` | Método que produjo `fwi`, `ffmc`, `dmc`, `dc`, `isi`, `bui` y `dsr`. Los documentos anteriores a 2026-09-22 no tienen el campo y se interpretan como `PROXY_V1`. |

### 3.12 Colecciones sin cambios

`territory_risk_snapshots` (su campo `alertDriver` es anterior a la integración) y `comunas` (solo se lee desde el cargador de semilla) no se modifican.

### Relaciones de datos nuevas (lógicas)

| Origen | Campo | Destino | Cardinalidad | Tipo |
|---|---|---|---|---|
| comuna_mapbiomas_stats | comunaId | comunas.id | N:1 (una fila por versión de datos) | FK lógica |
| comuna_mapbiomas_stats | regionId | regions.id | N:1 | FK lógica |
| comuna_fwi_state | id | comunas.id | 1:1 | FK lógica |
| comuna_risk_snapshots | mapbiomasDataVersion | comuna_mapbiomas_stats.dataVersion | N:1 (auditoría) | Referencia lógica |
| territory_weather_observations | regionId (= comunaId) | comunas.id | N:1 | FK lógica |

### Catálogo de valores nuevos

- `fwiMethod`: `PROXY_V1`, `VAN_WAGNER`.
- `comuna_fwi_state.qualityFlag`: `FWI_WARMUP`, `FWI_RESTARTED`.
- `comuna_risk_snapshots.alertDriver`: `SCORE`, `FWI`, `FIRMS_TODAY`, `FIRMS_CLUSTER`, `FIRMS_FRP`.
- `comuna_risk_snapshots.alertLevel`: `NORMAL`, `PREVENTIVO`, `ALTO`, `CRITICO`.
- `comuna_risk_snapshots.mapbiomasQualityFlag`: ver 3.10.

### Observaciones técnicas

- Los índices se crean automáticamente al arrancar (`spring.data.mongodb.auto-index-creation=true`).
- `comuna_mapbiomas_stats` está separada de `comunas` porque `ComunaInfo` se reescribe completa en cada arranque.
- La integración no agrega llaves foráneas físicas; todas las relaciones son lógicas.
