# Contrato Backend Minimo - Reportes Ciudadanos

Fecha: 2026-04-21  
Frontend consumidor: `simfat-web` (`CitizenReportsPage`)

## Objetivo

Habilitar captura y seguimiento operativo de reportes ciudadanos con bajo costo:

- geolocalizacion (lat/lon)
- fotos
- descripcion y categoria
- estado operativo del reporte

## 1) GET /api/citizen-reports

### Query params (opcionales)

- `regionId`
- `status` (`RECIBIDO|VALIDADO|DERIVADO|DESCARTADO`)
- `category` (`HUMO|FOCO|QUEMA|INFRAESTRUCTURA|OTRO`)

### Response

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "id": "report-1",
      "regionId": "biobio",
      "category": "HUMO",
      "description": "Columna de humo visible",
      "latitude": -36.81,
      "longitude": -73.04,
      "status": "RECIBIDO",
      "photoCount": 2,
      "createdAt": "2026-04-21T17:15:00Z"
    }
  ],
  "timestamp": "2026-04-21T17:15:01Z"
}
```

## 2) POST /api/citizen-reports

Formato recomendado para frontend actual: `multipart/form-data`

- `payload`: JSON string
- `files`: 0..n imagenes

`payload`:

```json
{
  "regionId": "araucania",
  "category": "FOCO",
  "description": "Foco activo en ladera",
  "latitude": -38.74,
  "longitude": -72.60
}
```

### Respuesta esperada

```json
{
  "success": true,
  "message": "Reporte creado",
  "data": {
    "id": "report-9",
    "regionId": "araucania",
    "category": "FOCO",
    "description": "Foco activo en ladera",
    "latitude": -38.74,
    "longitude": -72.60,
    "status": "RECIBIDO",
    "photoCount": 2,
    "createdAt": "2026-04-21T17:30:00Z"
  },
  "timestamp": "2026-04-21T17:30:01Z"
}
```

## 3) PATCH /api/citizen-reports/{id}/status

Body:

```json
{ "status": "VALIDADO" }
```

Estados permitidos:

- `RECIBIDO`
- `VALIDADO`
- `DERIVADO`
- `DESCARTADO`

## 4) DELETE /api/citizen-reports/{id}

Respuesta recomendada:

```json
{ "success": true, "message": "Eliminado", "data": true, "timestamp": "..." }
```

## Recomendaciones de costo y performance

1. Guardar originales de imagen en almacenamiento objeto; no en base documental principal.
2. Exponer `photoCount` en listados y evitar devolver blobs/urls pesadas en consulta masiva.
3. Validar peso/tipo de imagen en backend (frontend ya hace validaci?n basica).
4. Indexar por `regionId`, `status`, `category`, `createdAt`.
5. Mantener paginacion en listados cuando el volumen crezca.
