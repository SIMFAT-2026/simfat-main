# Design: FIRMS comuna geo-attribution and standardized detection counts

## Status

Design complete. Resolves all five open decisions from the proposal with concrete, implementable choices. The two prior judgment calls are settled facts here:

- **Constant reconciliation (SETTLED):** `ComunaRiskServiceImpl`'s stricter `FIRMS_MAX_COUNT=5`, `FIRMS_COUNT_CRITICO=4`, `FIRMS_FRP_CRITICO=60` become the single standard. `TerritoryRiskServiceImpl`'s `10`/`8`/`75` are deleted as an artifact of the now-removed double-counting workaround.
- **Offshore / no-polygon match (SETTLED):** `HeatAlertEvent.comunaId` stays `null`. No synthetic nearest-comuna fallback.

## Architecture Approach

**Pattern: persist-at-the-root, single source of truth.** Geometric comuna attribution is computed exactly once — at FIRMS ingest time, inside `NasaFirmsServiceImpl.parseCsvResponse`, via a MongoDB `$geoIntersects` point-in-polygon query against persisted comuna geometry. The resulting `comunaId` (or `null`) is written onto the `HeatAlertEvent` document. All five downstream surfaces then *read* that persisted field instead of each re-deriving attribution by nearest-centroid distance.

This collapses two independent nearest-centroid implementations (`ComunaRiskServiceImpl.findNearestComuna`, `TerritoryRiskServiceImpl.findNearestRegionId`) into one indexed geometric lookup whose result is durable and auditable. The region is no longer an independent input; it is *derived* from the matched comuna's `regionId`, which structurally eliminates cross-region double-counting at the data layer rather than patching it per-reader.

**Layering / boundaries unchanged.** No new module, job, controller, or endpoint. The seed extension rides the existing `MonitoredComunasConfig` `@EventListener(ApplicationReadyEvent.class)` loop; attribution rides the existing `@Scheduled` FIRMS sync; backfill rides the same startup listener (see Decision 1). The change is additive at the schema level (two nullable fields + one index) and substitutive at the service level (swap centroid methods for `comunaId` queries).

### Component & Data Flow

```
                        STARTUP (ApplicationReadyEvent)
  GeoJSON files ──► MonitoredComunasConfig.seedFromGeoJson
                        │  parse feature.geometry → GeoJsonMultiPolygon
                        │  validate ring closure / point count (Decision 3)
                        ▼
                    ComunaInfo { id, regionId, geometry }  ──► 2dsphere index
                        │
                        ▼
                    BackfillComunaIdRunner (same startup listener, after seed)
                        │  stream comunaId==null FIRMS rows → $geoIntersects → set comunaId
                        ▼
  ┌──────────────────── heat_alert_events { ..., comunaId (nullable) } ────────────────────┐
  │                                                                                          │
  SYNC (@Scheduled)                                READ surfaces (Slice B)                   │
  NasaFirms.parseCsvResponse                       ComunaRiskServiceImpl  ── by comunaId     │
    │ per row: $geoIntersects(lat,lon)             TerritoryRiskServiceImpl ─ by region of   │
    │ resolve comunaId (or null)                                              matched comuna │
    │ dedup by (lat,lon,fecha,fuente) (Decision 2) DashboardSnapshotServiceImpl ─ + fuente   │
    ▼                                                                          filter        │
  insert HeatAlertEvent{comunaId}                  TerritoryController.firmsLayer ─ raw bbox │
                                                     view, labeled (unchanged scope)         │
  └──────────────────────────────────────────────────────────────────────────────────────┘
```

### Integration Points

| Integration point | Change |
|---|---|
| `ComunaInfo` ↔ Mongo `comunas` | New `geometry: GeoJsonMultiPolygon` + `@GeoSpatialIndexed(type = GEO_2DSPHERE)` |
| `HeatAlertEvent` ↔ Mongo `heat_alert_events` | New nullable `comunaId: String`; new compound index `{comunaId:1, fechaEvento:-1}` |
| `ComunaInfoRepository` | New `findByGeometryIntersects(Point)` derived query (Decision 4) |
| `NasaFirmsServiceImpl` | Inject `ComunaInfoRepository`; per-row attribution; new dedup key (Decision 2) |
| `MonitoredComunasConfig` | Parse `feature.geometry`; validate before save |
| Startup backfill | New `ApplicationRunner`/listener component, ordered after seed (Decision 1) |

---

## Decision 1 — Backfill execution mechanism

**Decision: one-time, idempotent, startup-gated backfill that runs inside the application's `ApplicationReadyEvent` lifecycle, AFTER `MonitoredComunasConfig` has seeded geometry, in the same JVM. Implement it as a dedicated `@Component` ordered to run after the seed, NOT inline in `MonitoredComunasConfig` and NOT as an admin endpoint or external `railway run` script.**

### Rationale

The codebase already has exactly this idiom: `MonitoredComunasConfig` does an idempotent upsert-on-startup keyed by a stable id. The backfill is the same shape — idempotent (it only ever sets `comunaId` where it is currently `null` or recomputes deterministically), re-runnable (re-running on already-attributed data is a no-op or an identical write), and it has a hard ordering dependency on the geometry seed that the startup lifecycle expresses naturally.

Rejected alternatives:

- **Inline in `MonitoredComunasConfig.ensureMonitoredComunas`** — rejected. It would couple comuna seeding (a config concern over ~85 small documents) with a data migration over ~2k event rows, and the existing per-region `try/catch` that swallows seed failures would also swallow backfill failures silently. Keep them as separate components so the backfill's success/failure and ordering are explicit and independently observable in logs.
- **Admin-triggered endpoint** — rejected. Introduces a new controller + RBAC surface for a one-shot operation, and creates a window where the app is up and Slice B readers can run *before* the operator remembers to hit the endpoint. That is precisely the "readers filter `comunaId` before backfill runs" risk the proposal flags. Startup gating removes the window entirely.
- **Standalone script via `railway run` mongosh** — rejected as the primary mechanism. The team has Railway CLI access and used `mongosh` cleanup manually this session, but a hand-run script (a) cannot reuse the Java `$geoIntersects` + `GeoJsonMultiPolygon` mapping, forcing the point-in-polygon logic to be re-expressed in raw mongosh and risking divergence from the production attribution path, and (b) is not guaranteed to run before the first deploy of Slice B. The whole point is that attribution logic exists in exactly one place. `railway run` stays available as a manual re-trigger / disaster recovery path only (the backfill component can be invoked through a feature-flagged profile if ever needed), not as the source of truth.

### Implementation shape

```java
@Component
@Order(Ordered.LOWEST_PRECEDENCE)         // runs after MonitoredComunasConfig seed
public class BackfillComunaIdRunner {

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void backfill() {
        if (!backfillEnabled) { return; }            // firms.backfill.enabled, default true
        // Stream only rows that still need attribution.
        try (Stream<HeatAlertEvent> rows =
                 repo.streamByFuenteAndComunaIdIsNull("NASA_FIRMS")) {
            rows.forEach(ev -> {
                if (ev.getLatitud() == null || ev.getLongitud() == null) return;
                Point p = new GeoJsonPoint(ev.getLongitud(), ev.getLatitud()); // lon, lat order
                comunaRepo.findOneByGeometryIntersects(p)
                          .ifPresentOrElse(
                              c -> ev.setComunaId(c.getId()),
                              () -> ev.setComunaId(null));   // explicit offshore
                repo.save(ev);
            });
        }
        LOGGER.info("firms_backfill status=done attributed={} offshore={}", ...);
    }
}
```

Notes:
- **Idempotency / re-runnability:** the query filter is `comunaId IS NULL`. Once a row is attributed it is skipped on the next boot. A row that legitimately resolves to `null` (offshore) WILL be re-evaluated on every boot — this is acceptable (it is a no-op write that re-confirms `null`) and cheap because offshore rows are a small minority. If even that re-scan is undesirable later, add a `comunaIdResolvedAt` marker; not needed for ~2k rows now.
- **Order guarantee:** `@Order(LOWEST_PRECEDENCE)` on the `@EventListener` ensures it fires after `MonitoredComunasConfig`'s default-order listener, so geometry + 2dsphere index exist before the first `$geoIntersects` runs.

### Ordering / gating constraint (explicit, MANDATORY)

> **Slice B readers MUST NOT query `heat_alert_events` by `comunaId` until the backfill has completed for the deployed environment.**

Concretely:
- Slice A (schema + seed + sync attribution + backfill) ships and runs to completion FIRST. After the first successful boot with the backfill component present, every FIRMS row has either a real `comunaId` or an explicit `null`.
- Slice B (swapping `ComunaRiskServiceImpl` / `TerritoryRiskServiceImpl` to query by `comunaId`) MUST NOT be merged/deployed until that backfill boot has happened. Because the backfill runs at startup and Slice B reads also start at startup, deploying them together is safe ONLY because the backfill listener is ordered before any scheduled read recompute (the FIRMS sync and risk recompute crons fire on a schedule, not at boot; the first scheduled recompute is minutes-to-hours later). The risk recompute cron (`0 30 1,13`) and FIRMS sync cron (`0 0 */12`) do not run at `ApplicationReadyEvent`, so the backfill always wins the race within a single boot.
- **Verification gate before Slice B:** a query for `count({fuente:'NASA_FIRMS', comunaId: {$exists:false}})` must return 0 post-backfill. This is the success-criteria check and the go/no-go signal for enabling Slice B reads.

---

## Decision 2 — Dedup key rethink in `parseCsvResponse`

**Decision: drop `regionId` from the dedup key. New dedup is geometric identity of the physical detection: `(latitud, longitud, fechaEvento, fuente)`. Keep per-region bbox sync as-is — do NOT change the sync loop to carve out neighbor areas. Dedup and display by true identity downstream.**

### Rationale

Today's `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente` includes `regionId`, so the *same physical VIIRS pixel* that falls inside two overlapping region bboxes (Biobío/Ñuble, Biobío/Araucanía) passes the existence check separately on each region's cron leg and is inserted twice — once per region. That duplication is the root cause the whole change exists to kill. Removing `regionId` from the key makes the second insert a no-op: the point already exists, identified by where it physically is and when, independent of which region's bbox fetched it.

A FIRMS detection's true identity is `(lat, lon, acq datetime, source)`. Two different regions fetching the same NASA pixel are fetching the *same fact*, so they must collapse to one row. The first leg to see it wins and persists it (with its geometrically-correct `comunaId`); subsequent legs short-circuit.

### Why NOT change the sync loop (carve-out approach)

An alternative was: make each region's sync skip the slice of its bbox that geographically belongs to a neighbor's comuna, so the same point is only ever fetched by one leg. **Rejected** because:
- It pushes geometry into the *fetch* phase (deciding which bbox sub-area to request from NASA), but NASA's `/area/csv` API takes a single rectangular bbox — you cannot request a non-rectangular carve-out. You would have to fetch the full bbox and then discard, which is exactly what dedup-on-insert already does, only more complex and stateful.
- Region bboxes legitimately overlap by design (they are padded rectangles around irregular regions). Trying to make them disjoint at fetch time would require pre-computing region-ownership tiles — significant new machinery for zero benefit over insert-time dedup.
- Keeping per-region sync preserves resilience: if one region's leg fails (HTTP error, timeout), the overlapping neighbor still picks up the shared border detections.

So: **per-region sync stays; identity-dedup at insert collapses duplicates; `comunaId` (and the region derived from it) is the canonical attribution for all downstream counting.**

### Concrete implementation

New repository method (replaces the old one; old method is deleted once `parseCsvResponse` migrates):

```java
boolean existsByLatitudAndLongitudAndFechaEventoAndFuente(
    Double latitud, Double longitud, LocalDateTime fechaEvento, String fuente);
```

In `parseCsvResponse`, the dedup check becomes region-independent, and on a fresh insert we attribute `comunaId` BEFORE saving:

```java
if (heatAlertEventRepository.existsByLatitudAndLongitudAndFechaEventoAndFuente(
        lat, lon, fechaEvento, SOURCE)) {
    continue;                       // same physical detection already persisted by any region leg
}
...
String comunaId = comunaRepository
    .findOneByGeometryIntersects(new GeoJsonPoint(lon, lat))  // lon, lat
    .map(ComunaInfo::getId)
    .orElse(null);                  // offshore → null (SETTLED behavior)
event.setRegionId(regionId);        // kept: the fetching region, for the raw bbox map view
event.setComunaId(comunaId);        // canonical geo-attribution used by Slice B counts
```

`regionId` is retained on the document (the raw bbox map/dashboard view in `TerritoryController.firmsLayer` still legitimately scopes by fetching region). But it is no longer part of dedup or of risk-count attribution — risk counts derive region from `comunaId`'s comuna.

**Within-batch dedup caveat:** the existence check hits the DB per row; rows accumulated in the same `toSave` list within one `parseCsvResponse` call are not yet persisted, so two identical rows in one CSV would both pass. NASA does not emit exact duplicates within one area response, and the cross-region duplication (the real problem) happens across separate cron legs / separate `saveAll` calls, which the DB check catches. A defensive in-memory `Set<seenKey>` per parse call can be added cheaply; recommended but low priority.

---

## Decision 3 — 2dsphere index creation & invalid-geometry risk

**Decision: validate each comuna's geometry at seed time, BEFORE the 2dsphere index is built. On an invalid/unparseable geometry: log a warning, SKIP persisting that single comuna's `geometry` field (persist the rest of the ComunaInfo without geometry), and CONTINUE startup. Do NOT crash the application. The 2dsphere index is declared as a sparse index so comunas without geometry do not block index creation.**

### Rationale

The application must boot even if one GADM polygon is malformed — a single bad comuna geometry must not take down the whole territory monitoring system (life-safety context). But a comuna silently lacking geometry must be *observable*, so detections inside it can be diagnosed as "falling through to `null`" rather than appearing as a mysterious data gap.

Two failure modes to defend against:
1. **Parse-time invalidity** — `feature.geometry` is not a well-formed MultiPolygon (wrong ring nesting, non-closed rings, too few points). Caught when converting JSON → `GeoJsonMultiPolygon`.
2. **Index-build invalidity** — geometry parses but MongoDB's 2dsphere indexer rejects it (self-intersection, duplicate consecutive vertices). With a **sparse** 2dsphere index, only documents that *have* the field are indexed; a self-intersecting polygon that slips past the seed validator would still fail at index build for that document.

### Implementation shape

Seed-time validation in `MonitoredComunasConfig.seedFromGeoJson`:

```java
GeoJsonMultiPolygon geometry = null;
try {
    geometry = parseMultiPolygon(feature.path("geometry"));   // Jackson → GeoJsonMultiPolygon
    validateRings(geometry);   // each ring: >= 4 positions, first == last (closed)
} catch (Exception ex) {
    LOGGER.warn("monitored_comunas status=invalid_geometry comunaId={} error={}",
                comunaId, ex.getMessage());
    geometry = null;           // persist comuna without geometry; do not abort
}
comuna.setGeometry(geometry);
```

Index declaration on `ComunaInfo`:

```java
@GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
private GeoJsonMultiPolygon geometry;
```

- **Sparse behavior:** comunas with `geometry == null` are simply not in the 2dsphere index. `$geoIntersects` against the index returns no match for points that would have fallen in a skipped comuna → those detections get `comunaId = null` (consistent with the offshore rule), which is the safe, observable degradation.
- **Validation scope:** the seed-time `validateRings` is a cheap structural sanity check (closure + minimum vertex count), not a full self-intersection test. Full topological validation is left to MongoDB's indexer; if a polygon passes structural checks but the indexer rejects it, auto-index-creation logs the failure per-document and skips it — startup still proceeds because the field is sparse. We accept "log + skip that comuna" as the failure mode in both layers; we never crash.
- **One-time verification:** after first boot, confirm `comunas` index list contains the 2dsphere index and log how many comunas have non-null geometry (expected 85). A count < 85 is the signal that some GADM polygon was skipped and needs manual inspection.

### Failure mode summary

| Failure | Behavior |
|---|---|
| `feature.geometry` malformed JSON / wrong structure | log warn, `geometry=null` for that comuna, continue |
| Geometry parses but self-intersects (indexer rejects) | sparse index skips that doc, log, continue |
| All geometries valid | full 85-comuna 2dsphere index, normal operation |
| A detection lands in a skipped/null-geometry comuna | `comunaId=null` (treated as offshore) — observable via the null count |

---

## Decision 4 — Exact point-in-polygon query shape & call site

**Decision: a Spring Data derived query method on `ComunaInfoRepository` using `GeometryIntersects`, returning the first match. Called per-row inside the `parseCsvResponse` loop (not batched), guarded by the dedup short-circuit so it only runs for genuinely new detections.**

### Repository method

```java
public interface ComunaInfoRepository extends MongoRepository<ComunaInfo, String> {

    List<ComunaInfo> findByRegionId(String regionId);
    long countByRegionId(String regionId);

    // Point-in-polygon: returns comunas whose 2dsphere geometry intersects the point.
    // GADM polygons are non-overlapping by construction, so at most one match is expected;
    // we read the first for deterministic tie-break on shared boundaries (see proposal).
    List<ComunaInfo> findByGeometryIntersects(Point point);
}
```

Convenience wrapper for first-match determinism (in repo as default method or in the service):

```java
default Optional<ComunaInfo> findOneByGeometryIntersects(Point point) {
    List<ComunaInfo> matches = findByGeometryIntersects(point);
    return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
}
```

- `Point` is `org.springframework.data.mongodb.core.geo.GeoJsonPoint`, constructed as `new GeoJsonPoint(longitude, latitude)` — **note the lon, lat order**, the single most common bug in geo code. FIRMS CSV gives `latitude` and `longitude` separately, so the constructor call must explicitly pass `lon` first.
- Spring Data translates `findByGeometryIntersects(Point)` to `{ geometry: { $geoIntersects: { $geometry: <point> } } }`, which uses the 2dsphere index — O(log n), not a collection scan.
- **Deterministic tie-break:** on the rare boundary point that the indexer reports as intersecting two adjacent polygons (floating-point topology), `.get(0)` is the deterministic first match. Mongo's result order for an index lookup is stable for a fixed dataset; combined with the non-overlapping GADM source this is "effectively single match, first wins" as the proposal specified.

### Call site: per-row, post-dedup

In `parseCsvResponse`, the attribution call sits immediately after the dedup short-circuit and before building the event (see Decision 2 snippet). Rationale for per-row over batched:

- The dedup check already short-circuits per row; only *new* detections reach the attribution call. In steady state (12h cron, small pilot bboxes) that is a handful of rows per sync — per-row indexed lookups are negligible.
- A batched `$geoIntersects` (e.g. one aggregation matching many points) does not map cleanly to "attribute THIS point to ITS comuna" — you would still need to fan results back to individual events. The per-row form is simpler and the volume does not justify the complexity.
- Backfill (~2k rows, one-time) also uses the same per-row call inside its stream; 2k indexed lookups at startup is sub-second and runs once. No batching needed there either.

The attribution logic therefore lives in exactly ONE expression (`findOneByGeometryIntersects(new GeoJsonPoint(lon, lat)).map(...).orElse(null)`), reused by both the sync path and the backfill path — satisfying the single-source-of-truth invariant.

---

## Decision 5 — JUnit test strategy mapped to spec scenarios

**Decision: three test layers — (A) a `@DataMongoTest` geo-attribution repository test that exercises real `$geoIntersects` against seeded polygons; (B) plain Mockito unit tests for the reconciled constant / escalation logic in both risk services; (C) a focused `parseCsvResponse` / dedup test. Geo tests use `@DataMongoTest` with `auto-index-creation=true`, matching the project's existing `OpenEoRepositoriesIntegrationTest` pattern.**

### Test infrastructure reality (verified, corrects an exploration assumption)

The exploration said embedded flapdoodle Mongo backs the tests. **The actual config is more nuanced:** `pom.xml` declares `de.flapdoodle.embed.mongo:4.13.1` (test scope), AND `src/test/resources/application.properties` sets `spring.data.mongodb.uri=mongodb://localhost:27017/simfat-test`. The existing `@DataMongoTest` geo-capable test (`OpenEoRepositoriesIntegrationTest`) uses `@TestPropertySource("spring.data.mongodb.auto-index-creation=true")` and enforces real Mongo unique indexes — so 2dsphere/`$geoIntersects` will work through the same path the project already relies on. The `@SpringBootTest` `ComunaRiskSnapshotRepositoryIntegrationTest` explicitly comments "no embedded Mongo… requires a local/CI MongoDB". 

**Design instruction for tasks/apply:** model the new geo tests on `OpenEoRepositoriesIntegrationTest` (`@DataMongoTest` + `auto-index-creation=true`). Whether the URI resolves to flapdoodle or a local/CI mongod, the geo query path is identical; do NOT assume a zero-config embedded Mongo without confirming the runner picks one up. If CI has no Mongo, these geo tests are the same class as the existing repository integration tests and gate on the same infrastructure.

### Layer A — geo-attribution repository test (`@DataMongoTest`)

Seed 2–3 known `ComunaInfo` documents with small, hand-authored valid `GeoJsonMultiPolygon`s (simple squares, not the full GADM file — keep the test self-contained and the expected results obvious). Then assert:

| Spec scenario | Assertion |
|---|---|
| Detection inside a comuna polygon resolves correctly | a point known to be inside comuna-A's square → `findOneByGeometryIntersects` returns comuna-A |
| Offshore / no-match → null | a point outside every square (e.g. far in the ocean) → `findByGeometryIntersects` empty → `comunaId` null |
| 2dsphere index actually built | with `auto-index-creation=true`, the query uses the index; a malformed-geometry comuna is skipped (sparse) and does not break the others |
| Boundary determinism | a point on the shared edge of two adjacent squares → result is stable/first-match (document the chosen comuna) |

### Layer B — reconciled constant / escalation regression (Mockito, both services)

This is the proposal's HIGH-risk item ("changes live CRITICO escalation — MUST be regression-tested before merge"). Pure unit tests on `resolveAlertLevel` semantics and the reconciled constants, no Mongo needed:

| Spec scenario | Assertion |
|---|---|
| Standardized constants identical in both services | a single parameterized input set produces the SAME alert level from `ComunaRiskServiceImpl` and `TerritoryRiskServiceImpl` for `firmsCount`/`firmsFrpMean` at the reconciled thresholds (5/4/60) |
| CRITICO by count threshold | `firmsCount == 4` (== `FIRMS_COUNT_CRITICO`) and not-today → CRITICO in BOTH services (was 8 in territory before — regression proves the new value took effect) |
| CRITICO by FRP threshold | `firmsFrpMean == 60` (== `FIRMS_FRP_CRITICO`) and not-today → CRITICO in BOTH (was 75 in territory before) |
| No over-escalation below threshold | `firmsCount == 3`, `firmsFrpMean == 50`, not-today, low score, low FWI → NOT CRITICO in both |
| Today's detection always CRITICO | `hasTodayFirms == true` → CRITICO regardless of count (unchanged behavior, guard against regression) |

Because both services currently have private `resolveAlertLevel`, the tasks phase should either (a) drive these through the public `recomputeByComuna`/`recomputeRiskByRegion` with mocked repositories returning crafted `HeatAlertEvent` lists, or (b) extract the threshold logic to a shared, testable unit. Option (a) is lower-risk (no refactor) and tests the real path; prefer it unless the shared-constant duplication is extracted.

### Layer C — sync attribution & dedup test

| Spec scenario | Assertion |
|---|---|
| New detection inside polygon persists correct `comunaId` | feed `parseCsvResponse` a CSV row whose lat/lon is inside a seeded comuna → saved event has that `comunaId` |
| Offshore detection persists `comunaId == null` | CSV row in the ocean → saved event `comunaId == null` |
| Cross-region dedup | same `(lat, lon, fecha, fuente)` seen on a second region's leg → no second insert (new identity dedup key) |
| Dashboard `fuente` filter | `DashboardSnapshotServiceImpl.heatAlerts7d` counts only `NASA_FIRMS` rows — a non-FIRMS alert in the window is excluded (this is the separate-bug fix; needs a repo method `countByRegionIdAndFuenteAndFechaEventoBetween`) |

### Dashboard filter note (folds into Slice B)

`DashboardSnapshotServiceImpl` line 54 uses `countByRegionIdAndFechaEventoBetween` with NO `fuente` filter. Fix: add `countByRegionIdAndFuenteAndFechaEventoBetween(regionId, "NASA_FIRMS", from, to)` and use it. Covered by the Layer C dashboard assertion above.

---

## ADR Summary (decisions + rejected alternatives)

| # | Decision | Chosen | Rejected (why) |
|---|---|---|---|
| 1 | Backfill mechanism | Startup `@EventListener` component, ordered after seed, idempotent on `comunaId IS NULL` | Inline in config (couples concerns, swallows errors); admin endpoint (race window); `railway run` script (re-expresses geo logic, no ordering guarantee) |
| 2 | Dedup key | `(lat, lon, fechaEvento, fuente)` — drop `regionId`; keep per-region sync | Carve-out non-overlapping bboxes (NASA API can't request non-rectangles; needless machinery; loses border resilience) |
| 3 | Invalid geometry | Validate at seed; log + skip that comuna's geometry; sparse 2dsphere; never crash | Crash startup (one bad polygon kills life-safety system); skip validation (index build fails opaquely) |
| 4 | Query shape | `findByGeometryIntersects(Point)` derived query, first-match, per-row post-dedup | Batched `$geoIntersects` (no clean per-point fan-out; volume doesn't justify it) |
| 5 | Tests | `@DataMongoTest`+auto-index geo tests; Mockito escalation regression in BOTH services; sync/dedup test | Embedded-Mongo-assumed without verifying runner; testing only `ComunaRiskServiceImpl` (leaves sibling divergence) |

## Architectural Invariants (must hold after this change)

1. Comuna attribution logic exists in exactly ONE expression, reused by sync and backfill.
2. A FIRMS detection is one row, identified by `(lat, lon, fecha, fuente)`, regardless of how many region bboxes fetched it.
3. Region for risk counting is DERIVED from `comunaId`'s comuna, never re-guessed by centroid.
4. Offshore = `comunaId null`, never a fabricated nearest comuna.
5. Both risk services use one constant set: `FIRMS_MAX_COUNT=5`, `FIRMS_COUNT_CRITICO=4`, `FIRMS_FRP_CRITICO=60`.
6. Startup never crashes on a single invalid GADM polygon.
7. Slice B reads are gated on backfill completion (`count(comunaId missing & NASA_FIRMS) == 0`).
</content>
</invoke>
