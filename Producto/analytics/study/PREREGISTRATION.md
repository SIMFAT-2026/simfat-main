# Pre-Registration: MapBiomas Susceptibility Blend Validation Study

**Version:** 1.0
**Date:** 2026-10-01
**Change:** `mapbiomas-integration` (Premio MapBiomas Chile 2026, Category 2), slice S4a
**Status:** PRE-REGISTERED. Per design [D7] this file is committed, unmodified in substance, *before* any
backtest run that produces results. Git history must then show this commit as an ancestor of every
commit that adds backtest outputs [MVS-1a].

## 1. Authorship

**Author (sole):** David Vásquez.

**Acknowledgments (not co-authorship):** AIFBN, as the institutional sponsor of the submission and the
organization whose monitors use SIMFAT; the DUOC reviewing professor and the forest engineers who review
the class fuel-weight table [MVS-10, MRB-7b]; data providers MapBiomas Chile (CC BY 4.0), NASA FIRMS, and
Open-Meteo / ECMWF ERA5, cited in full in Section 3 and in the production attribution string [D6].

## 2. Purpose and hypotheses

This study evaluates whether blending the static MapBiomas-derived susceptibility score `S_mapbiomas`
into SIMFAT's dynamic risk score `S_dynamic` improves agreement with an independent fire-activity signal,
and characterizes the MapBiomas inputs on their own terms [D7, MVS-4].

- **H1 — Fuego vs. FIRMS agreement.** MapBiomas Fuego (burn scars) and NASA FIRMS (active-fire detections)
  measure different physical quantities from different sensors. H1 characterizes their agreement and
  disagreement; it justifies using FIRMS, not Fuego, as the independent backtest target, resolving the
  circularity that scoring against Fuego would partly measure `S_mapbiomas` against its own input [D7 "H2
  circularity resolved"]. Attempted only through 2026-10-12; if not completed by then it is reported as
  future work, not as a result [MVS-8]. Primary metric: share of Fuego scars `>= 250 ha` (connected burned
  pixels, annual raster) with `>= 1` filtered FIRMS detection (type 0, confidence `n`/`h`) within scar +
  375 m buffer (one VIIRS pixel) in the same calendar year. Secondary metric: Spearman correlation between
  per-comuna annual burned fraction and filtered FIRMS count (86 comunas x 13 years). Both are fixed here,
  before any H1 code runs, not chosen after inspecting the data [prereg-decisions #6].
- **H2 — the main test (non-inferiority).** Is blending `S_mapbiomas` (at some weight `wM` in the
  pre-registered grid) into the composite score **not worse** than `wM = 0` in pooled AUC-PR against the
  FIRMS-derived target, by more than the pre-declared margin `delta = 0.02`? This is the pre-registered
  decision rule of Section 10 [D7, MVS-2]. Passing it shows non-inferiority, **not** improvement; see the
  claim rule in Section 12.
- **H3 — burned area by land-cover class.** Descriptive only (no significance claims): burned hectares and
  a selectivity ratio (share of burned area vs. share of land cover) per MapBiomas class, per region,
  2013–2025, using the disjoint leaf-class set of [MCS-3] [MVS-7].

## 3. Data sources (versions and checksums)

All checksums are recorded in the committed manifests; this section restates them for the record.

| Source | Version / scope | File(s) | sha256 | Bytes |
|---|---|---|---|---|
| MapBiomas Chile LULC | Col 2, political level 2/3/4, sheet `COVERAGE`, years 1999–2024 | `statistics_lulc_chile_col2_political_level_234.xlsx` | `c4d5d6b3c8b227e2c280e06d4647730f9e72febd9e7faa390fb4f01b72e66cb9` | 48,422,307 |
| MapBiomas Chile Fuego | Col 1, 16 files (1 accumulated 2013–2025 cross-check + 13 annual 2013–2025 + 1 frequency + 1 year-last-fire) | `Producto/analytics/mapbiomas/data/manifest.json` | accumulated `1b0d5aa3...2dca5d8ce`; frequency `fbd46d45...97f8758bfe`; year-last-fire `5ac15486...37393f4eaf6d1`; 13 per-year hashes individually in the manifest | ~7.8–13.9 M/file |
| NASA FIRMS VIIRS S-NPP archive | Col 2 standard quality v2, request 809648, 2012-01-20 to 2025-12-31, 208,890 rows | `DL_FIRE_SV-C2_809648.zip` | `f9b8f6b2...fc7c6ecc862` | 3,746,098 |
| Open-Meteo Archive (ERA5) | not yet downloaded | — | to be recorded before the backtest runs [D7 rev3] | — |
| Fuel-weight table | literature-ordinal, **not empirically calibrated** | `.../mapbiomas/fuel-weights-v1.json` | n/a | — |

Note: the LULC file's `downloadedAt` was reconstructed from commit history, not captured live
[mapbiomas README]. Disclosed, not hidden.

## 4. Unit of analysis

One row per comuna-season: 86 monitored comunas (Biobío 33, Ñuble 21, Araucanía 32) × 9 test seasons,
pooled for the primary decision and reported per-season for diagnostics [MCS-1a, MVS-6c].

## 5. Seasons and split

Season `s` runs Dec 1 of `s-1` through Mar 31 of `s`: the summer peak, consistent with the Fuego `<= s-2`
leakage rule (Section 7) [D7 rev3, MVS-3, MVS-3c, prereg-decisions #2]. Eligible test seasons: `s =
2017..2025` (9 seasons). Expanding window: for test season `s`, any calibration (e.g., class-weight review,
threshold recalibration inputs) uses only seasons `< s` [D7 rev3, MVS-3, MVS-3c]. CONAF's Jul-Jun fire year
was considered and rejected because it mixes winter agricultural burning into the target
[prereg-decisions #2].

## 6. Target definition

`y(c,s) = 1` if comuna `c` has at least `N_FIRMS = 3` detections during season `s` with `type == 0`
(presumed vegetation fire) AND `confidence` in `{n, h}` (excluding `l`) [prereg-decisions #1, #3]. A
continuous secondary variant `log1p(count)` of the same filtered detections is reported with Spearman
correlation [prereg-decisions #3, MVS-5b].

Declared assumption: NASA documents the `type` column for MODIS (0 vegetation fire, 1 volcano, 2 other
static land source, 3 offshore); the VIIRS SV-C2 archive carries the same column with the same 4 values,
which is assumed, not independently confirmed, before pre-registration [prereg-decisions #1]. Rationale for
the filter: Biobío has persistent type-2 hot spots at pulp mills (Nueva Aldea, Santa Fe, Laja, Arauco) and
Araucanía has active volcanoes (Villarrica, Copahue); NASA associates low confidence with sun glint and weak
(`< 15 K`) thermal anomalies [prereg-decisions #1].

`N_FIRMS = 3` was fixed a priori, without inspecting the detection-count distribution, specifically to avoid
tuning the primary rule to the test data [prereg-decisions #3].

Pre-declared sensitivities (reported, never substituted for the primary rule): `N_FIRMS` = 1 and 5; the
season window Nov 1 of `s-1` through Apr 30 of `s`; and the target rebuilt without the `type` filter
[prereg-decisions #1, #2, #3].

**Spec amendment note:** `MVS-4b`'s point-in-time framing — a score "at `t`" against a label "in
`(t, t+H]`" — is superseded by the season-level target fixed above and the mean-aggregated `S_dynamic`
defined in Section 8.1 [prereg-decisions #4, reconciliations].

## 7. Leakage rules (MUST)

For test season `s` [D7 rev3, MVS-3a–e]:

1. Fuego-derived inputs (burned fraction, recurrence, year of last fire) use Fuego years `<= s-2` only.
   The annual raster of year `s-1` contains December of `s-1`, which falls inside season `s`.
2. Land-cover shares use year `s-1` (annual classification; no leakage).
3. The Fuego 2025 raster never enters any `S_mapbiomas` value used in the backtest.
4. Weather features are contemporaneous with season `s` (ERA5 archive, never future).
5. FIRMS is **not** used as a model input in the backtest at all: decision #4 (Section 8.1) zeroes the
   FIRMS component of `S_dynamic` in every arm, precisely so that in-season FIRMS detections cannot leak
   into the target they also help build. Production's `t-48h` cutoff for FIRMS-as-input therefore does not
   apply here — it is a production-only rule, not a backtest one [prereg-decisions #4].
6. Disclosed consequence: season 2017 has only Fuego years 2013–2015 (3 years) available under rule 1,
   so its history term is thin; the per-season table makes this visible rather than hiding it [D7 rev3].

`S_mapbiomas(c,s)` for the backtest is always recomputed for the years above — **never** taken from the
production document's full-window (2013–2025) aggregates, which remain anchored at 2025 for live scoring
[D7, mapbiomas README "Leakage warning for the validation study"]. Pixel-level quantities (the union burned
fraction behind `burnedFractionPct`, and the frequency behind `recurrenceNorm`) cannot be rebuilt from the
seed's per-comuna yearly totals, so they are recomputed from the committed-provenance **annual Fuego
rasters** restricted to years `<= s-2`, with the same zonal method, polygons and gates as the seed pipeline;
the percentile rank is recomputed over the 86 comunas for each season. Land-cover shares come from the
seed's `sharesByYear` for year `s-1`.

## 8. Candidate models

### 8.1 `S_dynamic` in the backtest

Production computes `S_dynamic` **daily**; this study needs **one** value per comuna-season. Decision #4
fixes the daily-to-season aggregation and the backtest formula together [prereg-decisions #4]:

- **Aggregation (option B):** the comuna-season prediction is the **mean, over the season's days, of that
  day's blended score**. Rejected: (a) a single Dec-1 snapshot — a one-day FWI reading is a strawman
  baseline that MapBiomas would beat by default; (c) multiple rows per comuna-season — abandons the
  comuna-season unit already fixed by `S4a`/[MVS-3]/[MVS-6] and would need a spec amendment, not a
  pre-registration choice [prereg-decisions #4].
- **Formula:** the backtest `S_dynamic` is production's **STANDARD** formula with the FIRMS and citizen-
  report components forced to **0** in every arm — citizen reports already carry weight 0 in the backtest
  (no production history, [MVS-5a]), and FIRMS is zeroed so the in-season detections that build the target
  (Section 6) cannot also feed the score. With `W_FWI_STD = 0.52` [`ComunaRiskServiceImpl`], this reduces
  to `S_dynamic(c, day) = 0.52 · fwiNorm(c, day)` in every arm, including the `wM = 0` baseline.
- **FWI computation:** `fwiNorm = clamp((fwi - 0) / (FWI_MAX - 0))` with `FWI_MAX = 50.0` — a linear 0-1
  rescale of the raw Fire Weather Index, clamped at both ends, exactly as production normalizes it
  [`ComunaRiskServiceImpl`, constants `FWI_MAX`/`normalize`]. The raw `fwi` is the Canadian FWI System
  computed by the **Van Wagner** method (`territory.fwi.method = VAN_WAGNER`) from daily noon inputs
  (temperature, relative humidity, wind speed, precipitation) read from the **Open-Meteo Archive (ERA5)**,
  one call per comuna per season window [`ComunaFwiStateService`, `FwiSpinUpRunner`].
- **Chain start-up (pre-registered choice):** the codebase implements no season-start trigger — the
  season-start rule is explicitly **not implemented** [`ComunaFwiStateService` class javadoc]; the only
  documented mechanism is the standard CFFWIS **startup values** (`FFMC = 85`, `DMC = 6`, `DC = 15`;
  design [D4], following Lawson & Armitage 2008) [`ComunaFwiStateService.STARTUP_FFMC/STARTUP_DMC/STARTUP_DC`], used
  for a cold start or after a gap, and backfilled by `FwiSpinUpRunner` over a 120-day lookback by default.
  For this study, each season's chain is spun up from those startup values beginning **Sep 1 of `s-1`**
  (91 daily advances before Dec 1) and run forward; only Dec 1-Mar 31 of season `s` enters the Section 8.1
  mean. Sep 1 is fixed across all 9 seasons, not tuned per season.
- **Data availability:** the Open-Meteo Archive series for the 86 comunas has **not** been downloaded as
  of this pre-registration. It will be fetched by committed code before any backtest result runs, with its own
  manifest and checksums recorded the same way the FIRMS and MapBiomas archives already are (Section 3)
  [prereg-decisions #4].

### 8.2 Baseline, grid, and blend

- **Baseline:** `wM = 0` (`S_dynamic` as defined in 8.1, alone).
- **Grid:** `wM` in `{0.10, 0.25, 0.35, 0.50}` [spec Global definitions].
- **Blend:** `score = clamp((1 - wM) * S_dynamic + wM * S_mapbiomas)` [MRB-1].
- **`S_mapbiomas` formula:** `fuel = Σ_c share_c · w_c` (weights from `fuel-weights-v1.json`, class 27
  excluded, not zero-weighted) [D3]; `history = clamp(0.60·burnedNorm + 0.40·recurrenceNorm) · recency`;
  `recency = 1 + R_MAX·exp(-yearsSinceLastFire/TAU)` with `R_MAX = 0` (disabled) by default, pending the
  recency-sign decision [D3, open question in design-part2]; `S_mapbiomas = clamp(0.65·fuel + 0.35·history)`
  [D3]. `burnedNorm` defaults to `burnedFractionPct`, an empirical percentile rank over the 86 comunas with
  non-zero ties at the mean rank and exact zeros pinned to 0.0, and is the variant that decides `wM`
  (Section 13) [D3, prereg-decisions #5].

## 9. Metrics

- **Primary:** AUC-PR, pooled over all comuna-season pairs of the 9 test seasons [D7 rev3, MVS-1, MVS-6].
- **Secondary:** AUC (ROC) and Spearman correlation (continuous target) [MVS-6].
- **Secondary (reconciled):** Brier score per `wM`, pooled, with CI. Design [D7 Outputs] lists it; spec
  [MVS-6] does not. Resolved as a design-over-spec choice: Brier is reported as a secondary metric
  alongside AUC (ROC) and Spearman, with no decision power over Section 10's rule
  [prereg-decisions, reconciliations].
- Every metric is reported with its 95% CI and `n`; prevalence (positive rate) is reported per split, with
  a warning flag if prevalence `< 5%` [MVS-6a, MVS-6b].

## 10. Decision rule (pre-registered, executable as `decision_rule.py`)

Ship the **largest** `wM` in the fixed sequence `0.50 → 0.35 → 0.25 → 0.10` for which the pooled, paired
bootstrap's **lower bound** of the 95% CI of `AUC-PR(wM) − AUC-PR(0)` is `>= −0.02` (`delta = 0.02` absolute)
[D7 rev3, MVS-2]. Bootstrap: `B = 2000` resamples, seed `20261027`, **stratified/clustered by comuna** (a
comuna's seasons resample together as one unit — they are repeated measures, not independent), with the
**same resample indices** used for baseline and candidate in every resample (paired) [D7 rev3, MVS-2c].

A **leave-one-season-out** sensitivity recomputes the pooled decision 9 times, each time dropping one of
the 9 seasons, and is reported next to the pooled decision. It is a sensitivity analysis: it does **not**
change the pre-registered rule, and its result is reported whether or not it agrees with the pooled
decision [D7 rev3, MVS-2d, MVS-6d].

## 11. Outcome if no `wM` qualifies

Ship `wM = 0` and report the negative result as a verifiable finding, not a failure to hide. The MapBiomas
layers and panel section remain shipped regardless (they do not depend on `wM`); the project's Pertinencia
criterion then rests on the map/panel, not on the score [D7, MVS-2b, proposal].

## 12. Reporting

- Table 1: class selectivity (burned ha share vs. land-cover share) per class per region (H3) [D7 Outputs].
- Table 2: `wM` grid × AUC-PR/Brier with CIs, pooled; Table 2b: the same, per-season and
  leave-one-season-out [D7 Outputs, MVS-6c, MVS-6d].
- Table 3: alert-level distribution per `wM` → the threshold-recalibration mapping (Section 14).
- Figure 1 (H3): burned ha by land-cover class per region. Figure 2: PR curves, pooled and per season.
  Figure 3: per-comuna `S_mapbiomas` map.
- The sensitivities of Sections 6 and 13 are reported alongside the primary result, never substituting it.
- **Claim rule.** The report may state that MapBiomas **improves** prediction only if the 95% CI of
  `AUC-PR(wM) − AUC-PR(0)` for the shipped `wM` lies entirely above 0. Otherwise a shipped `wM > 0` is
  reported as **non-inferior** (no worse than the dynamic score by more than `delta`), never as an
  improvement. This rule governs wording only; it does not change the Section 10 decision.

## 13. The burnedNorm variant comparison (Q27)

`burnedNorm` defaults to `burnedFractionPct` (percentile rank, Section 8.2) and is the **sole** input to
the Section 10 decision rule [prereg-decisions #5]. The config-switchable `burnedFractionLog`
(log1p + p95-clip) variant is reported as a **pre-declared sensitivity with no decision power** — it never
feeds the `wM` choice. Rejected: running the Section 10 rule once per variant and shipping whichever lets
the larger `wM` qualify (percentile as tie-break) — this would turn the normalization itself into a second,
un-pre-registered degree of freedom evaluated on the same test seasons as the primary result
[prereg-decisions #5]. Q27 is resolved as: the percentile stays the production default unless a future,
separately pre-registered evaluation changes it.

## 14. Threshold recalibration

For the shipped `wM`, alert-level thresholds (`PREVENTIVO/ALTO/CRITICO`, baseline 0.50/0.70/0.85) are
re-derived by quantile matching: pick thresholds so the alert-level distribution over the reference window
(19,818 production snapshots since 2026-06-01) under the blended score matches today's distribution under
`wM = 0` [D3 "Threshold recalibration", MRB-9]. The resulting `wM → (preventivo, alto, critico)` mapping is
reported as a study output and committed as application configuration.

## 15. Known limitations

- Citizen reports carry weight 0 in the backtest; no production history exists for them [MVS-5a].
- Copernicus NDVI/NDMI is excluded from the backtest (starts 2017, stored per-region not per-comuna; a
  known, unfixed production limitation) [MVS-5a, D7].
- Fuego underestimates CONAF-mapped burned area by ~24.95%, Jaccard 80.1% on scars > 250 ha, with no
  published pixel-level accuracy figure [D7, MCS-6c].
- The fuel-weight table is literature-ordinal, explicitly **not empirically calibrated**
  (`fuel-weights-v1.json` provenance, [Q9]); class 67 has no distinct literature evidence and is weighted
  as parent/primary/secondary forest by documented simplification.
- `N_FIRMS` has an area bias: larger comunas reach the fixed count threshold more easily
  [prereg-decisions #3].
- `fire.burnedFraction`'s denominator is each comuna's total polygon area, not a true mapped-area mask:
  `annual_burned_coverage_v1` equals the burned-pixel count exactly, so it cannot serve as that denominator
  and was replaced by a bounding-box extent check (`bbox_coverage_fraction`) [mapbiomas README]. The
  discrepancy is empirically small (bbox-vs-polygon ratio 1.00004–1.00092) [obs 784], but a lake-heavy
  comuna (e.g. Villarrica, Pucón) still includes lake surface in its denominator.
- The VIIRS `type` assumption of Section 6 (same 4 values as documented for MODIS) is unconfirmed.
- Season 2017 has only 3 years of Fuego history (2013–2015) under the leakage rule (Section 7).
- The LULC/Fuego seed's double-count gates (area ratio within [0.85, 1.15], duplicate-row ratio `<= 15%`)
  are real, evidence-based checks, not a formal accuracy guarantee: observed area ratios ranged 0.90
  (Coronel) to 1.012, and duplicate-row ratios up to 3.96% [obs 782].

## 16. What has been inspected so far

Only the FIRMS archive's metadata (column names, value domains, date range: 208,890 rows, 2012-01-20 to
2025-12-31, SNPP, version 2) has been read; **no FIRMS detection counts per comuna or season, and no
relation between FIRMS and any predictor, have been computed** [prereg-decisions, Data]. Fuego and land
cover have already been processed into the committed seed
(`comuna-mapbiomas-stats.lulc-col2+fuego-col1-2013-2025.jsonl`) through the pipeline's own gates (area
ratio, duplicate-row, disjointness, per-comuna-year sum) [obs 782, obs 784], but this is pipeline output,
not an analysis of the relationship this study will test. No backtest code (`features.py`, `backtest.py`,
`decision_rule.py`) exists in the repository yet; only `Producto/analytics/study/data/firms/` has been
populated to date.

## 17. Integrity mechanism

- **This file's sha256:** `<sha256 recorded after commit>`.
- **Commit sha that introduced it:** `<commit sha recorded after commit>`.

Both values are filled in and recorded in the study's run configuration immediately after this file is
committed — never computed from an uncommitted draft. `decision_rule.py` and `run_all.py` read the margin,
season set, seed, and grid from that configuration — never hard-coded — and **abort** if the live file's
hash differs from the recorded one [MVS-1b]. Git history must show this file's commit as an ancestor of
every commit adding backtest outputs [MVS-1a]. Any later change to this file is recorded as a **numbered,
dated deviation** (Section 18) with its reason, never silently reconciled in code alone. The final report
(`Documentacion/`) cites both the pre-registration commit sha and this file's hash.

## 18. Change log

- **v0.1 (2026-10-01):** Initial draft, assembled from design [D7/D8] and spec [MVS-1..10], with six
  decisions left open (target filter, season window, `N_FIRMS`, `S_dynamic` aggregation, `burnedNorm`
  selection, H1 metric).
- **v0.2 (2026-10-01):** All six decisions resolved with the user and integrated into the body (Sections
  2, 5, 6, 7, 8, 9, 13), each cited `[prereg-decisions #N]`; Brier and the `MVS-4b` point-in-time framing
  reconciled (Sections 6, 9); the OPEN ITEMS section removed — nothing remains undecided [obs 785].
- **v1.0 (2026-10-01):** Reviewed by the author. Corrections before commit: the decision-rule bound is
  `>= -0.02` as in [D7]; the FWI startup values are attributed to [D4]; pixel-level Fuego quantities are
  recomputed from the annual rasters (not from per-comuna totals); H2 is stated as non-inferiority and a
  claim rule for "improvement" is added (Section 12). This version is the pre-registration.
- Once committed, any deviation discovered during execution of the study (an input whose checksum no
  longer matches, a parameter resolved differently than written here, a rule found impossible to implement
  as stated) is recorded as a **numbered, dated deviation entry** in this log and disclosed in the final
  report's methods section — never silently corrected in the code alone.
