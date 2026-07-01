# Design: FIRMS comuna geo-attribution and standardized detection counts

## Status

**Design REVISED (post-incident).** The original design (Decisions 1-5) was implemented across Slices A+B, merged (PR #12/#13/#14), deployed, and then **reverted from production** after a real incident. This revision keeps everything that was correct and surgically corrects the two root causes the original design did not anticipate. It adds **Decision 6 (coverage-gap fallback)**, rewrites **Decision 1 (backfill mechanism)**, and corrects **Architectural Invariant 3**. Decisions 2, 3, 4, 5 stand unchanged in substance (Decision 5 gains one new regression scenario).

### Production incident that drove this revision (the WHY)

1. **16 of 19 monitored regions went silently blind.** The system monitors NASA FIRMS across **19 regions nationally** (confirmed via `regionRepository.findAll()` and active cron logs). But comuna GeoJSON polygons — the input that seeds `ComunaInfo.geometry` for the `$geoIntersects` attribution — exist for **only 3 regions**: Araucanía, Biobío, Ñuble (the only files under `src/main/resources/static/geojson/`). The original Invariant 3 ("region is DERIVED from `comunaId`, never re-guessed by centroid") and Phase 10.1 (which DELETED `assignFocosToComuna`/`findNearestComuna`/`findNearestRegionId`) assumed nationwide polygon coverage. For the 16 uncovered regions every `comunaId` is permanently `null` (no polygon ever intersects), and once Slice B made the risk services read **exclusively** by `comunaId`, those regions' FIRMS component went permanently to **zero detections** — a real regression vs. the old centroid behavior, which was imprecise but functional in all 19 regions.

2. **The startup backfill could not complete at real-world scale.** `BackfillComunaIdRunner` (original Decision 1) was a sequential loop — one `$geoIntersects` + one `.save()` per row — running on the main thread via `ApplicationReadyEvent`. Against production MongoDB Atlas (~400 ms round-trip per query), competing with the FIRMS cron and stuck OpenEO retries, it ran **9+ hours without finishing half of ~2,000 rows** and never logged `status=done`. The original "Slice B readers MUST NOT run until backfill completes" gate is therefore unworkable at Atlas latency under concurrent load.

The two prior judgment calls are still settled facts here:

- **Constant reconciliation (SETTLED):** `ComunaRiskServiceImpl`'s stricter `FIRMS_MAX_COUNT=5`, `FIRMS_COUNT_CRITICO=4`, `FIRMS_FRP_CRITICO=60` become the single standard. `TerritoryRiskServiceImpl`'s `10`/`8`/`75` are deleted as an artifact of the now-removed double-counting workaround.
- **Offshore / no-polygon match (SETTLED):** `HeatAlertEvent.comunaId` stays `null`. No synthetic nearest-comuna fallback at attribution time. **This is NOT the same as the coverage-gap case** — see Decision 6, which distinguishes "no geometry loaded for this region" (use centroid fallback) from "geometry loaded, point genuinely offshore" (`comunaId` stays null).

## Architecture Approach

**Pattern: persist-at-the-root, single source of truth.** Geometric comuna attribution is computed exactly once — at FIRMS ingest time, inside `NasaFirmsServiceImpl.parseCsvResponse`, via a MongoDB `$geoIntersects` point-in-polygon query against persisted comuna geometry. The resulting `comunaId` (or `null`) is written onto the `HeatAlertEvent` document. All five downstream surfaces then *read* that persisted field instead of each re-deriving attribution by nearest-centroid distance.

This collapses the two independent nearest-centroid implementations (`ComunaRiskServiceImpl.findNearestComuna`, `TerritoryRiskServiceImpl.findNearestRegionId`) into one indexed geometric lookup whose result is durable and auditable, **for every region that has comuna geometry loaded**. Where geometry exists, the region is *derived* from the matched comuna's `regionId`, which structurally eliminates cross-region double-counting at the data layer rather than patching it per-reader.

**Coverage-gap fallback (Decision 6 — added post-incident).** Geometry only exists for 3 of 19 monitored regions today. The two centroid methods are therefore **retained as a permanent secondary attribution path**, not deleted. At read time each risk service first asks "does this region currently have comuna geometry coverage?" (a cheap `countByRegionIdAndGeometryNotNull` check). If yes → query by persisted `comunaId` (the geometric source of truth). If no → fall back to the old centroid logic, so the region's FIRMS component stays *functional* (imprecise) instead of going *blind* (always zero). The system auto-upgrades region-by-region as more GeoJSON is loaded — **no code change** required to light up a new region, only a new GeoJSON file and a re-seed. This is the single most important correction in this revision.

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
  NasaFirms.parseCsvResponse                       ComunaRiskServiceImpl ─┐                  │
    │ per row: $geoIntersects(lat,lon)               region has geometry? ─┤ yes: by comunaId│
    │ resolve comunaId (or null)                                          └ no:  centroid     │
    │ dedup by (lat,lon,fecha,fuente) (Decision 2)                              fallback (D6) │
    ▼                                              TerritoryRiskServiceImpl ┐                  │
  insert HeatAlertEvent{comunaId}                   region has geometry? ──┤ yes: by comunaId│
                                                                          └ no:  centroid     │
                                                    DashboardSnapshotServiceImpl ─ + fuente   │
                                                                               filter         │
                                                    TerritoryController.firmsLayer ─ raw bbox │
                                                      view, labeled (unchanged scope)         │
  └──────────────────────────────────────────────────────────────────────────────────────┘
```

### Integration Points

| Integration point | Change |
|---|---|
| `ComunaInfo` ↔ Mongo `comunas` | New `geometry: GeoJsonMultiPolygon` + `@GeoSpatialIndexed(type = GEO_2DSPHERE)` |
| `HeatAlertEvent` ↔ Mongo `heat_alert_events` | New nullable `comunaId: String`; new compound index `{comunaId:1, fechaEvento:-1}` |
| `ComunaInfoRepository` | New `findByGeometryIntersects(Point)` derived query (Decision 4); new `countByRegionIdAndGeometryNotNull(String)` coverage probe (Decision 6) |
| `NasaFirmsServiceImpl` | Inject `ComunaInfoRepository`; per-row attribution; new dedup key (Decision 2) |
| `ComunaRiskServiceImpl` / `TerritoryRiskServiceImpl` | Coverage-gap router: `comunaId` query when covered, retained centroid methods when not (Decision 6) |
| `MonitoredComunasConfig` | Parse `feature.geometry`; validate before save |
| Startup backfill | New bulk-write `ApplicationRunner`, async, ordered after seed; no longer gates reads (Decision 1) |

---

## Decision 1 — Backfill execution mechanism (REVISED post-incident)

**Decision: a one-time, idempotent backfill implemented as a dedicated `@Component` that (a) writes in BULK via `BulkOperations`/`bulkWrite` batches instead of one `.save()` per row, and (b) runs ASYNCHRONOUSLY (off the `ApplicationReadyEvent` thread, on a dedicated single executor), no longer blocking startup and no longer gating Slice B reads. The fallback from Decision 6 — not the backfill — is what guarantees correctness for any not-yet-attributed row. The backfill is now a precision-improvement job, not a correctness prerequisite.**

### Why this changed (the incident)

The original Decision 1 made two assumptions that real production falsified:

1. *"Backfill always wins the race within a single boot."* True only for trivial row counts against low-latency Mongo. Against MongoDB Atlas (~400 ms per round-trip), a sequential one-`save()`-per-row loop over ~2,000 rows — competing with the FIRMS cron and stuck OpenEO retries — ran **9+ hours and never reached `status=done`**. The startup thread was effectively hung.
2. *"Slice B readers MUST NOT run until backfill completes."* With the original design this gate was a hard correctness requirement: an unattributed row read by `comunaId` simply vanished from counts. That gate is unenforceable when the backfill never completes. **Decision 6 dissolves the gate**: a region with no geometry (or a row not yet attributed) falls back to centroid attribution, so a *late* or *incomplete* backfill is no longer a correctness risk — only a transient precision-staleness one (the row is counted via the imprecise centroid path until the backfill upgrades it to the precise geometric `comunaId`).

So both root causes get addressed: **bulk writes** make the job finish in minutes instead of hours, and **async + fallback** removes the boot-blocking and the unenforceable gate.

### Rationale (what stays)

The job is still idempotent (filter is `comunaId IS NULL`, re-running is a no-op or an identical write) and still has a hard ordering dependency on the geometry seed. It stays a **separate** `@Component` (not inline in `MonitoredComunasConfig`) so its success/failure is independently observable in logs and its bulk-batch progress is logged per batch.

Rejected alternatives:

- **Keep it synchronous on `ApplicationReadyEvent` (original choice)** — rejected by the incident. Blocking the boot thread for hours against Atlas latency is operationally unacceptable; health checks and the scheduler start late, and a deploy can appear hung. Async on a dedicated executor lets the app become ready immediately while the backfill proceeds in the background; the fallback covers reads in the meantime.
- **Inline in `MonitoredComunasConfig.ensureMonitoredComunas`** — still rejected. It couples a config concern (~85 small documents) with a data migration (~2k+ event rows), and the seed's per-region `try/catch` would silently swallow backfill failures.
- **Admin-triggered endpoint** — *re-evaluated and now ACCEPTABLE as a complementary re-trigger, still not the sole mechanism.* The original rejection was "it creates a window where Slice B readers run before the operator hits the endpoint." **That objection no longer holds**: with Decision 6's fallback, a reader hitting an unattributed region degrades to centroid attribution rather than to zero — there is no correctness window to protect anymore. The async startup runner remains the default (zero operator action, runs every boot), and an admin/feature-flagged manual trigger is the disaster-recovery / re-run path. This is the key judgment reversal this revision makes.
- **Standalone `railway run` mongosh script** — still rejected as primary: it re-expresses the `$geoIntersects` + `GeoJsonMultiPolygon` logic in raw mongosh, risking divergence from the production attribution path. Available as manual DR only.

### Implementation shape (bulk + async)

```java
@Component
public class BackfillComunaIdRunner {

    private static final int BATCH = 500;               // tune against Atlas; 500 keeps each bulkWrite well under the 16MB op limit

    @Async("backfillExecutor")                          // dedicated single-thread executor; does NOT block boot
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)                   // still fires after the seed listener
    public void backfill() {
        if (!backfillEnabled) { return; }               // firms.backfill.enabled, default true
        long attributed = 0, offshore = 0, batches = 0;
        try (Stream<HeatAlertEvent> rows =
                 repo.streamByFuenteAndComunaIdIsNull("NASA_FIRMS")) {
            BulkOperations bulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, HeatAlertEvent.class);
            int inBatch = 0;
            for (Iterator<HeatAlertEvent> it = rows.iterator(); it.hasNext(); ) {
                HeatAlertEvent ev = it.next();
                if (ev.getLatitud() == null || ev.getLongitud() == null) continue;
                Point p = new GeoJsonPoint(ev.getLongitud(), ev.getLatitud());   // lon, lat order
                String comunaId = comunaRepo.findOneByGeometryIntersects(p)
                                            .map(ComunaInfo::getId).orElse(null);
                if (comunaId != null) attributed++; else offshore++;
                bulk.updateOne(query(where("_id").is(ev.getId())),
                               new Update().set("comunaId", comunaId));            // explicit null for offshore
                if (++inBatch == BATCH) {
                    bulk.execute(); batches++;
                    bulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, HeatAlertEvent.class);
                    inBatch = 0;
                    LOGGER.info("firms_backfill status=progress batches={} attributed={} offshore={}",
                                batches, attributed, offshore);
                }
            }
            if (inBatch > 0) { bulk.execute(); batches++; }
        }
        LOGGER.info("firms_backfill status=done batches={} attributed={} offshore={}",
                    batches, attributed, offshore);
    }
}
```

Notes:
- **Bulk write is the core fix.** The `$geoIntersects` *read* is still per-row (it must be — each point resolves to its own comuna; see Decision 4), but the *writes* are batched into one `bulkWrite` per `BATCH` rows. That collapses ~2,000 individual `.save()` round-trips into ~4 bulk round-trips — the dominant cost against Atlas latency. `BulkMode.UNORDERED` lets the server parallelize within a batch.
- **Async, non-blocking.** `@Async("backfillExecutor")` runs the job on a dedicated single-thread executor so `ApplicationReadyEvent` returns immediately and the app is healthy at once. Requires `@EnableAsync` and a `backfillExecutor` bean (single thread, so the job never competes with itself across boots).
- **Idempotency / re-runnability** unchanged: filter is `comunaId IS NULL`; attributed rows are skipped next boot; offshore rows re-confirm `null` cheaply.
- **Order guarantee:** `@Order(LOWEST_PRECEDENCE)` still fires after `MonitoredComunasConfig`'s seed listener, so geometry + 2dsphere index exist before the first `$geoIntersects`.

### Ordering constraint (RELAXED — no longer a hard gate)

> The backfill **improves precision**; it is no longer a correctness prerequisite for Slice B reads.

Concretely:
- Slice A (schema + seed + sync attribution + bulk async backfill) and Slice B (geometry-aware risk reads with centroid fallback) can ship together. There is no boot-race to win: while the backfill runs in the background, a read against a not-yet-attributed row in a *covered* region briefly resolves via fallback (centroid) and is upgraded to the precise `comunaId` once the backfill batch lands. A read against an *uncovered* region always uses fallback, by design (Decision 6).
- **Observability replaces the gate:** the success signal is `count({fuente:'NASA_FIRMS', comunaId:{$exists:false}})` trending to 0 *for covered regions* after the backfill logs `status=done`. A non-zero count is no longer a blocker — it is a precision-staleness metric, not a correctness failure, because the fallback already covers those rows. Uncovered regions will legitimately retain `comunaId=null` rows forever (until their GeoJSON is added) and must not be treated as a failed backfill.

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

### Layer D — coverage-gap fallback regression (Decision 6, MANDATORY, post-incident)

This is the regression that the production incident proved was missing. It must make the "uncovered region goes blind" failure **impossible to silently reintroduce**.

| Spec scenario | Assertion |
|---|---|
| **Uncovered region uses centroid fallback, NOT zero** | `countByRegionIdAndGeometryNotNull(region) == 0` → `TerritoryRiskServiceImpl.recomputeRiskByRegion` (and `ComunaRiskServiceImpl.recomputeByComuna`) routes to the centroid path; with FIRMS events present in that region, `firmsCount > 0` (i.e. the FIRMS component is functional, not silently zero/blind) |
| Covered region uses `comunaId` path | `countByRegionIdAndGeometryNotNull(region) > 0` → service queries by persisted `comunaId`; centroid method is NOT invoked (`verify(..., never())`) |
| Covered region, point truly offshore stays null | a covered region whose FIRMS row has `comunaId == null` (geometry loaded, point offshore) is NOT re-routed to fallback — it stays excluded from comuna-scoped counts (preserves Invariant 4; the gap-vs-offshore distinction from Decision 6 holds) |
| Auto-upgrade on coverage change | after geometry is added to a previously-uncovered region (coverage probe flips 0 → >0), the next recompute switches from centroid to `comunaId` path with no code change |

These run as Mockito unit tests on both services (mock the coverage probe `countByRegionIdAndGeometryNotNull` to return 0 vs. >0, and verify which read path executes), plus optionally one `@DataMongoTest` end-to-end on the probe. They directly defend the corrected Invariant 3.

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

## Decision 6 — Coverage-gap fallback (NEW, post-incident root-cause fix)

**Decision: the centroid-based attribution methods (`ComunaRiskServiceImpl.assignFocosToComuna`/`findNearestComuna`, `TerritoryRiskServiceImpl.findNearestRegionId`) are RETAINED as a permanent secondary attribution path. Each risk service, before reading FIRMS events, probes whether the region currently has comuna geometry loaded via `comunaInfoRepository.countByRegionIdAndGeometryNotNull(regionId) > 0`. If COVERED → read by persisted `comunaId` (geometric source of truth). If UNCOVERED → use the retained centroid path so the region's FIRMS component stays functional instead of going blind. This reverses the original Phase 10.1 deletion of those methods.**

### Rationale (the WHY — driven directly by the incident)

The original design assumed nationwide comuna-polygon coverage and therefore deleted the centroid fallback as dead code. In production only **3 of 19 regions** have GeoJSON, so for the other 16 every `comunaId` is permanently `null`, and a `comunaId`-only read returns **zero FIRMS detections forever** for those regions — a silent, permanent regression in a life-safety alerting system. The user's explicit direction: do NOT shrink monitoring to 3 regions (national coverage is delivered value); instead keep the precise geometric path where geometry exists and fall back to the old imprecise-but-functional centroid path where it does not, auto-upgrading region-by-region as GeoJSON is added — **with no code change** to onboard a new region.

### The critical distinction: coverage-gap vs. genuine offshore (MUST NOT be conflated)

Two states both produce `comunaId == null`, but they are semantically different and route differently:

| State | How to detect | Routing |
|---|---|---|
| **Coverage gap** — region has no geometry loaded at all | `countByRegionIdAndGeometryNotNull(regionId) == 0` | Use **centroid fallback** (the whole region is uncovered) |
| **Genuine offshore / no-polygon match** — region HAS geometry, this specific point intersects nothing (ocean, GADM edge) | `countByRegionIdAndGeometryNotNull(regionId) > 0` AND that row's `comunaId == null` | `comunaId` stays null, row excluded from comuna counts — **NOT a fallback trigger** (preserves Invariant 4) |

The probe is at **region granularity**, never per-row: "does this region have ANY comuna geometry?" If yes, a null `comunaId` on an individual row is a legitimate offshore/edge result and must be honored as null (the original SETTLED offshore rule). If no, the whole region is uncovered and the entire region's FIRMS attribution runs through centroid. This keeps the offshore semantics from Decisions 2/4 intact while closing the coverage gap.

### Implementation shape

New repository probe (cheap, indexed count — `regionId` is already queried, `geometry` is sparse-indexed):

```java
// ComunaInfoRepository
long countByRegionIdAndGeometryNotNull(String regionId);
```

`TerritoryRiskServiceImpl.recomputeRiskByRegion` (region-level), grounded in the real post-revert method:

```java
boolean covered = comunaInfoRepository.countByRegionIdAndGeometryNotNull(regionId) > 0;
List<HeatAlertEvent> firmsEvents;
if (covered) {
    // geometric source of truth: events whose persisted comunaId belongs to this region
    List<String> comunaIds = comunaInfoRepository.findByRegionId(regionId).stream()
        .map(ComunaInfo::getId).toList();
    firmsEvents = heatAlertEventRepository
        .findByComunaIdInAndFechaEventoAfter(comunaIds, firms48hAgo).stream()
        .filter(/* NASA_FIRMS, confidence, lat/lon */).toList();
} else {
    // RETAINED centroid fallback — region uncovered, keep it functional (not blind)
    List<Region> allRegions = regionRepository.findAll();
    firmsEvents = heatAlertEventRepository.findByRegionId(regionId).stream()
        .filter(/* window, NASA_FIRMS, confidence, lat/lon */)
        .filter(e -> regionId.equals(findNearestRegionId(e.getLatitud(), e.getLongitud(), allRegions)))
        .toList();
}
```

`ComunaRiskServiceImpl.recomputeByComuna` (comuna-level) mirrors this: probe `countByRegionIdAndGeometryNotNull(comuna.getRegionId())`; if covered, read `findByComunaIdAndFechaEventoAfter(comunaId, firms48h)`; if uncovered, keep `assignFocosToComuna(regionFocos, comunaId, comunaRepository.findByRegionId(...))` exactly as it is today.

- **`assignFocosToComuna`, `findNearestComuna`, `findNearestRegionId` stay** (private methods, current signatures verified against post-revert source). Do not re-delete them. They are the documented secondary path, not dead code.
- **Auto-upgrade:** the moment a region's GeoJSON is seeded (geometry becomes non-null for its comunas), the probe flips `0 → >0` and the next recompute switches to the `comunaId` path automatically — no deploy, no code change.

### Rejected alternatives

- **Shrink monitoring to the 3 covered regions** — rejected by explicit user decision. National 19-region coverage is delivered value; retreating to 3 regions discards it.
- **Per-row "if `comunaId` null, try centroid"** — rejected. It conflates genuine offshore (null is correct, must stay null per Invariant 4) with coverage gap, re-introducing the silent-fabrication problem the offshore rule exists to prevent. Region-granularity probing keeps the two cases distinct.
- **A boolean `coverageLoaded` flag on `Region`** — rejected as redundant state to maintain. The geometry count IS the source of truth; deriving coverage from it (`countByRegionIdAndGeometryNotNull > 0`) cannot drift out of sync with the actual seeded data.
- **Re-derive constants per path** — rejected. Both paths feed the SAME reconciled constants (Decision-5 / Invariant 5); only the *event-selection* differs, not the scoring.

### Amendment — comuna-granularity correction (post-review fix, findings C1/C5/C6/C7/C9)

A fresh-context review of the first implementation of this decision found that "region-granularity probing keeps the two cases distinct" (above) was true but insufficient: `countByRegionIdAndGeometryNotNull(regionId) > 0` answers "does ANY comuna in this region have geometry," not "does THIS comuna." A region can be 9/10 covered while one comuna's GADM polygon failed Decision-3 validation and has `geometry = null` — that comuna's region still reads as "covered," so `recomputeByComuna` for it took the geometric path, but its own `comunaId` can never resolve (no polygon to intersect against). That one comuna went permanently blind with the fallback never triggering — a narrower recurrence of the exact incident this decision exists to prevent.

**Fix**: the probe is **per-comuna**, not per-region: `comuna.getGeometry() != null`, checked directly on the already-loaded entity (free — no DB round trip, which also fixed a separate finding that the old region-level probe was being re-queried once per comuna per batch run instead of once per region). This is a refinement of the granularity, not a reversal of the original reasoning above — it is still **not** per-row: a covered comuna's individual offshore events still resolve `comunaId = null` and stay excluded (Invariant 4 unchanged), because the per-comuna geometry check happens once per comuna/region call, before any row is read. `TerritoryRiskServiceImpl`'s region-level rollup now splits its comunas into covered/uncovered subsets and sums both contributions (geometric for covered, centroid for uncovered) instead of an all-or-nothing region decision.

A second, related bug was found in the centroid fallback itself: it sourced its candidate event pool via `findByRegionId(thisRegion)` before applying centroid distance — but Decision 2 made dedup region-independent, so a row's persisted `regionId` is just "whichever cron leg synced it first," not a geographic fact. For two uncovered, overlapping regions, a detection physically owned by region B but synced first by region A's leg was invisible to B's fallback (B's `findByRegionId('B')` never returns it), silently undercounting B with no recovery path. Git archaeology against the pre-incident commit (before any comuna-geo-attribution work existed) confirmed the original centroid logic did not pre-filter by region — it scored every recent FIRMS event by distance and let proximity decide ownership. The fallback's candidate pool is now sourced by `fuente` + recency only, matching that original behavior, never pre-filtered by persisted `regionId`.

Both corrections, plus the previously-duplicated routing logic between the two risk services, are now consolidated into a single `FirmsAttributionRouter` component that both `ComunaRiskServiceImpl` and `TerritoryRiskServiceImpl` delegate to — the one sanctioned place that knows how to read FIRMS events by attribution, closing the gap where a future caller could query `HeatAlertEventRepository` by `comunaId` directly and bypass the coverage check.

---

## ADR Summary (decisions + rejected alternatives)

| # | Decision | Chosen | Rejected (why) |
|---|---|---|---|
| 1 | Backfill mechanism (REVISED) | **Bulk-write** batched `@Component`, **async** off `ApplicationReadyEvent`, idempotent on `comunaId IS NULL`; no longer gates reads (fallback covers them) | Sync on boot (hung 9+h vs Atlas latency — the incident); inline in config (couples concerns); `railway run` (re-expresses geo logic). Admin endpoint **re-accepted** as DR re-trigger since the fallback removes the race window |
| 2 | Dedup key | `(lat, lon, fechaEvento, fuente)` — drop `regionId`; keep per-region sync | Carve-out non-overlapping bboxes (NASA API can't request non-rectangles; needless machinery; loses border resilience) |
| 3 | Invalid geometry | Validate at seed; log + skip that comuna's geometry; sparse 2dsphere; never crash | Crash startup (one bad polygon kills life-safety system); skip validation (index build fails opaquely) |
| 4 | Query shape | `findByGeometryIntersects(Point)` derived query, first-match, per-row post-dedup | Batched `$geoIntersects` (no clean per-point fan-out; volume doesn't justify it) |
| 5 | Tests | `@DataMongoTest`+auto-index geo tests; Mockito escalation regression in BOTH services; sync/dedup test; **coverage-gap fallback regression (Layer D)** | Embedded-Mongo-assumed without verifying runner; testing only `ComunaRiskServiceImpl` (leaves sibling divergence) |
| 6 | Coverage-gap fallback (NEW) | Retain centroid methods; per-region probe `countByRegionIdAndGeometryNotNull > 0` → `comunaId` path, else centroid fallback; auto-upgrades on re-seed | Shrink to 3 covered regions (loses national value); per-row null→centroid (conflates offshore with gap); `coverageLoaded` flag on Region (redundant, drift-prone) |

## Architectural Invariants (must hold after this change)

1. The geometric attribution logic exists in exactly ONE expression (`findOneByGeometryIntersects`), reused by sync and backfill.
2. A FIRMS detection is one row, identified by `(lat, lon, fecha, fuente)`, regardless of how many region bboxes fetched it.
3. **(CORRECTED post-incident)** For a region that HAS comuna geometry loaded (`countByRegionIdAndGeometryNotNull > 0`), risk counting is DERIVED from `comunaId`'s comuna. For a region with NO geometry loaded, risk counting falls back to the retained centroid attribution — never silently zero. The centroid methods (`assignFocosToComuna`/`findNearestComuna`/`findNearestRegionId`) are a permanent secondary path, not dead code. *(Supersedes the original "never re-guessed by centroid", which assumed nationwide coverage and caused 16/19 regions to go blind in production.)*
4. **Genuine offshore (geometry loaded, point intersects nothing) = `comunaId null`, never a fabricated nearest comuna.** This is distinct from the coverage gap of Invariant 3: offshore is a per-row null in a COVERED region; the gap is a whole UNCOVERED region. The two are routed by the region-granularity coverage probe and must never be conflated.
5. Both risk services use one constant set: `FIRMS_MAX_COUNT=5`, `FIRMS_COUNT_CRITICO=4`, `FIRMS_FRP_CRITICO=60`. Both attribution paths (geometric and centroid fallback) feed these SAME constants.
6. Startup never crashes on a single invalid GADM polygon.
7. **(RELAXED post-incident)** Slice B reads are NO LONGER gated on backfill completion — the Decision 6 fallback makes a late/incomplete backfill a precision-staleness concern, not a correctness one. `count(comunaId missing & NASA_FIRMS) → 0` for COVERED regions remains an observability signal (precision), not a deploy gate; uncovered regions legitimately retain null `comunaId` rows until their GeoJSON is added.
8. **(NEW)** A region auto-upgrades from centroid fallback to geometric `comunaId` attribution the moment its comuna geometry is seeded — with no code change, only a new GeoJSON file and a re-seed.
</content>
</invoke>
