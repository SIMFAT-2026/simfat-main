# Estado de Modulos Frontend - SIMFAT

- Fecha: 2026-04-23
- Version: 1.0
- Alcance: modulos actualmente visibles/demostrables en `simfat-web` y soportados por `simfat-backend`.

## Contexto de desarrollo

El estado actual del producto es **prototipo funcional**.  
Los modulos ya permiten demostracion de flujo end-to-end, pero se mantendran en evolucion para incorporar nuevas variables de trabajo territorial en mapa (capas, filtros y reglas de negocio adicionales).

## Estado por modulo (frontend)

| Modulo visible en frontend | Objetivo funcional | Endpoints backend principales | Estado actual | Observaciones de desarrollo |
|---|---|---|---|---|
| Autenticacion y sesion | Registro, login, perfil y recuperacion de acceso | `/api/auth/*` (`register`, `login`, `refresh`, `me`, `logout`, `forgot-password`, `reset-password`) | Prototipo operativo | Flujo base estable con JWT y refresh rotatorio. Continuar con endurecimiento de produccion y observabilidad. |
| Dashboard ejecutivo | Resumen de indicadores, alertas y regiones criticas | `/api/dashboard/summary`, `/critical-regions`, `/loss-trend`, `/alerts-summary`, `/data-freshness` | Prototipo operativo | Responde desde snapshots/observaciones para bajo costo de lectura en UI. |
| Monitoreo territorial en mapa | Visualizacion de capas territoriales por region y rango temporal | `/api/territory/bounds`, `/api/territory/layers`, `/api/dashboard/indicators/map` | Prototipo operativo en evolucion | Base funcional activa; proxima fase incluye nuevas variables/capas para analisis territorial mas fino. |
| Comunidad y coordinacion local | Mural, recursos y contactos por region | `/api/community/board`, `/api/community/resources`, `/api/community/contacts` | Prototipo operativo | CRUD principal disponible para demostracion colaborativa territorial. |
| Reportes ciudadanos y seguimiento | Creacion de reportes geolocalizados con evidencia | `/api/citizen-reports` (GET/POST/PATCH/DELETE) | Prototipo operativo | Soporta adjuntos con priorizacion Supabase y fallback local para demo de galeria/continuidad. |

## Resumen ejecutivo del estado

- El conjunto de modulos visibles en frontend se encuentra en estado **demostrable** para iteracion academico-profesional.
- El nivel actual es **prototipo**, no cierre final de producto.
- La siguiente ola de desarrollo prioriza **variables adicionales para mapa** y refinamiento de reglas territoriales.

## Linea de trabajo siguiente (mapa)

1. Ampliar variables geoespaciales por capa (territorial, ambiental y comunitaria).
2. Consolidar filtros temporales y por region para comparativas historicas.
3. Fortalecer calidad de datos y trazabilidad de fuentes por feature del mapa.
4. Alinear contratos backend/frontend para mantener compatibilidad durante iteraciones.

