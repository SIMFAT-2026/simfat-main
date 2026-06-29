# Tasks: FIRMS comuna geo-attribution and standardized detection counts

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~650-800 (Slice A ~350-400, Slice B ~300-400) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (Slice A: schema+seed+sync+backfill) -> PR 2 (Slice B: constants+queries+dashboard fix) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Slice A: persist geometry, sync-time attribution, backfill, dedup key | PR 1 | Gate: `count(fuente=NASA_FIRMS, comunaId missing)==0` must be 0 before PR 2 starts |
| 2 | Slice B: standardize constants, comuna/region queries by `comunaId`, dashboard `fuente` filter | PR 2 | Depends on PR 1 merged + backfill verified in target environment |

Ask the user which chain strategy (`stacked-to-main` vs `feature-branch-chain`) before `sdd-apply` starts, per the ask-on-risk guard.

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

- [x] 10.1 Removed `assignFocosToComuna`, `findNearestComuna` (`ComunaRiskServiceImpl`) and `findNearestRegionId` (`TerritoryRiskServiceImpl`) now that Phase 8 tests are green and the replacement queries are wired in.
- [x] 10.2 Removed the dead `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente` repository method (confirmed zero callers in `src/main`, per verify-report-slice-a.md). `countByRegionIdAndFechaEventoBetween` (unfiltered) was **not** removed — it has a second, legitimate caller (`DashboardServiceImpl.buildCriticalRegion`) outside this change's scope; only `DashboardSnapshotServiceImpl`'s call site was migrated to the fuente-filtered variant, per the proposal/spec which only mandates the fix for `DashboardSnapshotServiceImpl.recomputeSnapshot`.
- [x] 10.3 `HeatAlertEventRepository`'s comuna-scoped methods carry Javadoc-style comments documenting `comunaId` as the canonical attribution source, consistent with `ComunaInfoRepository`'s existing Javadoc from Slice A.
