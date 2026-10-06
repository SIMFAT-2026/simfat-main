# Deviations and implementation notes

Append-only log for the study pre-registered in `PREREGISTRATION.md` v1.0
(commit `337c68f`, sha256 `541b9266…a4b4b`). Entries are numbered and dated
and are never edited or removed after they are committed. The final report
cites this file next to the pre-registration.

**Deviations** change something the pre-registration states.
**Implementation notes** fix details the pre-registration leaves open; they
are not deviations, but they are recorded so a reader can audit every choice.

## Deviations

### D1 — Deviations are logged here, not in PREREGISTRATION.md (2026-10-02)

- **Pre-registered:** Section 18 says a deviation "is recorded as a numbered,
  dated deviation entry in this log", meaning the pre-registration's own
  change log.
- **Done instead:** deviations are recorded in this separate append-only file.
- **Why:** editing `PREREGISTRATION.md` changes its sha256, so the hash in
  `PREREGISTRATION.lock.json` would no longer match and every study entry
  point would abort, as Section 17 requires. A separate file keeps the
  pre-registration and its lock frozen.
- **Effect on results:** none.

### D2 — ERA5 downloaded with one request per comuna (2026-10-02)

- **Pre-registered:** Section 8.1 says the Open-Meteo archive is read "one call
  per comuna per season window".
- **Done instead:** one request per comuna covering 2016-08-31 to 2025-04-30
  (`scripts/download_era5.py`).
- **Why:** it cuts the number of requests ninefold. Open-Meteo's free tier
  already returned HTTP 429 during the download.
- **Effect on results:** none. The FWI chain still restarts each season on
  Sep 1 of `s-1` with the pre-registered startup values
  (`season_fwi.run_chain`), and each season reads the same hourly values it
  would have received from a per-season request.

## Implementation notes

### N1 — ERA5 range ends on 2025-04-30 (2026-10-02)

The pre-registration fixes no download range. The download ends on
2025-04-30 so the pre-registered Nov 1 to Apr 30 sensitivity window is fully
covered by real data.

### N2 — FIRMS detections are dated in Chile local time (2026-10-02)

FIRMS `acq_date` and `acq_time` are UTC; the season windows are local calendar
dates. Each detection is converted to an America/Santiago local date before
the window check. This environment has no IANA tzdata, so the exact DST
transitions for 2016-2026 were read from the Windows time-zone database
("Pacific SA Standard Time") and hard-coded in
`firms_target.CHILE_DST_INTERVALS_UTC`. A weekday rule tried first was wrong:
Chile left DST in mid-May in 2016-2018 and started it on Sep 11 in 2022. Both
reviewers checked the table independently against Windows. Rebuilding the
target with the corrected table gave a byte-identical file, because S-NPP
overpasses fall around 06:00 and 18:00 UTC.

### N3 — One weather point per comuna (2026-10-02)

Each comuna's weather comes from one point inside its polygon: the vertex
average of its largest part if that lies inside the polygon (ray casting,
holes excluded), otherwise the nearest inside point on a 101x101 grid over
that part (`era5.representative_point`). All 86 points were verified to lie
inside their polygons.

### N4 — Detections outside the 86 comunas are dropped (2026-10-02)

Detections are assigned by point-in-polygon against the repository GeoJSON.
18,083 of the 208,890 archive rows fall outside every monitored comuna and
are dropped and counted, never assigned to the nearest comuna.

### N5 — A cross-check of the Fuego union was skipped (2026-10-02)

The planned cross-check of the 2013-2017 union against
`frequency_burned_2013_2017.tif` was skipped because that file has no
provenance entry in the manifest. The union method itself matched MapBiomas'
`accumulated_burned_v1` product in S1b5 (max difference 1.1e-16).

### N6 — AUC-PR is computed as average precision (2026-10-03)

Section 9 names "AUC-PR" without fixing the estimator. It is computed as
average precision: the non-interpolated step function `sum (R_k - R_{k-1}) *
P_k` over distinct score thresholds (the scikit-learn definition), not a
trapezoid under the PR curve, which can be optimistic. Tied scores form one
threshold, so the value does not depend on row order. (`metrics.average_precision`.)

### N7 — Brier is computed on the raw, uncalibrated score (2026-10-03)

Section 9 asks for a Brier score per `wM`. The blended score is a risk index
in `[0, 1]`, not a calibrated probability, so the Brier score is the mean
squared difference between that raw score and the 0/1 target. It is a
secondary diagnostic with no decision power, and its absolute level should not
be read as probabilistic accuracy. (`metrics.brier`.)

### N8 — Bootstrap generator, seed, B and interval method (2026-10-03)

Section 10 fixes B = 2000 and seed 20261027 but not the generator or the
interval. The generator is `numpy.random.default_rng(seed)` (PCG64). Comunas
are indexed in sorted order, and each resample draws as many comunas as the
data holds (86 in the pooled analysis), with replacement; every drawn comuna
brings all its seasons. The 95% interval is the percentile interval: the 2.5th
and 97.5th percentiles of the resampled statistic (`numpy.percentile`, linear
interpolation). Every analysis (pooled, per-season, each leave-one-season-out
rerun, each sensitivity) starts its own generator from the same seed, so each
one is reproducible on its own and does not depend on the order of the others.
(`bootstrap.cluster_bootstrap`, `bootstrap.percentile_ci`.)

### N9 — Degenerate resamples are redrawn and counted (2026-10-03)

The contract does not say what to do with a resample that holds no positives
(AUC-PR is undefined) or no negatives (ROC AUC is undefined). Such a resample
is discarded and redrawn from the same generator, so exactly B usable
resamples are always returned and the rule is deterministic for a given seed.
The number of discarded draws is reported as `n_redrawn` in every table and in
`decision.json`. The redraw budget is 100 x B draws; exceeding it, or a data
set holding a single class, aborts the analysis. A single-class season in the
per-season table is not an abort: its CI columns are NaN and `ci_status` reads
`single-class`. Redrawing slightly conditions the bootstrap distribution on
having both classes; with the observed prevalence of 0.70 to 0.92 over 86
comunas this is expected to be a very rare event, and `n_redrawn` makes any
departure visible.

### N10 — The clamp never binds, so the stored season mean is used (2026-10-03)

Section 8.2 defines the blend with an outer clamp, and Section 8.1 defines the
prediction as the season mean of the daily blended score. `S_dynamic(day)` lies
in `[0, 0.52]` and `S_mapbiomas` in `[0, 1]`, so the blend is a convex
combination of values in `[0, 1]` (maximum `(1 - wM) * 0.52 + wM <= 1`) and the
clamp can never bind. The mean of the daily blend therefore equals `(1 - wM) *
mean(S_dynamic) + wM * S_mapbiomas`, and the engine uses `sdyn_mean` from
`season_fwi.csv` directly. A test proves the identity on random daily series
and shows it would fail outside those bounds, so the bounds are also asserted
at runtime (`scores.ScoreBoundsError`); the run aborts rather than silently
trusting the identity. (`scores.blend`.)

### N11 — Secondary metrics, per-season and prevalence reporting (2026-10-03)

- ROC AUC, Spearman (blended score against `log1p(count)` of the same filtered
  detections) and Brier are bootstrapped with the same resamples and reported
  with percentile CIs for the primary pooled analysis only. The
  leave-one-season-out reruns and the sensitivities report AUC-PR and the
  paired difference, which is all the decision rule uses. A NaN Spearman (a
  constant score in a resample) is excluded from that interval.
- The per-season table reports AUC-PR, prevalence, `n`, and the paired
  difference with its own comuna bootstrap CI for every weight. Each season
  holds one row per comuna, so the cluster bootstrap there is a plain
  bootstrap over its 86 comunas.
- The prevalence warning fires when prevalence is strictly below 5%, as written
  in Section 9 (exactly 5% is not flagged). It is reported per analysis and per
  season.
- The shipped decision uses the raw floating-point CI lower bound with the
  comparison `>= -0.02` and no tolerance.

### N12 — Sensitivities, leave-one-season-out and the entry-point gate (2026-10-03)

- Each pre-declared sensitivity (N = 1, N = 5, Nov-Apr window, no type filter,
  log burnedNorm) rebuilds its own target and, where relevant, predictor and
  runs the full rule. The Nov-Apr sensitivity uses the target's `sensitivity`
  window together with `season_fwi.csv`'s `sensitivity` window. All are
  reported next to the primary result and none replaces it.
- Leave-one-season-out drops one season and reruns the pooled decision nine
  times, with a fresh bootstrap each time; it never alters the primary rule.
- `scripts/run_backtest.py` refuses to run on the real inputs unless
  `--confirm-preregistered-run` is passed. A file counts as real if its path
  resolves to the committed file or its sha256 equals the committed file's, so
  a renamed copy does not bypass the gate. `--dry-run` loads the three files
  and reports only row, comuna and season counts and missing-key counts. It
  computes no metric and prints no value derived from the target-predictor
  relation.
- The engine was built and tested on synthetic data only. No metric was
  computed on the real inputs before the pre-registered run.

### N13 — Secondary metrics in every table, per Section 12 (2026-10-03)

Supersedes the restriction in N11 that limited ROC AUC, Spearman and Brier to
the primary pooled analysis. Section 12 defines Table 2b as the same grid by
AUC-PR/Brier with CIs, per season and leave-one-season-out, and Section 9
requires every metric to carry its 95% CI and `n`. The tables now follow that
text: ROC AUC, Spearman (against `log1p(count)`) and Brier are computed with
percentile CIs from the same paired resamples in the pooled primary, every
leave-one-season-out rerun, every sensitivity and the per-season table. They
have no decision power; the rule still reads the AUC-PR difference only, so the
shipped decision is unchanged by this entry. N11 stays as written.

### N14 — Redraw only resamples without positives (2026-10-03)

Supersedes the "no negatives" condition in N9. A resample without negatives
leaves AUC-PR (the decision metric) and Brier defined, so redrawing it
conditioned every interval on the presence of a negative, and the effect grows
with prevalence (synthetically about 4% of draws at 83 of 86 positives). The
corrected rule: a resample with no positives (AUC-PR undefined) is discarded and
redrawn, as in N9, and counted as `n_redrawn`. A resample with no negatives is
kept; ROC AUC is undefined for it, so that resample is excluded from the ROC AUC
interval only, the same way a NaN Spearman is, and counted as `n_auc_undefined`.
AUC-PR, Brier and the paired difference always use all B resamples. Both counters
are reported in every table and in `decision.json`, and a warning is printed to
stderr (and stored in `decision.json`) when either exceeds 1% of B. The redraw
budget (100 x B) and the abort on data without positives are unchanged. A
per-season analysis is labelled `single-class` only when the season holds no
positive or no negative; any other bootstrap failure is `bootstrap-failed` with
its message in `ci_error`, never mislabelled. N9 stays as written.

### N15 — High-prevalence flag, a reporting addition (2026-10-03)

Section 9 flags prevalence below 5%. The contract has no flag for the opposite
case, but the observed prevalence is expected between 0.70 and 0.92, where
AUC-PR sits near its ceiling and the non-inferiority margin (`delta = 0.02`) is
easier to meet than at moderate prevalence. A reporting flag now marks
prevalence strictly above 50% (`high_prevalence_warning`) next to the existing
`< 5%` flag in every table (pooled, per-season, decisions) and in
`decision.json`, together with a note stating that reading. It is a reporting
addition only: it does not enter the decision rule, the claim rule, any
interval, or the shipped weight. (`config.StudyParameters.prevalence_high`.)

### N16 — Engine scope and implementation notes (2026-10-03)

- Scope. The S4b2 engine implements the Section 10 decision rule, the Section 12
  claim rule, Table 2 and Table 2b (pooled, per-season and leave-one-season-out
  grids, AUC-PR, ROC AUC, Spearman and Brier, each with a 95% CI) and the
  Section 13 sensitivity. It does NOT implement Table 1 and the H3 class
  selectivity analysis, Table 3 (alert levels per `wM`), the three figures, the
  Section 14 threshold recalibration or H1. These are deferred to later units
  and the absence of their outputs from `outputs/` is expected, not an omission
  in the engine.
- Completeness. Every analysis dataset (primary, each season, each
  leave-one-season-out run, each sensitivity) is asserted to be a balanced panel
  of exactly 86 comunas x its expected seasons (9 pooled, 8 per
  leave-one-season-out run, 1 per season) with no duplicate keys, and the
  comuna-season set must be identical across windows and filters. A violation
  raises; in a sensitivity or leave-one-season-out run it is recorded as a failed
  analysis with the error, in the primary analysis it aborts the run.
- Fault tolerance. The primary pooled result and the per-season table are
  written to disk before any rerun starts. Each leave-one-season-out run and
  each sensitivity is guarded: an exception is recorded as `status: failed` with
  its error in the tables and `decision.json` and the remaining analyses run. The
  primary analysis is not guarded and aborts loudly. `decision.json` carries
  `complete: false` until the last analysis finishes.
- Run-once and provenance. `scripts/run_backtest.py` refuses to start when
  `outputs/decision.json` exists unless `--allow-overwrite` is passed, in which
  case `overwrote_previous` and the previous file's sha256 are recorded. It
  refuses to start unless the pre-registration commit in the lock is an ancestor
  of HEAD, as Section 17 requires, and `decision.json` records whether the git
  working tree (and the study directory alone) was dirty.
- Hardening. The `qualifies` column is read from the decision rule's outcome
  rather than recomputed in `report.py`, and `StudyParameters` rejects a
  `decision_sequence` that is not the grid in descending order.
- The engine remains built and tested on synthetic data only; no metric was
  computed on the real inputs.

### N17 — Last hardening guards before the single real run (2026-10-03)

Implementation notes; no change to the pre-registered analysis.

- Exit code 4. After every table and `decision.json` are on disk,
  `scripts/run_backtest.py` returns 4 (and prints a final `INCOMPLETE:` line
  listing them) if any analysis has `status: failed` or any per-season row has
  `ci_status` of `single-class` or `bootstrap-failed`. Codes 2 (refused) and 3
  (provenance) are unchanged; all codes are documented in the script docstring
  and the README.
- Dirty study directory. A real run (`--confirm-preregistered-run`) refuses to
  start with exit 3 if `git status --porcelain` for `Producto/analytics/study/`
  is non-empty or git cannot answer (fail closed), so the recorded git sha
  describes the code that ran. Changes outside the study directory do not block
  and are still recorded as `git_dirty`. Synthetic runs are unaffected.
- Per-season warnings. `degeneracy_warnings` now also names every season whose
  `ci_status` is `single-class` or `bootstrap-failed` (once per season), so it is
  printed to stderr and stored in `decision.json`.

### N18 — H1 scars: connectivity, clip and edge handling (2026-10-06)

The pre-registration says "connected burned pixels in an annual raster" and
fixes neither the connectivity nor the clip. Fixed here before any H1 code ran
on the real data.

- Connectivity is 8: two burned pixels sharing only a corner are one scar. It
  is labelled by `study.h1.connected_components`, a numpy-only hook-and-compress
  union-find over the sorted burned pixels, tested against an independent
  breadth-first search on random arrays.
- Scars are built per annual raster (value 1; zero is data) from the 13
  sha256-verified annual rasters of 2013-2025, never from the accumulated or
  frequency products. A pixel burned in two years belongs to a scar of each
  year.
- The raster is clipped to the union of the 86 comuna polygons with the pipeline's
  rule (pixel centre inside, holes excluded, `all_touched=False`). A scar that
  crosses a comuna border stays one scar and lists every comuna it touches. Only
  pixels inside the union are kept, so a scar that leaves the monitored area is
  measured on its inside part.
- Area is the sum of the pipeline's WGS84 per-row pixel areas
  (`mb_pipeline.zonal.row_pixel_areas_m2`). The 250 ha test is `area >= 250`
  (inclusive).
- Edge handling: `edge_flag` marks a scar with a pixel whose 8-neighbour lies
  outside the union (or outside the read window). `truncated` marks the stricter
  case where that outside neighbour is itself burned, i.e. the scar visibly
  continues beyond the clip and its area is understated. Both are columns of
  `h1_scars.csv`; no scar is dropped for either flag, and the summary reports the
  primary share also without edge-flagged scars as a descriptive line.

### N19 — H1 buffer method and detection matching (2026-10-06)

- A detection is within the buffer of a scar iff its Euclidean distance to the
  nearest burned pixel square of the scar is at most 375 m, i.e. it lies inside
  the scar polygon (union of its pixel squares) buffered by 375 m. A detection
  inside a burned pixel is at distance 0. No raster dilation is used.
- Distances are in local WGS84 metres: degree offsets are scaled by
  `N cos(phi) pi/180` (longitude) and `M pi/180` (latitude) at the detection's own
  latitude, with the same `M`/`N` as the pixel-area formula. At -38 degrees a
  pixel is about 23.6 m wide and 29.8 m tall, so the buffer spans more pixels
  along columns than along rows. The comparison is `<= 375 m + 1e-6 m` so a
  detection built at exactly 375 m is inside.
- Method: for each detection the burned pixels of the kept scars in a pixel
  window that surely covers 375 m are looked up by binary search and the
  distance computed for each hit. Tests use hand-built geometry (exactly 375 m
  and 400 m east and north at several latitudes, a diagonal case that separates
  Euclidean from square buffers, an anisotropy case, a brute-force comparison on
  random points).
- The detections are the filtered ones (type 0, confidence `n` or `h`) of the
  FIRMS archive, matched to a scar of the same LOCAL calendar year (N2). A
  detection outside the 86 comunas is NOT dropped here (N4 applies to the
  backtest target): a detection just beyond the border can lie within 375 m of a
  scar inside it. One detection can count for two neighbouring scars.
  `detected` is `n_detections_in_buffer >= 1`.
- The read window extends 40 pixels (about 1 km) beyond the union so edge scars
  see all their buffer.

### N20 — H1 area classes (2026-10-06)

The pre-registration asks for no class breakdown, but the run reports one. The
classes were fixed before the code ran and are half-open: 250-500 ha, 500-1,000
ha, 1,000-5,000 ha and >= 5,000 ha. They are descriptive and have no decision
power.

### N21 — H1 intervals and the secondary correlation (2026-10-06)

- Primary interval. The share's 95% CI is a percentile bootstrap that resamples
  YEARS with replacement (`bootstrap.cluster_bootstrap`, B = 2000, seed
  20261027, a fresh generator per analysis, as in N8). Scars of one year share
  weather, season and VIIRS overpasses, and one large fire complex is often split
  into several neighbouring scars, so scars are not independent; the year is the
  coarsest dependence unit the data hold. With 13 clusters the interval is coarse
  and a percentile bootstrap can undercover, which is why the scar-clustered
  interval (`ci_by_scar`, scars treated as independent, necessarily narrower) is
  stored next to it for contrast. Quote `ci_by_year`.
- Per-year shares carry an interval from resampling that year's scars; per-class
  shares resample years. An interval is `null` with fewer than two clusters.
- Secondary. Per comuna and year: the burned fraction is the seed's
  `burnedFractionByYear` (the seed the backtest used; its denominator is the
  comuna polygon area, Section 15) and the count is the number of filtered
  detections whose local calendar year is that year and whose point lies inside
  the comuna (the same point-in-polygon rule as the target, holes excluded, over
  the whole calendar year, not a season window). Spearman uses average ranks
  (`metrics.spearman`) over the 86 x 13 pairs; its CI resamples comunas with all
  their years (the backtest bootstrap). A resample with an undefined correlation
  is excluded and counted out of `n_valid`.
- No H1 quantity enters the backtest, the decision rule or any claim.

### N22 — H1 run guards and scope (2026-10-06)

`scripts/run_h1.py` verifies the pre-registration hash first, refuses to start
when `outputs/h1/h1_summary.json` exists (`--allow-overwrite` records the
overwrite and the previous file's sha256), when the pre-registration commit is
not an ancestor of HEAD, or when the study directory is dirty or git cannot
answer (exit 3), and aborts on any Fuego raster whose sha256 differs from
`mapbiomas/data/manifest.json`. H1 reads only Fuego rasters, the committed seed,
the FIRMS archive and the comuna polygons. It reads none of the backtest inputs
or outputs and writes only under `outputs/h1/`; the backtest, `outputs/tables/`
and `outputs/decision.json` are untouched. `h1_summary.json` records the input
hashes, the git sha, the pre-registration hash and the parameters.
