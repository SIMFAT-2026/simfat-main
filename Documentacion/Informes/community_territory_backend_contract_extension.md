# Extensión contrato backend — Territorio: Score de Riesgo y Sincronización

Fecha: 2026-05-31
Extiende: `territory_backend_contract.md`

## Endpoints nuevos

### GET /api/territory/risk-score/{regionId}

Retorna el último snapshot de score de riesgo para una región.

**Auth:** `ROLE_VERIFIED_USER`, `ROLE_MODERATOR`, `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`

**Response (éxito, con datos):**
```json
{
  "success": true,
  "data": {
    "regionId": "biobio",
    "computedAt": "2026-05-31T02:42:51Z",
    "scoreComposite": 0.72,
    "alertLevel": "ALTO",
    "qualityFlag": "OK",
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

**Response (sin datos aún):**
```json
{
  "success": true,
  "data": {
    "regionId": "biobio",
    "alertLevel": "NORMAL",
    "scoreComposite": 0.0,
    "qualityFlag": "NO_DATA"
  }
}
```

**qualityFlag values:**
- `OK` — 4+ variables disponibles
- `PARTIAL` — 2-3 variables disponibles
- `MINIMAL` — menos de 2 variables
- `NO_DATA` — sin snapshot calculado

---

### POST /api/territory/sync

Dispara sync completo para una región: FIRMS → FWI → recálculo de score.

**Auth:** `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`

**Query params:** `regionId` (string, requerido)

**Response:**
```json
{
  "success": true,
  "message": "Sync completo para biobio",
  "data": {
    "regionId": "biobio",
    "alertLevel": "NORMAL",
    "scoreComposite": 0.0,
    "qualityFlag": "MINIMAL"
  }
}
```

---

## Extensión GET /api/territory/layers

Nuevos indicadores aceptados en el parámetro `indicators`:

### FIRMS

Focos activos VIIRS NOAA-20 de las últimas 48h para la región, filtrados a confidence ≥ nominal.

```json
{
  "type": "Feature",
  "properties": {
    "indicator": "FIRMS",
    "label": "Foco activo VIIRS",
    "confidence": "h",
    "frp": 18.4,
    "satellite": "N",
    "acquiredAt": "2026-05-31T01:30:00"
  },
  "geometry": { "type": "Point", "coordinates": [-72.5, -37.5] }
}
```

### RISK_SCORE

Score de riesgo actual por región.

```json
{
  "type": "Feature",
  "properties": {
    "indicator": "RISK_SCORE",
    "label": "Biobio",
    "score": 0.72,
    "alertLevel": "ALTO",
    "computedAt": "2026-05-31T02:42:51"
  },
  "geometry": { "type": "Point", "coordinates": [-72.5, -37.5] }
}
```

---

## Degradación

Si alguna fuente externa falla durante el sync, el score se calcula con las variables disponibles y se marca con `qualityFlag: "PARTIAL"` o `"MINIMAL"`. El sistema nunca falla la respuesta por ausencia de datos externos.
