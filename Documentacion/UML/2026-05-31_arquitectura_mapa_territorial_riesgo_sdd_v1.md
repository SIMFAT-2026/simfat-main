# Arquitectura - Mapa Territorial de Riesgo SDD v1

Fecha: 2026-05-31
Cambio SDD: `mapa-territorial-riesgo`

## Componentes

### Backend (Spring Boot)

```
NasaFirmsServiceImpl
  - @Scheduled cada 12h (cron configurable via FIRMS_SYNC_CRON)
  - HTTP GET a firms.modaps.eosdis.nasa.gov/api/area/json/
  - Filtra confidence=low, persiste en HeatAlertEvent con campos FIRMS
  - HttpClient static final (reutilizado entre llamadas)

OpenWeatherFwiServiceImpl (usa Open-Meteo, sin API key)
  - @Scheduled cada 12h (cron configurable via OPENMETEO_SYNC_CRON)
  - HTTP GET a api.open-meteo.com/v1/forecast?daily=fire_danger_index
  - Persiste en TerritoryWeatherObservation

TerritoryRiskServiceImpl
  - @Scheduled diario 01:00 UTC (configurable via TERRITORY_RISK_CRON)
  - Lee: TerritoryWeatherObservation, OpenEoIndicatorObservation,
         HeatAlertEvent (FIRMS), ForestLossRecord, CitizenReport
  - Calcula WLC normalizado, determina alertLevel por umbrales individuales
  - Persiste TerritoryRiskSnapshot

MonitoredRegionsConfig
  - @EventListener(ApplicationReadyEvent)
  - Upserta biobio y araucania con aoiBbox al arranque
```

### Endpoints nuevos

```
GET  /api/territory/risk-score/{regionId}
     Auth: ROLE_VERIFIED_USER+
     Retorna último TerritoryRiskSnapshot con breakdown de componentes

POST /api/territory/sync?regionId=
     Auth: ROLE_ADMIN+
     Ejecuta en secuencia: FIRMS sync → FWI sync → WLC recompute

GET  /api/territory/layers?regionId=&indicators=FIRMS,RISK_SCORE,...
     Nuevas capas: FIRMS (focos activos) y RISK_SCORE (score por región)
```

### Frontend (React)

```
territoryApiService.js
  fetchTerritoryLayers()     — incluye FIRMS y RISK_SCORE
  fetchTerritoryRiskScore()  — endpoint dedicado para breakdown

useTerritoryLayers.js
  — fetch paralelo: bounds + layers + riskScore
  — fallback silencioso a mock si riskScore falla

TerritoryMapPanel.jsx
  RiskScoreBadge
    — usa riskScore.components para barras de contribución
    — fallback a feature RISK_SCORE de layers si no hay endpoint data
  
  toPointStyle(indicator, feature)
    — FIRMS: radio proporcional a FRP, color por confidence (naranja/rojo)
  
  featureMeta(feature)
    — FIRMS: muestra FRP en MW, confianza, timestamp de detección
```

## Flujo de datos

```
[Copernicus CDSE]                    [NASA FIRMS API]     [Open-Meteo API]
       |                                    |                    |
[openeo-service]                    [NasaFirmsService]  [OpenWeatherFwiService]
       |                                    |                    |
[OpenEoSyncService]            [heat_alert_events]  [territory_weather_obs]
       |                                    |                    |
[openeo_indicator_observations]             └──────────┬─────────┘
                                                       ▼
                                           [TerritoryRiskService]
                                           WLC: FWI(38%) NDMI(22%)
                                                FIRMS(18%) LOSS(10%)
                                                NDVI(8%) REP(4%)
                                                       │
                                           [territory_risk_snapshots]
                                                       │
                                           [TerritoryController]
                                           GET /risk-score/{id}
                                           POST /sync
                                           GET /layers (RISK_SCORE, FIRMS)
                                                       │
                                           [React TerritoryMapPanel]
                                           RiskScoreBadge + breakdown
```

## Niveles de alerta y umbrales

| Nivel | Score | FWI | FIRMS |
|---|---|---|---|
| NORMAL | < 0.40 | < 20 | sin focos |
| PREVENTIVO | ≥ 0.50 | ≥ 20 | — |
| ALTO | ≥ 0.70 | ≥ 30 | foco confidence=nominal |
| CRITICO | ≥ 0.85 | ≥ 45 | foco confidence=high (inmediato) |

El nivel final es el máximo entre el score y los umbrales individuales.

## Decisiones de arquitectura

| Decisión | Razón |
|---|---|
| FIRMS → backend Java directo | API REST simple, sin procesamiento geoespacial. `HeatAlertEvent` ya existía. |
| FWI → Open-Meteo (sin API key) | Gratuito, sin registro, provee `fire_danger_index` CFWI por lat/lon. |
| WLC pesos iniciales configurables | No hardcodeados — parámetros ajustables sin redeployment. |
| Crons configurables via env vars | `FIRMS_SYNC_CRON`, `OPENMETEO_SYNC_CRON`, `TERRITORY_RISK_CRON`. |
| Regiones upsertadas al arranque | `MonitoredRegionsConfig` garantiza que biobio/araucania existan con bbox. |
| `prepareThreshold=0` | Compatibilidad con PgBouncer transaction mode de Supabase (puerto 6543). |
| `forward-headers-strategy=NATIVE` | Railway actúa como reverse proxy HTTPS — Spring necesita respetar X-Forwarded-Proto para generar URLs correctas en Swagger. |
