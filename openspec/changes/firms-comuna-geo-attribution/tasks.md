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

- [ ] 5.1 `service/impl/ComunaRiskServiceImpl.java`: confirm/keep `FIRMS_MAX_COUNT=5`, `FIRMS_COUNT_CRITICO=4`, `FIRMS_FRP_CRITICO=60` as the canonical constants.
- [ ] 5.2 `service/impl/TerritoryRiskServiceImpl.java`: remove `10`/`8`/`75` constants; replace with the same `5`/`4`/`60` values (shared source per Decision 5 — extract to shared constant or duplicate literal per design's lower-risk option (a)).

## Phase 6: Slice B — Comuna & Region Query Rework

- [ ] 6.1 `service/impl/ComunaRiskServiceImpl.java`: replace centroid-based focos assignment with a query filtered by persisted `comunaId`; remove `assignFocosToComuna` and `findNearestComuna`.
- [ ] 6.2 `service/impl/TerritoryRiskServiceImpl.java`: derive region from each event's `comunaId` -> comuna's `regionId`; remove `findNearestRegionId`; aggregate region FIRMS totals as sum of attributed comuna counts, excluding null `comunaId`.
- [ ] 6.3 `repository/HeatAlertEventRepository.java`: add/confirm comuna-scoped query method (e.g. `findByComunaIdAndFechaEventoAfter`) to replace the removed centroid-assignment read path.

## Phase 7: Slice B — Dashboard Fix

- [ ] 7.1 `repository/HeatAlertEventRepository.java`: add `countByRegionIdAndFuenteAndFechaEventoBetween(regionId, fuente, from, to)`.
- [ ] 7.2 `service/impl/DashboardSnapshotServiceImpl.java`: update `recomputeSnapshot`'s `heatAlerts7d` computation to call the new method with `fuente="NASA_FIRMS"`, replacing the unfiltered `countByRegionIdAndFechaEventoBetween`.

## Phase 8: Slice B — Tests (spec: firms-risk-scoring)

- [ ] 8.1 Mockito test on `ComunaRiskServiceImpl`/`TerritoryRiskServiceImpl` (parameterized, same input set both services): `firmsCount==4`+`firmsFrpMean>=60` -> CRITICO in both (spec: "Comuna-level CRITICO escalation at standardized threshold", "Region-level CRITICO escalation uses the same threshold as comuna-level").
- [ ] 8.2 Same parameterized test: `firmsCount==3`, `firmsFrpMean==50`, not-today, low score/FWI -> NOT CRITICO in both (spec: "Below-threshold counts do not escalate").
- [ ] 8.3 Same test: `hasTodayFirms==true` -> CRITICO regardless of count, unchanged in both services (regression guard from design Layer B).
- [ ] 8.4 `ComunaRiskServiceImpl` test: two adjacent comunas each with correctly attributed `comunaId` events -> recompute for one comuna counts only its own events, none from the adjacent comuna (spec: "Comuna risk count reflects only its own attributed detections").
- [ ] 8.5 Same test: events with `comunaId=null` excluded from every comuna's count (spec: "Null comunaId events are excluded from comuna-scoped counts").
- [ ] 8.6 `TerritoryRiskServiceImpl` test: a single physical detection (one persisted row, one `comunaId`) contributes to exactly one region's total; region total equals sum of its comunas' attributed counts; null `comunaId` excluded (spec: "Single physical detection counted once across regions", "Region total equals sum of its comunas' attributed counts").
- [ ] 8.7 `DashboardSnapshotServiceImpl` test: `heat_alert_events` with mixed `fuente` values in the 7-day window -> `heatAlerts7d` counts only `NASA_FIRMS` rows (spec: "Dashboard 7-day count excludes non-FIRMS alert sources", "Dashboard count matches FIRMS-only total").

## Phase 9: Frontend Labeling (Slice B, low-risk follow-on)

- [ ] 9.1 `frontend/.../reportPrint.js`: label comuna-score `firmsCount` and dashboard `firms.total/today/highFrp` with their respective window ("48h" vs "7 dias") and scope ("por comuna" vs "vista regional bruta") (spec: "PDF report no longer shows unlabeled contradictory counts").
- [ ] 9.2 `frontend/.../DashboardPage.tsx`, `TerritoryMapPanel.jsx`: add the same window/scope labels to on-screen widgets/tooltips (spec: "Comuna tooltip and dashboard widget show distinct labels").

## Phase 10: Cleanup

- [ ] 10.1 Remove now-dead `assignFocosToComuna`, `findNearestComuna`, `findNearestRegionId` once Phase 8 tests are green (design Rollback Plan: keep until Slice B verified, then delete in follow-up — delete here since Slice B is fully scoped in this change).
- [ ] 10.2 Remove the old `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente` and `countByRegionIdAndFechaEventoBetween` (unfiltered) repository methods once their call sites are fully migrated.
- [ ] 10.3 Update `ComunaInfoRepository`/`HeatAlertEventRepository` Javadoc/comments to reflect `comunaId` as canonical attribution source.
