# Verify Report: firms-comuna-geo-attribution - Slice A

**Scope**: tasks.md Phases 1-4 only (schema, seed extension, sync-time attribution, backfill mechanism, and their tests). Slice B (Phases 5-10) is explicitly out of scope for this verification, per apply-progress.md.

**Verdict: PASS WITH WARNINGS**

## Completeness - Task Checklist (Phases 1-4)

| Task | Status | Evidence |
|------|--------|----------|
| 1.1 ComunaInfo.geometry + GeoSpatialIndexed(GEO_2DSPHERE) | DONE | ComunaInfo.java:36-37 |
| 1.2 HeatAlertEvent.comunaId (nullable) + compound index | DONE | HeatAlertEvent.java:18,35 (idx_heat_comuna_fecha_desc) |
| 1.3 findByGeometryIntersects/findOneByGeometryIntersects | DONE (deviated impl, semantics preserved) | ComunaInfoRepository.java:30-42 |
| 1.4 New dedup key + backfill stream method | DONE | HeatAlertEventRepository.java:47-53 |
| 1.5 MonitoredComunasConfig geometry parse/validate/skip | DONE | MonitoredComunasConfig.java:130-219 |
| 2.1 Dedup switched to region-independent key | DONE | NasaFirmsServiceImpl.java:176-179 |
| 2.2 comunaId attribution wired before save | DONE | NasaFirmsServiceImpl.java:183-193 |
| 3.1 BackfillComunaIdRunner | DONE | BackfillComunaIdRunner.java |
| 3.2 Ordering verification | DONE (re-verified independently below) | Order(LOWEST_PRECEDENCE) vs default-order seed listener |
| 3.3 Deploy gate check | DONE, trivial pass - see WARNING W1 | apply-progress.md Backfill gate status |
| 4.1-4.8 Tests | DONE, all passing, re-run independently | see Test Evidence below |

All 17 checked tasks in Phases 1-4 are genuinely implemented in source, not just checked off cosmetically.

## Test Evidence (re-executed independently, not trusted from apply-progress.md)

Ran against the real local MongoDB (localhost:27017), same environment apply-progress.md describes.

mvn -Dtest=ComunaGeoAttributionRepositoryIntegrationTest,NasaFirmsServiceImplTest,BackfillComunaIdRunnerIntegrationTest test
- ComunaGeoAttributionRepositoryIntegrationTest: Tests run: 4, Failures: 0, Errors: 0
- NasaFirmsServiceImplTest: Tests run: 3, Failures: 0, Errors: 0
- BackfillComunaIdRunnerIntegrationTest: Tests run: 2, Failures: 0, Errors: 0

Full suite (mvn test): Tests run: 78, Failures: 0, Errors: 0, Skipped: 0.

This matches apply-progress.md's reported "78, Failures: 0, Errors: 0" exactly. Claim independently confirmed, not just trusted.

## Spec Compliance Matrix (comuna-geo-attribution domain)

| Spec Scenario | Status | Evidence |
|---|---|---|
| Valid MultiPolygon seeded successfully | PASS | MonitoredComunasConfig.seedFromGeoJson + manual GeoJSON inspection: 86/86 features parse with closed, non-self-intersecting outer rings |
| Invalid geometry is skipped, not fatal | PASS (untriggered in this dataset, mechanism unit-tested) | validateRings/try-catch in seedFromGeoJson; malformedGeometryComuna_isSkipped test |
| Idempotent re-seed on restart | PASS (by construction - findById().orElseGet() + save, no separate creation path) | MonitoredComunasConfig.java:116-120; no dedicated test |
| Detection inside exactly one comuna polygon | PASS | NasaFirmsServiceImplTest.parseCsvResponse_pointInsideSeededComuna...; geo repo test pointInsideComunaA |
| Detection outside every comuna polygon (offshore/no-match) | PASS | NasaFirmsServiceImplTest.parseCsvResponse_offshorePoint_savedEventHasNullComunaId_notDropped - explicitly asserts comunaId == null AND result.size() == 1 (not dropped) |
| Boundary point matches more than one polygon | PASS with caveat | boundaryPoint test repeats the call twice and asserts the same result; spec's exact wording about ascending comuna id is NOT enforced by an explicit sort in the query - see WARNING W2 |
| Backfill attributes existing rows correctly | PASS | BackfillComunaIdRunnerIntegrationTest (2 tests, inside-to-id, offshore-to-null) |
| Backfill is idempotent and re-runnable | PASS | Same test class - re-run produces attributed=0 on already-attributed rows |
| Backfill ordering gate | PASS mechanism, WEAK gate verification - see WARNING W1 | Order(LOWEST_PRECEDENCE) + manual boot-log claim in apply-progress.md |

## Detailed Findings

### CRITICAL
None. No blocking defects found in Slice A's implementation, schema, or sync-time logic.

### WARNING

W1 - Backfill deploy gate passed trivially, not against realistic data (confirms the orchestrator's concern in question 4).
apply-progress.md is honest about this, but it is a real verification gap, not just a documentation note. The local simfat Mongo has 0 rows with fuente equal to NASA_FIRMS (the 3 existing rows use the legacy "NASA FIRMS" with a space, a pre-existing data bug unrelated to this change but discovered by it). This means the deploy gate query is true only because the precondition set is empty, not because real backfill logic ran against non-trivial data in this environment. The mechanism (point-in-polygon resolution, idempotency, null-handling) is validated by the Phase 4 integration tests against synthetic 2-3 square-comuna fixtures - that part is solid runtime evidence. What is NOT yet verified: behavior of BackfillComunaIdRunner at realistic scale (hundreds/thousands of HeatAlertEvent rows) and against the real GADM polygons (86 real, possibly more complex/larger-vertex-count multipolygons) rather than 4-vertex test squares. A custom self-intersection check performed in this verification used a simplified segment-intersection check capped at 600 vertices per ring for performance - comunas with denser coastlines were not exhaustively checked. Before trusting this in staging/production: re-run the gate query against a Mongo with real FIRMS history, AND fix the fuente string mismatch (or confirm it has already been remediated) so the gate is not measuring an empty set.

W2 - Boundary-point tie-break is not provably deterministic by a documented ordering, only empirically stable.
Spec requires the first match by a fixed, documented ordering, e.g. ascending comuna id. The implementation's findOneByGeometryIntersects takes the first element from findByGeometryIntersects, which has no explicit sort clause in the Query annotation. MongoDB does not guarantee natural-order stability across query plans or index changes for unsorted queries. The test only proves repeat calls within the same process and data state return the same result - it does not prove the result is "ascending comuna id" specifically, and does not prove stability across a server restart, index rebuild, or document reinsertion. This is a real, if narrow, gap between spec wording and implementation - low risk in practice since GADM polygons are non-overlapping by construction, but the determinism guarantee as literally specified is not enforced in code.

W3 - The Order(LOWEST_PRECEDENCE) ordering claim rests on a single manual boot-log read, not an automated test.
Task 3.2 says "Verified via real boot logs" - this is a legitimate but manual, one-time check. There is no automated test asserting BackfillComunaIdRunner always runs after MonitoredComunasConfig's seed listener. Order on EventListener-annotated methods is honored by Spring's event multicaster for synchronous listeners (which both of these are, since neither declares Async), so the mechanism itself is sound - but a future regression in listener ordering would not be caught by any test in this codebase today.

### SUGGESTION

S1 - Consider adding an explicit sort (e.g. by id) to findByGeometryIntersects's Query to make the ascending-comuna-id determinism claim in spec.md literally true in code, not just empirically true today because GADM polygons rarely overlap.

S2 - Consider a lightweight ordering-assertion test to convert the Phase 3.2 manual boot-log verification into an automated regression guard, since the deploy-gate sequencing (seed before backfill) is load-bearing for Slice B's correctness.

S3 - The pre-existing fuente data inconsistency ("NASA FIRMS" with a space vs "NASA_FIRMS" with an underscore) flagged in apply-progress.md is out of this change's scope but should be tracked as a follow-up ticket before Slice B's dashboard/risk-scoring filters go live against real data, since it silently excludes those rows from every FIRMS-filtered surface in the app, not just this change's gate.

## Independent Confirmation of Reported Risks (per orchestrator's specific questions)

1. Offshore/no-match behavior matches spec - Confirmed via source read (NasaFirmsServiceImpl.java:183-193) and Mockito test assertion (assertNull plus result size equal to 1, i.e. not dropped). No centroid fallback exists in the new code path. PASS.

2. New dedup key is wired into parseCsvResponse, replacing the old one for new inserts - Confirmed. parseCsvResponse calls only existsByLatitudAndLongitudAndFechaEventoAndFuente (line 176-179); the old per-region dedup method still exists in the repository interface but has zero callers in src/main (grep-confirmed). It is genuinely dead code awaiting Phase 10 cleanup, not silently still active. PASS.

3. Query-based findByGeometryIntersects substitution preserves point-in-polygon semantics - Confirmed. The raw MongoDB geoIntersects query against a point is the standard idiom for point-in-polygon containment against a 2dsphere-indexed MultiPolygon/Polygon field - functionally identical to what a derived GeometryIntersects keyword would have generated, had it existed in this Spring Data Commons version. Verified both by code inspection and by the passing integration tests exercising real geoIntersects against seeded square polygons (inside, outside, and boundary cases all behave correctly). Not a silent behavior gap - design.md's intended semantics are preserved.

4. Backfill gate "passes trivially" - is Slice A's backfill logic unverified against realistic data? - Partially. The mechanism is verified by integration tests against synthetic data with non-empty preconditions (Phase 4.8). What is unverified is behavior at realistic scale and against the real GADM polygon set with real historical FIRMS rows. See WARNING W1 for what a meaningful verification would require: a real or staging Mongo with actual FIRMS history, re-running the gate query there, and fixing the fuente string mismatch first.

5. 2dsphere index and geometry parsing handle all 86 comunas without silently dropping any - Confirmed for the current dataset. Independently parsed all 3 source GeoJSON files: 86 of 86 features have closed rings, all rings have 4 or more vertices, and a custom self-intersection check (capped at 600 vertices per ring for performance) found zero self-intersecting rings. This matches apply-progress.md's "86/86 seeded, 0 invalid-geometry skips" claim. The design.md-flagged risk of self-intersecting or malformed MultiPolygons causing silent comuna drops did not materialize in this dataset, and the skip-on-failure mechanism (validateRings plus try/catch) is independently tested by the malformed-geometry test. Residual gap: rings with more than 600 vertices were not exhaustively checked for self-intersection by this verification pass, though MongoDB's own 2dsphere indexer would still reject any genuinely self-intersecting polygon at write time without crashing, per ComunaInfo.java's stated design.

## Design Coherence

The one documented design deviation (derived query method replaced by an explicit Query annotation) is a mechanical substitution with preserved semantics, not a design regression. Correctly classified as WARNING-level per the Decision Gates rule that a design deviation is a WARNING unless it breaks a spec - it does not break any spec requirement.

## Final Verdict

PASS WITH WARNINGS. Slice A's schema, sync-time attribution, and backfill mechanism are correctly implemented and covered by real, independently-reproduced passing tests (78 of 78) against a live MongoDB. No CRITICAL defects. Three WARNINGs concern verification depth (gate ran against empty/trivial data, boundary-tie-break determinism not enforced in code, ordering claim resting on a manual log read) rather than implementation correctness. Safe to proceed to Slice B implementation, but the WARNINGs - especially W1 - should be resolved, or at minimum explicitly accepted as residual risk, before Slice B's readers are trusted against real production FIRMS data.
