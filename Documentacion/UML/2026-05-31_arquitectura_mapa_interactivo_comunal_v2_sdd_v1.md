# Arquitectura — Mapa Interactivo de Riesgo Comunal v2

Fecha: 2026-05-31
Cambio SDD: `mapa-interactivo-comunal-v2`

---

## Flujo modo ENHANCED Copernicus

```
[Sync diario openeo-service]
        |
        v
[OpenEoIndicatorObservation]  ←── ya existente en MongoDB
        |
        | read latest(regionId, NDVI, NDMI, observedAt <= 6 dias)
        v
[ComunaRiskService.computeScore(comunaInfo)]
        |
        |-- score_standard = FWI(52%) + FIRMS(33%) + Reportes(15%)
        |
        |-- si score_standard >= 0.50 AND observacion valida:
        |       score_enhanced = FWI(38%) + NDMI(22%) + FIRMS(18%)
        |                      + Loss(10%) + NDVI(8%) + Reportes(4%)
        |       mode = ENHANCED
        |
        |-- sino:
        |       score = score_standard
        |       mode = STANDARD
        |       qualityFlag = COPERNICUS_UNAVAILABLE (si falta dato)
        |
        v
[ComunaRiskSnapshot] persistido en MongoDB
        |
        v
[GET /api/territory/risk-score/comunas/{gadmGid}]   ← extendido con mode/qualityFlag/components
[GET /api/territory/risk-score/comunas/{gadmGid}/history]  ← nuevo
```

---

## Flujo seed bbox regional

```
[Arranque Spring Boot]
        |
        v
[MonitoredRegionsConfig o RegionSeedService]
        |
        |-- para cada Region(biobio, araucania):
        |       si Region.aoiBbox == null:
        |           leer simfat.openeo.region-bbox-defaults[regionId]
        |           Region.aoiBbox = defaults
        |           regionRepository.save(region)
        |
        v
[OpenEoSyncServiceImpl.resolveBoundingBox(region)]
        |   lee Region.aoiBbox (ya poblado)
        v
[openeo-service] recibe bbox real en AoiRequest
```

---

## Componentes frontend nuevos

```
TerritoryMapPanel.jsx  (existente)
    |
    |── ComunaChoropleth.jsx  (existente)
    |       onHover → ComunaTooltip.jsx   [nuevo]
    |       onClick → ComunaRiskPanel.jsx [nuevo]
    |
    └── ComunaRiskPanel.jsx  [nuevo]
            |── header: nombre, region, nivel badge, computedAt
            |── badge STANDARD / ENHANCED
            |── tabla ComponentesWLC
            |── RiskHistoryChart.jsx  [nuevo]  ← Recharts LineChart
            |── boton SyncNow (ROLE_ADMIN only)
```

---

## Secuencia tooltip al hover

```
Usuario hover sobre poligono comunal
        |
        v
ComunaChoropleth.onHover(gadmGid)
        |   datos ya en memoria (cargados con GeoJSON)
        v
ComunaTooltip renderiza:
    - nombre comuna
    - nivel badge (color segun alertLevel)
    - barra score: scoreComposite
    - top-2 componentes por peso (segun mode)
```

---

## Secuencia panel lateral al click

```
Usuario click sobre poligono comunal
        |
        v
ComunaChoropleth.onClick(gadmGid)
        |
        v
fetch GET /api/territory/risk-score/comunas/{gadmGid}
        |
        v
ComunaRiskPanel renderiza ficha completa
        |
        v
fetch GET /api/territory/risk-score/comunas/{gadmGid}/history?days=7
        |
        v
RiskHistoryChart renderiza evolucion temporal
```

---

## Componentes sin cambios

- `openeo-service` (FastAPI): sin modificaciones — ya acepta bbox en AoiRequest
- `OpenEoSyncServiceImpl`: sin modificaciones en logica de sync regional
- `NasaFirmsService`, `OpenWeatherFwiService`: sin cambios
- `AlertRule`, `AlertEvent`: sin cambios en este sprint
