# Modelo Logico de Actualizacion - SIMFAT

Fecha: 2026-04-22  
Version: 1.0

## 1) Contexto

La actualizacion integra tres lineas:

1. Fortalecimiento auth (PostgreSQL)
2. Consolidacion dominio territorial (MongoDB)
3. Integracion satelital y snapshot para dashboard (MongoDB + servicios)

## 2) Logica funcional por modulo

### 2.1 Auth y seguridad

- Registro/login emiten access token + refresh token.
- Refresh token se rota y revoca.
- Reset de password usa token hash con expiracion y consumo unico.

### 2.2 Dominio territorial

- `regions` actua como nodo raiz territorial.
- `forest_loss_records`, `heat_alert_events` y `alert_rules` referencian region.
- Las reglas pueden ser globales (`regionId = null`) o especificas.

### 2.3 Analitica satelital

- `openeo_job_runs`: trazabilidad de ejecucion de sincronizaciones.
- `openeo_indicator_observations`: serie historica NDVI/NDMI por region.
- `dashboard_region_snapshots`: estado agregado listo para respuesta rapida.

## 3) Modelo logico (vista resumida)

```mermaid
flowchart LR
    U[Auth User] --> RT[Refresh Tokens]
    U --> PR[Password Reset Tokens]

    R[Region] --> FL[Forest Loss]
    R --> HA[Heat Alerts]
    R --> AR[Alert Rules]
    R --> JR[OpenEO Job Runs]
    R --> IO[Indicator Observations]
    R --> DS[Dashboard Snapshot]
```

## 4) Estructuras nuevas/proyectadas de iteracion frontend

Para la iteracion territorial-comunitaria, el contrato backend debe incorporar:

- `community_board`
- `community_resources`
- `community_contacts`
- `citizen_reports`

Estado actual: contrato documentado y frontend preparado, pendiente implementacion backend.

## 5) Reglas de consistencia

1. `regionId` debe existir en `regions` para todo dato territorial.
2. `openeo_indicator_observations` mantiene unicidad logica por:
   - `regionId + indicator + observedAt`
3. `dashboard_region_snapshots` mantiene una fila vigente por region.
4. Tokens auth nunca se guardan en texto plano (solo hash).
