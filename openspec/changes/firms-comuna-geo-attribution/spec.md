# Spec: FIRMS comuna geo-attribution and standardized detection counts

Covers two capabilities from the proposal: `comuna-geo-attribution` (new) and `firms-risk-scoring` (modified delta). Both domains have no prior `openspec/specs/` entry; written as full specs.

---

## Domain: comuna-geo-attribution (New Capability — Slice A)

### Purpose

Persist comuna geometry and attribute each FIRMS detection to its containing comuna at sync time via point-in-polygon lookup, replacing on-read nearest-centroid guessing. `comunaId` becomes the single source of truth all FIRMS-derived surfaces consume.

### Requirements

#### Requirement: ComunaInfo geometry storage

`ComunaInfo` MUST store a `geometry` field of type `GeoJsonMultiPolygon` with a `2dsphere` index, populated from the static GeoJSON source files.

##### Scenario: Valid MultiPolygon seeded successfully

- GIVEN a GeoJSON feature with `properties.comunaId` matching an existing or new `ComunaInfo` and a well-formed `MultiPolygon` geometry
- WHEN `MonitoredComunasConfig`'s startup seed loop runs
- THEN the corresponding `ComunaInfo` document persists `geometry` as `GeoJsonMultiPolygon`
- AND the `2dsphere` index exists on `ComunaInfo.geometry` after startup

##### Scenario: Invalid geometry is skipped, not fatal

- GIVEN a GeoJSON feature with a self-intersecting or malformed `MultiPolygon`
- WHEN the seed loop processes that feature
- THEN that comuna's geometry write is skipped and logged as an error
- AND the application startup completes successfully (other valid comunas are still seeded)

##### Scenario: Idempotent re-seed on restart

- GIVEN `ComunaInfo` documents already have `geometry` persisted from a prior startup
- WHEN the application restarts and the seed loop runs again with unchanged source GeoJSON
- THEN no duplicate `ComunaInfo` documents are created
- AND existing `geometry` values are upserted to the same value (no-op in effect)

#### Requirement: Sync-time point-in-polygon attribution

`NasaFirmsServiceImpl.parseCsvResponse` MUST attribute each incoming FIRMS detection to a `comunaId` using `$geoIntersects` against `ComunaInfo.geometry` at insert time, before the document is persisted.

##### Scenario: Detection inside exactly one comuna polygon

- GIVEN a FIRMS CSV row with lat/lon coordinates that fall inside exactly one seeded comuna polygon
- WHEN `parseCsvResponse` processes that row
- THEN the persisted `HeatAlertEvent.comunaId` equals that comuna's id

##### Scenario: Detection outside every comuna polygon (offshore/no-match)

- GIVEN a FIRMS CSV row with lat/lon coordinates that fall outside all seeded comuna polygons (e.g. offshore)
- WHEN `parseCsvResponse` processes that row
- THEN `HeatAlertEvent.comunaId` persists as `null`
- AND no synthetic nearest-comuna fallback is applied
- AND the event is NOT silently dropped — it still persists with `comunaId = null`

##### Scenario: Boundary point matches more than one polygon

- GIVEN a FIRMS CSV row whose coordinates lie on or near a shared boundary between two adjacent comuna polygons, such that `$geoIntersects` returns more than one match
- WHEN `parseCsvResponse` processes that row
- THEN exactly one `comunaId` is assigned, deterministically (first match by a fixed, documented ordering — e.g. ascending comuna id)
- AND repeated runs against the same input produce the same assignment

#### Requirement: Backfill of existing rows

Existing `heat_alert_events` documents lacking `comunaId` MUST be backfilled via point-in-polygon lookup before any reader is allowed to filter by `comunaId`.

##### Scenario: Backfill attributes existing rows correctly

- GIVEN existing `HeatAlertEvent` documents with `comunaId` absent or null and valid lat/lon
- WHEN the backfill process runs
- THEN each document's `comunaId` is set to the comuna whose polygon contains its coordinates, or remains `null` if no polygon matches
- AND the count of newly-attributed documents matches the count of documents whose coordinates fall inside a seeded polygon

##### Scenario: Backfill is idempotent and re-runnable

- GIVEN a backfill has already run and populated `comunaId` on all existing rows
- WHEN the backfill process runs again
- THEN no document's `comunaId` changes
- AND no errors occur from re-running against already-attributed rows

##### Scenario: Backfill ordering gate

- GIVEN Slice B readers (`ComunaRiskServiceImpl`, `TerritoryRiskServiceImpl`) are deployed to query by `comunaId`
- WHEN the deployment sequence is followed
- THEN the backfill MUST have completed successfully before Slice B reader code is enabled
- AND no comuna-scoped query may silently return incomplete results due to unbackfilled `comunaId`

---

## Domain: firms-risk-scoring (Modified Capability — Slice B)

### Purpose

Standardize comuna- and region-level FIRMS risk counts on persisted `comunaId`, eliminating duplicated nearest-centroid logic, reconciling divergent escalation constants into one standard, and correcting the dashboard's missing source filter.

### Requirements

#### Requirement: Standardized escalation constants

Both `ComunaRiskServiceImpl` and `TerritoryRiskServiceImpl` MUST use one shared constant set for FIRMS escalation: `FIRMS_MAX_COUNT=5`, `FIRMS_COUNT_CRITICO=4`, `FIRMS_FRP_CRITICO=60`. `TerritoryRiskServiceImpl`'s prior values (`10`/`8`/`75`) MUST be removed.

##### Scenario: Comuna-level CRITICO escalation at standardized threshold

- GIVEN a comuna has 4 or more FIRMS detections in its scoring window with mean FRP >= 60
- WHEN `ComunaRiskServiceImpl.recomputeByComuna` runs
- THEN the comuna's risk level escalates to `CRITICO`

##### Scenario: Region-level CRITICO escalation uses the same threshold as comuna-level

- GIVEN a region's aggregated FIRMS detections (derived from `comunaId`-attributed events) reach `FIRMS_COUNT_CRITICO=4` with mean FRP >= `FIRMS_FRP_CRITICO=60`
- WHEN `TerritoryRiskServiceImpl` recomputes the region's risk snapshot
- THEN the region escalates to `CRITICO`
- AND this matches the same numeric thresholds used by `ComunaRiskServiceImpl` (no divergent constants remain in either service)

##### Scenario: Below-threshold counts do not escalate

- GIVEN a comuna or region has fewer than 4 FIRMS detections, or mean FRP below 60
- WHEN risk is recomputed
- THEN the risk level does NOT escalate to `CRITICO` due to FIRMS criteria alone

#### Requirement: Comuna-level queries use persisted comunaId

`ComunaRiskServiceImpl` MUST query `HeatAlertEvent` by persisted `comunaId` rather than computing nearest-centroid assignment on read. `assignFocosToComuna` and `findNearestComuna` MUST be removed.

##### Scenario: Comuna risk count reflects only its own attributed detections

- GIVEN two adjacent comunas each with FIRMS detections persisted with their correct `comunaId`
- WHEN `ComunaRiskServiceImpl.recomputeByComuna` runs for one comuna
- THEN only events with that comuna's `comunaId` are counted
- AND no event from the adjacent comuna is included

##### Scenario: Null comunaId events are excluded from comuna-scoped counts

- GIVEN FIRMS detections exist with `comunaId = null` (offshore/no-match)
- WHEN any comuna-scoped risk computation runs
- THEN those null-`comunaId` events are excluded from every comuna's count

#### Requirement: Region-level queries derive from comunaId, no double-count

`TerritoryRiskServiceImpl` MUST derive each detection's region from its `comunaId`'s real region mapping, rather than region-centroid reassignment. `findNearestRegionId` MUST be removed. The same physical detection MUST be counted in exactly one region.

##### Scenario: Single physical detection counted once across regions

- GIVEN a single physical FIRMS detection that, under the old per-region dedup key, was persisted as multiple `HeatAlertEvent` documents (one per overlapping region cron leg) — or, post-fix, is persisted once with one attributed `comunaId`
- WHEN region-level totals are computed for all overlapping regions
- THEN the detection contributes to exactly one region's total (the region owning its attributed comuna)
- AND no region double-counts it

##### Scenario: Region total equals sum of its comunas' attributed counts

- GIVEN a region has N comunas, each with a known count of attributed FIRMS detections in the scoring window
- WHEN `TerritoryRiskServiceImpl` computes the region's FIRMS count
- THEN the region total equals the sum of its comunas' counts (derived from `comunaId` → region mapping)
- AND null-`comunaId` events are excluded from every region total

#### Requirement: Dashboard snapshot filters by NASA_FIRMS source

`DashboardSnapshotServiceImpl.recomputeSnapshot`'s `heatAlerts7d` computation MUST filter by `fuente=NASA_FIRMS`, matching the filter already applied by comuna- and region-level risk services.

##### Scenario: Dashboard 7-day count excludes non-FIRMS alert sources

- GIVEN `heat_alert_events` includes both `fuente=NASA_FIRMS` and other alert source values within the 7-day window
- WHEN `DashboardSnapshotServiceImpl.recomputeSnapshot` computes `heatAlerts7d`
- THEN only documents with `fuente=NASA_FIRMS` are counted

##### Scenario: Dashboard count matches FIRMS-only total

- GIVEN a region has a known count of `fuente=NASA_FIRMS` events in the trailing 7 days, plus some non-FIRMS events in the same window
- WHEN `heatAlerts7d` is recomputed
- THEN the resulting value equals the FIRMS-only count, not the combined total

#### Requirement: FIRMS surfaces are labeled with window and geo-scope

Every FIRMS-derived number exposed to comuna tooltips, dashboard widgets, or exported PDF reports MUST carry a documented time window and geo-scope, and surfaces measuring different things MUST be visibly labeled as such rather than presented as equivalent.

##### Scenario: Comuna tooltip and dashboard widget show distinct labels

- GIVEN a comuna tooltip displays a 48h comuna-scoped FIRMS count and a dashboard widget displays a 7-day region-bbox raw FIRMS count
- WHEN both are rendered on screen or in `reportPrint.js`'s exported PDF
- THEN each number is labeled with its window (e.g. "48h" vs "7 días") and scope (e.g. "por comuna" vs "vista regional bruta")
- AND no two differently-scoped FIRMS numbers are presented without a distinguishing label

##### Scenario: PDF report no longer shows unlabeled contradictory counts

- GIVEN the exported PDF includes both the comuna-score `firmsCount` and the dashboard `firms.total/today/highFrp`
- WHEN the report is generated
- THEN both figures are visibly labeled with their respective window and scope
- AND a reader can determine why the two numbers differ without consulting source code

### REMOVED Requirements

#### Requirement: Nearest-centroid comuna assignment

(Reason: Replaced by persisted `comunaId` via sync-time point-in-polygon attribution. Nearest-centroid assignment always finds *some* comuna for *any* point, including offshore detections, which is the silent fabrication this change eliminates.)
(Migration: `ComunaRiskServiceImpl.assignFocosToComuna`/`findNearestComuna` are removed; queries switch to `findByComunaIdAndFechaEventoAfter`-style lookups against persisted `comunaId`.)

#### Requirement: Nearest-region-centroid reassignment

(Reason: `TerritoryRiskServiceImpl.findNearestRegionId` was an undocumented parallel workaround for the same cross-region double-counting problem solved generally by persisted `comunaId` → region derivation.)
(Migration: Region derivation now reads each event's `comunaId`, maps it to that comuna's region, and aggregates. `findNearestRegionId` is removed.)

#### Requirement: TerritoryRiskServiceImpl's divergent escalation constants

(Reason: `FIRMS_MAX_COUNT=10`/`FIRMS_COUNT_CRITICO=8`/`FIRMS_FRP_CRITICO=75` were an artifact of the now-removed double-counting workaround, not a deliberate calibration. Once detections are counted once, these looser thresholds would under-escalate.)
(Migration: Replaced by the standardized `5`/`4`/`60` constants shared with `ComunaRiskServiceImpl`, per the "Standardized escalation constants" requirement above.)
