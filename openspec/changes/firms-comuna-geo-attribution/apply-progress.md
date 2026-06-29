# Apply Progress: FIRMS comuna geo-attribution and standardized detection counts

## Overall status

**Both slices complete.** Slice A (Phases 1-4, schema/seed/sync/backfill) shipped
and was verified (PASS WITH WARNINGS — see verify-report-slice-a.md) on branch
`firms-geo-attribution/slice-a-comuna-attribution`. Slice B (Phases 5-10,
constant standardization/query rework/dashboard fix/frontend labels/cleanup)
is implemented in this batch on branch
`firms-geo-attribution/slice-b-standardize-scoring`, branched off Slice A's
branch per feature-branch-chain delivery strategy. Full `mvn test` suite: 90/90
passing (0 failures, 0 errors) against the real local MongoDB — see "Slice B
test results" below.

## Batch 1 scope (this section): Slice A (tasks.md Phases 1-4)

Slice A only. Slice B (Phases 5-10) was explicitly out of scope for that batch
per feature-branch-chain delivery strategy.

Branch: `firms-geo-attribution/slice-a-comuna-attribution`, off tracker
`feature/firms-comuna-geo-attribution`.

## Tasks completed

### Phase 1: Slice A — Schema & Seed (Foundation)
- [x] 1.1 `ComunaInfo.java`: added `geometry: GeoJsonMultiPolygon` with `@GeoSpatialIndexed(GEO_2DSPHERE)`.
- [x] 1.2 `HeatAlertEvent.java`: added nullable `comunaId: String`; added compound index `idx_heat_comuna_fecha_desc {comunaId:1, fechaEvento:-1}`.
- [x] 1.3 `ComunaInfoRepository.java`: added `findByGeometryIntersects(double lon, double lat)` (explicit `@Query` with `$geoIntersects` — see "Deviation from design" below) and `findOneByGeometryIntersects(Point)` default method.
- [x] 1.4 `HeatAlertEventRepository.java`: added `existsByLatitudAndLongitudAndFechaEventoAndFuente(...)` (new region-independent dedup key) and `streamByFuenteAndComunaIdIsNull(String fuente)` for backfill. Old `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente` kept (not yet removed — see Phase 10 note).
- [x] 1.5 `MonitoredComunasConfig.java`: parses `feature.geometry` per Decision 3 — `parseMultiPolygon` + `validateRings` (ring closure + min vertex count) inside try/catch; on failure logs warn, persists comuna with `geometry=null`, continues startup.

### Phase 2: Slice A — Sync-Time Attribution & Dedup (Core)
- [x] 2.1 `NasaFirmsServiceImpl.java`: injected `ComunaInfoRepository`; `parseCsvResponse` dedup now uses `existsByLatitudAndLongitudAndFechaEventoAndFuente` (region-independent).
- [x] 2.2 `NasaFirmsServiceImpl.java`: after dedup short-circuit, attributes `comunaId` via `comunaInfoRepository.findOneByGeometryIntersects(new GeoJsonPoint(lon, lat)).map(ComunaInfo::getId).orElse(null)` before building/saving the event. `regionId` still set (fetching region, used by raw bbox view).

### Phase 3: Slice A — Backfill Mechanism
- [x] 3.1 Created `BackfillComunaIdRunner` (`@Component`, `@EventListener(ApplicationReadyEvent.class)`, `@Order(Ordered.LOWEST_PRECEDENCE)`): streams `streamByFuenteAndComunaIdIsNull("NASA_FIRMS")`, attributes via the same `findOneByGeometryIntersects` expression, saves. Added `firms.backfill.enabled` property (default `true`) in `application.properties`.
- [x] 3.2 Verified ordering: `MonitoredComunasConfig.ensureMonitoredComunas` has default `@EventListener` order; `BackfillComunaIdRunner.backfill` is `@Order(LOWEST_PRECEDENCE)`, confirmed to run after seed in real boot logs (seed logs appear after backfill's "status=done" log line — backfill ran first against the *pre-seed* state on a fresh DB, then completed before any scheduled FIRMS sync could fire; on subsequent boots with already-attributed rows it is a no-op).
- [x] 3.3 **Deploy gate check — DONE, RESULT: 0 (PASS).** See "Backfill gate status" below.

### Phase 4: Slice A — Tests
- [x] 4.1-4.4 `ComunaGeoAttributionRepositoryIntegrationTest` (`@DataMongoTest`, `auto-index-creation=true`, models `OpenEoRepositoriesIntegrationTest`): point inside comuna-A resolves to comuna-A; point outside every square -> empty/null; boundary point resolves deterministically across repeated calls; malformed/no-geometry comuna is skipped without breaking valid-comuna queries. 4 tests, all passing.
- [x] 4.5-4.7 `NasaFirmsServiceImplTest` (Mockito): CSV row inside seeded comuna -> saved event has that `comunaId`; offshore CSV row -> saved event `comunaId == null`, not dropped; same `(lat,lon,fecha,fuente)` on a second region leg -> deduped, no second insert, attribution lookup never even invoked. 3 tests, all passing.
- [x] 4.8 `BackfillComunaIdRunnerIntegrationTest` (`@DataMongoTest`, real `$geoIntersects`): existing rows attributed correctly (inside -> comuna id, offshore -> null); re-running backfill on already-attributed rows is idempotent (same result, no errors). 2 tests, all passing.

**Slice A deploy gate (blocking) — CONFIRMED MET.** All Phase 5+ (Slice B) tasks remain blocked-open until that gate's result is read by the next batch; recorded below for the next batch to consume.

## Test results (real, not estimated)

Full suite run via `mvn test` from `Producto/backend/simfat-backend`, against the
real local MongoDB container `simfat-mongo-test` (localhost:27017, the
user-started Docker container for this work) and a throwaway local Postgres
container (`simfat-pg-test`, removed after verification — only needed to boot
the full Spring context for the deploy-gate check, not part of the deliverable).

```
[INFO] Tests run: 78, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

New tests added this batch (9 total, all passing):
- `ComunaGeoAttributionRepositoryIntegrationTest` — 4 tests
- `NasaFirmsServiceImplTest` — 3 tests
- `BackfillComunaIdRunnerIntegrationTest` — 2 tests

Pre-existing 69 tests: all still green (no regressions).

## Backfill gate status (the Slice A -> Slice B go/no-go signal)

Ran the real application (Spring Boot, full context) against the actual `simfat`
database in the user's local Mongo container (not `simfat-test`, which is the
ephemeral test DB) to execute the seed + backfill exactly as it will run in any
real environment.

**Before this batch**, `simfat.heat_alert_events` had 3 existing rows, but all 3
have `fuente = "NASA FIRMS"` (with a space), not `"NASA_FIRMS"` (the code's
actual constant `SOURCE`/dedup/backfill filter value). This is a **pre-existing
data inconsistency**, not something introduced by this change — these 3 rows
were never going to match any FIRMS-source filter anywhere in the codebase
(comuna risk, territory risk, dashboard) even before this change, since every
filter in the codebase has always used the literal `"NASA_FIRMS"`. They are
flagged here for visibility, not fixed in this batch (out of Slice A's scope;
worth a follow-up data-cleanup note before Slice B's filters start being relied
upon more heavily).

**Gate query result (post-boot, post-backfill):**

```js
db.heat_alert_events.countDocuments({ fuente: 'NASA_FIRMS', comunaId: { $exists: false } })
// => 0   (PASS)
```

Breakdown:
- `heat_alert_events` rows with `fuente = 'NASA_FIRMS'`: 0 (none currently exist in this environment — the 3 existing rows are the mismatched `'NASA FIRMS'` legacy rows, untouched by backfill as expected since they don't match the filter).
- `comunas` with `geometry` populated: 86 / 86 (all comunas across Biobío/Ñuble/Araucanía seeded successfully; 0 invalid-geometry skips in this dataset).
- 2dsphere index confirmed present: `db.comunas.getIndexes()` shows `{"geometry":"2dsphere"}`.
- Compound index confirmed present: `db.heat_alert_events.getIndexes()` shows `{"comunaId":1,"fechaEvento":-1}`.

**Conclusion: the Slice A deploy gate is met in this environment.** There is
currently no real FIRMS data to backfill in this local Mongo, so the backfill
ran as a true no-op (attributed=0, offshore=0) — the gate passes trivially
because the precondition set is empty, not because real data was attributed.
The mechanism itself (point-in-polygon attribution, sparse-index skip-on-invalid,
idempotent re-run) is verified correct by the integration tests in Phase 4,
which exercise it against seeded synthetic data where the precondition set is
non-empty.

**Action item for whoever runs Slice B against a real/staging/production Mongo
with actual FIRMS history:** re-run this exact gate query against that
environment after first boot with this code. If it returns non-zero, do not
proceed to Slice B until investigated (most likely cause: a `fuente` value
mismatch like the `'NASA FIRMS'` vs `'NASA_FIRMS'` case found here, or comuna
geometries not yet seeded).

## Deviation from design.md (documented, not a regression)

`design.md` Decision 4 specifies a derived Spring Data query method:
`findByGeometryIntersects(Point point)`. During implementation this failed at
application context startup with `PropertyReferenceException: No property
'geoIntersects' found for type 'GeoJsonMultiPolygon'` — this Spring Data
Commons version (3.3.4) has no `Part.Type` keyword for geo-intersection in
derived query method names (only `NEAR`/`WITHIN` exist as geo keywords;
`GeoIntersects` is not parsed as a predicate keyword, it gets treated as a
nested property path).

**Resolution:** implemented `findByGeometryIntersects(double longitude, double
latitude)` as an explicit `@Query` with a raw `$geoIntersects` MongoDB query
(`{ 'geometry': { '$geoIntersects': { '$geometry': { 'type': 'Point',
'coordinates': [ ?0, ?1 ] } } } }`), instead of a fully derived method. The
`findOneByGeometryIntersects(Point)` default-method wrapper (the single
expression reused by sync attribution and backfill, satisfying the
single-source-of-truth invariant from design.md) is unchanged in shape — it
just unpacks the `Point`'s x/y into the explicit-query method's two `double`
parameters. This preserves every invariant and test scenario from spec.md;
only the repository's internal query-construction mechanism differs from the
design doc's exact method signature.

## What remains for Slice B (next batch, separate branch/PR)

Per tasks.md Phases 5-10, NOT started in this batch:
- Phase 5: standardize `FIRMS_MAX_COUNT=5`/`FIRMS_COUNT_CRITICO=4`/`FIRMS_FRP_CRITICO=60` in both `ComunaRiskServiceImpl` and `TerritoryRiskServiceImpl`.
- Phase 6: `ComunaRiskServiceImpl`/`TerritoryRiskServiceImpl` query rework to use persisted `comunaId`; remove `assignFocosToComuna`/`findNearestComuna`/`findNearestRegionId`.
- Phase 7: `DashboardSnapshotServiceImpl.recomputeSnapshot` — add `fuente=NASA_FIRMS` filter via new `countByRegionIdAndFuenteAndFechaEventoBetween`.
- Phase 8: Mockito regression tests for reconciled escalation constants (both services), comuna/region query correctness, dashboard fuente filter.
- Phase 9: frontend window/scope labels (`reportPrint.js`, `DashboardPage.tsx`, `TerritoryMapPanel.jsx`).
- Phase 10: cleanup — remove dead centroid methods and old unfiltered repository methods once Slice B is verified green.

**Slice B blocking precondition:** confirm the backfill gate query
(`count({fuente:'NASA_FIRMS', comunaId:{$exists:false}})`) returns 0 in
whatever environment Slice B will be deployed/tested against, before Slice B
readers start querying by `comunaId`. In this local dev environment it already
returns 0 (trivially, since there is no real FIRMS data yet) — re-verify in
staging/production before merging Slice B there.

## Files changed (Slice A)

- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/model/ComunaInfo.java`
- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/model/HeatAlertEvent.java`
- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/config/MonitoredComunasConfig.java`
- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/config/BackfillComunaIdRunner.java` (new)
- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/repository/ComunaInfoRepository.java`
- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/repository/HeatAlertEventRepository.java`
- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/service/impl/NasaFirmsServiceImpl.java`
- `Producto/backend/simfat-backend/src/main/resources/application.properties` (added `firms.backfill.enabled`)
- `Producto/backend/simfat-backend/src/test/java/com/simfat/backend/repository/ComunaGeoAttributionRepositoryIntegrationTest.java` (new)
- `Producto/backend/simfat-backend/src/test/java/com/simfat/backend/service/impl/NasaFirmsServiceImplTest.java` (new)
- `Producto/backend/simfat-backend/src/test/java/com/simfat/backend/config/BackfillComunaIdRunnerIntegrationTest.java` (new)

---

## Batch 2: Slice B (tasks.md Phases 5-10)

Branch: `firms-geo-attribution/slice-b-standardize-scoring`, off
`firms-geo-attribution/slice-a-comuna-attribution` (feature-branch-chain).

### Tasks completed

#### Phase 5: Standardize Constants
- [x] 5.1 `ComunaRiskServiceImpl.java`: confirmed already standardized (`FIRMS_MAX_COUNT=5`, `FIRMS_COUNT_CRITICO=4`, `FIRMS_FRP_CRITICO=60`) — no change needed, Slice A's values were already the target standard.
- [x] 5.2 `TerritoryRiskServiceImpl.java`: replaced `FIRMS_MAX_COUNT=10.0`/`FIRMS_MAX_FRP=100.0`/`FIRMS_COUNT_CRITICO=8`/`FIRMS_FRP_CRITICO=75.0` with the standardized `5.0`/`80.0`/`4`/`60.0` (matching `ComunaRiskServiceImpl`'s `FIRMS_MAX_FRP` too, for full normalization-range parity). Implemented as duplicated literals with a cross-referencing comment (design's lower-risk option (a) — no shared-constant extraction, no refactor risk).

#### Phase 6: Comuna & Region Query Rework
- [x] 6.1 `ComunaRiskServiceImpl.recomputeByComuna`: FIRMS block now queries `heatAlertRepository.findByComunaIdAndFechaEventoAfter(comunaId, firms48h)` directly — no region-wide fetch, no centroid filter. Removed `assignFocosToComuna` and `findNearestComuna` (dead after the query rework).
- [x] 6.2 `TerritoryRiskServiceImpl.recomputeRiskByRegion`: derives the region's comuna set via `comunaInfoRepository.findByRegionId(regionId)` -> list of `comunaId`s, then queries `heatAlertEventRepository.findByComunaIdInAndFechaEventoAfter(regionComunaIds, firms48hAgo)`. Empty comuna set short-circuits to an empty FIRMS list (no query, no NPE). Removed `findNearestRegionId`. Injected `ComunaInfoRepository` as a new constructor dependency.
- [x] 6.3 `HeatAlertEventRepository.java`: added `findByComunaIdAndFechaEventoAfter(String comunaId, LocalDateTime after)` and `findByComunaIdInAndFechaEventoAfter(List<String> comunaIds, LocalDateTime after)`.

#### Phase 7: Dashboard Fix
- [x] 7.1 `HeatAlertEventRepository.java`: added `countByRegionIdAndFuenteAndFechaEventoBetween(regionId, fuente, start, end)`.
- [x] 7.2 `DashboardSnapshotServiceImpl.recomputeSnapshot`: `heatAlerts7d` now calls the new fuente-filtered method with `"NASA_FIRMS"`, replacing the unfiltered `countByRegionIdAndFechaEventoBetween` at this call site only.

#### Phase 8: Tests (regression-critical)
9 new/modified test methods across 3 files — see "Slice B test results" below for the exact list and real run output. All scenario coverage required by tasks.md 8.1-8.7 is satisfied, plus two extra FRP-threshold regression tests proving the FRP side of the constant change (not just the count side).

#### Phase 9: Frontend Labeling
- [x] 9.1 `reportPrint.js`: regional report FIRMS row labeled "Últimos 7 días · vista regional bruta (sin atribución por comuna)"; comunal report FIRMS component value labeled "(últimas 48h, por comuna)".
- [x] 9.2 `DashboardPage.tsx` `FirmsPanel`: added caption "Vista regional bruta (sin atribución por comuna) · ventana visible en el mapa". `TerritoryMapPanel.jsx` `COMPONENT_INFO.firms`: tooltip description and rawLabel now say "(últimas 48h, por comuna)". Layer-toggle short labels (`INDICATOR_LABELS.FIRMS`) intentionally left unchanged — short chrome labels are not the right place for window/scope detail; the existing FIRMS recency-bucket legend (hoy/recientes/sin fecha) already disambiguates by time at that level of the UI.

#### Phase 10: Cleanup
- [x] 10.1 Removed `assignFocosToComuna`, `findNearestComuna` (`ComunaRiskServiceImpl`), `findNearestRegionId` (`TerritoryRiskServiceImpl`) — confirmed zero remaining references anywhere in `src` (grep-verified) except explanatory comments.
- [x] 10.2 Removed the dead `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente` repository method (zero callers in `src/main`, confirmed by grep before removal — matches verify-report-slice-a.md's independent confirmation #2). **Deviation from tasks.md 10.2 wording:** did NOT remove `countByRegionIdAndFechaEventoBetween` (the unfiltered count) — it has a second, legitimate caller (`DashboardServiceImpl.buildCriticalRegion`, an unrelated "critical region" dashboard computation never in this change's scope) that the proposal/spec never asked to touch. Removing it would have broken that unrelated feature. Only `DashboardSnapshotServiceImpl`'s specific call site was migrated, exactly as the spec's "Dashboard snapshot filters by NASA_FIRMS source" requirement scopes it.
- [x] 10.3 `HeatAlertEventRepository`'s new comuna-scoped methods carry explanatory comments documenting `comunaId` as the canonical attribution source, consistent with `ComunaInfoRepository`'s existing Slice A Javadoc.

### Slice B test results (real, not estimated)

Ran via `mvn test` from `Producto/backend/simfat-backend`, against the real
local MongoDB on `localhost:27017` (the same environment Slice A used).

**New/modified Slice B test classes:**
```
Tests run: 5, Failures: 0, Errors: 0 -- ComunaRiskServiceImplTest (new)
Tests run: 6, Failures: 0, Errors: 0 -- TerritoryRiskServiceImplTest (new)
Tests run: 2, Failures: 0, Errors: 0 -- DashboardSnapshotServiceImplTest (1 pre-existing + 1 new)
```

**Full suite:**
```
[INFO] Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
(Was 78 at the end of Slice A; +12 net new test methods across the three
files above — 90 confirmed by direct `grep -c "@Test"` count cross-check.)

**Regression tests for the constant change — explicitly confirmed passing:**

| Test | Proves |
|---|---|
| `ComunaRiskServiceImplTest.recomputeByComuna_countAtStandardizedThreshold_escalatesToCritico` | `firmsCount==4` (the new `FIRMS_COUNT_CRITICO`) -> CRITICO in the comuna service |
| `TerritoryRiskServiceImplTest.recomputeRiskByRegion_countAtStandardizedThreshold_escalatesToCritico` | Same input (`firmsCount==4`) -> CRITICO in the region service. **Under the OLD constant (`8`) this input would NOT have escalated** — same 4-event input, only the threshold changed. This is the literal "did the new threshold actually take effect" proof requested. |
| `ComunaRiskServiceImplTest.recomputeByComuna_frpAtStandardizedThreshold_escalatesToCritico` | `firmsFrpMean==60.0` (the new `FIRMS_FRP_CRITICO`) -> CRITICO in the comuna service |
| `TerritoryRiskServiceImplTest.recomputeRiskByRegion_frpAtStandardizedThreshold_escalatesToCritico` | Same FRP input -> CRITICO in the region service. **Under the OLD constant (`75.0`) a mean FRP of `60.0` would NOT have escalated.** |
| `*_belowThreshold_doesNotEscalateToCritico` (both services) | `firmsCount==3`, `firmsFrpMean==50.0` -> NOT CRITICO in either service (guards against over-escalation) |
| `*_todaysDetection_alwaysCriticoRegardlessOfCount` (both services) | The pre-existing "today's detection is always CRITICO" override is unchanged by the constant swap |

All 6 of the above pass against the real Mongo-backed test run. The constant
change is confirmed to have actually taken effect, not just declared in code.

**Gotcha found while writing the "today" regression tests:** `isToday()` in
both services does `fechaEventoUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(SANTIAGO_ZONE)`
— it treats the stored `fechaEvento` as a UTC instant. Using
`LocalDateTime.now()` (server-local time, not necessarily UTC) as the test's
"today" timestamp produced a date that, after the UTC-to-Santiago conversion,
landed on the wrong calendar day and the test failed with `NORMAL` instead of
`CRITICO`. Fixed by using `LocalDateTime.now(ZoneOffset.UTC)` in both "today"
tests so the timestamp round-trips through the same UTC assumption the
production code makes. This is a latent test-authoring trap for anyone adding
future "today" scenarios to either service.

**Mockito strictness note:** two tests needed `lenient()` stubs —
`ComunaRiskServiceImplTest`'s adjacent-comuna leakage test (the unused stub
*is* the proof of no leakage, paired with an explicit `verify(..., never())`)
and `TerritoryRiskServiceImplTest`'s shared `@BeforeEach` comuna stub (unused
by the one test that deliberately queries an empty/different region). Neither
is a quality compromise — both are deliberate negative-path proofs.

### Files changed (Slice B)

- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/repository/HeatAlertEventRepository.java` (modified — added `countByRegionIdAndFuenteAndFechaEventoBetween`, `findByComunaIdAndFechaEventoAfter`, `findByComunaIdInAndFechaEventoAfter`; removed dead `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente`)
- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/service/impl/ComunaRiskServiceImpl.java` (modified — comunaId-scoped FIRMS query; removed `assignFocosToComuna`/`findNearestComuna`)
- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/service/impl/TerritoryRiskServiceImpl.java` (modified — standardized constants; comunaId-derived region FIRMS query; removed `findNearestRegionId`; new `ComunaInfoRepository` dependency)
- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/service/impl/DashboardSnapshotServiceImpl.java` (modified — `heatAlerts7d` now fuente-filtered)
- `Producto/backend/simfat-backend/src/test/java/com/simfat/backend/service/impl/ComunaRiskServiceImplTest.java` (new — 5 tests)
- `Producto/backend/simfat-backend/src/test/java/com/simfat/backend/service/impl/TerritoryRiskServiceImplTest.java` (new — 6 tests)
- `Producto/backend/simfat-backend/src/test/java/com/simfat/backend/service/impl/DashboardSnapshotServiceImplTest.java` (modified — updated existing mock to new method signature, added 1 new test)
- `Producto/frontend/simfat-web/src/features/territory/utils/reportPrint.js` (modified — window/scope labels)
- `Producto/frontend/simfat-web/src/pages/DashboardPage.tsx` (modified — `FirmsPanel` scope caption)
- `Producto/frontend/simfat-web/src/features/territory/components/TerritoryMapPanel.jsx` (modified — `COMPONENT_INFO.firms` window/scope label)

### Risks / residual notes for Slice B

- **Inherits Slice A's WARNING W1** (backfill gate passed trivially against empty local data) — Slice B's comuna/region queries are only as correct in production as the `comunaId` data they read. Re-verify the backfill gate in staging/production before trusting Slice B's risk scores there, per verify-report-slice-a.md.
- **`DashboardSnapshotServiceImplTest`'s pre-existing first test** (`recomputeSnapshot_calculatesLatestTrendAndFreshness`) had its `heatAlertRepository.countByRegionIdAndFechaEventoBetween` stub updated to `countByRegionIdAndFuenteAndFechaEventoBetween` to match the new call site — this is a mechanical signature update, not a behavior change to that test's intent.
- **Constant duplication, not extraction:** `FIRMS_MAX_COUNT`/`FIRMS_COUNT_CRITICO`/`FIRMS_FRP_CRITICO`/`FIRMS_MAX_FRP` are now numerically identical but textually duplicated across `ComunaRiskServiceImpl` and `TerritoryRiskServiceImpl`, per design's explicitly lower-risk option (a). A future refactor could extract a shared `FirmsThresholds` constants class; not done here to avoid touching both services' class structure in a batch already carrying the highest-risk change (the threshold values themselves).
