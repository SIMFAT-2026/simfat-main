# Evidencia de pruebas - Mapa territorial de riesgo SDD v1

Fecha: 2026-05-31
Cambio SDD: `mapa-territorial-riesgo`
Branch: `main`

## Resumen ejecutivo

La especificacion de mapa territorial de riesgo de incendio fue implementada en tres iteraciones y verificada en build, integración con servicios externos y prueba manual en producción (Vercel + Railway). El badge de riesgo territorial quedó operativo con datos parciales a la espera del primer ciclo de sync completo.

## Evidencia automatizada

| Capa | Comando | Resultado |
|---|---|---|
| Frontend React/Vite | `npm run lint` | PASS — ESLint sin warnings |
| Frontend React/Vite | `npm run build` | PASS — Vite 8 build correcto, 780 módulos |

## Evidencia de integración en producción

| Verificación | Estado | Detalle |
|---|---|---|
| Backend Railway arranca limpio | ✅ | `Started SimfatBackendApplication in 18.9s` |
| MongoDB Atlas conectado | ✅ | Replica set south-america-east-1 |
| PostgreSQL Supabase conectado | ✅ | Flyway validated 3 migrations, schema at v3 |
| Regiones piloto con bbox creadas | ✅ | `monitored_regions status=upserted regionId=biobio` y `araucania` |
| Endpoint `/api/territory/sync` funciona | ✅ | `POST /api/territory/sync?regionId=biobio` retorna 200 con score |
| Badge de riesgo visible en producción | ✅ | Mapa territorio muestra nivel NORMAL con 6 componentes WLC |
| Capas FIRMS y RISK_SCORE en layers | ✅ | `GET /api/territory/layers` responde 200 con nuevas capas |
| Endpoint `/api/territory/risk-score/{regionId}` | ✅ | Retorna snapshot con breakdown de componentes |

## Respuesta de sync manual verificada

```json
{
  "success": true,
  "message": "Score de riesgo recalculado para biobio",
  "data": {
    "alertLevel": "NORMAL",
    "scoreComposite": 0.0,
    "regionId": "biobio"
  }
}
```

Nota: `scoreComposite=0.0` esperado — FIRMS y FWI pendientes de primer ciclo de sync (00:00/00:30 UTC). Score se actualizará automáticamente.

## Problemas encontrados y resueltos en el sprint

| Problema | Causa | Resolución |
|---|---|---|
| `setId` duplicado en Region.java | Edit tool aplicó setter dos veces | Eliminado el duplicado |
| `ERROR: prepared statement "S_N" already exists` | Supabase usa PgBouncer transaction mode (puerto 6543) — no soporta prepared statements | `spring.datasource.hikari.data-source-properties.prepareThreshold=0` |
| Swagger genera URLs `http://` | Spring no detecta proxy HTTPS de Railway | `server.forward-headers-strategy=NATIVE` |
| Regiones sin `aoiBbox` — sync FIRMS/FWI saltea | Seed existente crea regiones ficticias sin bbox | `MonitoredRegionsConfig` upserta biobio y araucania al arranque |
| `NEXT_PUBLIC_API_BASE_URL` dead code en Vite | Prefijo Next.js no inyectado por Vite | Eliminado, queda solo `VITE_API_URL` |

## Iteraciones implementadas

### Iteración 1 — Fuentes externas y motor WLC

- `TerritoryWeatherObservation` y `TerritoryRiskSnapshot` (modelos MongoDB)
- `NasaFirmsService`: sync VIIRS NOAA-20 cada 12h, filtra confidence=low
- `OpenWeatherFwiService`: sync Open-Meteo FWI cada 12h (sin API key)
- `TerritoryRiskService`: motor WLC con 6 variables, normalización min-max, 4 niveles de alerta
- `TerritoryController`: endpoints `/risk-score/{regionId}`, `/sync`, capas `FIRMS` y `RISK_SCORE`
- Frontend: `RiskScoreBadge` con breakdown de componentes, marcadores FIRMS con FRP proporcional

### Iteración 2 — Cablea OpenEO a Copernicus real

- Cron sync OpenEO cambiado de cada 15min a diario (00:00 UTC)
- `IndicatorService` placeholder eliminado (código muerto, flujo real usa `OpenEOProbeService`)

### Iteración 3 — Frontend end-to-end

- `territoryApiService`: normaliza capas FIRMS y RISK_SCORE, agrega `fetchTerritoryRiskScore`
- `useTerritoryLayers`: fetch paralelo del score detail con fallback silencioso
- Popup FIRMS con FRP en MW, confianza y timestamp de detección
- Leyenda actualizada con capa Focos

## Variables de entorno requeridas en producción

| Variable | Servicio | Estado |
|---|---|---|
| `FIRMS_MAP_KEY` | simfat-backend (Railway) | ✅ Configurada |
| `OPENEO_AOI_BBOX_MAP` | simfat-backend (Railway) | Pendiente |
| `OPENEO_REFRESH_TOKEN` | openeo-service (Railway) | Pendiente para sync satelital |

## Deuda técnica documentada

- Choropleth regional con polígonos GADM (deuda visual)
- Capa de viento animada con Open-Meteo + leaflet-velocity
- Calibración de pesos WLC con datos históricos acumulados
- Granularidad a nivel comunal (fuente IDE Chile)
- ML predictivo (requiere 60+ días de observaciones)
