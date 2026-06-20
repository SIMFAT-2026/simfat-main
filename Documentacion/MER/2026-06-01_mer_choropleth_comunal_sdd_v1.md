# MER - Choropleth Comunal de Riesgo SDD v1

Fecha: 2026-06-01
Cambio SDD: `mapa-territorial-riesgo` (extensión comunal)

## Nuevas colecciones MongoDB

### comunas

Catálogo de las 86 comunas piloto, sembrado desde GeoJSON GADM 4.1 al arranque.

| Campo | Tipo | Descripción |
|---|---|---|
| _id | String | GID_3 de GADM (ej. `CHL.6.1.1_1`) |
| nombre | String | Nombre de la comuna (ej. `Arauco`) |
| provincia | String | Nombre de la provincia (ej. `Arauco`) |
| regionId | String | ID de la región piloto (`biobio` / `araucania`) |
| regionGadm | String | Nombre según GADM (`Bío-Bío`, `Ñuble`, `Araucanía`) |
| gadmGid | String | Igual a `_id` (redundante para queries por GID) |
| centerLat | Double | Latitud del centroide del polígono |
| centerLon | Double | Longitud del centroide del polígono |

**Índice:** `regionId` (para obtener todas las comunas de una región)

**Fuente de datos:** GADM 4.1 Level 3, gadm.org, licencia libre para uso académico.
Biobío = `NAME_1 IN ('Bío-Bío', 'Ñuble')` (33 + 21 = 54 comunas)
Araucanía = `NAME_1 = 'Araucanía'` (32 comunas)

### comuna_risk_snapshots

Snapshots del score de riesgo WLC calculado por comuna.

| Campo | Tipo | Descripción |
|---|---|---|
| _id | String | ObjectId |
| comunaId | String | GID_3 de GADM — FK a `comunas._id` |
| regionId | String | `biobio` / `araucania` |
| nombreComuna | String | Nombre denormalizado para queries rápidas |
| computedAt | DateTime | Fecha/hora del cálculo |
| scoreComposite | Double | Score WLC normalizado [0.0, 1.0] |
| alertLevel | String | NORMAL / PREVENTIVO / ALTO / CRITICO |
| qualityFlag | String | STANDARD (sin Copernicus) / ENHANCED (con NDVI/NDMI) |
| componentFwi | Double | Contribución FWI al score (peso 0.52 modo STANDARD) |
| componentFirms | Double | Contribución FIRMS (peso 0.33) |
| componentReports | Double | Contribución reportes (peso 0.15) |
| componentNdmi | Double | Solo modo ENHANCED (peso 0.22) |
| componentNdvi | Double | Solo modo ENHANCED (peso 0.08) |
| fwiRaw | Double | Proxy FWI calculado (escala 0-60) |
| firmsCount | Integer | Focos activos VIIRS confidence ≥ nominal (48h) |
| firmsFrpMean | Double | FRP promedio de focos activos (MW) |
| reportsCount | Integer | Reportes ciudadanos últimos 7 días en la región |
| ndmiRaw | Double | Solo modo ENHANCED |
| ndviRaw | Double | Solo modo ENHANCED |

**Índices:**
- `comunaId + computedAt DESC` (último snapshot por comuna)
- `regionId + computedAt DESC` (todos los snapshots de una región)

## Assets estáticos

Los archivos GeoJSON de comunas se sirven como recursos estáticos de Spring Boot:

| Archivo | Ruta en classpath | URL de acceso | Tamaño |
|---|---|---|---|
| `comunas-biobio.geojson` | `static/geojson/comunas-biobio.geojson` | `/geojson/comunas-biobio.geojson` | 238 KB |
| `comunas-araucania.geojson` | `static/geojson/comunas-araucania.geojson` | `/geojson/comunas-araucania.geojson` | 160 KB |

Cada feature GeoJSON contiene: `comunaId`, `nombre`, `provincia`, `regionGadm`, `centerLat`, `centerLon`.

## Relaciones

```
comunas
  └── comuna_risk_snapshots (comunaId)

comunas.regionId → regions._id (biobio / araucania)

territory_weather_observations.regionId = comunas._id
  (FWI por centroide de cada comuna)

heat_alert_events (FIRMS) → asignados a comunas por distancia-al-centroide
citizen_reports → agrupados por regionId para cómputo comunal
```

## Proxy FWI — fórmula documentada

```
tempFactor = max(0, min(1, temp_max / 40.0))
drynessFactor = max(0, (100 - rh_min) / 100.0)
windFactor = max(0, min(1, wind_max / 60.0))
rainFactor = max(0, 1 - precip / 3.0)

proxyFwi = 60.0 * (0.40 * dryness + 0.30 * temp + 0.30 * wind) * rain
```

Escala 0-60 (análoga a CFWI). Fuente: variables Open-Meteo free tier.
Documentado como aproximación MVP — sujeto a calibración con datos históricos.
