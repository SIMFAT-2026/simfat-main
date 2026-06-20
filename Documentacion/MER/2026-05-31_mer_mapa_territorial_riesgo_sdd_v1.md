# MER - Mapa Territorial de Riesgo SDD v1

Fecha: 2026-05-31
Cambio SDD: `mapa-territorial-riesgo`

## Nuevas colecciones MongoDB

### territory_weather_observations

Observaciones meteorológicas FWI por región y fecha.

| Campo | Tipo | Descripción |
|---|---|---|
| _id | String | ObjectId generado por MongoDB |
| regionId | String | ID de la región piloto (biobio, araucania) |
| observedAt | DateTime | Fecha/hora de la observación |
| source | String | Fuente: `open-meteo` |
| fwi | Double | Fire Weather Index compuesto |
| ffmc | Double | Fine Fuel Moisture Code (null si no disponible) |
| dmc | Double | Duff Moisture Code |
| dc | Double | Drought Code |
| isi | Double | Initial Spread Index |
| bui | Double | Build-up Index |
| dsr | Double | Daily Severity Rating |
| lat | Double | Latitud del punto de consulta (centro del bbox) |
| lon | Double | Longitud del punto de consulta |
| ingestedAt | DateTime | Fecha de ingesta al sistema |

**Índices:** `regionId + observedAt DESC` (búsqueda de última observación), unique `regionId + observedAt`

### territory_risk_snapshots

Snapshots del score de riesgo compuesto WLC por región.

| Campo | Tipo | Descripción |
|---|---|---|
| _id | String | ObjectId |
| regionId | String | ID de la región |
| computedAt | DateTime | Fecha/hora del cálculo |
| scoreComposite | Double | Score WLC normalizado [0.0, 1.0] |
| alertLevel | String | NORMAL / PREVENTIVO / ALTO / CRITICO |
| qualityFlag | String | OK / PARTIAL / MINIMAL / NO_DATA |
| componentFwi | Double | Contribución FWI al score (peso 0.38) |
| componentNdmi | Double | Contribución NDMI al score (peso 0.22) |
| componentFirms | Double | Contribución FIRMS al score (peso 0.18) |
| componentLoss | Double | Contribución pérdida forestal (peso 0.10) |
| componentNdvi | Double | Contribución NDVI al score (peso 0.08) |
| componentReports | Double | Contribución reportes ciudadanos (peso 0.04) |
| fwiRaw | Double | Valor raw del FWI |
| ndmiRaw | Double | Valor raw del NDMI |
| ndviRaw | Double | Valor raw del NDVI |
| firmsCount | Integer | Focos activos confidence ≥ nominal (48h) |
| firmsFrpMean | Double | FRP promedio de focos activos (MW) |
| lossRateRaw | Double | Tasa de pérdida forestal histórica máxima |
| reportsCount | Integer | Reportes ciudadanos últimos 7 días |

**Índices:** `regionId + computedAt DESC`

## Colecciones existentes extendidas

### heat_alert_events

Campos nuevos agregados para focos FIRMS:

| Campo | Tipo | Descripción |
|---|---|---|
| firmsConfidence | String | `l` (low), `n` (nominal), `h` (high) |
| firmsFrp | Double | Fire Radiative Power en MW |
| firmsSatellite | String | `N` (NOAA-20), `S` (Suomi NPP) |
| firmsSource | String | `VIIRS_NOAA20_NRT` |

### regions (MongoDB)

Campo nuevo:

| Campo | Tipo | Descripción |
|---|---|---|
| aoiBbox | List\<Double\> | [west, south, east, north] — bbox para consultas externas |

## Regiones piloto configuradas

| regionId | codigo | aoiBbox |
|---|---|---|
| biobio | BIOBIO | [-74.1, -38.9, -71.0, -36.3] |
| araucania | ARAUCANIA | [-73.9, -39.8, -71.2, -37.8] |

## Relaciones

```
regions
  └── territory_weather_observations (regionId)
  └── territory_risk_snapshots (regionId)
  └── heat_alert_events (regionId) ← incluye focos FIRMS

openeo_indicator_observations (NDVI, NDMI) ──┐
territory_weather_observations (FWI) ─────────┤
heat_alert_events (FIRMS) ────────────────────┤──► territory_risk_snapshots
forest_loss_records (pérdida) ────────────────┤
citizen_reports (reportes) ───────────────────┘
```
