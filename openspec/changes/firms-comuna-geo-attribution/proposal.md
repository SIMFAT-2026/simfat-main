# Proposal: FIRMS comuna geo-attribution and standardized detection counts

## Intent

Today every FIRMS-derived number (comuna tooltip, dashboard widget, risk score, exported PDF) is computed independently — different time windows, different geo-scopes, two divergent sets of escalation constants, and the same physical detection counted once per overlapping region. A "fotografía" of the system is therefore not trustworthy: `reportPrint.js` already prints two contradictory FIRMS counts on one page.

Goal (user, verbatim): *"alineemos la lectura de manera que al reportar... entreguemos datos confiables."* Concretely, after this ships: (1) `comunaId` is a persisted, geometrically-correct field on each detection — no on-read nearest-centroid guessing; (2) the same physical detection is never double-counted across regions; (3) every FIRMS number on screen or PDF has a knowable, documented window and scope, and surfaces that measure different things are labeled as such.

## Scope

### In Scope

**Slice A — persist geo-attribution (source of truth):**
- Add `ComunaInfo.geometry: GeoJsonMultiPolygon` + `2dsphere` index; validate geometry at seed time.
- Extend the existing `MonitoredComunasConfig` startup seed loop to parse `feature.geometry` from the 3 static GeoJSON files (no new job/endpoint).
- Add nullable `HeatAlertEvent.comunaId`; populate it in `NasaFirmsServiceImpl.parseCsvResponse` via `$geoIntersects` point-in-polygon at sync time.
- Backfill `comunaId` on existing `heat_alert_events` rows **before** any reader filters by it.

**Slice B — standardize counts (consume source of truth):**
- `ComunaRiskServiceImpl`: query by persisted `comunaId`; remove `assignFocosToComuna`/`findNearestComuna`.
- `TerritoryRiskServiceImpl`: derive region from `comunaId`'s real region; remove `findNearestRegionId`; reconcile constants (see Decision).
- `DashboardSnapshotServiceImpl.recomputeSnapshot`: add the missing `fuente=NASA_FIRMS` filter on `heatAlerts7d`.
- Add window/scope labels to surfaces that legitimately differ (comuna 48h vs. dashboard 7-day raw).

### Out of Scope
- Redesigning the 7-day map/dashboard "raw view" into a comuna-attributed view — it stays a region-bbox raw view, only clearly labeled.
- Frontend visual redesign beyond the window/scope legend labels.

## Capabilities

### New Capabilities
- `comuna-geo-attribution`: persisted comuna geometry + point-in-polygon attribution of FIRMS detections at sync time, including the no-match (offshore) rule and backfill.

### Modified Capabilities
- `firms-risk-scoring`: comuna- and region-level risk counts standardized on persisted `comunaId`; escalation constants reconciled; dashboard `fuente` filter corrected.

## Approach

Approach 3 (full standardization) from exploration. Reuse the already-idempotent `MonitoredComunasConfig` startup loop as the migration mechanism. Attribute at the root (insert time), not on every read, so `comunaId` becomes the single source of truth all five surfaces consume.

**Decision — no-match (ocean/offshore):** `comunaId` stays `null`. No synthetic nearest-comuna fallback. Reasoning: a detection outside every polygon is genuinely "not attributed"; inventing a nearest comuna is exactly the silent fabrication that makes today's snapshot untrustworthy. Comuna-scoped views exclude null; a region-bbox raw view may still show it, labeled "no atribuido". Boundary ties: deterministic first-match.

**Decision — constant reconciliation:** Standardize both services on `ComunaRiskServiceImpl`'s stricter `5`/`4`/`60` (`FIRMS_MAX_COUNT`/`COUNT_CRITICO`/`FRP_CRITICO`), retiring `TerritoryRiskServiceImpl`'s `10`/`8`/`75`. Reasoning: the region values were never documented as intentional — they read as an artifact of the same double-counting workaround being removed here. Once a detection is counted once, region totals drop, so the looser region thresholds would *under*-escalate. The stricter comuna constants are the safer default for life-safety alerts. Design phase may refine exact numbers, but the position is: one set of constants, comuna values win, divergence treated as accidental until proven intentional. **This changes live CRITICO escalation — it MUST be regression-tested before merge.**

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `model/ComunaInfo.java` | Modified | Add `geometry` + 2dsphere index |
| `model/HeatAlertEvent.java` | Modified | Add nullable `comunaId` |
| `config/MonitoredComunasConfig.java` | Modified | Seed `feature.geometry` |
| `service/impl/NasaFirmsServiceImpl.java` | Modified | `$geoIntersects` at insert; revisit dedup key |
| `service/impl/ComunaRiskServiceImpl.java` | Modified | Query by `comunaId`; drop centroid methods |
| `service/impl/TerritoryRiskServiceImpl.java` | Modified | Drop region-centroid; reconcile constants |
| `service/impl/DashboardSnapshotServiceImpl.java` | Modified | Add `fuente=NASA_FIRMS` filter |
| `repository/HeatAlertEventRepository.java` | Modified | Comuna-aware queries + backfill support |
| `frontend/.../reportPrint.js`, `DashboardPage.tsx`, `TerritoryMapPanel.jsx` | Modified | Window/scope labels |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Constant change mis-escalates live CRITICO alerts | High | JUnit regression on escalation paths before merge |
| Backfill ordering: readers filter `comunaId` before backfill runs | Med | Backfill is a Slice A gate; Slice B blocks on it |
| Invalid GADM MultiPolygon fails 2dsphere index at startup | Low | Validate geometry at seed; skip+log invalid |
| Boundary point matches >1 polygon | Low | Deterministic first-match tie-break |

## Rollback Plan

- Slice A: `comunaId`/`geometry` are additive nullable fields — revert the seed/sync code; orphan fields are harmless. Backfill is idempotent and re-runnable.
- Slice B: behavioral; revert per service. Keep old centroid methods until Slice B is verified green, then delete in a follow-up. Constants change is a one-line revert per service.

## Dependencies

- Static GeoJSON files already in `src/main/resources/static/geojson/` (verified, `properties.comunaId` matches `ComunaInfo.id` 1:1).
- Spring Data Mongo 4.3.x + flapdoodle embedded Mongo — `$geoIntersects` verified supported.

## Success Criteria

- [ ] New FIRMS detection inside a comuna polygon persists the correct `comunaId`; offshore detection persists `comunaId = null`.
- [ ] Existing `heat_alert_events` rows backfilled; count attributed matches point-in-polygon, remainder explicitly null.
- [ ] The same physical detection is counted exactly once across all regions (no cross-region duplication in any total).
- [ ] Both risk services use one reconciled constant set; escalation behavior covered by passing JUnit regression tests.
- [ ] `DashboardSnapshotServiceImpl.heatAlerts7d` counts only `fuente=NASA_FIRMS`.
- [ ] Every FIRMS number on the exported PDF and dashboard carries a documented window + geo-scope label; surfaces measuring different things are labeled, not silently inconsistent.
