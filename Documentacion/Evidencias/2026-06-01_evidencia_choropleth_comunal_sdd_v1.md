# Evidencia de pruebas - Choropleth comunal de riesgo SDD v1

Fecha: 2026-06-01
Cambio SDD: `mapa-territorial-riesgo` (extensión comunal)
Branch: `main`

## Resumen ejecutivo

El mapa territorial evolucionó de puntos sueltos a un choropleth comunal completo con 86 comunas
(Biobío 33, Ñuble 21, Araucanía 32) coloreadas por nivel de riesgo meteorológico real. Los datos
provienen de Open-Meteo (proxy FWI por centroide comunal) procesados con el motor WLC adaptado.

## Evidencia automatizada

| Capa | Comando | Resultado |
|---|---|---|
| Frontend React/Vite | `npm run lint` | PASS — ESLint sin warnings |
| Frontend React/Vite | `npm run build` | PASS — Vite 8 build correcto |

## Evidencia de integración en producción

| Verificación | Estado | Detalle |
|---|---|---|
| 86 comunas sembradas en MongoDB al arranque | ✅ | `monitored_comunas status=ok regionId=biobio upserted=54` y `araucania upserted=32` |
| FWI calculado para cada centroide comunal | ✅ | `fwi_api status=ok` para todos los 86 GIDs |
| Score WLC calculado para 86 comunas | ✅ | `comuna_risk_recompute status=done ok=86 errors=0` |
| GeoJSON GADM accesible con CORS | ✅ | `GET /geojson/comunas-biobio.geojson` → 200 con CORS headers |
| Endpoint scores comunales | ✅ | `GET /api/territory/risk-score/comunas/biobio` → 200 |
| Choropleth visible en producción | ✅ | Comunas coloreadas verde/naranja según nivel de riesgo |
| Diferenciación geográfica real | ✅ | Interior: FWI 25-29 (PREVENTIVO/ALTO), Costa: FWI 11-15 (NORMAL) |

## Resultado de sync manual verificado (producción)

```
fwi_api status=ok regionId=CHL.6.2.1_1 temp=15.9 rh=20.0 wind=6.7 precip=0.0 proxyFwi=28.37
fwi_api status=ok regionId=CHL.6.1.1_1 temp=12.6 rh=81.0 wind=19.9 precip=0.4 proxyFwi=14.04
fwi_api status=ok regionId=CHL.13.3.4_1 temp=21.7 rh=27.0 wind=7.6 precip=0.0 proxyFwi=29.57
comuna_risk_recompute status=done ok=86 errors=0
```

La diferencia de humedad entre costa (81%) e interior (20%) genera variación real de colores en
el choropleth — exactamente el gradiente meteorológico esperado para otoño en Biobío/Araucanía.

## Problemas encontrados y resueltos

| Problema | Causa | Resolución |
|---|---|---|
| CORS 403 en `/geojson/` | `CorsConfig` solo cubría `/api/**`, no recursos estáticos | Agregar mapping `/geojson/**` con `allowCredentials(true)` |
| GeoJSON classpath `file_not_found` | Ruta `geojson/...` incorrecta en JAR; debía ser `static/geojson/...` | Corregir prefijo en `MonitoredComunasConfig` |
| Map zoom incorrecto (mostraba Sudamérica) | `MapContainer` de react-leaflet inicializa una sola vez; `fitBounds` sin `maxZoom` | Agregar `key={regionData.regionId}` + `maxZoom: 10` en `FitRegionBounds` |
| FIRMS 400 en regiones seed ficticias | Las regiones `SIM-RA-01` no tienen bbox válido para FIRMS | Aceptado — solo biobio/araucania tienen bbox, las ficticias se ignoran |

## Iteraciones implementadas

### Iteración 1 — GeoJSON y semilla comunal
- Descarga y procesamiento GADM 4.1 Level 3 Chile (filtrado Biobío, Ñuble, Araucanía)
- Simplificación a 4 decimales: biobio 238KB, araucania 160KB
- Assets estáticos en `src/main/resources/static/geojson/`
- `ComunaInfo` + `ComunaInfoRepository` + `MonitoredComunasConfig`
- CORS habilitado para `/geojson/**`
- Frontend: fetch GeoJSON y renderizado de polígonos

### Iteración 2 — Score comunal con FWI real
- `ComunaRiskSnapshot` + `ComunaRiskSnapshotRepository`
- `ComunaRiskService`: FWI Open-Meteo por centroide + FIRMS por distancia + reportes
- Weights: FWI(52%) + FIRMS(33%) + Reportes(15%) — modo STANDARD sin Copernicus
- `GET /api/territory/risk-score/comunas/{regionId}`
- `POST /api/territory/sync` extendido para lanzar recompute comunal en background
- Frontend: `ComunaChoropleth` con colores por `alertLevel`

## Pendientes documentados

- Copernicus ENHANCED mode (NDVI/NDMI condicional cuando score ≥ PREVENTIVO)
- Choropleth para región Biobío al seleccionar: ambas regiones muestran sus comunas
- Integración Copernicus condicional documentada como próxima iteración
