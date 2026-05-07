# Contrato Backend Minimo - Monitorizacion Territorial

Fecha: 2026-04-21  
Frontend consumidor: `simfat-web` (modulo `TerritoryPage`)  
Objetivo: soportar mapa principal con bajo costo y carga progresiva por region.

## Principios de diseno

- Entregar datos listos para visualizacion (sin geoprocesamiento pesado en frontend).
- Soportar datos imperfectos y degradacion controlada (`source`, `generatedAt`, `qualityFlag`).
- Reducir costo de API: respuestas agregadas, por region y rango acotado.

## 1) GET /api/territory/bounds

Obtiene encuadre inicial (bounds/center/zoom) por region.

### Query params

- `regionId` (string, requerido)

Valores iniciales recomendados:

- `biobio`
- `araucania`

### Response (exito)

```json
{
  "success": true,
  "message": "Bounds obtenidos",
  "data": {
    "regionId": "biobio",
    "bounds": [[-38.9, -74.1], [-36.3, -71.0]],
    "center": [-37.5, -72.5],
    "zoom": 8,
    "generatedAt": "2026-04-21T20:15:00Z"
  },
  "timestamp": "2026-04-21T20:15:01Z"
}
```

### Notas

- `bounds` usa formato `[[southWestLat, southWestLng], [northEastLat, northEastLng]]`.
- Si no existe `regionId`, devolver `404` con error normalizado.

## 2) GET /api/territory/layers

Retorna capas geoespaciales simplificadas (puntos + GeoJSON simplificado) para mapa.

### Query params

- `regionId` (string, requerido)
- `indicators` (csv, opcional, default: `NDVI,NDMI,LOSS,ALERTS,REPORTS`)
- `from` (date `YYYY-MM-DD`, opcional)
- `to` (date `YYYY-MM-DD`, opcional)

### Response (exito)

```json
{
  "success": true,
  "message": "Capas territoriales obtenidas",
  "data": {
    "regionId": "araucania",
    "generatedAt": "2026-04-21T20:20:00Z",
    "source": "backend-cache",
    "qualityFlag": "OK",
    "layers": {
      "NDVI": {
        "type": "FeatureCollection",
        "features": [
          {
            "type": "Feature",
            "id": "ar-ndvi-1",
            "properties": {
              "label": "Temuco",
              "indicator": "NDVI",
              "value": 0.54,
              "observedAt": "2026-04-21T18:00:00Z"
            },
            "geometry": {
              "type": "Point",
              "coordinates": [-72.58, -38.74]
            }
          }
        ]
      },
      "ALERTS": {
        "type": "FeatureCollection",
        "features": []
      },
      "REPORTS": {
        "type": "FeatureCollection",
        "features": []
      }
    }
  },
  "timestamp": "2026-04-21T20:20:01Z"
}
```

### Propiedades minimas por feature

- `label` (string)
- `indicator` (string: `NDVI|NDMI|LOSS|ALERTS|REPORTS`)
- Metadato contextual segun capa:
  - `value` para NDVI/NDMI
  - `hectares` para LOSS
  - `level` para ALERTS
  - `category` para REPORTS

## 3) Manejo de degradacion / costos

Recomendaciones backend para costo bajo:

1. Cachear por llave: `regionId + indicators + from + to` (TTL sugerido: 60-180s).
2. Si falla fuente externa (por ejemplo openEO), responder ultimo snapshot con:
   - `source: "backend-stale-cache"`
   - `qualityFlag: "STALE"`
3. Limitar cantidad de features por capa y simplificar payload.

## 4) Evolucion futura del contrato

Estado actual (cerrado para esta iteracion):

- geometria puntual + GeoJSON simplificado

Evolucion prevista:

- extender `geometry.type` a `Polygon`/`MultiPolygon` sin romper el contrato base.
- agregar versionado opcional (`schemaVersion`) cuando se habiliten geometrias complejas.
