# Mapa Territorial de Riesgo de Incendio - SDD v1

Fecha: 2026-05-30
Cambio SDD: `mapa-territorial-riesgo`
Modulo propietario: Territorio
Estado: especificacion aprobada — pendiente de implementacion

---

## 1. Objetivo

Evolucionar el modulo de territorio desde una visualizacion de indicadores aislados hacia un **sistema de monitoreo de riesgo de incendio forestal** que permita al coordinador territorial tomar decisiones preventivas informadas y que genere alertas automaticas por umbrales definidos.

El pivote central es la incorporacion de un **score de riesgo compuesto** que integra fuentes satelitales, meteorologicas, deteccion activa de focos y cobertura historica, visible en el mapa como capa diferenciada por region.

Usuarios objetivo: coordinadores territoriales (ROLE_VERIFIED_USER con perfil comunitario), ROLE_ADMIN y ROLE_SUPER_ADMIN.

---

## 2. Alcance MVP

### Entra en este sprint

| Area | Alcance |
|---|---|
| Fuentes externas | FIRMS (focos activos), OpenWeatherMap FWI, OpenEO NDVI/NDMI (sync diario activado) |
| Cobertura historica | Consume `ForestLossRecord` existente, sin nuevas fuentes externas |
| Score de riesgo | Calculo compuesto por region, almacenado y actualizable |
| Triggers de alerta | 4 niveles (NORMAL/PREVENTIVO/ALTO/CRITICO) con umbrales individuales |
| Frontend mapa | Score badge por region, capa FIRMS como puntos de fuego, indicador de nivel de alerta |
| Sync programado | Activacion de sync diario OpenEO (ya estaba dormido), FIRMS cada 12h, FWI cada 12h |

### Queda diferido (deuda documentada)

| Item | Motivo |
|---|---|
| Choropleth regional (poligonos GADM) | Requiere simplificacion de geometria y ajuste del contrato GeoJSON |
| Capa de viento animada (Open-Meteo + leaflet-velocity) | Dependencia adicional de frontend, diferida para iteracion visual |
| Calibracion de pesos por datos historicos | Requiere acumulacion de observaciones post-despliegue |
| Granularidad comunal | Fuente IDE Chile / BCN, procesamiento shapefile pendiente |
| ML predictivo | Requiere dataset de al menos 60 dias de observaciones |

---

## 3. Fuentes de datos y pipeline

### 3.1 Fuentes

| Fuente | Indicador | Frecuencia sync | Integracion | Costo |
|---|---|---|---|---|
| Copernicus CDSE via OpenEO | NDVI, NDMI | 1x/dia | openeo-service (Railway hobby) | Gratis (free tier CDSE) |
| NASA FIRMS VIIRS NOAA-20 | Focos activos (FRP, confidence) | 2x/dia | Backend Java directo | Gratis (MAP_KEY NASA Earthdata) |
| OpenWeatherMap FWI API | FWI, FFMC, DMC, DC, ISI, BUI | 2x/dia | Backend Java directo | Gratis (free tier, 1000 calls/dia) |
| ForestLossRecord (interno) | Tasa de perdida historica por region | Estatico (no sync) | Ya disponible en BD | — |
| CitizenReport (interno) | Densidad de reportes activos | On-demand al calcular score | Ya disponible en BD | — |

### 3.2 Pipeline de datos

```
[Copernicus CDSE]
      |
[openeo-service] --POST /api/openeo/ingest--> [Spring Boot backend]
                                                      |
[NASA FIRMS API] ------NasaFirmsService @Scheduled--> |---> [HeatAlertEvent]
                                                      |
[OpenWeatherMap FWI] --OpenWeatherFwiService @Sched-> |---> [TerritoryWeatherObservation]
                                                      |
[ForestLossRecord] (ya en BD)                         |
[CitizenReport] (ya en BD)                            |
                                                      v
                                        [TerritoryRiskService]
                                          calcula score compuesto
                                                      |
                                                      v
                                        [TerritoryRiskSnapshot] (BD)
                                                      |
                                        [AlertRule triggers]
                                                      |
                                        [AlertEvent generado automaticamente]
```

---

## 4. Modelo de score de riesgo

### 4.1 Metodologia

**Weighted Linear Combination (WLC)** — metodo estandar en SIG para indices de riesgo compuesto. Cada variable se normaliza a [0,1] antes de ponderar. La normalizacion usa z-score contra el historico acumulado de la region; durante las primeras semanas sin historico suficiente, se usa normalizacion min-max con rangos de referencia de la literatura.

> Los pesos iniciales se derivan de literatura en evaluacion de riesgo de incendio forestal (CFFDRS, Chuvieco et al. 2010; Ceccato et al. 2001 para NDMI como predictor de inflamabilidad) y son parametros configurables sujetos a calibracion con datos historicos del area de estudio.

### 4.2 Variables y pesos

| Variable | Peso | Tipo | Justificacion |
|---|---|---|---|
| FWI (Fire Weather Index) | 38% | Dinamico — meteorologico | Estandar operacional internacional, ya incorpora temperatura, humedad, viento y precipitacion acumulada. Utilizado por CONAF. |
| NDMI normalizado (inverso) | 22% | Dinamico — satelital | Mide contenido hidrico estructural de la vegetacion. Complementa FWI: este modela humedad superficial por meteorologia, NDMI mide la planta misma. (Ceccato et al. 2001) |
| Focos activos FIRMS (densidad + FRP) | 18% | Deteccion activa | Senial directa: si hay fuego confirmado, el riesgo es real. Solo focos confidence=nominal y high. |
| Perdida de cobertura forestal historica | 10% | Estructural — vulnerabilidad | Zonas degradadas tienen vegetacion secundaria (matorral) mas inflamable que bosque maduro. Modulador de vulnerabilidad del paisaje. |
| NDVI normalizado (inverso) | 8% | Dinamico — satelital | Densidad de combustible disponible. Peso reducido: en temporada de incendios de Chile central-sur, NDVI es relativamente estable; NDMI es mas diagnostico. |
| Densidad reportes ciudadanos | 4% | Verdad de campo | Senial de suelo. Peso bajo por escasez de datos en fase piloto. |

**Score final**: valor en [0.0, 1.0] donde 1.0 = riesgo maximo.

### 4.3 Normalizacion durante fase de arranque (sin historico)

Rangos de referencia iniciales (ajustables):

| Variable | Rango referencia | Fuente |
|---|---|---|
| FWI | 0 — 50+ (Very High/Extreme) | CFFDRS scale |
| NDMI | −0.4 (muy seco) — +0.4 (humedo) | Literatura satelital |
| NDVI | 0.1 (muy bajo) — 0.8 (denso) | Literatura Sentinel-2 |
| FRP (FIRMS) | 0 — 150+ MW por region | FIRMS documentation |
| Perdida forestal | 0 — tasa maxima observada en regiones piloto | Datos internos |

---

## 5. Triggers y niveles de alerta

### 5.1 Niveles

| Nivel | Etiqueta | Color | Score compuesto |
|---|---|---|---|
| 0 | NORMAL | Verde | < 0.40 |
| 1 | PREVENTIVO | Amarillo | >= 0.50 |
| 2 | ALTO | Naranja | >= 0.70 |
| 3 | CRITICO | Rojo | >= 0.85 |

### 5.2 Umbrales individuales (independientes del score)

El nivel de alerta de una region es el **maximo** entre el que arroja el score y el que disparan los umbrales individuales.

| Variable | Umbral | Nivel que dispara |
|---|---|---|
| FWI | >= 20 | PREVENTIVO |
| FWI | >= 30 | ALTO |
| FWI | >= 45 | CRITICO |
| NDMI | < -0.10 | PREVENTIVO |
| NDMI < -0.15 AND FWI >= 20 | combinado | ALTO |
| FIRMS: foco confidence=nominal en region | cualquiera | ALTO |
| FIRMS: foco confidence=high en region | cualquiera | CRITICO (inmediato, sin esperar score) |

**Regla CRITICO por FIRMS**: se genera alerta inmediata en cuanto llega el sync, sin esperar el ciclo de calculo del score compuesto.

### 5.3 Integracion con AlertRule existente

Los umbrales se almacenan como `AlertRule` en la tabla existente con un nuevo campo `source=TERRITORIAL`. El `AlertRuleController` ya existe; se extiende para aceptar reglas de tipo territorial.

---

## 6. Decisiones de arquitectura

| Decision | Razon |
|---|---|
| FIRMS → backend Java directo (no microservicio) | API REST simple, sin procesamiento geoespacial complejo. `HeatAlertEvent` ya existe. |
| FWI → backend Java directo | Un GET por region, sin procesamiento. Misma logica que FIRMS. |
| Wind (Open-Meteo) → capa visual diferida | El FWI ya incorpora viento en el score. La capa animada en Leaflet es deuda visual. |
| OpenEO → sync diario activado | Sentinel-2 tiene revisita ~5 dias en Chile. Sync diario captura cada nuevo pase. 33 dias = baseline para normalizacion. |
| Pesos WLC como parametros configurables | No hardcodeados. Se ajustan sin cambio de codigo cuando haya datos historicos suficientes. |
| Poligonos GADM como assets estaticos | Licencia libre academica. Simplificados para web. Diferido a iteracion visual. |
| Score como snapshot en BD | Se almacena el resultado calculado con todos sus componentes. No se recalcula en cada request. |

---

## 7. Modelo de datos — cambios

### Migracion V4: nuevas tablas

**`territory_weather_observations`** (PostgreSQL)
```
id              VARCHAR(36) PK
region_id       VARCHAR(80) NOT NULL
observed_at     TIMESTAMPTZ NOT NULL
source          VARCHAR(40) NOT NULL  -- 'openweathermap'
fwi             DECIMAL(6,2)
ffmc            DECIMAL(6,2)
dmc             DECIMAL(6,2)
dc              DECIMAL(6,2)
isi             DECIMAL(6,2)
bui             DECIMAL(6,2)
dsr             DECIMAL(6,2)
created_at      TIMESTAMPTZ NOT NULL
```

**`territory_risk_snapshots`** (PostgreSQL)
```
id                        VARCHAR(36) PK
region_id                 VARCHAR(80) NOT NULL
computed_at               TIMESTAMPTZ NOT NULL
score_composite           DECIMAL(5,4) NOT NULL   -- 0.0000 a 1.0000
alert_level               VARCHAR(20) NOT NULL    -- NORMAL/PREVENTIVO/ALTO/CRITICO
component_fwi             DECIMAL(5,4)
component_ndmi            DECIMAL(5,4)
component_firms           DECIMAL(5,4)
component_loss            DECIMAL(5,4)
component_ndvi            DECIMAL(5,4)
component_reports         DECIMAL(5,4)
fwi_raw                   DECIMAL(6,2)
ndmi_raw                  DECIMAL(6,4)
ndvi_raw                  DECIMAL(6,4)
firms_count               INTEGER
firms_frp_mean            DECIMAL(8,2)
loss_rate_raw             DECIMAL(6,4)
reports_count             INTEGER
created_at                TIMESTAMPTZ NOT NULL
```

### Extension tabla existente `heat_alert_events`

Agregar columnas via migracion:
```
firms_confidence   VARCHAR(10)   -- 'l', 'n', 'h'
firms_frp          DECIMAL(8,2)  -- Fire Radiative Power en MW
firms_satellite    VARCHAR(10)   -- 'N' (NOAA-20), 'S' (Suomi NPP)
firms_source       VARCHAR(40)   -- 'VIIRS_NOAA20_NRT'
```

---

## 8. Contratos API

### 8.1 GET /api/territory/layers (extension)

Nuevos indicadores aceptados en parametro `indicators`:
- `RISK_SCORE` — devuelve feature por region con score y nivel de alerta
- `FIRMS` — focos activos de las ultimas 48h (antes: capa ALERTS era generica)

Propiedad adicional en cada feature de `RISK_SCORE`:
```json
{
  "type": "Feature",
  "properties": {
    "indicator": "RISK_SCORE",
    "label": "Biobio",
    "score": 0.72,
    "alertLevel": "ALTO",
    "computedAt": "2026-05-30T06:00:00Z",
    "components": {
      "fwi": 0.68,
      "ndmi": 0.71,
      "firms": 0.80,
      "loss": 0.45,
      "ndvi": 0.30,
      "reports": 0.10
    }
  },
  "geometry": { "type": "Point", "coordinates": [-72.5, -37.5] }
}
```

### 8.2 GET /api/territory/risk-score/{regionId}

Detalle completo del ultimo snapshot de riesgo para una region.

```json
{
  "success": true,
  "data": {
    "regionId": "biobio",
    "computedAt": "2026-05-30T06:00:00Z",
    "scoreComposite": 0.72,
    "alertLevel": "ALTO",
    "components": {
      "fwi":     { "score": 0.68, "rawValue": 32.4, "weight": 0.38 },
      "ndmi":    { "score": 0.71, "rawValue": -0.18, "weight": 0.22 },
      "firms":   { "score": 0.80, "focosCount": 3, "frpMean": 18.4, "weight": 0.18 },
      "loss":    { "score": 0.45, "lossRate": 0.12, "weight": 0.10 },
      "ndvi":    { "score": 0.30, "rawValue": 0.54, "weight": 0.08 },
      "reports": { "score": 0.10, "count": 1, "weight": 0.04 }
    },
    "activeThresholdsTriggers": ["FWI_HIGH", "FIRMS_NOMINAL"]
  }
}
```

### 8.3 POST /api/territory/sync (admin)

Dispara sync manual de todas las fuentes para una region. Solo ROLE_ADMIN y ROLE_SUPER_ADMIN.

```json
// Request
{ "regionId": "biobio" }

// Response
{ "success": true, "message": "Sync encolado para biobio", "data": { "jobId": "..." } }
```

---

## 9. Iteraciones planificadas

### Iteracion 1 — Fuentes externas y almacenamiento

- Migracion V4 (tablas nuevas + extension `heat_alert_events`)
- `NasaFirmsService`: sync VIIRS NOAA-20, filtro confidence, persistencia en `HeatAlertEvent`
- `OpenWeatherFwiService`: sync FWI por region, persistencia en `TerritoryWeatherObservation`
- Activacion sync diario OpenEO NDVI/NDMI (cablear `IndicatorService` contra `fetch_indicator_latest` real)

### Iteracion 2 — Motor de score y triggers

- `TerritoryRiskService`: calculo WLC normalizado, persistencia en `TerritoryRiskSnapshot`
- Normalizacion z-score con fallback min-max para arranque sin historico
- Extension `AlertRule` para reglas territoriales
- Generacion automatica de `AlertEvent` cuando threshold es superado

### Iteracion 3 — Contratos y frontend

- Nuevos endpoints `/api/territory/risk-score/{regionId}` y extension de `/api/territory/layers`
- Score badge por region en `TerritoryMapPanel`
- Capa FIRMS como puntos de fuego con color segun intensidad FRP
- Indicador de nivel de alerta (NORMAL/PREVENTIVO/ALTO/CRITICO) visible en la UI

---

## 10. Criterios de aceptacion

1. El backend sincroniza focos FIRMS cada 12h y los almacena con FRP y confidence correctos.
2. El backend sincroniza FWI cada 12h y almacena los 6 componentes del indice.
3. `TerritoryRiskService` calcula el score compuesto y lo persiste en `TerritoryRiskSnapshot` tras cada sync.
4. Si un foco FIRMS con confidence=high es detectado, se genera alerta CRITICO en menos de 15 minutos del sync.
5. Si FWI >= 30 en cualquier region monitoreada, se genera alerta ALTO de forma automatica.
6. `GET /api/territory/risk-score/{regionId}` devuelve score actual con todos sus componentes.
7. El mapa muestra el nivel de alerta actual de cada region de forma visible al coordinador.
8. Los pesos del score son configurables sin redeployment (parametros en BD o configuracion).
9. Si alguna fuente externa falla, el score se calcula con las variables disponibles y se marca con `qualityFlag: "PARTIAL"`.
10. El sistema responde correctamente para las dos regiones piloto: Biobio y La Araucania.

---

## 11. Deuda tecnica documentada

| Item | Severidad | Descripcion |
|---|---|---|
| Choropleth GADM | Media | Poligonos regionales para visualizacion tipo heatmap. Fuente: gadm.org. Requiere simplificacion de geometria. |
| Capa de viento (Open-Meteo) | Baja | Vectores de viento animados con leaflet-velocity. Util para visualizar propagacion potencial. |
| Calibracion de pesos WLC | Media | Ajuste de pesos con datos historicos acumulados post-despliegue. Requiere minimo 60 dias de observaciones. |
| Granularidad comunal | Alta (post-defensa) | Score a nivel de comuna en vez de region. Fuente: IDE Chile / BCN shapefile. |
| ML predictivo | Alta (post-defensa) | Modelo de prediccion de riesgo a 24-72h. Requiere dataset acumulado y pipeline ML separado. |
| FWI historico de referencia | Media | Normalizacion estacional requiere FWI historico por mes para Chile central-sur. |
| Notificaciones push (CU09) | Media | Canal de notificacion para alertas generadas automaticamente. Pendiente SDD separado. |
