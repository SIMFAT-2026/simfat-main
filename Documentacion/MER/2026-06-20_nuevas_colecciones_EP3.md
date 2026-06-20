# Nuevas Colecciones de Base de Datos — EP3
## SIMFAT — Sistema de Monitorización Forestal y Análisis Territorial

- **Curso:** TPY1101 – Taller Aplicado de Programación
- **Institución:** Duoc UC
- **Estudiante:** David Vásquez
- **Fecha:** 2026-06-20
- **Versión:** 1.0

---

## Contexto

Este documento registra las colecciones MongoDB nuevas o significativamente modificadas desde EP2. Las tablas PostgreSQL (identidad, roles, permisos) están documentadas en:

> `Documentacion/MER/modelo-logico-actualizacion-backend-2026-05-14-rbac.md`

Todos los campos aquí documentados corresponden al estado de la base de datos al cierre de EP3 (2026-06-20).

---

## 1. Colección: `alertRules`

### Propósito

Almacena las reglas de alerta configurables por región. Cada regla define uno o más umbrales para variables de riesgo (FWI, NDMI, NDVI, FIRMS, reportes ciudadanos). Cuando algún indicador supera su umbral, el sistema genera una `Alert`.

### Campos principales

| Campo | Tipo | Descripción |
|---|---|---|
| `_id` | ObjectId | Identificador único generado por MongoDB |
| `nombre` | String | Nombre descriptivo de la regla (ej. "Alerta Araucanía FWI alto") |
| `regionId` | String | Referencia a la región monitoreada (`Region._id`) |
| `umbralFwi` | Double | Umbral de Fire Weather Index; se dispara cuando `FWI >= umbralFwi` |
| `umbralNdmi` | Double | Umbral de Normalized Difference Moisture Index; se dispara cuando `NDMI <= umbralNdmi` |
| `umbralNdvi` | Double | Umbral de Normalized Difference Vegetation Index; se dispara cuando `NDVI <= umbralNdvi` |
| `umbralFirmsCount` | Integer | Umbral de focos activos FIRMS; se dispara cuando `count >= umbralFirmsCount` |
| `umbralReportesCiudadanos` | Integer | Umbral de reportes ciudadanos activos; se dispara cuando `count >= umbralReportesCiudadanos` |
| `activa` | Boolean | Indica si la regla está habilitada para evaluación en el ciclo de sync |
| `createdAt` | DateTime | Fecha y hora de creación de la regla |
| `updatedAt` | DateTime | Fecha y hora de la última modificación |

### Índices relevantes

| Índice | Campos | Tipo |
|---|---|---|
| Por región activa | `{ regionId: 1, activa: 1 }` | Compuesto |

### Relaciones

- `regionId` → `Region._id` (colección `regions`)
- Una `AlertRule` puede disparar múltiples `Alert` en el tiempo

---

## 2. Colección: `alerts`

### Propósito

Registra las alertas generadas automáticamente cuando el sistema detecta que alguna regla de alerta ha sido superada durante un ciclo de evaluación. Cada alerta tiene un nivel de severidad y puede referenciar una o más comunas afectadas.

### Campos principales

| Campo | Tipo | Descripción |
|---|---|---|
| `_id` | ObjectId | Identificador único |
| `level` | String (enum) | Nivel de severidad: `PREVENTIVO`, `ALTO`, `CRÍTICO` |
| `regionId` | String | Región donde se generó la alerta |
| `comunaId` | String | Comuna específica afectada (puede ser nulo si la alerta es regional) |
| `message` | String | Mensaje descriptivo generado automáticamente por el sistema |
| `ruleId` | String | Referencia a la `AlertRule` que disparó esta alerta |
| `acknowledged` | Boolean | Si un operador marcó la alerta como revisada |
| `createdAt` | DateTime | Fecha y hora de generación de la alerta |

### Índices relevantes

| Índice | Campos | Tipo |
|---|---|---|
| Por región y fecha | `{ regionId: 1, createdAt: -1 }` | Compuesto |
| Por nivel | `{ level: 1 }` | Simple |

### Relaciones

- `regionId` → `Region._id`
- `ruleId` → `AlertRule._id` (colección `alertRules`)
- Una `Alert` puede generar una o más `Notification`

---

## 3. Colección: `notifications`

### Propósito

Almacena las notificaciones dirigidas a usuarios específicos o a roles. Se generan como consecuencia de alertas activas o de eventos del sistema (ej. sync completado, regla actualizada). Los usuarios las ven en la barra de notificaciones de la aplicación.

### Campos principales

| Campo | Tipo | Descripción |
|---|---|---|
| `_id` | ObjectId | Identificador único |
| `title` | String | Título breve de la notificación |
| `body` | String | Cuerpo del mensaje con el detalle |
| `type` | String (enum) | Tipo: `ALERT`, `SYNC_COMPLETE`, `SYSTEM`, `INFO` |
| `read` | Boolean | Indica si el usuario ya leyó la notificación |
| `createdAt` | DateTime | Fecha y hora de creación |
| `regionId` | String | Región relacionada (puede ser nulo para notificaciones globales) |
| `userId` | Long | ID del usuario destinatario (referencia a `User.id` en PostgreSQL) |
| `alertId` | String | Referencia a la alerta que originó la notificación (puede ser nulo) |

### Índices relevantes

| Índice | Campos | Tipo |
|---|---|---|
| Por usuario y lectura | `{ userId: 1, read: 1, createdAt: -1 }` | Compuesto |
| Por región | `{ regionId: 1, createdAt: -1 }` | Compuesto |

### Relaciones

- `userId` → `User.id` (tabla PostgreSQL `users`)
- `alertId` → `Alert._id` (colección `alerts`)
- `regionId` → `Region._id` (colección `regions`)

---

## 4. Colección: `comunaRiskScores`

### Propósito

Almacena el score de riesgo WLC (Weighted Linear Combination) calculado por comuna. Cada documento representa el estado de riesgo más reciente de una comuna, con el desglose por componente y el modo de cálculo (STANDARD o ENHANCED según disponibilidad de datos Copernicus).

### Campos principales

| Campo | Tipo | Descripción |
|---|---|---|
| `_id` | ObjectId | Identificador interno |
| `gadmGid` | String | Identificador de la unidad comunal según GADM (ej. `CHL.9.1_1`) |
| `nombreComuna` | String | Nombre legible de la comuna |
| `regionId` | String | Referencia a la región a la que pertenece la comuna |
| `scoreComposite` | Double | Score WLC compuesto (0.0 – 1.0); resultado de `Σ(component.score × component.weight)` |
| `alertLevel` | String (enum) | Nivel de alerta derivado del score: `NORMAL`, `PREVENTIVO`, `ALTO`, `CRÍTICO` |
| `mode` | String (enum) | Modo de cálculo: `STANDARD` (sin Copernicus) o `ENHANCED` (con Copernicus) |
| `components` | Map | Mapa de componentes por variable (ver estructura abajo) |
| `computedAt` | DateTime | Timestamp del último cálculo del score |
| `copernicusSyncedAt` | DateTime | Timestamp del último sync con Copernicus/OpenEO (nulo si no hay sync) |

**Estructura de `components`:**

```json
{
  "FWI":      { "score": 0.72, "weight": 0.30, "rawValue": 18.5 },
  "NDMI":     { "score": 0.45, "weight": 0.25, "rawValue": -0.12 },
  "NDVI":     { "score": 0.38, "weight": 0.20, "rawValue": 0.41 },
  "FIRMS":    { "score": 0.60, "weight": 0.15, "focosCount": 3 },
  "REPORTS":  { "score": 0.20, "weight": 0.10, "count": 1 }
}
```

### Índices relevantes

| Índice | Campos | Tipo |
|---|---|---|
| Por región y nivel | `{ regionId: 1, alertLevel: 1 }` | Compuesto |
| Por gadmGid | `{ gadmGid: 1 }` | Único |

### Relaciones

- `regionId` → `Region._id` (colección `regions`)
- Alimenta el cálculo de `RegionalRiskScore`
- Se usa para colorear el choropleth comunal en el mapa territorial

---

## 5. Colección: `regionalRiskScores`

### Propósito

Almacena el score de riesgo consolidado a nivel regional, calculado como agregación de los `ComunaRiskScore` de todas las comunas de la región. Se usa para el choropleth regional, los KPIs del dashboard analítico y el sistema de alertas.

### Campos principales

| Campo | Tipo | Descripción |
|---|---|---|
| `_id` | ObjectId | Identificador interno |
| `regionId` | String | Referencia a la región (`Region._id`) |
| `scoreComposite` | Double | Score regional consolidado (0.0 – 1.0) |
| `alertLevel` | String (enum) | Nivel de alerta de la región: `NORMAL`, `PREVENTIVO`, `ALTO`, `CRÍTICO` |
| `comunasCount` | Integer | Total de comunas incluidas en el cálculo |
| `comunasEnAlto` | Integer | Cantidad de comunas con nivel ALTO |
| `comunasEnCritico` | Integer | Cantidad de comunas con nivel CRÍTICO |
| `generatedAt` | DateTime | Timestamp de la última generación del score regional |

### Índices relevantes

| Índice | Campos | Tipo |
|---|---|---|
| Por regionId y fecha | `{ regionId: 1, generatedAt: -1 }` | Compuesto |
| Por nivel de alerta | `{ alertLevel: 1 }` | Simple |

### Relaciones

- `regionId` → `Region._id` (colección `regions`)
- Se calcula a partir de los documentos de `comunaRiskScores` correspondientes a la región
- Los KPIs `totalAlertas` del dashboard se obtienen de este score (`comunasEnAlto + comunasEnCritico`)

---

## Referencia de esquema PostgreSQL

Las tablas PostgreSQL nuevas desde EP2 están documentadas en:

> `Documentacion/MER/modelo-logico-actualizacion-backend-2026-05-14-rbac.md`

Incluyen: `users`, `user_roles`, `roles`, `permissions`, `role_permissions`, `refresh_tokens`.

---

## Resumen de colecciones MongoDB al cierre de EP3

| Colección | Estado | Propósito |
|---|---|---|
| `regions` | Existente (actualizada) | Regiones monitoreadas con `monitoringEnabled` |
| `alertRules` | Nueva | Reglas de alerta configurables por región |
| `alerts` | Nueva | Alertas generadas automáticamente |
| `notifications` | Nueva | Notificaciones para usuarios |
| `comunaRiskScores` | Nueva | Score WLC por comuna con componentes |
| `regionalRiskScores` | Nueva | Score regional consolidado |
| `communityPosts` | Existente | Publicaciones del tablero comunitario |
| `communityContacts` | Existente (corregida) | Agenda de contactos con `comunaId` |
| `communityResources` | Existente | Biblioteca de recursos (solo PDF desde EP3) |
| `citizenReports` | Existente | Reportes ciudadanos de incidentes |
| `forestLossRecords` | Existente | Registros históricos de pérdida forestal |
| `firmsDetections` | Existente | Focos activos sincronizados desde NASA FIRMS |
| `weatherReadings` | Existente | Lecturas meteorológicas (FWI, viento, humedad) |
