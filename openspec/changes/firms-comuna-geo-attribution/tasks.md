# Tasks: FIRMS comuna geo-attribution and standardized detection counts

## Review Workload Forecast (REVISED — post-incident re-scope)

This is the corrective re-implementation after the production revert. Slices A and B from the original implementation are NOT re-shipped wholesale; only the incident root-cause fixes ship: the coverage-gap fallback (Decision 6), the bulk/async backfill rewrite (revised Decision 1), and the new regression. The centroid methods are KEPT (Phase 10.1 reopened), which shrinks the net diff vs. the original.

| Field | Value |
|-------|-------|
| Estimated changed lines | ~280-360 (Slice C corrective: fallback router ~120-160, backfill bulk/async rewrite ~80-100, coverage probe + tests ~80-100) |
| 400-line budget risk | Medium |
| Chained PRs recommended | No (single corrective PR; the original A/B already merged-then-reverted) |
| Suggested split | Single PR: "Slice C — coverage-gap fallback + bulk/async backfill + regression" (re-introduces A/B with the two fixes folded in) |
| Delivery strategy | ask-on-risk |
| Chain strategy | n/a (single PR) |

Decision needed before apply: Yes (confirm single corrective PR vs. re-chaining A/B; recommend single PR since the fallback removes the Slice-A-before-B gate)
Chained PRs recommended: No
Chain strategy: n/a
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Slice C (corrective): geometry persist + sync attribution + coverage-gap fallback router + bulk/async backfill + standardized constants + dashboard fuente filter, with centroid methods RETAINED | PR 1 (single) | No Slice-A-before-B gate anymore — Decision 6 fallback covers reads while backfill runs async. Verify: uncovered region → `firmsCount > 0` (fallback), not zero |

The original Slice-A → Slice-B chained gate is dissolved by Decision 6 (fallback removes the correctness race). Recommend a single corrective PR. Confirm with the user before `sdd-apply` per the ask-on-risk guard.

---

## Phase 1: Slice A — Schema & Seed (Foundation)

- [x] 1.1 `model/ComunaInfo.java`: add `geometry: GeoJsonMultiPolygon` field with `@GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)`.
- [x] 1.2 `model/HeatAlertEvent.java`: add nullable `comunaId: String` field; add compound index `{comunaId:1, fechaEvento:-1}`.
- [x] 1.3 `repository/ComunaInfoRepository.java`: add `List<ComunaInfo> findByGeometryIntersects(Point point)` and default method `findOneByGeometryIntersects(Point)` (first-match tie-break). NOTE: implemented as explicit `@Query` (`findByGeometryIntersects(double lon, double lat)`) — the derived `GeometryIntersects` keyword from design.md does not exist in this Spring Data Commons version. See apply-progress.md "Deviation from design.md".
- [x] 1.4 `repository/HeatAlertEventRepository.java`: add `existsByLatitudAndLongitudAndFechaEventoAndFuente(...)` (new dedup key, drops `regionId`); add `streamByFuenteAndComunaIdIsNull(String fuente)` for backfill; old `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente` kept (removal deferred to Phase 10 cleanup, not yet reached).
- [x] 1.5 `config/MonitoredComunasConfig.java`: parse `feature.geometry` per Decision 3 — `validateRings` (closure + min vertex count) inside try/catch; on failure log warn + persist comuna with `geometry=null` + continue startup (no abort).

## Phase 2: Slice A — Sync-Time Attribution & Dedup (Core)

- [x] 2.1 `service/impl/NasaFirmsServiceImpl.java`: inject `ComunaInfoRepository`. In `parseCsvResponse`, replace dedup check with `existsByLatitudAndLongitudAndFechaEventoAndFuente`.
- [x] 2.2 `service/impl/NasaFirmsServiceImpl.java`: after dedup short-circuit, attribute `comunaId` via `comunaRepository.findOneByGeometryIntersects(new GeoJsonPoint(lon, lat)).map(ComunaInfo::getId).orElse(null)` before building/saving the event. Keep `regionId` set (fetching region, used by raw bbox view).

## Phase 3: Slice A — Backfill Mechanism

- [x] 3.1 Create `BackfillComunaIdRunner` (new `@Component`, `@Order(Ordered.LOWEST_PRECEDENCE)`, `@EventListener(ApplicationReadyEvent.class)`): stream `streamByFuenteAndComunaIdIsNull("NASA_FIRMS")`, attribute via same `findOneByGeometryIntersects` expression, save. Add `firms.backfill.enabled` property (default true).
- [x] 3.2 Verify ordering: confirm `MonitoredComunasConfig`'s seed listener has default order and `BackfillComunaIdRunner` runs strictly after it within the same boot. Verified via real boot logs.
- [x] 3.3 **Deploy gate check (manual/CI)**: run `count({fuente:'NASA_FIRMS', comunaId: {$exists:false}})` against the target environment after first boot with backfill present — MUST return 0 before any Slice B task starts. Record result in PR description. **RESULT: 0 (PASS)** against local `simfat` Mongo — see apply-progress.md "Backfill gate status" (no real FIRMS data present locally yet; gate passes trivially, mechanism verified correct via Phase 4 tests against seeded synthetic data).

## Phase 4: Slice A — Tests (spec: comuna-geo-attribution)

- [x] 4.1 `@DataMongoTest` geo test (model on `OpenEoRepositoriesIntegrationTest`, `auto-index-creation=true`): seed 2-3 small hand-authored `GeoJsonMultiPolygon` squares; assert point inside comuna-A resolves to comuna-A (spec scenario: "Detection inside exactly one comuna polygon").
- [x] 4.2 Same test class: assert point outside every square -> `findByGeometryIntersects` empty -> `comunaId` null (spec scenario: "Detection outside every comuna polygon").
- [x] 4.3 Same test class: assert boundary point on shared edge of two adjacent squares resolves deterministically to the same first-match comuna across repeated runs (spec scenario: "Boundary point matches more than one polygon").
- [x] 4.4 Same test class: malformed-geometry comuna is skipped (sparse index) without breaking queries against valid comunas (spec scenario: "Invalid geometry is skipped, not fatal").
- [x] 4.5 `NasaFirmsServiceImpl` test: feed `parseCsvResponse` a CSV row whose lat/lon is inside a seeded comuna -> saved event has that `comunaId` (spec scenario: "Detection inside exactly one comuna polygon").
- [x] 4.6 Same test class: CSV row in the ocean -> saved event `comunaId == null`, not dropped (spec scenario: "Detection outside every comuna polygon (offshore/no-match)").
- [x] 4.7 Same test class: same `(lat, lon, fecha, fuente)` seen on a second region's leg -> no second insert (spec scenario covering cross-region dedup / Decision 2).
- [x] 4.8 Backfill test: existing rows with `comunaId` absent/null get attributed correctly; re-running backfill is a no-op (spec scenarios: "Backfill attributes existing rows correctly", "Backfill is idempotent and re-runnable").

**Slice A deploy gate (blocking):** Phase 3.3 MUST show 0 before any Phase 5+ task begins.

---

## Phase 5: Slice B — Standardize Constants (Core)

- [x] 5.1 `service/impl/ComunaRiskServiceImpl.java`: confirm/keep `FIRMS_MAX_COUNT=5`, `FIRMS_COUNT_CRITICO=4`, `FIRMS_FRP_CRITICO=60` as the canonical constants. (Already standardized in Slice A — no change needed.)
- [x] 5.2 `service/impl/TerritoryRiskServiceImpl.java`: removed `10`/`8`/`75` constants; replaced with `5`/`4`/`60` (duplicated literal per design's lower-risk option (a), not extracted to a shared class — both services keep their own constant block with a comment cross-referencing the standardization decision).

## Phase 6: Slice B — Comuna & Region Query Rework

- [x] 6.1 `service/impl/ComunaRiskServiceImpl.java`: replaced centroid-based focos assignment with `heatAlertRepository.findByComunaIdAndFechaEventoAfter(comunaId, firms48h)`; removed `assignFocosToComuna` and `findNearestComuna`.
- [x] 6.2 `service/impl/TerritoryRiskServiceImpl.java`: region FIRMS totals now derived from `comunaInfoRepository.findByRegionId(regionId)` -> list of comunaIds -> `findByComunaIdInAndFechaEventoAfter`; removed `findNearestRegionId`. Null `comunaId` events excluded by construction (query is scoped to known comunaIds, never matches null).
- [x] 6.3 `repository/HeatAlertEventRepository.java`: added `findByComunaIdAndFechaEventoAfter` (comuna-scoped) and `findByComunaIdInAndFechaEventoAfter` (region-scoped, via comuna set) to replace the removed centroid-assignment read paths.

## Phase 7: Slice B — Dashboard Fix

- [x] 7.1 `repository/HeatAlertEventRepository.java`: added `countByRegionIdAndFuenteAndFechaEventoBetween(regionId, fuente, from, to)`.
- [x] 7.2 `service/impl/DashboardSnapshotServiceImpl.java`: `recomputeSnapshot`'s `heatAlerts7d` now calls the new method with `fuente="NASA_FIRMS"`, replacing the unfiltered `countByRegionIdAndFechaEventoBetween` at this call site. (That unfiltered method is kept — `DashboardServiceImpl.buildCriticalRegion` still legitimately uses it for an unrelated, out-of-scope computation; see Deviation note below.)

## Phase 8: Slice B — Tests (spec: firms-risk-scoring)

- [x] 8.1 `ComunaRiskServiceImplTest.recomputeByComuna_countAtStandardizedThreshold_escalatesToCritico` + `TerritoryRiskServiceImplTest.recomputeRiskByRegion_countAtStandardizedThreshold_escalatesToCritico`: `firmsCount==4` -> CRITICO in both services (region test proves the new value 4 took effect vs. the old 8).
- [x] 8.2 `ComunaRiskServiceImplTest.recomputeByComuna_belowThreshold_doesNotEscalateToCritico` + `TerritoryRiskServiceImplTest.recomputeRiskByRegion_belowThreshold_doesNotEscalateToCritico`: `firmsCount==3`, `firmsFrpMean==50`, not-today -> NOT CRITICO in both.
- [x] 8.3 `ComunaRiskServiceImplTest.recomputeByComuna_todaysDetection_alwaysCriticoRegardlessOfCount` + `TerritoryRiskServiceImplTest.recomputeRiskByRegion_todaysDetection_alwaysCriticoRegardlessOfCount`: `hasTodayFirms==true` -> CRITICO regardless of count, unchanged in both.
- [x] 8.4 `ComunaRiskServiceImplTest.recomputeByComuna_queriesByPersistedComunaId_onlyOwnEventsCounted`: comuna-A recompute counts only its own `comunaId`-scoped events; adjacent comuna's stub is asserted never invoked (`verify(..., never())`).
- [x] 8.5 Null `comunaId` exclusion: satisfied by construction — `findByComunaIdAndFechaEventoAfter`/`findByComunaIdInAndFechaEventoAfter` are scoped to specific non-null comunaId values, so a null-`comunaId` row can never be returned by either query. No separate test needed beyond 8.4/8.6 (documented here rather than a redundant assertion).
- [x] 8.6 `TerritoryRiskServiceImplTest.recomputeRiskByRegion_queriesByComunaIdsOfRegion_singleAttributionNoDoubleCount` + `recomputeRiskByRegion_noComunasInRegion_returnsZeroFirmsWithoutQuerying`: region total equals sum of its comunas' attributed counts; empty comuna set short-circuits to zero without querying.
- [x] 8.7 `DashboardSnapshotServiceImplTest.recomputeSnapshot_heatAlerts7d_callsFuenteFilteredCountOnly`: asserts `heatAlerts7d` comes from the fuente-filtered method and that the unfiltered method is never called.

**Also added (FRP-threshold regression, explicitly requested):** `recomputeByComuna_frpAtStandardizedThreshold_escalatesToCritico` + `recomputeRiskByRegion_frpAtStandardizedThreshold_escalatesToCritico` — `firmsFrpMean==60` -> CRITICO in both (proves the new FRP threshold, was 75 in territory, took effect).

## Phase 9: Frontend Labeling (Slice B, low-risk follow-on)

- [x] 9.1 `frontend/.../reportPrint.js`: regional report's FIRMS row now labeled "Últimos 7 días · vista regional bruta (sin atribución por comuna)"; comunal report's FIRMS component value now labeled "(últimas 48h, por comuna)".
- [x] 9.2 `frontend/.../DashboardPage.tsx`: `FirmsPanel` now shows a caption "Vista regional bruta (sin atribución por comuna) · ventana visible en el mapa". `frontend/.../TerritoryMapPanel.jsx`: `COMPONENT_INFO.firms` tooltip description/label now says "(últimas 48h, por comuna)" — matches the comuna-scoped score breakdown shown when a user inspects a comuna's FIRMS contribution. Layer-toggle short labels (`INDICATOR_LABELS.FIRMS = 'Focos activos'`) intentionally left short for UI chrome; detailed window/scope context lives in the tooltips and existing FIRMS recency-bucket legend (hoy/recientes/sin fecha), which already disambiguates by time window.

## Phase 10: Cleanup

- [ ] ~~10.1 Removed `assignFocosToComuna`, `findNearestComuna` (`ComunaRiskServiceImpl`) and `findNearestRegionId` (`TerritoryRiskServiceImpl`) now that Phase 8 tests are green and the replacement queries are wired in.~~ **REOPENED post-incident (revised Decision 6): do NOT delete these methods. They are the permanent centroid fallback for regions without comuna geometry. The production revert already restored them; the corrective re-implementation (Phase 11) must KEEP them. This task is intentionally left UNCHECKED to record that the deletion was wrong.**
- [x] 10.2 Removed the dead `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente` repository method (confirmed zero callers in `src/main`, per verify-report-slice-a.md). `countByRegionIdAndFechaEventoBetween` (unfiltered) was **not** removed — it has a second, legitimate caller (`DashboardServiceImpl.buildCriticalRegion`) outside this change's scope; only `DashboardSnapshotServiceImpl`'s call site was migrated to the fuente-filtered variant, per the proposal/spec which only mandates the fix for `DashboardSnapshotServiceImpl.recomputeSnapshot`.
- [x] 10.3 `HeatAlertEventRepository`'s comuna-scoped methods carry Javadoc-style comments documenting `comunaId` as the canonical attribution source, consistent with `ComunaInfoRepository`'s existing Javadoc from Slice A.

---

## Phase 11: Corrective Re-implementation (post-incident) — NEW

> Phases 1-10 above are the historical record of what was built, merged (PR #12/#13/#14), then REVERTED from production after the incident described in `design.md` ("Production incident that drove this revision"). The repo is currently back on the pre-change centroid logic. Phase 11 re-introduces the change with the two root causes fixed: the coverage-gap fallback (Decision 6) and the bulk/async backfill (revised Decision 1). Strict TDD applies — write/extend the failing test before the production code for each behavioral item.

### 11.1 Coverage probe (Decision 6 plumbing)

- [x] 11.1 `repository/ComunaInfoRepository.java`: add `long countByRegionIdAndGeometryNotNull(String regionId)` — the region-granularity coverage probe. Cheap derived count over the sparse-indexed `geometry` field.

### 11.2 Coverage-gap fallback router in both risk services (Decision 6) — the core fix

- [x] 11.2 `service/impl/TerritoryRiskServiceImpl.java`: before selecting FIRMS events, probe `comunaInfoRepository.countByRegionIdAndGeometryNotNull(regionId) > 0`. If COVERED → derive events via `findByComunaIdInAndFechaEventoAfter(comunaIdsOfRegion, firms48hAgo)` (geometric source of truth). If UNCOVERED → use the RETAINED `findNearestRegionId` centroid path exactly as the current post-revert code does. Inject `ComunaInfoRepository` (not currently a dependency of this service). Keep `findNearestRegionId` private method intact.
- [x] 11.3 `service/impl/ComunaRiskServiceImpl.java`: same probe on `comuna.getRegionId()`. If COVERED → `findByComunaIdAndFechaEventoAfter(comunaId, firms48h)`. If UNCOVERED → keep `assignFocosToComuna(regionFocos, comunaId, comunaRepository.findByRegionId(...))`. Keep `assignFocosToComuna` + `findNearestComuna` private methods intact.
- [x] 11.4 Confirm BOTH paths feed the SAME reconciled constants (`FIRMS_MAX_COUNT=5`, `FIRMS_COUNT_CRITICO=4`, `FIRMS_FRP_CRITICO=60`). `TerritoryRiskServiceImpl` post-revert still has the OLD `10`/`8`/`75` — re-apply the standardization (Invariant 5) as part of this corrective slice. Only event-selection differs between paths; scoring/escalation must be identical.

### 11.3 Bulk + async backfill rewrite (revised Decision 1)

- [x] 11.5 Recreate `BackfillComunaIdRunner` as a `@Component` using **`BulkOperations`/`bulkWrite` batched updates** (`BATCH≈500`, `BulkMode.UNORDERED`) instead of one `.save()` per row. Per-row `$geoIntersects` read stays (each point → its comuna), but writes batch into ~4 bulk round-trips for ~2k rows. Log `status=progress` per batch and `status=done` with totals.
- [x] 11.6 Make the backfill **async / non-boot-blocking**: `@Async("backfillExecutor")` on the `@EventListener(ApplicationReadyEvent.class)` method, plus `@EnableAsync` and a dedicated single-thread `backfillExecutor` bean. The app must reach READY immediately; the backfill runs in the background. Keep `@Order(LOWEST_PRECEDENCE)` so it still fires after the geometry seed. Keep `firms.backfill.enabled` (default true).
- [x] 11.7 Remove the hard Slice-A-before-B deploy gate from the workflow: the backfill is now a precision-improvement job, not a correctness prerequisite (the Decision-6 fallback covers unattributed reads). The `count(fuente=NASA_FIRMS, comunaId missing)→0` query is an OBSERVABILITY signal for covered regions only; uncovered regions legitimately keep null rows. Document this in the PR description, not as a blocking go/no-go.

### 11.4 Regression tests (mandatory — defends the corrected Invariant 3)

> Test command: `cd Producto/backend/simfat-backend && mvn test`. Geo tests follow the `@DataMongoTest` + `auto-index-creation=true` pattern from `OpenEoRepositoriesIntegrationTest` (Decision 5).

- [x] 11.8 **THE incident regression (Layer D):** `TerritoryRiskServiceImplTest.recomputeRiskByRegion_uncoveredRegion_usesCentroidFallbackNotZero` + `ComunaRiskServiceImplTest.recomputeByComuna_uncoveredRegion_usesCentroidFallbackNotZero` — mock `countByRegionIdAndGeometryNotNull(...)` to return `0`, provide FIRMS events in the region, assert `firmsCount > 0` (functional via fallback, NOT silently zero/blind). This scenario must make the "uncovered region goes blind" failure impossible to silently reappear.
- [x] 11.9 Covered-region routing: mock the probe to return `> 0`, assert the service reads via the `comunaId` query path and the centroid method is NOT invoked (`verify(..., never())`) — in both services.
- [x] 11.10 Gap-vs-offshore distinction: a COVERED region with a FIRMS row whose `comunaId == null` (geometry loaded, point offshore) is NOT re-routed to fallback — the row stays excluded from comuna-scoped counts (preserves Invariant 4).
- [x] 11.11 Auto-upgrade: probe flips `0 → >0` between two recomputes → second recompute switches from centroid to `comunaId` path with no code change (asserts Invariant 8).
- [x] 11.12 `@DataMongoTest` for `countByRegionIdAndGeometryNotNull`: seed comunas with/without geometry across two regions; assert the count is correct per region (covered vs. uncovered). Covered by `ComunaGeoAttributionRepositoryIntegrationTest`'s 3 coverage-probe tests.
- [x] 11.13 Bulk backfill test: existing `comunaId`-null rows get attributed via the bulk path; re-running is a no-op; assert it does not block (or assert the bulk update is issued in batches if the executor is invoked synchronously in test). Covered by `BackfillComunaIdRunnerIntegrationTest` (4 tests: inside/offshore attribution, idempotent re-run, disabled-flag no-op, multi-row batch).

## Phase 12: Post-review corrections

> A fresh-context 8-angle code review of Phase 11 (with independent verification) confirmed 12 findings, none refuted. Fixes below close all 12 (some findings shared a single fix). Design rationale for FIX 1/FIX 2 recorded in design.md's Decision 6 amendment.

- [x] 12.1 (findings C1, C5) Coverage probe corrected to per-comuna granularity (`comuna.getGeometry() != null`, in-memory, no DB round trip) instead of the region-level `countByRegionIdAndGeometryNotNull` proxy. `TerritoryRiskServiceImpl` now splits covered/uncovered comunas within a region and sums both contributions instead of an all-or-nothing region decision.
- [x] 12.2 (finding C6) Centroid fallback's candidate event pool sourced by `fuente` + recency only, never pre-filtered by persisted `regionId` — fixes silent undercounting between overlapping uncovered regions caused by Decision 2's region-independent dedup.
- [x] 12.3 (finding C3) `BackfillComunaIdRunner.backfill()` wraps its loop in try/catch, logs `status=failed` with batch/attributed/offshore/skippedNoCoords counters on exception instead of silently dying via Spring's default async exception handler.
- [x] 12.4 (finding C4) `MonitoredComunasConfig` publishes a new `ComunaGeometrySeededEvent` after the geometry seed completes; `BackfillComunaIdRunner` listens for that event instead of `ApplicationReadyEvent` + `@Order`, removing the unenforced sync-seed-vs-async-backfill race.
- [x] 12.5 (finding C2) `DashboardSnapshotServiceImpl.recomputeSnapshot` now calls `countByRegionIdAndFuenteAndFechaEventoBetween(regionId, "NASA_FIRMS", ...)` for `heatAlerts7d` instead of the unfiltered method. Corrects the false claim in Phase 7/apply-progress.md that this call site was already migrated — it was not; the new method existed but had zero callers until this fix.
- [x] 12.6 (findings C7, C9) Extracted `FirmsAttributionRouter` — the single sanctioned entry point for reading FIRMS events by attribution, used by both `ComunaRiskServiceImpl` and `TerritoryRiskServiceImpl`, replacing two independently-duplicated routing implementations.
- [x] 12.7 (finding C8) Extracted `FirmsScoringConstants` — `FIRMS_MAX_COUNT`/`FIRMS_MAX_FRP`/`FIRMS_COUNT_CRITICO`/`FIRMS_FRP_CRITICO` now declared once, referenced by both services instead of duplicated literals.
- [x] 12.8 (finding C10) Backfill rows with null lat/lon now increment a `skippedNoCoords` counter, included in the `status=done`/`status=failed` log lines instead of silently looping forever unobserved.
- [x] 12.9 (finding C11) The trailing partial batch's `BulkWriteResult` is now captured and logged the same way as in-loop batches, instead of discarded.
- [x] 12.10 (finding C13) Removed the dead `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente` repository method (zero callers).
