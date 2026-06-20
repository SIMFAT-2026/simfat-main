# Arquitectura - Choropleth Comunal de Riesgo SDD v1

Fecha: 2026-06-01
Cambio SDD: `mapa-territorial-riesgo` (extensión comunal)

## Nuevos componentes

### Backend

```
MonitoredComunasConfig (@EventListener ApplicationReadyEvent)
  - Siembra 86 comunas desde GeoJSON GADM en MongoDB al arranque
  - Fuente: static/geojson/comunas-{regionId}.geojson
  - Upsert por GID_3 — idempotente en cada restart

ComunaRiskServiceImpl
  @Scheduled(cron: 0 30 1,13 * * *) — 01:30 y 13:30 UTC
  Para cada comuna:
    1. Llama OpenWeatherFwiService.syncFwiByRegion(comunaId, lat, lon)
    2. Obtiene focos FIRMS del regionId, asigna por distancia-al-centroide
    3. Cuenta reportes ciudadanos (últimos 7 días, mismo regionId)
    4. Calcula score WLC y persiste ComunaRiskSnapshot
```

### Endpoints nuevos

```
GET /geojson/{regionId}
    → ClassPathResource("static/geojson/comunas-{regionId}.geojson")
    → Cache-Control: max-age=86400
    → CORS: allowedOrigins + allowCredentials=true
    → Sin autenticación

GET /api/territory/risk-score/comunas/{regionId}
    Auth: ROLE_VERIFIED_USER+
    → Map<comunaId, {alertLevel, scoreComposite, qualityFlag, fwiRaw, ...}>

POST /api/territory/sync?regionId= (extendido)
    Auth: ROLE_ADMIN+
    → FIRMS sync + FWI sync + risk regional recompute (síncrono)
    → ComunaRiskService.recomputeAllComunas() en background thread
```

### Frontend

```
fetchTerritoryGeoJson(regionId)
  → GET /geojson/comunas-{regionId}.geojson
  → Retorna GeoJSON FeatureCollection

fetchComunalRiskScores(regionId)
  → GET /api/territory/risk-score/comunas/{regionId}
  → Retorna { [comunaId]: {alertLevel, scoreComposite, ...} }

useTerritoryLayers (actualizado)
  → fetch paralelo: bounds + layers + riskScore + comunalGeoJson + comunalScores
  → regionData incluye: comunalGeoJson, comunalScores

ComunaChoropleth (nuevo componente)
  → L.GeoJSON con style dinámico por alertLevel
  → fillColor: verde/amarillo/naranja/rojo según nivel
  → fillOpacity: 0.55 con datos, 0.15 sin datos
  → Popup: nombre + nivel + score + FWI raw
```

## Flujo completo de datos

```
Open-Meteo API (free, sin key)
  → ComunaRiskServiceImpl (centroide de cada comuna)
  → TerritoryWeatherObservation (regionId = comunaId)
  → ComunaRiskSnapshot.fwiRaw

NASA FIRMS VIIRS NOAA-20
  → NasaFirmsServiceImpl (bbox de la región piloto)
  → HeatAlertEvent
  → ComunaRiskServiceImpl (distancia-al-centroide → comunaId)
  → ComunaRiskSnapshot.firmsCount

ComunaRiskServiceImpl
  → score = FWI(52%) + FIRMS(33%) + Reportes(15%)
  → alertLevel = NORMAL/PREVENTIVO/ALTO/CRITICO
  → ComunaRiskSnapshot (86 documentos por ciclo)

Frontend (useTerritoryLayers)
  → comunalGeoJson: GET /geojson/comunas-biobio.geojson
  → comunalScores: GET /api/territory/risk-score/comunas/biobio
  → ComunaChoropleth → polígonos coloreados en Leaflet
```

## Decisiones de arquitectura documentadas

| Decisión | Razón |
|---|---|
| Sin JTS (no point-in-polygon exacto) | VIIRS ±375m de precisión; error en bordes no cambia decisión operacional; evita ~1MB dependencia |
| Distancia-al-centroide para FIRMS | Correcta >95% del tiempo; O(n×m) trivial para n<100 focos y m=86 comunas |
| GeoJSON como asset estático | Datos públicos GADM, no cambian — no necesita endpoint dinámico; cacheable 24h |
| Open-Meteo free tier | Sin API key, sin registro, datos meteorológicos suficientes para proxy FWI |
| regionId = comunaId en TerritoryWeatherObservation | Reutiliza tabla existente para FWI comunal sin migración adicional |
| FWI como proxy (no CFWI real) | CFWI requiere datos históricos para inicializar; proxy documentado como MVP válido |
| Weights STANDARD documentados como parámetros | FWI(52%) + FIRMS(33%) + Reportes(15%) son iniciales; ajustables sin redeployment |

## Resultado visual

El mapa muestra la diferenciación meteorológica real del 1 junio 2026:
- Costa Biobío (Arauco, Coronel): FWI ~14, humedad 81% → NORMAL (verde)
- Interior cordillerano (Biobío, Mulchén): FWI ~28, humedad 20-25% → PREVENTIVO/ALTO (naranja)
- Araucanía cordillerana (sector Lonquimay): FWI ~29, temp 21.7°C → ALTO (naranja)

Esto representa el gradiente meteorológico otoñal real de la zona piloto.

## Proximas iteraciones documentadas

1. **Copernicus ENHANCED**: si score_standard ≥ 0.50, disparar NDVI/NDMI para la comuna
   y recalcular con pesos ENHANCED (FWI 38%, NDMI 22%, FIRMS 18%, Cobertura 10%, NDVI 8%, Reportes 4%)
2. **Polígonos granularidad comunal IDE Chile**: fuente oficial BCN/SUBDERE con código CUT
3. **ML predictivo**: requiere acumulación de 60+ días de observaciones comunales
