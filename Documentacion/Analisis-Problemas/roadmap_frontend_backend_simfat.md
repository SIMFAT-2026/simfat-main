# Hoja de Ruta Frontend + Backend SIMFAT

Fecha: 2026-04-21  
Repositorio base auditado: `simfat-web` (rama `develop`)  
Alcance de este documento: adaptacion incremental de arquitectura, no reconstruccion total.

## a) Proposito del documento

Definir una ruta tecnica realista para evolucionar `simfat-web` desde su estado actual (dashboard tecnico + CRUD operativos) hacia una plataforma web funcional de inteligencia territorial y articulacion comunitaria para prevencion, monitoreo y alerta temprana de incendios forestales.

Este documento prioriza:

- Reutilizacion maxima del codigo existente.
- Bajo costo operacional (API, tokens, procesamiento).
- Escalabilidad progresiva sin sobredisenio.
- Claridad de dependencias con `simfat-backend` y `openeo-service`.

## b) Vision actualizada del producto

SIMFAT debe operar como plataforma integral con foco inicial en Biobio y La Araucania, articulando:

1. Coordinacion comunitaria (comunicacion y protocolos).
2. Reportes ciudadanos (evidencia territorial y trazabilidad).
3. Monitorizacion territorial (mapa interactivo + analitica integrada).
4. Alertas operativas (eventos y reglas conectadas al contexto espacial).

La analitica deja de estar aislada como "tablero tecnico" y pasa a estar integrada al flujo de monitorizacion territorial.

## c) Modulos priorizados

## A. Coordinacion comunitaria (prioridad alta)

- Mural comunitario (avisos, acciones recomendadas, estado operativo).
- Biblioteca de recursos (guias, protocolos, material de prevencion).
- Directorio de contactos y numeros de emergencia por region/comuna.
- Estructura preparada para coordinacion multi-actor futura (municipios, brigadas, juntas de vecinos).

## B. Reportes ciudadanos (prioridad alta)

- Formulario de reporte con geolocalizacion, categoria, descripcion y fotos.
- Historial de reportes con estado (recibido, validado, descartado, derivado).
- Conexion gradual con alertas y monitorizacion (sin acoplar logica compleja en frontend).

## C. Monitorizacion territorial (nucleo principal)

- Mapa principal interactivo.
- Capas territoriales: perdida forestal, NDVI, NDMI, alertas, reportes.
- Reutilizacion del dashboard existente dentro de esta vista (no reconstruccion).

## D. Alertas (ya implementado, adaptar)

- Mantener CRUD actual como base operativa.
- Integrar mejor con mapa principal y reglas de priorizacion territorial.

## d) Diagnostico del frontend existente

## Stack y capacidades actuales (confirmado en codigo)

- React 18 + Vite + React Router + Axios + Recharts.
- Arquitectura por capas base ya presente:
  - `src/api` (cliente HTTP, errores, endpoints)
  - `src/services` (servicios CRUD)
  - `src/features/dashboard` (modulo analitico moderno con hooks TS)
  - `src/pages`, `src/components`, `src/layouts`, `src/auth`, `src/router`.
- Autenticacion JWT robusta con refresh y guards de ruta.

## Rutas actuales

- Publicas: `/login`, `/register`, `/forgot-password`, `/reset-password`.
- Protegidas: `/`, `/dashboard`, `/regions`, `/forest-loss`, `/alerts`, `/rules`.
- Layout comun protegido ya implementado (`Navbar + Sidebar + main + Footer`).

## Modulos implementados y reutilizables

- Dashboard funcional conectado a backend real (summary, critical regions, loss trend, alerts summary, latest/series/map indicadores, data freshness, sync run).
- Modulo de alertas CRUD operativo (incluye latitud/longitud y filtro por region).
- CRUD de regiones, perdida forestal y reglas (base util para backoffice).
- Componentes reutilizables ya maduros:
  - `DataTable`, `FilterBar`, `SectionTitle`, `EmptyState`, `ErrorMessage`, `ConfirmModal`, `MetricCard`.
- Capa de datos dashboard con cache en memoria + deduplicacion de requests (`useDashboardResource`).

## Fricciones con la nueva vision

1. Navegacion actual orientada a CRUD tecnico, no a flujos de producto (comunidad/reportes/territorio).
2. No existe libreria cartografica; "mapa" actual es una lista con barras (no mapa geoespacial real).
3. No existe modulo de coordinacion comunitaria.
4. No existe modulo de reportes ciudadanos con fotos y geolocalizacion asistida.
5. No hay lazy loading de rutas/pantallas.
6. Coexisten capas antiguas y nuevas de dashboard:
   - `src/services/dashboardService.js` ya no se usa en pantallas activas.
7. Mezcla JS + TS sin `tsconfig`, por lo que no hay type-checking formal de TypeScript.
8. Doble configuracion de menu (Navbar y Sidebar) con riesgo de divergencia de rutas.
9. README y `.env.example` presentan diferencias de puerto backend (deuda operativa menor).

## Ausencias clave para siguiente iteracion

- Motor de mapa interactivo + capas GeoJSON.
- Contratos API para reportes ciudadanos y recursos comunitarios.
- Contrato API de capas territoriales filtrables por region/rango/indicador.
- Mecanismo de adjuntar imagenes de reporte (upload directo o URL prefirmada).

## e) Estrategia de reutilizacion de codigo existente

## Reutilizar sin cambios grandes

- `MainLayout`, `AuthContext`, guards de rutas y flujo JWT.
- Componentes base de UI (`DataTable`, `FilterBar`, `ConfirmModal`, `SectionTitle`).
- Hook de feedback y patrones de carga/error/vacio.
- Capa de adaptacion de respuestas en dashboard (`dashboardApiService.ts`) para datos imperfectos.
- `useDashboardResource` como base de cache liviano para reducir llamadas.

## Reutilizar con adaptacion

- `DashboardPage.tsx` y componentes `features/dashboard/*` pasan a subvista de `Monitorizacion Territorial`.
- `AlertsPage.jsx` se mantiene como base de "Alertas operativas" y se integra al contexto de mapa.
- `RegionsPage.jsx`, `ForestLossPage.jsx`, `RulesPage.jsx` migran a seccion de administracion (no ruta principal de navegacion).
- `IndicatorsMapLayer.tsx` evoluciona desde lista-resumen a panel de leyenda/inspeccion conectado al mapa real.

## Reducir deuda tecnica incremental

- Unificar navegacion en una sola fuente de verdad (config central de menu/rutas).
- Remover o deprecar `src/services/dashboardService.js` cuando se confirme no uso.
- Introducir `React.lazy` + `Suspense` por modulo principal.

## f) Hoja de ruta frontend

## Fase 0 (corta, estabilizacion de base)

1. Crear config central de navegacion y rutas (`src/router/navigationConfig.js`).
2. Reorganizar menu principal a:
   - `/territorio` (monitorizacion)
   - `/comunidad` (coordinacion)
   - `/reportes` (ciudadanos)
   - `/alertas` (operativo)
3. Mantener accesibles rutas actuales en seccion admin:
   - `/admin/regions`, `/admin/forest-loss`, `/admin/rules`.
4. Activar lazy loading por rutas de primer nivel.

## Fase 1 (nucleo: monitorizacion territorial)

1. Crear `src/features/territory` con:
   - `components/TerritoryMapPanel.jsx`
   - `components/LayerControls.jsx`
   - `components/TerritoryLegend.jsx`
   - `hooks/useTerritoryLayers.js`
2. Integrar mapa principal en nueva pagina `TerritoryPage`.
3. Reusar dashboard actual como bloque analitico inferior (opcion recomendada):
   - Mapa arriba (foco operativo inmediato).
   - Dashboard debajo con scroll (reuso maximo y menor complejidad).

Justificacion de opcion:

- Mas eficiente que tabs para el caso operativo (evita cambio constante de contexto).
- Menor costo de implementacion al reutilizar `DashboardPage` como seccion embebida.
- Evita duplicar estado/filtros entre "tab mapa" y "tab analitica".

## Fase 2 (coordinacion comunitaria)

1. Crear `CommunityCoordinationPage`.
2. Submodulos UI:
   - mural comunitario
   - biblioteca de recursos
   - directorio de contactos/protocolos
3. Reusar componentes existentes de tabla/filtro/estado vacio para paneles administrativos ligeros.

## Fase 3 (reportes ciudadanos)

1. Crear `CitizenReportsPage` con dos zonas:
   - formulario de envio
   - listado/seguimiento de reportes
2. Campos base: categoria, descripcion, lat/lon, foto(s), region/comuna.
3. Integrar geolocalizacion del navegador como ayuda (opt-in).
4. Evitar procesamiento pesado de imagen en frontend (solo validaciones minimas, compresion opcional limitada).

## Fase 4 (alertas integradas)

1. Adaptar `AlertsPage` al nuevo contexto (filtros compartidos con territorio).
2. Asociar alertas a capas territoriales y reportes (join por region, comuna, proximidad simple).
3. Preparar vista de priorizacion territorial (alertas + reportes + estado NDVI/NDMI).

## g) Hoja de ruta backend (requisitos para sostener frontend)

## Contratos API minimos nuevos/ajustados

## 1) Monitorizacion territorial

- `GET /api/territory/layers`
  - Query: `regionId`, `from`, `to`, `indicators=NDVI,NDMI,LOSS,ALERTS,REPORTS`, `zoom`
  - Respuesta: `FeatureCollection` simplificada por capa.
  - Debe incluir `dataQuality`, `source`, `observedAt`, `isCached`.

- `GET /api/territory/summary`
  - Resumen rapido para tarjetas de cabecera del mapa.

- `GET /api/territory/bounds?regionId=...`
  - Extent inicial para centrar mapa (Biobio/Araucania).

## 2) Reportes ciudadanos

- `POST /api/citizen-reports`
  - Multipart o flujo con URL prefirmada.
  - Campos: categoria, descripcion, latitud, longitud, regionId/comunaId, evidencia(s).

- `GET /api/citizen-reports`
  - Filtros por region, estado, fecha, categoria.

- `PATCH /api/citizen-reports/{id}/status`
  - Estados operativos: `RECIBIDO | VALIDADO | DESCARTADO | DERIVADO`.

## 3) Coordinacion comunitaria

- `GET /api/community/resources`
- `GET /api/community/contacts`
- `GET /api/community/board`
- CRUD administrativo protegido para gestion de contenido.

## 4) Alertas integradas al mapa

- Mantener endpoints actuales de alertas.
- Agregar endpoint agregado para capa de alertas georreferenciadas:
  - `GET /api/alerts/map?regionId=&from=&to=&level=`.

## Principio tecnico backend

El backend debe entregar datos listos para visualizacion (agregados/simplificados), evitando que el frontend haga:

- geoprocesamiento pesado
- joins complejos sobre grandes colecciones
- normalizaciones costosas por request de usuario

## h) Dependencias eventuales con openeo-service

`openeo-service` no es foco de esta iteracion, pero hay dependencias indirectas que deben quedar claras:

1. Disponibilidad y frescura de NDVI/NDMI para `territory/layers`.
2. Necesidad de snapshots cacheados por region y ventana temporal para no disparar consultas openEO por cada vista.
3. Definicion de politicas de degradacion:
   - si openEO falla, backend responde ultimo snapshot + bandera `STALE`.
4. Estandarizacion de metadatos minimos por capa:
   - `source`, `observedAt`, `pipelineRunId`, `qualityFlag`.
5. Programacion de sync por ventana regional (Biobio/Araucania primero) para controlar costo.

## i) Decisiones abiertas o bloqueadores

1. Estrategia de imagenes de reportes:
   - storage backend directo vs URL prefirmada a objeto.
2. Modelo geoespacial base:
   - CERRADO en esta iteracion: geometria puntual + GeoJSON simplificado.
   - Evolucion prevista: extender contrato a poligonos cuando se valide necesidad operativa.
3. Politica de proveedor cartografico base (tiles):
   - OSM publico con limites vs proveedor dedicado/caché propio.
4. Gobernanza de catalogo comunitario:
   - quien publica/valida recursos y protocolos.
5. Definicion de SLA de frescura para indicadores satelitales por region.

## j) Recomendaciones de performance, costo y optimizacion

1. Aplicar lazy loading por modulo principal para bajar costo de carga inicial.
2. Mantener y extender cache por TTL ya existente en dashboard para nuevas capas.
3. Evitar polling agresivo; preferir refresco manual + eventos de estado cuando sea necesario.
4. Limitar payload geoespacial por nivel de zoom y region.
5. No descargar imagenes completas en listados; usar thumbnails comprimidos.
6. Centralizar filtros regionales (Biobio/Araucania) para evitar llamadas amplias por defecto.
7. Mover agregaciones y joins de capas al backend.
8. Mantener frontend como consumidor de contratos limpios y estables, no como motor ETL.

## Recomendacion cartografica (decision tecnica inicial)

Decision cerrada para esta iteracion: iniciar con **Leaflet + react-leaflet** y alcance geoespacial de
**puntos + GeoJSON simplificado**.

Se recomienda esta base por:

- Costo: sin lock-in obligatorio y sin token comercial de base.
- Curva de implementacion baja para equipo actual React.
- Buen soporte para overlays GeoJSON, markers y choropleth de complejidad media.
- Bundle razonable para alcance inicial regional.

No se recomienda partir con soluciones mas pesadas (MapLibre GL + vector tiles avanzados) hasta validar volumen real de datos y necesidades de render WebGL.

Estrategia de escalado:

1. Iteracion actual: Leaflet + GeoJSON agregado desde backend.
2. Iteracion futura: ampliar contrato de capas (incluyendo poligonos) cuando el producto lo requiera.
3. Iteracion futura avanzada: evaluar MapLibre si crecen capas, detalle espacial y volumen concurrente.
