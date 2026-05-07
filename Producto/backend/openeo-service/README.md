# SIMFAT openEO Microservice

Microservicio especializado para obtencion de datos satelitales desde openEO/Copernicus dentro del ecosistema SIMFAT.

Este servicio no implementa logica de negocio principal de SIMFAT. Su rol en este sprint es entregar una base tecnica limpia para futuras ejecuciones de jobs satelitales.

## Alcance de este sprint

- Estructura base por capas
- API HTTP con FastAPI
- Endpoints de salud, chequeo de configuracion y jobs
- Contratos request/response consistentes con Pydantic
- Cliente openEO desacoplado para probes y calculo puntual
- Publicacion del resultado al backend SIMFAT
- Configuracion por variables de entorno
- Manejo basico de errores
- Tests minimos de humo

## Estructura

```text
.
|-- app/
|   |-- __init__.py
|   |-- main.py
|   |-- api/
|   |   |-- __init__.py
|   |   `-- routes/
|   |       |-- __init__.py
|   |       |-- config.py
|   |       |-- health.py
|   |       `-- openeo.py
|   |-- adapters/
|   |   |-- __init__.py
|   |   `-- openeo_adapter.py
|   |-- clients/
|   |   |-- __init__.py
|   |   `-- openeo_client.py
|   |-- core/
|   |   |-- __init__.py
|   |   |-- config.py
|   |   `-- exceptions.py
|   |-- models/
|   |   |-- __init__.py
|   |   `-- job.py
|   |-- schemas/
|   |   |-- __init__.py
|   |   |-- config.py
|   |   |-- jobs.py
|   |   `-- openeo.py
|   `-- services/
|       |-- __init__.py
|       `-- indicator_service.py
|-- tests/
|   |-- __init__.py
|   `-- test_api.py
|-- .env.example
`-- requirements.txt
```

## Requisitos

- Python 3.11+
- pip

## Configuracion

1. Copiar variables de entorno:

```bash
cp .env.example .env
```

2. Ajustar valores segun ambiente local.

Variables relevantes para flujo end-to-end:

- `OPENEO_BASE_URL`
- `OPENEO_CLIENT_ID`
- `OPENEO_CLIENT_SECRET`
- `OPENEO_ACCESS_TOKEN` (opcional; token de procesamiento de corta duracion)
- `OPENEO_REFRESH_TOKEN` (recomendado; habilita renovacion automatica de access token)
- `OPENEO_REFRESH_CLIENT_ID` (opcional; fallback: `OPENEO_CLIENT_ID`)
- `OPENEO_REFRESH_CLIENT_SECRET` (opcional; solo para clientes confidenciales)
- `SIMFAT_BACKEND_URL`
- `SIMFAT_BACKEND_INDICATOR_INGEST_PATH` (default: `/api/indicators/measurements`)
- `SIMFAT_BACKEND_TIMEOUT_SECONDS` (default: `10`)
- `SIMFAT_BACKEND_AUTH_TOKEN` (opcional)
- `SIMFAT_BACKEND_SYNC_ENABLED` (default: `true`; usar `false` para validar openEO sin depender del backend)
- `APP_CORS_ALLOW_ORIGINS` (default: `*`; lista separada por coma)

## Ejecucion local

1. Crear y activar entorno virtual.
2. Instalar dependencias:

```bash
pip install -r requirements.txt
```

3. Iniciar servicio:

```bash
uvicorn app.main:app --reload --port 8000
```

Si deseas usar el puerto por variable:

```bash
uvicorn app.main:app --reload --port ${APP_PORT}
```

## Endpoints base

- `GET /health`
- `GET /config/check`
- `GET /openeo/capabilities`
- `GET /openeo/collections?limit=5`
- `POST /openeo/indicators/latest/{indicator}` (`indicator` = `NDVI` o `NDMI`)
- `POST /openeo/ui/daily/{indicator}` (`indicator` = `NDVI` o `NDMI`, respuesta normalizada para frontend)

Nota: los endpoints de indicadores requieren `aoi` tipo `bbox` con 4 coordenadas (`west,south,east,north`).

## Verificacion liviana de conexion openEO

Los endpoints `GET /openeo/capabilities` y `GET /openeo/collections` estan orientados a pruebas de conectividad de bajo costo.

- Usan timeout corto.
- Cachean respuestas por 5 minutos.
- Cachean token de acceso segun su expiracion.

Para computo satelital de indicadores (endpoint `POST /openeo/indicators/latest/{indicator}`) el servicio usa `/result` de openEO y requiere token de procesamiento de usuario.
Opciones soportadas:

- `OPENEO_ACCESS_TOKEN`: token corto (manual).
- `OPENEO_REFRESH_TOKEN`: renovacion automatica de access token (recomendado para operacion continua).

En CDSE, el flujo `client_credentials` no habilita endpoints de procesamiento, solo metadatos/probes.

Ademas, despues de calcular el indicador, el microservicio publica automaticamente el resultado al backend SIMFAT via HTTP POST.

## Ejemplo rapido de request (placeholder)

```json
{
  "regionId": "region-001",
  "aoi": {
    "type": "bbox",
    "coordinates": [-72.6, -38.8, -72.3, -38.5]
  },
  "periodStart": "2025-01-01",
  "periodEnd": "2025-01-31"
}
```

## Ejemplo de request de indicador real

`POST /openeo/indicators/latest/NDVI`

```json
{
  "regionId": "region-001",
  "aoi": {
    "type": "bbox",
    "coordinates": [-72.6, -38.8, -72.3, -38.5]
  },
  "periodStart": "2026-04-01",
  "periodEnd": "2026-04-12"
}
```

## Respuesta esperada en indicador latest

`POST /openeo/indicators/latest/NDVI`

```json
{
  "status": "ok",
  "source": "openEO",
  "cached": false,
  "fetchedAt": "2026-04-15T00:00:00Z",
  "measuredAt": "2026-04-15T00:00:00Z",
  "indicatorType": "NDVI",
  "regionId": "region-001",
  "periodStart": "2026-04-01",
  "periodEnd": "2026-04-12",
  "collectionId": "SENTINEL2_L2A",
  "value": 0.42,
  "backendSynced": true,
  "backendStatusCode": 201,
  "backendTargetUrl": "http://localhost:8080/api/indicators/measurements"
}
```

## Notas de arquitectura

- Este microservicio encapsula la integracion futura con openEO mediante `clients` + `adapters`.
- Las rutas HTTP solo orquestan validacion y delegan a `services`.
- La persistencia y reglas de negocio del dominio SIMFAT permanecen en `simfat-backend`.
- El frontend no debe consumir este servicio directamente.
