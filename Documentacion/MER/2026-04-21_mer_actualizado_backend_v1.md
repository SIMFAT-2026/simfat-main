# MER Actualizado - SIMFAT Backend

- Fecha: 2026-04-21
- Version: 1.0
- Nota: modelo hibrido (relacional + documental).

## MER (vista logica)

```mermaid
erDiagram
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
      string replaced_by_token_id
      string created_by_ip
      string user_agent
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
      float hectareas_bosque_referencia
      string aoi_bbox
    }

    FOREST_LOSS_RECORDS {
      string id PK
      string region_id
      int anio
      float hectareas_perdidas
      float porcentaje_perdida
      string fuente
      datetime fecha_registro
    }

    HEAT_ALERT_EVENTS {
      string id PK
      string region_id
      datetime fecha_evento
      string nivel_riesgo
      float latitud
      float longitud
      string fuente
      string descripcion
    }

    ALERT_RULES {
      string id PK
      string nombre
      string region_id
      float umbral_porcentaje_perdida
      int umbral_eventos_calor
      boolean activa
    }

    OPENEO_JOB_RUNS {
      string id PK
      string job_id UK
      string region_id
      string indicator
      datetime period_start
      datetime period_end
      string status
      datetime requested_at
      datetime updated_at
      datetime finished_at
    }

    OPENEO_INDICATOR_OBSERVATIONS {
      string id PK
      string region_id
      string indicator
      datetime observed_at
      float value
      string source
      datetime ingested_at
    }

    DASHBOARD_REGION_SNAPSHOTS {
      string id PK
      string region_id UK
      float latest_ndvi
      float latest_ndmi
      float ndvi_trend_30d
      float ndmi_trend_30d
      int heat_alerts_7d
      float forest_loss_current_pct
      string criticality
      datetime computed_at
    }

    APP_USERS ||--o{ REFRESH_TOKENS : "user_id"
    APP_USERS ||--o{ PASSWORD_RESET_TOKENS : "user_id"

    REGIONS ||--o{ FOREST_LOSS_RECORDS : "region_id"
    REGIONS ||--o{ HEAT_ALERT_EVENTS : "region_id"
    REGIONS ||--o{ ALERT_RULES : "region_id (opcional)"
    REGIONS ||--o{ OPENEO_JOB_RUNS : "region_id"
    REGIONS ||--o{ OPENEO_INDICATOR_OBSERVATIONS : "region_id"
    REGIONS ||--|| DASHBOARD_REGION_SNAPSHOTS : "region_id"
```

## Observaciones

- Las relaciones con `region_id` y `user_id` son controladas por aplicacion.
- SQL se versiona con Flyway (`V1__create_auth_tables.sql`).
- Mongo prioriza indices en flujos operacionales (`openeo_*`, snapshots).
