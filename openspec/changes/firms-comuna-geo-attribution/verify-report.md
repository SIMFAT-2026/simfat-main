# Verify Report: firms-comuna-geo-attribution (Full Change — Slice A + Slice B)

**Scope**: Consolidated verification of the entire change — Slice A (tasks.md Phases 1-4: schema, seed, sync-time attribution, backfill) and Slice B (tasks.md Phases 5-10: constant standardization, comuna/region query rework, dashboard fix, frontend labeling, cleanup) — together, as they will ship and run in production. This supersedes verify-report-slice-a.md as the authoritative final-state report; that file remains valid as a historical record of the Slice A-only checkpoint.

**Verdict: PASS WITH WARNINGS**

---

## 1. Completeness, Task Checklist (Phases 1-10)

All 36 checked tasks across both slices were independently inspected in source (not trusted from apply-progress.md's checkmarks alone).

| Phase | Tasks | Status |
|---|---|---|
| 1, Schema and Seed | 1.1-1.5 | DONE, confirmed in source (carries forward from verify-report-slice-a.md, re-confirmed unchanged on this branch) |
| 2, Sync Attribution and Dedup | 2.1-2.2 | DONE, confirmed in source |
| 3, Backfill | 3.1-3.3 | DONE, confirmed in source |
| 4, Slice A Tests | 4.1-4.8 | DONE, 9 tests, all passing in full suite re-run |
| 5, Standardize Constants | 5.1-5.2 | DONE, confirmed in source (see section 2 below, exhaustive repo-wide grep, not just the two named services) |
| 6, Comuna and Region Query Rework | 6.1-6.3 | DONE, confirmed in source, findByComunaIdAndFechaEventoAfter and findByComunaIdInAndFechaEventoAfter are the only FIRMS read paths in both risk services, zero remaining centroid logic |
| 7, Dashboard Fix | 7.1-7.2 | DONE, confirmed in source |
| 8, Slice B Tests | 8.1-8.7 | DONE, 16 new/modified test methods across 3 files, all passing |
| 9, Frontend Labeling | 9.1-9.2 | DONE, confirmed in source, labels match actual backend window/scope (see section 5) |
| 10, Cleanup | 10.1-10.3 | DONE, confirmed by grep, zero remaining references to removed methods/constants anywhere in src/main |

No unchecked or cosmetically-checked tasks found in either slice.

---

## 2. Cross-Slice Integration Check (orchestrator question 1)

**Question**: Does Slice B's standardized scoring actually consume Slice A's persisted comunaId end-to-end, or is there an unaudited fallback to old centroid logic?

**Finding: Fully wired, no gaps.**

- ComunaRiskServiceImpl.recomputeByComuna (the only call path, reached via the scheduled recomputeAllComunas cron, and via syncCopernicusAndRecompute) queries heatAlertRepository.findByComunaIdAndFechaEventoAfter(comunaId, firms48h) directly, no region-wide fetch, no centroid filter, no intermediate cache.
- TerritoryRiskServiceImpl.recomputeRiskByRegion (reached via the scheduled cron) derives regionComunaIds from comunaInfoRepository.findByRegionId(regionId), then queries heatAlertEventRepository.findByComunaIdInAndFechaEventoAfter(regionComunaIds, firms48hAgo). Empty comuna sets short-circuit before any query (no NPE risk).
- Grep for findNearestComuna, findNearestRegionId, assignFocosToComuna across all of src/main returns zero matches, not even commented-out code, only explanatory comments in HeatAlertEventRepository.java referencing the replaced methods by name for documentation purposes.
- Both query methods (findByComunaIdAndFechaEventoAfter, findByComunaIdInAndFechaEventoAfter) are scoped to specific non-null comunaId value(s) by construction, a null-comunaId row can structurally never be returned by either, satisfying the spec's null-exclusion scenarios without a separate runtime check.
- ComunaRiskServiceImplTest.recomputeByComuna_queriesByPersistedComunaId_onlyOwnEventsCounted explicitly asserts via verify(heatAlertRepository, never()) that an adjacent comuna's repository call is never invoked, proving comuna isolation at the test level, not just by code inspection.

No fallback path to old centroid logic exists anywhere in the consumed code. This was verified by reading both services' full FIRMS-handling blocks end to end, not just grepping for the deleted method names.

---

## 3. Constant Standardization Completeness (orchestrator question 2)

**Question**: Is the 5/4/60 standardization complete, with no remaining 10/8/75 anywhere in src/main?

**Finding: Complete. Confirmed by exhaustive repo-wide grep, not just the two named services.**

A grep for FIRMS_MAX_COUNT, FIRMS_COUNT_CRITICO, FIRMS_FRP_CRITICO, FIRMS_MAX_FRP across src/main returns exactly four declarations, two per service, both using the standardized values:

| File | Constants | Values |
|---|---|---|
| ComunaRiskServiceImpl.java lines 57-58, 74-75 | FIRMS_MAX_COUNT / FIRMS_MAX_FRP / FIRMS_COUNT_CRITICO / FIRMS_FRP_CRITICO | 5.0 / 80.0 / 4 / 60.0 |
| TerritoryRiskServiceImpl.java lines 52-53, 70-71 | same names | 5.0 / 80.0 / 4 / 60.0 |

A broader grep for the literal old values inside TerritoryRiskServiceImpl.java turns up zero matches against the old FIRMS thresholds, the only adjacent hits in the file are unrelated (NDVI_MAX = 0.8, a comment referencing the historical value for documentation). No other file in src/main outside these two services ever declared FIRMS escalation constants, so there is no third location to check, ComunaRiskSnapshotService, DashboardServiceImpl, and AlertRuleEvaluationServiceImpl consume the output (RiskLevel) of these services, not raw thresholds.

Constants are duplicated, not extracted to a shared class, per design.md's explicitly-chosen lower-risk option (a), this is a documented, deliberate tradeoff, not an oversight, and both blocks carry cross-referencing comments.

---

## 4. Dashboard Fuente-Filter Fix, Verifying It Does Not Break buildCriticalRegion (orchestrator question 3)

**Question**: Does DashboardSnapshotServiceImpl's fix silently break DashboardServiceImpl.buildCriticalRegion, which apply-progress.md says deliberately still uses the unfiltered countByRegionIdAndFechaEventoBetween?

**Finding: Genuinely intentional and correct, not a missed call site.**

Verified by reading both call sites directly:

- DashboardSnapshotServiceImpl.recomputeSnapshot (line 57-58) now calls heatAlertRepository.countByRegionIdAndFuenteAndFechaEventoBetween(regionId, "NASA_FIRMS", now.minusDays(7), now), the fix, with an inline comment explaining why the filter is mandatory there.
- DashboardServiceImpl.buildCriticalRegion (line 146-150) calls heatAlertRepository.countByRegionIdAndFechaEventoBetween(region.getId(), now.minusDays(7), now), the unfiltered method, unchanged.

These are two distinct service classes with two distinct, unrelated purposes:
- DashboardSnapshotServiceImpl.recomputeSnapshot populates DashboardRegionSnapshot.heatAlerts7d, a labeled FIRMS-specific metric that the proposal/spec explicitly targets (dashboard snapshot filters by NASA_FIRMS source).
- DashboardServiceImpl.buildCriticalRegion computes CriticalRegionDTO.eventosCalorRecientes, a generic any heat event in the last 7 days threshold gate used to flag a region as CRITICA/EN_RIESGO. This DTO and its threshold logic were never named in the proposal, spec, design, or tasks, they are a pre-existing, unrelated feature that happens to call a same-named repository method.

A repo-wide grep confirms countByRegionIdAndFechaEventoBetween (unfiltered) has exactly one remaining caller in src/main (DashboardServiceImpl.buildCriticalRegion), and countByRegionIdAndFuenteAndFechaEventoBetween (filtered) has exactly one caller (DashboardSnapshotServiceImpl.recomputeSnapshot). No ambiguity, no accidental overlap. This is correctly scoped per the spec's literal requirement text, which names DashboardSnapshotServiceImpl.recomputeSnapshot specifically and does not mention buildCriticalRegion.

Conclusion: not a missed call site. Widening the fix to buildCriticalRegion would change behavior of an out-of-scope feature never analyzed by this change's proposal/spec/design, doing so would itself be the kind of undocumented scope creep this verification should flag, not require.

---

## 5. Full mvn test Suite, Real Execution (orchestrator question 4)

Ran mvn test from Producto/backend/simfat-backend against the real local MongoDB (localhost:27017, confirmed listening via netstat) and the project's configured Postgres/H2 test stack.

Result, independently re-executed (not trusted from apply-progress.md):

Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

Exit code 0. Per-class breakdown confirms the new Slice B test classes are present and green:
- ComunaRiskServiceImplTest: 5 tests
- TerritoryRiskServiceImplTest: 6 tests
- DashboardSnapshotServiceImplTest: 2 tests (1 pre-existing updated, 1 new)
- NasaFirmsServiceImplTest: 3 tests (Slice A, unchanged)
- ComunaGeoAttributionRepositoryIntegrationTest: 4 tests (Slice A, unchanged)
- BackfillComunaIdRunnerIntegrationTest: 2 tests (Slice A, unchanged)

apply-progress.md's claim of 90/90 passing is confirmed accurate, matching exactly, including the zero-failures/zero-errors/zero-skipped breakdown.

---

## 6. Frontend Label Coherence Spot-Check (orchestrator question 5)

**Question**: Do the frontend window/scope labels added in Slice B actually match what the backend returns, or does a label claim 48h when the query window is something else?

**Finding: Coherent. Verified by tracing each label to its backing query.**

| Surface | Label text | Backing backend window | Match |
|---|---|---|---|
| reportPrint.js comunal report, FIRMS component | últimas 48h, por comuna | ComunaRiskServiceImpl firms48h = now.minusHours(48); TerritoryRiskServiceImpl firms48hAgo = now.minusHours(48) | YES, exact match, both services use 48h |
| reportPrint.js regional report, FIRMS row | Ultimos 7 dias, vista regional bruta sin atribucion por comuna | TerritoryController.firmsLayer default fromDate = LocalDate.now().minusDays(7) | YES, the regional report's firms.total/today/highFrp is built from DashboardPage.tsx's firmsFeatures, which derives from layers.FIRMS.features (the same 7-day bbox layer fetched from TerritoryController.firmsLayer), traced via buildReportData into generateRegionalReport(data) |
| DashboardPage.tsx FirmsPanel caption | Vista regional bruta sin atribucion por comuna, ventana visible en el mapa | Same layers.FIRMS 7-day bbox source as above | YES |
| TerritoryMapPanel.jsx COMPONENT_INFO.firms tooltip | ultimas 48h, por comuna | This is the comuna risk-score component breakdown (score.components.firms), backed by ComunaRiskServiceImpl's 48h query | YES |

No label claims a window the underlying query does not actually use. The two distinct FIRMS surfaces (comuna-scoped 48h vs regional raw-bbox 7-day) are each labeled with their correct, traced window and scope, satisfying spec.md's labeling requirement scenarios.

---

## 7. Spec Acceptance Criteria, End-to-End Satisfiability (orchestrator question 6)

Re-checking spec.md's full requirement set against the combined Slice A plus Slice B implementation (not per-slice in isolation):

| Spec Requirement | End-to-End Status |
|---|---|
| ComunaInfo geometry storage (Slice A) | SATISFIED, unchanged from verify-report-slice-a.md, still holds |
| Sync-time point-in-polygon attribution (Slice A) | SATISFIED, unchanged |
| Backfill of existing rows (Slice A) | SATISFIED, unchanged. W1 carries forward, gate passed trivially against empty local data, mechanism is test-verified but not yet proven at realistic production scale |
| Standardized escalation constants (Slice B) | SATISFIED end-to-end, confirmed identical constants in both services (section 3), confirmed by passing regression tests proving the new thresholds actually drive escalation |
| Comuna-level queries use persisted comunaId (Slice B) | SATISFIED end-to-end, confirmed in section 2, with a negative-path test |
| Region-level queries derive from comunaId, no double-count (Slice B) | SATISFIED end-to-end, confirmed in section 2, the dedup-key change (Slice A, drop regionId from dedup) plus region-from-comunaId derivation (Slice B) jointly satisfy single physical detection counted once across regions, this is the one requirement that genuinely requires both slices together to hold, and it does |
| Dashboard snapshot filters by NASA_FIRMS source (Slice B) | SATISFIED for its named call site, confirmed NOT to silently break the separate buildCriticalRegion feature (section 4) |
| FIRMS surfaces labeled with window and geo-scope (Slice B) | SATISFIED, confirmed coherent against actual backend windows (section 5) |
| REMOVED, Nearest-centroid comuna assignment | CONFIRMED REMOVED, zero references in src/main |
| REMOVED, Nearest-region-centroid reassignment | CONFIRMED REMOVED, zero references in src/main |
| REMOVED, TerritoryRiskServiceImpl's divergent escalation constants | CONFIRMED REMOVED, zero references to old FIRMS thresholds anywhere in src/main |

All spec.md acceptance criteria are now fully satisfiable end-to-end, not merely per-slice. The cross-slice requirement (single physical detection counted once across regions) was the highest-risk integration point and is confirmed correctly wired, Slice A's dedup key change prevents the same physical pixel from being persisted twice across overlapping region cron legs, and Slice B's region-from-comunaId derivation prevents a persisted detection from being double-counted across two regions' totals at read time. Both halves are necessary and both are present.

---

## Detailed Findings

### CRITICAL
None. No blocking defects found across either slice or at their integration seam.

### WARNING

W1 (carried forward from verify-report-slice-a.md, unresolved). Backfill deploy gate passed trivially, not against realistic data.
Still applies unchanged at the full-change level. The local Mongo has zero rows with fuente equal to NASA_FIRMS (existing rows use the legacy NASA FIRMS with a space). The backfill mechanism is verified correct against synthetic non-empty Phase 4 test data, but Slice B's comuna/region risk queries are only as correct in production as the comunaId data they read, and that data's correctness at realistic volume (hundreds/thousands of rows, real 86-comuna GADM polygons) remains unverified in any environment with real FIRMS history. This is now a higher-stakes warning than it was in the Slice A-only report, because Slice B's readers are now live consumers of that potentially-unverified-at-scale data.

W2 (carried forward, unresolved). Boundary-point tie-break is not provably deterministic by a documented ordering, only empirically stable.
Unchanged from Slice A. findByGeometryIntersects has no explicit sort clause, the ascending comuna id determinism spec.md specifies is not literally enforced in code, only empirically true today because GADM polygons are non-overlapping by construction. Low risk in practice, still a real gap between spec wording and implementation.

W3 (NEW). The NASA FIRMS vs NASA_FIRMS fuente data inconsistency is now actively load-bearing for Slice B's dashboard fix, not just a Slice A side-observation.
apply-progress.md documents 3 legacy rows in the local Mongo with fuente equal to NASA FIRMS (space) instead of NASA_FIRMS (underscore). Slice A flagged this as an out-of-scope pre-existing bug (S3 in verify-report-slice-a.md). At the full-change level, this is more consequential than a suggestion, DashboardSnapshotServiceImpl's newly-added fuente=NASA_FIRMS filter (the literal point of Phase 7) will silently exclude any row that still has the legacy spaced value, in any environment where that data inconsistency exists. Before this ships against a production Mongo with historical FIRMS rows ingested under the old constant/naming, the fuente values must be confirmed/normalized, or heatAlerts7d will undercount in a way indistinguishable from filter is working correctly. Promoted from SUGGESTION (Slice A) to WARNING here because Slice B's correctness now directly depends on it.

### SUGGESTION

S1 (carried forward). Consider an explicit sort (e.g. by id) on findByGeometryIntersects's query to make the spec's ascending-comuna-id determinism claim literally true in code.

S2 (carried forward). Consider an automated regression test asserting BackfillComunaIdRunner always runs after MonitoredComunasConfig's seed listener, rather than relying on a one-time manual boot-log read.

S3 (NEW). Constant duplication across ComunaRiskServiceImpl and TerritoryRiskServiceImpl (four numerically-identical constants, declared twice) is a deliberate, documented tradeoff per design.md option (a). Worth a follow-up ticket to extract a shared FirmsThresholds class once both services are stable in production, to remove the duplicate-edit risk for any future threshold change.

S4 (NEW). The Slice B test suite's today date-handling gotcha (documented in apply-progress.md, isToday treats fechaEvento as a UTC instant, and naive LocalDateTime.now() in tests landed on the wrong calendar day after UTC to Santiago conversion) is exactly the kind of subtle, easy-to-reintroduce bug that benefits from a one-line comment at the isToday declaration itself, not just in test code, warning future maintainers about the UTC assumption.

---

## Independent Confirmation of Reported Risks (per orchestrator's six specific questions)

1. Cross-slice integration, CONFIRMED fully wired end-to-end, zero unaudited fallback to centroid logic anywhere in src/main. See section 2.
2. Constant standardization completeness, CONFIRMED complete via exhaustive grep beyond the two named services, no third location exists. See section 3.
3. Dashboard fix vs buildCriticalRegion, CONFIRMED genuinely intentional, not a missed call site, the two call sites serve different, unrelated features. See section 4.
4. Full mvn test real execution, CONFIRMED 90/90, BUILD SUCCESS, independently re-run, matches apply-progress.md exactly. See section 5.
5. Frontend label coherence, CONFIRMED all labels trace correctly to their backing query windows, no 48h label found backed by a different actual window. See section 6.
6. Spec acceptance criteria end-to-end, CONFIRMED all satisfiable, including the one requirement that genuinely needs both slices together (single-counting across regions). See section 7.

## Final Verdict

PASS WITH WARNINGS. Both slices are correctly implemented, correctly integrated at their seam, and covered by 90/90 real, independently-reproduced passing tests against a live MongoDB. No CRITICAL defects in either slice or at their integration point. Three WARNINGs apply: two carried forward from Slice A (W1 backfill-at-scale unverified, W2 boundary tie-break determinism not code-enforced), and one promoted from a Slice A suggestion to a Slice B-relevant warning (W3, the fuente string mismatch is now load-bearing for the dashboard fix's correctness in any environment with that legacy data). None of these block archiving the change as designed and tested; all three should be tracked as explicit follow-up items before this is trusted against real production FIRMS history at scale.
