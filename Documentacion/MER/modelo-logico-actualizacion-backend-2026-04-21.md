# Modelo Logico de Actualizacion - SIMFAT

- Fecha: 2026-04-21
- Version: 1.0

## Objetivo

Alinear backend con la evolucion del producto SIMFAT hacia monitorizacion territorial, coordinacion comunitaria y reportes ciudadanos, maximizando reutilizacion del stack actual.

## Modelo l?gico actual (post-actualizacion)

1. **Autenticaci?n y seguridad (SQL)**
   - Usuarios (`app_users`)
   - Sesiones renovables (`refresh_tokens`)
   - Recuperacion de credenciales (`password_reset_tokens`)

2. **Nucleo territorial (MongoDB)**
   - Catalogo de regiones (`regions`)
   - Perdida forestal hist?rica (`forest_loss_records`)
   - Eventos de calor georreferenciados (`heat_alert_events`)
   - Reglas de alerta (`alert_rules`)
   - Integraci?n openEO (`openeo_job_runs`, `openeo_indicator_observations`)
   - Snapshot para dashboard (`dashboard_region_snapshots`)

## Reglas logicas relevantes

- `regionId` es clave de integraci?n transversal entre m?dulos territoriales.
- El frontend no debe calcular agregaciones pesadas; el backend expone datos agregados/simplificados.
- Los datos de openEO pasan por backend antes de llegar al frontend.
- El dashboard se abastece desde snapshots para reducir latencia y costo.

## Extension prevista para la siguiente iteraci?n

Sin romper el modelo actual, se proyecta incorporar:

- `community_resources` (biblioteca y protocolos)
- `community_contacts` (red de contactos y emergencias)
- `citizen_reports` (reportes geolocalizados con categoria, estado y evidencia)
- `territory_layers_cache` (metadatos de capas + TTL por region)

## Criterios de dise?o

- Adaptacion incremental sobre reconstruccion.
- Contratos de API por m?dulo con versionado simple.
- Foco de costo: cache/TTL, filtros por region, payloads pequenos.
- Trazabilidad: logs de carga, jobs y evidencias QA por iteraci?n.
