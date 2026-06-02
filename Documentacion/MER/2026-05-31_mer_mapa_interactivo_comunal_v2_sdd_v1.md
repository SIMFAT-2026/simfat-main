# MER — Mapa Interactivo de Riesgo Comunal v2

Fecha: 2026-05-31
Cambio SDD: `mapa-interactivo-comunal-v2`

---

## Cambios en modelo de datos

### 1. ComunaRiskSnapshot (MongoDB) — campos nuevos

Coleccion existente `comuna_risk_snapshots`. Se agregan campos a documentos nuevos; documentos historicos sin estos campos siguen siendo validos (STANDARD implicito).

```
mode                 String   -- 'STANDARD' | 'ENHANCED'   (nuevo)
qualityFlag          String   -- null | 'PARTIAL' | 'COPERNICUS_UNAVAILABLE'   (nuevo)
componentNdmi        Double   -- score normalizado [0,1] NDMI (null si STANDARD)   (nuevo)
componentNdvi        Double   -- score normalizado [0,1] NDVI (null si STANDARD)   (nuevo)
componentLoss        Double   -- score normalizado [0,1] perdida cobertura (null si STANDARD)   (nuevo)
ndmiRaw              Double   -- valor Sentinel-2 crudo   (nuevo)
ndviRaw              Double   -- valor Sentinel-2 crudo   (nuevo)
openeoObservationId  String   -- ref a OpenEoIndicatorObservation._id   (nuevo)
```

Campos existentes sin cambios:
```
_id, gadmGid, comunaNombre, regionId, computedAt,
scoreComposite, alertLevel,
componentFwi, componentFirms, componentReports,
fwiRaw, firmsCount, firmsFrpMean, reportsCount
```

### 2. Region (MongoDB) — campo a poblar en seed

Campo `aoiBbox` ya existe en el modelo. Se pobla en seed al arranque si esta vacio.

```
aoiBbox   List<Double>   -- [west, south, east, north] en EPSG:4326
```

Valores para las regiones piloto:
- biobio:    `[-73.6, -38.5, -71.0, -36.7]`
- araucania: `[-73.6, -40.0, -70.8, -37.6]`

### 3. Relaciones relevantes

```
ComunaInfo (comunas)
  regionId → Region._id

ComunaRiskSnapshot (comuna_risk_snapshots)
  gadmGid → ComunaInfo.gadmGid
  openeoObservationId → OpenEoIndicatorObservation._id  [nuevo]

OpenEoIndicatorObservation (openeo_indicator_observations)
  regionId → Region._id
  indicator: NDVI | NDMI | ...
```

### 4. Nuevo endpoint — sin cambios en BD

`GET /api/territory/risk-score/comunas/{gadmGid}/history?days=N`
Consulta sobre `ComunaRiskSnapshot` filtrando por `gadmGid` y rango de fechas en `computedAt`. No requiere nueva coleccion.

---

## Sin cambios

- `OpenEoIndicatorObservation`: no se modifica — se consume tal cual existe.
- `ComunaInfo`: no se modifica — ya tiene `gadmGid`, `centerLat`, `centerLon`, `regionId`.
- `HeatAlertEvent`, `CitizenReport`, `AlertRule`: sin cambios en este sprint.
