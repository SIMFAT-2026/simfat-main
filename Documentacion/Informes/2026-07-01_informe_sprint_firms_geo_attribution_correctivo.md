# Informe de Sprint — FIRMS Geo-Atribución Correctivo (Slice C: Coverage-Gap Fallback)

Fecha: 2026-07-01
Sprint: Correctivo post-revert PR #14 — branch `firms-geo-attribution/slice-c-coverage-fallback`
Participantes: David Vasquez (dev), Claude (asistencia dev)
Estado: mergeado a main (PR #15), deployado a producción

---

## 1. Resumen ejecutivo

El PR #14 (geo-atribución FIRMS por polígono) fue revertido de producción inmediatamente tras el merge porque 16 de 19 regiones monitorizadas quedaron ciegas en el score FIRMS, y el job de backfill tardó 9+ horas bloqueando la disponibilidad de la app. Este sprint implementa la reimplementación correctiva con los dos problemas raíz resueltos y 10 fixes adicionales identificados en un code review de 8 ángulos independientes.

| Área | Cambio | Estado |
|---|---|---|
| Probe de cobertura | Cambiado de nivel-región a **per-COMUNA** | Cerrado, mergeado |
| Backfill bloqueante | Reemplazado por bulk async con evento explícito | Cerrado, mergeado |
| Pool fallback centroide | Ya no filtra por `regionId` persistido | Cerrado, mergeado |
| `FirmsAttributionRouter` | Único punto de entrada para lecturas FIRMS | Cerrado, mergeado |
| `FirmsScoringConstants` | Constantes compartidas entre ambos servicios | Cerrado, mergeado |
| Dashboard `countByFuente` | Filtrado por `fuente=NASA_FIRMS` | Cerrado, mergeado |
| Wire seed→backfill | `BackfillComunaIdRunner` escucha `ComunaGeometrySeededEvent` | Cerrado (hallazgo durante revisión final) |
| Tests de regresión | 103/103 pasando, incluyendo `@DataMongoTest` contra Mongo real | Verificado |

---

## 2. Incidente original y causa raíz

### 2.1 Síntoma en producción (PR #14 revertido)

- **16/19 regiones sin score FIRMS** — aparecían en `NORMAL` aunque hubiera focos activos.
- **Backfill 9+ horas** — el job corría síncronamente en el thread de `ApplicationReadyEvent` contra latencia de Atlas (~50ms/query × miles de filas).
- Solo Biobío, Ñuble y La Araucanía (las 3 regiones con archivo GeoJSON sembrado) funcionaban.

### 2.2 Causas raíz

**Causa raíz 1 — Probe de cobertura a nivel región (C1):**
El código original evaluaba si una región tenía suficientes comunas con geometría antes de decidir si usaba el path geométrico o el fallback centroide. Consecuencia: si 1 de 10 comunas de una región tenía un polígono GADM inválido (falla Decision-3), toda la región caía al path geométrico pero sin fallback → silenciosamente cero focos.

**Causa raíz 2 — Backfill síncrono bloqueante (C3/C4):**
`BackfillComunaIdRunner` usaba `.save()` por fila (N queries a Atlas) y corría en el thread de `ApplicationReadyEvent` sin mecanismo que garantizara que el seed de geometrías había terminado antes de que el backfill intentara leer los polígonos.

---

## 3. Cambios de backend

### 3.1 FirmsAttributionRouter (nuevo componente)

Extraído como único punto de entrada para lecturas FIRMS por atribución, compartido por `ComunaRiskServiceImpl` y `TerritoryRiskServiceImpl` (antes cada uno duplicaba lógica propia).

**Routing por comuna (`resolveForComuna`):**
```
si comuna.getGeometry() != null
    → findByComunaIdAndFechaEventoAfter(comunaId, since)  // path geométrico
sino
    → fallbackCandidatePool(since) + assignFocosToComuna()  // centroide retenido
```

**Routing por región (`resolveForRegion`):**
Divide las comunas de la región en `covered` (geometry != null) y `uncovered`, suma ambas contribuciones en vez de decidir todo-o-nada para toda la región.

**Pool de candidatos del fallback (FIX 2 — finding C6):**
`findByFuenteAndFechaEventoAfter("NASA_FIRMS", since)` — nunca pre-filtra por `regionId` persistido. Bajo dedup region-independent (Decision 2), el `regionId` guardado es "el leg del cron que ganó la carrera", no verdad geográfica. El centroide — no el `regionId` — decide la propiedad real.

### 3.2 BackfillComunaIdRunner (nuevo componente)

Reimplementación del job de backfill con las siguientes correcciones:

- **Bulk writes** — `BulkOperations.UNORDERED`, BATCH=500 (reducción de N queries → batches)
- **Async** — `@Async("backfillExecutor")` con `ThreadPoolTaskExecutor` single-thread dedicado
- **Ordering race-free** — escucha `ComunaGeometrySeededEvent` (publicado por `MonitoredComunasConfig` después de su `saveAll`) en vez de `ApplicationReadyEvent` + `@Order`
- **Observabilidad** — try/catch con `status=failed` + contadores `attributed/offshore/skippedNoCoords`; resultado del batch parcial final capturado y logueado

**Propiedad de control:** `firms.backfill.enabled=true` (configurable vía properties/Railway).

### 3.3 MonitoredComunasConfig (modificado)

Agrega `eventPublisher.publishEvent(new ComunaGeometrySeededEvent(this))` al final de `ensureMonitoredComunas()`, después de que todos los `saveAll` de comunas por región completan. Esto dispara el backfill de forma determinística y sin race condition.

### 3.4 FirmsScoringConstants (nuevo)

Constantes compartidas para evitar divergencia entre `ComunaRiskServiceImpl` y `TerritoryRiskServiceImpl`:
- `FIRMS_MAX_COUNT = 5.0`
- `FIRMS_MAX_FRP = 80.0`
- `FIRMS_COUNT_CRITICO = 4`
- `FIRMS_FRP_CRITICO = 60.0`

### 3.5 DashboardSnapshotServiceImpl (fix)

`countByRegionIdAndFechaEventoBetween` → `countByRegionIdAndFuenteAndFechaEventoBetween(regionId, "NASA_FIRMS", ...)`. Evita incluir alertas manuales/CONAF en el contador de focos FIRMS del dashboard.

### 3.6 HeatAlertEventRepository (modificado)

Métodos nuevos para atribución:
- `findByComunaIdAndFechaEventoAfter` — path geométrico por comuna
- `findByComunaIdInAndFechaEventoAfter` — path geométrico bulk (region-level)
- `findByFuenteAndFechaEventoAfter` — pool de candidatos del fallback
- `streamByFuenteAndComunaIdIsNull` — stream para backfill
- `countByRegionIdAndFuenteAndFechaEventoBetween` — dashboard filtrado

Método eliminado: `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente` — reemplazado en PR anterior por el dedup region-independent `existsByLatitudAndLongitudAndFechaEventoAndFuente`.

---

## 4. Cambios de base de datos

**MongoDB únicamente — sin migraciones PostgreSQL este sprint.**

- `HeatAlertEvent.comunaId` — campo ya existente, el backfill lo puebla retroactivamente
- `ComunaInfo.geometry` — campo ya existente (GeoJsonMultiPolygon, índice 2dsphere sparse)
- No hay colecciones nuevas ni esquemas nuevos

---

## 5. Tests agregados

| Archivo | Tipo | Cubre |
|---|---|---|
| `FirmsAttributionRouterTest` | Unitario (Mockito) | Probe per-COMUNA, pool sin regionId, split covered/uncovered, dedup cross-región |
| `BackfillComunaIdRunnerIntegrationTest` | Integración (`@DataMongoTest` + Mongo real) | Atribución por `$geoIntersects`, idempotencia, flag disabled, múltiples batches |
| `BackfillComunaIdRunnerTest` | Unitario (Mockito + Logback appender) | FIX 3 (status=failed observable), FIX 8 (skippedNoCoords), FIX 9 (batch parcial logueado) |
| `ComunaGeoAttributionRepositoryIntegrationTest` | Integración (`@DataMongoTest` + Mongo real) | `$geoIntersects` por índice 2dsphere, punto afuera, borde compartido, comuna sin geometría |
| `ComunaRiskServiceImplTest` | Unitario (Mockito) | Delegación a FirmsAttributionRouter, umbrales CRITICO estandarizados |
| `TerritoryRiskServiceImplTest` | Unitario (Mockito) | Delegación a FirmsAttributionRouter, umbrales CRITICO estandarizados |
| `NasaFirmsServiceImplTest` | Unitario (Mockito + MockWebServer) | Atribución en insert, offshore con comunaId null, dedup region-independent |
| `DashboardSnapshotServiceImplTest` | Unitario (Mockito) | Llama a `countByRegionIdAndFuenteAndFechaEventoBetween`, nunca al método anterior |

**Resultado:** 103/103 tests pasando (BUILD SUCCESS). Todos los tests de integración verificados con Docker MongoDB local (`:27017`).

---

## 6. Hallazgo durante revisión final (sesión 2026-07-01)

Durante la revisión manual de todos los archivos antes del commit, se detectó que **FIX 4 estaba implementado a medias**:

- `MonitoredComunasConfig` publicaba `ComunaGeometrySeededEvent` correctamente ✓
- `BackfillComunaIdRunner` todavía escuchaba `ApplicationReadyEvent` + `@Order(LOWEST_PRECEDENCE)` ✗

El wire del listener nunca fue race-free a pesar de que la infraestructura existía. Los tests no lo detectaron porque instancian el runner directamente (sin el mecanismo `@EventListener`). Corregido antes del commit con el cambio de anotación y limpieza de imports.

**Lección:** siempre verificar ambos lados de un event-listener wire (publisher + subscriber) por separado. Los tests unitarios/integración que instancian el componente directamente no ejercitan el mecanismo de publicación/suscripción de Spring.

---

## 7. Acciones de seguimiento

1. Verificar si el módulo de reportería exporta FIRMS leyendo `HeatAlertEvent` directamente por `regionId` (bypasando `FirmsAttributionRouter`) o consume `ComunaRiskSnapshot` (que ya tiene la atribución correcta). Si es el primer caso, el export debe pasar por el router.
2. Ejecutar checklist de pruebas manuales en producción para confirmar que las 16 regiones antes ciegas ahora muestran score FIRMS (vía fallback centroide) y que las 3 regiones con GeoJSON muestran atribución geométrica.
3. Revisar las vulnerabilidades de Dependabot reportadas por GitHub (14 high, 9 moderate, 1 low) — no investigadas en este sprint.
