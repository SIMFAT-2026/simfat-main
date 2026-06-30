# Exploration: FIRMS comuna geo-attribution and standardized detection counts

## Current State

Confirmed the original 3-surface diagnosis, but discovered the blast radius is wider: there are **five** surfaces computing FIRMS-derived numbers from `heat_alert_events`, each with its own time window, geo-scope, and in two cases its own risk-score weighting constants.

1. **`ComunaRiskServiceImpl.recomputeByComuna`** (lines 155-166) — 48h window, nearest-centroid comuna attribution via `assignFocosToComuna`/`findNearestComuna` (lines 344-369). Feeds `ComunaRiskSnapshot.firmsCount`/`firmsFrpMean`, drives WLC score + CRITICO escalation + the comuna tooltip.
2. **`TerritoryRiskServiceImpl`** (region-level, previously undiscovered) — own 48h window, region-level nearest-centroid reassignment via `findNearestRegionId` (lines 144-153, 270-286) using region `aoiBbox` centroids — an undocumented parallel workaround for the same cross-region double-count problem. Has **different constants** from `ComunaRiskServiceImpl`: `FIRMS_MAX_COUNT=10`, `FIRMS_COUNT_CRITICO=8`, `FIRMS_FRP_CRITICO=75` vs `5`/`4`/`60`. Feeds `TerritoryRiskSnapshot`, `GET /risk-score/{regionId}`.
3. **`TerritoryController.firmsLayer`** via `findTopFirmsEvents` — 7-day default window, capped 300, sorted by `fechaEvento DESC` (already fixed), zero comuna attribution, scoped to region bbox only. Feeds the Leaflet map and the dashboard FIRMS widget.
4. **`DashboardPage.tsx`** — reuses (3)'s feature collection, re-derives `total`/`today`/`highFrp` client-side, plus a third independent client-side `filterByBbox` layer.
5. **`DashboardSnapshotServiceImpl.recomputeSnapshot`** (previously undiscovered) — `heatAlerts7d` via `countByRegionIdAndFechaEventoBetween` (line 54), 7-day window, but **does not filter by `fuente=NASA_FIRMS` at all** — counts every alert source. Feeds `DashboardRegionSnapshot.heatAlerts7d`. This is a separate bug, not just a different window.

**Confirmed root cause of cross-region double-counting**: `NasaFirmsServiceImpl.parseCsvResponse` dedups via `existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente` — scoped by `regionId`, so the same physical detection legitimately gets one document per overlapping region's cron leg. `TerritoryRiskServiceImpl` already patches this at read-time with its own region-centroid reassignment — a second, divergent implementation of the same fix this change is meant to solve generally.

`HeatAlertEvent` has no `comunaId` field today — all comuna attribution everywhere is computed on the fly via nearest-centroid distance, never persisted.

**Already visible on a real printed artifact**: `reportPrint.js` renders BOTH the comuna-score `firmsCount` (48h/centroid, lines 269-271) AND the dashboard `firms.total/today/highFrp` (7-day/bbox, lines 189-191) on the same exported PDF — this is the "fotografía no confiable" the user flagged, already shipping.

## GeoJSON structure (verified)

- 3 files: `comunas-araucania.geojson` (31), `comunas-biobio.geojson` (33), `comunas-nuble.geojson` (21) — 85 comunas total, minified `FeatureCollection`s under `src/main/resources/static/geojson/`.
- All features are `"type":"MultiPolygon"` — target field must be `GeoJsonMultiPolygon`, not `GeoJsonPolygon`.
- `properties.comunaId` (GADM GID, e.g. `"CHL.13.1.1_1"`) already matches `ComunaInfo.gadmGid`/`ComunaInfo.id` 1:1 — no new mapping needed.

## Critical discovery: the migration mechanism already exists

`MonitoredComunasConfig.java` (`@EventListener(ApplicationReadyEvent.class)`) **already reads these exact 3 GeoJSON files on every app startup** and upserts `ComunaInfo` by `comunaId` idempotently — but only extracts `properties`, discarding `geometry` entirely. The natural extension point: add a `geometry` field to `ComunaInfo` and parse `feature.geometry` into a `GeoJsonMultiPolygon` in the same loop. No new seed script, no new endpoint, no new job needed.

## Geospatial query feasibility (verified)

- Spring Data MongoDB 4.3.x (via Spring Boot 3.3.4) — `GeoJsonMultiPolygon` + `$geoIntersects` has been stable since Spring Data Mongo 2.x.
- Embedded test Mongo (`flapdoodle` 4.13.1, no version pin) uses a modern default `mongod` binary — full `2dsphere`/`$geoIntersects` support, no test-infra risk.
- `ComunaInfoRepository` has no geospatial query methods yet — clean slate.

## Affected Areas (expanded blast radius)

- `model/ComunaInfo.java` — add `geometry: GeoJsonMultiPolygon` + `2dsphere` index.
- `model/HeatAlertEvent.java` — add `comunaId` field (currently absent).
- `config/MonitoredComunasConfig.java` — extend seed loop to persist `feature.geometry`.
- `service/impl/NasaFirmsServiceImpl.java` — `parseCsvResponse` is where point-in-polygon lookup populates `comunaId` at insert time; dedup key (`existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente`) needs rethinking once true comuna/region is known upfront.
- `service/impl/ComunaRiskServiceImpl.java` — `assignFocosToComuna`/`findNearestComuna` become obsolete once `comunaId` is persisted.
- `service/impl/TerritoryRiskServiceImpl.java` — sibling duplicate logic, own constants, own region-centroid dedup workaround — must be addressed, not just `ComunaRiskServiceImpl`.
- `repository/HeatAlertEventRepository.java` — `findTopFirmsEvents` needs either a comuna-aware variant or explicit "raw map view" labeling.
- `service/impl/DashboardSnapshotServiceImpl.java` — missing `fuente=NASA_FIRMS` filter on `heatAlerts7d` — separate bug, fold into scope.
- `controller/TerritoryController.java` — `getLayers`/`firmsLayer`/`getComunasGeoJson`.
- `frontend/.../DashboardPage.tsx` — client-side `today`/`highFrp` recompute + `filterByBbox` duplicate backend filtering; `buildReportData` is where PDF export numbers originate.
- `frontend/.../reportPrint.js` — the file where both inconsistent numbers already collide on one printed page.
- `frontend/.../TerritoryMapPanel.jsx` — confirm consistency once Java-side window changes.

## Edge Cases

- **Ocean/outside-all-polygons detections**: VIIRS can flag offshore anomalies inside a region's bbox but outside every comuna polygon. `$geoIntersects` returns no match; `comunaId` must be nullable. Today's nearest-centroid fallback always finds *some* comuna for *any* point — removing that fallback is a behavior change needing an explicit decision (drop vs. "not attributed" bucket).
- **Adjacent polygon boundary points**: GADM polygons are non-overlapping by construction, but floating-point/topology precision at `$geoIntersects` boundaries could match more than one neighbor. Need a deterministic tie-break (e.g. first match).
- **Volume**: `$geoIntersects` runs once per event at sync/insert time (indexed, O(log n)) — doesn't scale with table size. The actual scaling risk is the *existing* read pattern: `ComunaRiskServiceImpl`/`TerritoryRiskServiceImpl` both do `findByRegionId(...)` (loads ALL region events into memory) then filter/group in Java. Once `comunaId` is persisted, these could become indexed `findByComunaIdAndFechaEventoAfter(...)` queries instead.

## Approaches Considered

1. **Persist `comunaId` at sync time via `$geoIntersects`, extending the existing `MonitoredComunasConfig` seed loop.** Reuses existing idempotent mechanism; solves cross-region double-count at the root; removes two duplicated nearest-centroid implementations. Needs a backfill for existing rows and an explicit "no match" (ocean) decision. **Medium effort.**
2. **Keep centroid-distance but use real region polygons instead of bbox** (no new field). No schema change, faster — but doesn't solve the underlying problem (still recomputed on every read, doesn't fix the `DashboardSnapshotServiceImpl` bug, doesn't give a persisted source of truth for reporting). **Not recommended** given the user's explicit reporting-trust goal.
3. **Full standardization**: Approach 1 + collapse the two risk services' duplicated logic into one shared implementation/window + fix `DashboardSnapshotServiceImpl`'s missing filter + label every surface with its window/scope. Directly satisfies the "fotografía confiable" requirement. Touches alert-escalation constants — needs regression coverage via the existing JUnit suite before changing thresholds. **High effort, but this is what was actually asked for.**

## Recommendation

Approach 1 as the immediate technical fix, with `TerritoryRiskServiceImpl`'s duplication and `DashboardSnapshotServiceImpl`'s missing filter explicitly in scope (narrowing to only `ComunaRiskServiceImpl` would leave the same inconsistency alive in a sibling service). Suggested sequencing:
- **Slice A**: schema (`ComunaInfo.geometry`, `HeatAlertEvent.comunaId`) + seed extension + sync-time attribution + backfill of existing rows.
- **Slice B**: window/constant standardization across both risk services and the dashboard snapshot, once both services have a real shared `comunaId` to query against.

## Risks

- Changing FIRMS scoring constants affects live alert escalation (`NotificationService.triggerComunaRiskAlert`) — must be regression-tested, not assumed safe.
- No polygon validation step exists yet — if GADM source data has invalid/self-intersecting MultiPolygons, `2dsphere` index creation could fail at startup; validate at seed time.
- Backfill ordering risk: `comunaId` must be backfilled on existing rows before any service starts filtering by it, or recent data silently disappears from comuna-scoped views during rollout.
- `DashboardSnapshotServiceImpl`'s missing `fuente` filter was not part of the original diagnosis — must not be dropped from scope.

## Status

Ready for proposal. Blast radius is wider than originally diagnosed (5 surfaces, not 3; a duplicate region-level risk service exists with divergent constants) — flagged to the user before locking proposal scope.
