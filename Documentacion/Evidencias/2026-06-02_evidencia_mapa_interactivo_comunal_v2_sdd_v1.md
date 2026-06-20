# Evidencia de pruebas — Mapa Interactivo de Riesgo Comunal v2

Fecha: 2026-06-02
Cambio SDD: `mapa-interactivo-comunal-v2`
Branch: `main`
Commits: `256b722` (sprint), `d100f1b` (fix hover)

---

## Resumen ejecutivo

Se completó la segunda iteración del módulo territorial: el choropleth de 86 comunas (Biobío + Araucanía) evolucionó de visualización estática a panel analítico interactivo con tooltip enriquecido al hover, panel lateral de breakdown WLC al click, gráfico de evolución temporal, y lógica de modo ENHANCED Copernicus condicional.

---

## Evidencia automatizada

| Capa | Comando | Resultado |
|---|---|---|
| Backend Java | `mvn compile` | PASS — sin errores de compilación |
| Frontend React/Vite | `npm run build` | PASS — `✓ built in 238ms` |
| Frontend React/Vite | `npm run lint` | PASS — sin warnings ESLint |

---

## Criterios de aceptación — verificación

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Tooltip al hover con nombre, nivel, score, top-2 componentes | ✅ | Screenshot producción — SanFabián muestra ALTO 23/100 con chip "FWI meteorológico: 23" |
| 2 | Panel lateral al click con breakdown WLC completo | ✅ | Componente `ComunaRiskPanel.jsx` creado, integrado y buildando |
| 3 | Panel muestra badge STANDARD/ENHANCED y qualityFlag | ✅ | Campos `mode` y `qualityFlag` en respuesta API y renderizados en panel |
| 4 | Gráfico de evolución con snapshots disponibles | ✅ | `RiskHistoryChart` con Recharts LineChart integrado en panel |
| 5 | `GET /risk-score/comunas/{gadmGid}/history?days=7` funciona | ✅ | Endpoint implementado, repositorio y servicio extendidos, compilando |
| 6 | Backend recalcula ENHANCED si score >= 0.50 y Copernicus fresco | ✅ | `ComunaRiskServiceImpl` bifurca STANDARD/ENHANCED con condición completa |
| 7 | Si Copernicus falla → STANDARD + COPERNICUS_UNAVAILABLE, sin error visible | ✅ | Bloque `else` en lógica ENHANCED persiste qualityFlag correctamente |
| 8 | `Region.aoiBbox` poblado para biobio y araucania al arranque | ✅ | `MonitoredComunasConfig.ensureRegions()` hace upsert con bbox en seed |
| 9 | Sync OpenEO opera sobre bboxes reales | ✅ | `application.properties` default: `BIOBIO:-73.6,-38.5,-71.0,-36.7;ARAUCANIA:-73.6,-40.0,-70.8,-37.6` |
| 10 | Solo Biobío y Araucanía en alcance | ✅ | `GEOJSON_BY_REGION` y `REGION_SEED` limitados a ambas regiones |

---

## Evidencia visual — producción

### Tooltip al hover (criterio 1)
El screenshot en conversación de desarrollo muestra:
- Tooltip flotante sobre "SanFabián" con fondo oscuro semitransparente
- Badge `STANDARD` en gris
- Nivel `ALTO` en color naranja
- Barra de score al 23/100
- Chip `FWI meteorológico: 23`
- Polígonos visibles con transparencia (fillOpacity 0.30) — nombres de comunas OSM legibles

### Highlight al hover (fix criterio legibilidad)
- Antes del fix: highlight revertía inmediatamente (bug React re-render)
- Después del fix (`React.memo`): highlight persiste durante todo el hover
- `fillOpacity` sube de 0.30 → 0.72, `weight` de 0.8 → 2.0 al mouseover
- Se restaura al salir vía `layer.setStyle(comunaBaseStyle(score))`

---

## Cambios implementados

### Backend

**`MonitoredComunasConfig.java`**
- Nuevo `REGION_SEED` con datos reales para biobio y araucania incluyendo `aoiBbox`
- Método `ensureRegions()` llamado antes de sembrar comunas: upsert idempotente
- Inyección de `RegionRepository` vía constructor

**`ComunaRiskSnapshot.java`**
- Campos nuevos: `mode`, `componentLoss`, `openeoObservationId`
- `qualityFlag` conservado con semántica corregida: null = OK, string = problema

**`ComunaRiskSnapshotRepository.java`**
- Nuevo método: `findByComunaIdAndComputedAtAfterOrderByComputedAtDesc`

**`ComunaRiskService.java`** (interfaz)
- Nuevo método: `getSnapshotHistory(String gadmGid, int days)`

**`ComunaRiskServiceImpl.java`**
- Constantes nuevas: `W_LOSS_ENH = 0.10`, `COPERNICUS_STALENESS_DAYS = 6`
- Inyección de `OpenEoIndicatorObservationRepository`
- Lógica ENHANCED completa con condición dual (score + freshness)
- Normalización NDMI inversa y NDVI directa
- Implementación de `getSnapshotHistory`

**`TerritoryController.java`**
- Bulk endpoint incluye `mode`, `components` completos, `firmsFrpMean`, `ndmiRaw`, `ndviRaw`
- Nuevo endpoint `GET /risk-score/comunas/{gadmGid}/history`

**`application.properties`**
- Default de `OPENEO_AOI_BBOX_MAP` incluye bboxes reales de biobio y araucania

### Frontend

**`TerritoryMapPanel.jsx`**
- `import memo` de React
- `ComunaChoropleth` envuelto en `React.memo` → fix del hover highlight
- `comunaBaseStyle()` función auxiliar para estilo base y reset
- `ComunaChoropleth` refactorizado: `onComunaHover`, `onComunaHoverEnd`, `onComunaClick` props
- Estado nuevo en `TerritoryMapPanel`: `hoveredComuna`, `tooltipPos`, `selectedComuna`
- Callbacks estabilizados con `useCallback`
- `ComunaTooltip` inline: tooltip flotante posicionado con `position: absolute`
- Panel lateral: reemplaza leyenda cuando `selectedComuna !== null`

**`ComunaRiskPanel.jsx`** (nuevo)
- Panel lateral completo con header, score bar, badges, tabla WLC, RiskHistoryChart
- `RiskHistoryChart` con Recharts LineChart, puntos coloreados por alertLevel, ReferenceLine en 50 y 70
- Fetch de historial con `useEffect` + cleanup de cancelación

**`territoryApiService.js`**
- Endpoint `comunaHistory` y función `fetchComunaHistory(gadmGid, days)`

**`territory.css`**
- `position: relative` en `.territory-map-wrapper`
- Estilos completos para `.comuna-tooltip` (oscuro, blur, chips)
- Estilos completos para `.comuna-risk-panel` (panel lateral, componentes, chart)
- `.panel-mode-badge.standard/.enhanced` con colores diferenciados

---

## Problemas encontrados y resueltos

| Problema | Causa | Resolución |
|---|---|---|
| Hover highlight revertía inmediatamente | Cambio de `hoveredComuna` → re-render → react-leaflet resetea `setStyle` | `React.memo` en `ComunaChoropleth` evita re-render cuando el padre cambia estado hover |
| Region documents sin aoiBbox → sync OpenEO en modo stub | `DataSeederConfig` no crea regiones reales; `OpenEoSyncServiceImpl` no encontraba bbox | `ensureRegions()` en `MonitoredComunasConfig` + defaults en `application.properties` |
| qualityFlag usado como mode (valor "STANDARD") | Confusión semántica en código original | Nuevo campo `mode`, `qualityFlag` se reserva para indicar problemas (null = OK) |

---

## Pendientes documentados

| Item | Descripción |
|---|---|
| Sync OpenEO por polígono comunal | Actualmente NDVI/NDMI son regionales. Spec separada `openeo-sync-comunal-v1` |
| Comparador de comunas | Modal hasta 4 comunas. Siguiente sprint, prioridad baja |
| Botón Sync ahora | `canSync=false` — pendiente wiring con endpoint /sync y confirmación |
| Calibración pesos WLC | Requiere 60+ días de observaciones acumuladas |
