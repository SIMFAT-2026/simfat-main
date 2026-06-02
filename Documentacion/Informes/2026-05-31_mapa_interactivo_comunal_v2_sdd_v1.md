# Mapa Interactivo de Riesgo Comunal v2 - SDD v1

Fecha: 2026-05-31
Cambio SDD: `mapa-interactivo-comunal-v2`
Modulo propietario: Territorio
Estado: especificacion aprobada — pendiente de implementacion

---

## 1. Objetivo

Evolucionar el choropleth de riesgo comunal desde snapshot estatico hacia un **panel analitico interactivo** que permita al coordinador territorial de las regiones Biobio (8va) y Araucania (9na):

1. Entender **por que** una comuna tiene su nivel de riesgo actual (breakdown WLC visible en el mapa)
2. Ver la **evolucion temporal** del score compuesto (historial de snapshots)
3. Activar la integracion **Copernicus ENHANCED** condicional cuando el score comunal supera el umbral PREVENTIVO, incorporando NDVI/NDMI al calculo WLC

Usuarios objetivo: coordinadores territoriales (ROLE_VERIFIED_USER con perfil comunitario), ROLE_ADMIN y ROLE_SUPER_ADMIN.

---

## 2. Alcance MVP

### Entra en este sprint

| Area | Alcance |
|---|---|
| Tooltip enriquecido | Breakdown WLC completo al hover/click sobre una comuna |
| Panel lateral comunal | Ficha de comuna: score, nivel, componentes, modo, ultima sync |
| Historial de riesgo | Grafico de evolucion temporal del score compuesto (7/14/30 dias) |
| Modo ENHANCED Copernicus | NDVI/NDMI desde OpenEoIndicatorObservation regional al score comunal cuando score >= 0.50 |
| Bbox regional en BD | Poblar Region.aoiBbox en seed para Biobio + Araucania; defaults en application.yml |

### Queda diferido (deuda documentada)

| Item | Motivo |
|---|---|
| Comparador de comunas | Siguiente sprint, prioridad baja |
| Capa de viento animada (leaflet-velocity) | Deuda visual, sin fecha |
| Notificaciones push CU09 | SDD separado pendiente |
| Nuble (21 comunas) | Fuera del alcance actual — solo 8va y 9na |
| Sync OpenEO por poligono comunal | Spec separada: openeo-sync-comunal-v1 |

---

## 3. Modo ENHANCED Copernicus

### 3.1 Arquitectura de datos

El sync OpenEO opera a nivel regional y almacena resultados en `OpenEoIndicatorObservation` (MongoDB). `ComunaRiskService` lee las observaciones mas recientes de la region padre de cada comuna, sin llamadas individuales por poligono comunal.

```
[openeo-service] → sync diario regional (ya operativo)
        ↓
[OpenEoIndicatorObservation] en MongoDB (coleccion existente)
        ↓ read latest por regionId
[ComunaRiskService.computeScore(comunaInfo)]
        ↓
si score_standard >= 0.50
    AND OpenEoIndicatorObservation NDVI+NDMI para la region
    AND observedAt <= 6 dias de antiguedad:
        recalcular con pesos ENHANCED
        mode = ENHANCED
sino:
    mode = STANDARD
    qualityFlag = COPERNICUS_UNAVAILABLE  (si el motivo es ausencia de dato)
```

> La granularidad comunal para OpenEO (poligono por comuna en vez de bbox regional) es una mejora futura documentada en spec separada `openeo-sync-comunal-v1`.

### 3.2 Condicion de activacion ENHANCED

- Score STANDARD (FWI + FIRMS + Reportes) >= 0.50 **Y**
- Existe `OpenEoIndicatorObservation` con `indicatorType=NDVI` y `indicatorType=NDMI` para la region padre de la comuna
- `observedAt` dentro de los ultimos 6 dias (ventana revisita Sentinel-2 en Chile)

Si falla cualquier condicion → modo STANDARD, `qualityFlag` indica motivo.

### 3.3 Pesos STANDARD vs ENHANCED

| Variable | Peso STANDARD | Peso ENHANCED |
|---|---|---|
| FWI proxy Open-Meteo | 52% | 38% |
| FIRMS NASA | 33% | 18% |
| Reportes ciudadanos | 15% | 4% |
| NDMI Sentinel-2 (inverso) | — | 22% |
| NDVI Sentinel-2 (inverso) | — | 8% |
| Perdida cobertura historica | — | 10% |

> Pesos ENHANCED derivados del SDD `mapa-territorial-riesgo` §4.2 (Ceccato et al. 2001). STANDARD es el fallback operacional garantizado.

---

## 4. Configuracion bbox regional — manejo en codigo

### Problema actual

`OpenEoSyncServiceImpl` resuelve el bbox de una region desde `Region.aoiBbox` (campo MongoDB) con fallback a `aoiBboxByRegionCode` (mapa en memoria poblado desde env var `OPENEO_AOI_BBOX_MAP`). Las regiones piloto no tienen `aoiBbox` persistido → sync satelital opera en modo stub.

### Solucion: seed en BD + defaults en configuracion

**application.yml** — defaults para las 2 regiones piloto:

```yaml
simfat:
  openeo:
    region-bbox-defaults:
      biobio: [-73.6, -38.5, -71.0, -36.7]
      araucania: [-73.6, -40.0, -70.8, -37.6]
```

**Seed al arranque**: si `Region.aoiBbox` esta vacio, poblarlo desde los defaults de configuracion. Valores quedan persistidos en MongoDB tras el primer arranque. `OPENEO_AOI_BBOX_MAP` en Railway queda como override opcional — no es bloqueante.

---

## 5. Modelo de datos — cambios

### 5.1 Extension ComunaRiskSnapshot (MongoDB)

Campos nuevos en documentos creados a partir de este sprint. Los snapshots STANDARD anteriores conservan su estructura sin cambios.

| Campo | Tipo | Descripcion |
|---|---|---|
| `mode` | String | `STANDARD` o `ENHANCED` |
| `qualityFlag` | String | `null`, `PARTIAL`, o `COPERNICUS_UNAVAILABLE` |
| `componentNdmi` | Double | Score normalizado NDMI (null si STANDARD) |
| `componentNdvi` | Double | Score normalizado NDVI (null si STANDARD) |
| `componentLoss` | Double | Score normalizado perdida cobertura (null si STANDARD) |
| `ndmiRaw` | Double | Valor Sentinel-2 crudo |
| `ndviRaw` | Double | Valor Sentinel-2 crudo |
| `openeoObservationId` | String | ID de OpenEoIndicatorObservation usada (trazabilidad) |

### 5.2 Nuevo endpoint historial comunal

```
GET /api/territory/risk-score/comunas/{gadmGid}/history?days=7
```

Devuelve array de snapshots del periodo, ordenados `computedAt` descendente.

---

## 6. Contratos API

### 6.1 GET /api/territory/risk-score/comunas/{gadmGid} (extendido)

```json
{
  "success": true,
  "data": {
    "gadmGid": "CHL.6.2.1_1",
    "comunaNombre": "Los Angeles",
    "regionId": "biobio",
    "computedAt": "2026-05-31T06:00:00Z",
    "scoreComposite": 0.71,
    "alertLevel": "ALTO",
    "mode": "ENHANCED",
    "qualityFlag": null,
    "components": {
      "fwi":     { "score": 0.68, "rawValue": 32.4, "weight": 0.38 },
      "ndmi":    { "score": 0.71, "rawValue": -0.18, "weight": 0.22 },
      "firms":   { "score": 0.80, "focosCount": 3, "frpMean": 18.4, "weight": 0.18 },
      "loss":    { "score": 0.45, "lossRate": 0.12, "weight": 0.10 },
      "ndvi":    { "score": 0.30, "rawValue": 0.54, "weight": 0.08 },
      "reports": { "score": 0.10, "count": 1, "weight": 0.04 }
    }
  }
}
```

### 6.2 GET /api/territory/risk-score/comunas/{gadmGid}/history

```json
{
  "success": true,
  "data": {
    "gadmGid": "CHL.6.2.1_1",
    "comunaNombre": "Los Angeles",
    "snapshots": [
      {
        "computedAt": "2026-05-31T06:00:00Z",
        "scoreComposite": 0.71,
        "alertLevel": "ALTO",
        "mode": "ENHANCED"
      },
      {
        "computedAt": "2026-05-30T06:00:00Z",
        "scoreComposite": 0.65,
        "alertLevel": "PREVENTIVO",
        "mode": "STANDARD"
      }
    ]
  }
}
```

---

## 7. Iteraciones planificadas

### Iteracion 1 — Backend: seed bbox + modo ENHANCED

- Poblar `Region.aoiBbox` en seed desde `application.yml` si esta vacio (Biobio + Araucania)
- Extension `ComunaRiskSnapshot` con campos `mode`, `qualityFlag` y componentes Copernicus
- Logica condicional ENHANCED en `ComunaRiskService`: leer `OpenEoIndicatorObservation` regional mas reciente, recalcular con pesos ENHANCED si condicion cumplida
- `GET /api/territory/risk-score/comunas/{gadmGid}/history?days=N`

### Iteracion 2 — Frontend: tooltip enriquecido + panel lateral

- Tooltip al hover: nombre + nivel badge + barra de score + top-2 componentes por peso
- Panel lateral al click: `ComunaRiskPanel.jsx` — tabla de componentes completa, badge STANDARD/ENHANCED, `qualityFlag` si aplica
- Boton "Sync ahora" visible solo para ROLE_ADMIN y ROLE_SUPER_ADMIN

### Iteracion 3 — Frontend: historial

- Mini-grafico de evolucion en panel lateral (Recharts — ya en el proyecto)
- Eje X: fecha, eje Y: scoreComposite, color de punto segun alertLevel
- Funciona con pocos puntos desde el inicio; se llena progresivamente

---

## 8. Criterios de aceptacion

1. Al hover sobre una comuna del choropleth se muestra tooltip con nombre, nivel de alerta, score y los 2 componentes de mayor peso.
2. Al hacer click se abre panel lateral con breakdown completo de todos los componentes WLC.
3. El panel lateral muestra el badge STANDARD o ENHANCED y el qualityFlag cuando aplica.
4. El grafico de evolucion temporal muestra los snapshots disponibles (minimo 1; se llena con el tiempo).
5. `GET /api/territory/risk-score/comunas/{gadmGid}/history?days=7` devuelve snapshots del periodo ordenados por fecha.
6. Cuando `scoreComposite >= 0.50` **y** existe `OpenEoIndicatorObservation` NDVI+NDMI con antiguedad <= 6 dias, el backend recalcula con pesos ENHANCED y persiste `mode=ENHANCED`.
7. Si la observacion Copernicus no existe o es demasiado antigua, el snapshot se persiste con `mode=STANDARD` y `qualityFlag=COPERNICUS_UNAVAILABLE` sin error visible al usuario.
8. `Region.aoiBbox` queda poblado para Biobio y Araucania tras el primer arranque post-deploy.
9. El sync OpenEO diario opera sobre los bboxes reales de ambas regiones (verificable en logs: `openeo_sync status=ok regionId=biobio`).
10. Todo lo anterior aplica solo para Biobio (8va) y Araucania (9na).

---

## 9. Deuda tecnica documentada

| Item | Severidad | Descripcion |
|---|---|---|
| Sync OpenEO por poligono comunal | Media | Spec separada openeo-sync-comunal-v1 — cada comuna pide su propio NDVI/NDMI |
| Comparador de comunas | Baja | Modal hasta 4 comunas en paralelo. Siguiente sprint. |
| Calibracion pesos WLC | Media | Requiere 60+ dias de observaciones acumuladas. |
| Capa de viento animada | Baja | leaflet-velocity. Sin fecha definida. |
| Nuble (21 comunas) | Media | Pendiente cuando se amplíe cobertura territorial. |
| ML predictivo | Alta (post-defensa) | Requiere dataset acumulado y pipeline ML separado. |
| Notificaciones push CU09 | Media | SDD separado pendiente. |
