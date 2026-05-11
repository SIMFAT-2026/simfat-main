# MER Actualizado - SIMFAT Backend

Fecha: 2026-04-22  
Version: 1.0

## Objetivo

Representar el modelo entidad-relacion/l?gico actualizado considerando estructuras activas en:

- PostgreSQL (auth)
- MongoDB (dominio territorial y analitica)

## MER (Mermaid)

```mermaid
erDiagram
    APP_USERS ||--o{ REFRESH_TOKENS : "user_id (logica)"
    APP_USERS ||--o{ PASSWORD_RESET_TOKENS : "user_id (logica)"

    REGIONS ||--o{ FOREST_LOSS_RECORDS : "regionId (logica)"
    REGIONS ||--o{ HEAT_ALERT_EVENTS : "regionId (logica)"
    REGIONS ||--o{ ALERT_RULES : "regionId opcional"
    REGIONS ||--o{ OPENEO_JOB_RUNS : "regionId (logica)"
    REGIONS ||--o{ OPENEO_INDICATOR_OBSERVATIONS : "regionId (logica)"
    REGIONS ||--|| DASHBOARD_REGION_SNAPSHOTS : "regionId unico"

    APP_USERS {
        string id PK
        string email UK
        string full_name
        string password_hash
        boolean enabled
        string roles
        datetime created_at
        datetime updated_at
    }

    REFRESH_TOKENS {
        string id PK
        string token_id UK
        string user_id
        string token_hash UK
        datetime issued_at
        datetime expires_at
        datetime revoked_at
    }

    PASSWORD_RESET_TOKENS {
        string id PK
        string token_hash UK
        string user_id
        datetime created_at
        datetime expires_at
        datetime consumed_at
    }

    REGIONS {
        string id PK
        string nombre
        string codigo
        string zona
        double hectareas_bosque_referencia
        array aoi_bbox
    }

    FOREST_LOSS_RECORDS {
        string id PK
        string regionId
        int anio
        double hectareas_perdidas
        double porcentaje_perdida
        string fuente
        datetime fecha_registro
    }

    HEAT_ALERT_EVENTS {
        string id PK
        string regionId
        datetime fecha_evento
        string nivel_riesgo
        double latitud
        double longitud
        string fuente
        string descripcion
    }

    ALERT_RULES {
        string id PK
        string nombre
        string regionId
        double umbral_porcentaje_perdida
        int umbral_eventos_calor
        boolean activa
    }

    OPENEO_JOB_RUNS {
        string id PK
        string jobId UK
        string regionId
        string indicator
        datetime periodStart
        datetime periodEnd
        string status
        datetime requestedAt
        datetime updatedAt
    }

    OPENEO_INDICATOR_OBSERVATIONS {
        string id PK
        string regionId
        string indicator
        datetime observedAt
        double value
        datetime ingestedAt
    }

    DASHBOARD_REGION_SNAPSHOTS {
        string id PK
        string regionId UK
        double latestNdvi
        double latestNdmi
        string criticality
        datetime computedAt
    }
```

## Nota DUOC

Las relaciones marcadas como "l?gica" son gestionadas por aplicacion (sin FK fisica en Mongo ni FK SQL expl?cita en auth).
