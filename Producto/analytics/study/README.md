# Validation study: MapBiomas susceptibility blend

Pre-registered study (`PREREGISTRATION.md`, committed before any result) that
evaluates whether blending the MapBiomas-derived susceptibility score
`S_mapbiomas` into SIMFAT's dynamic risk score improves agreement with an
independent fire-activity signal (NASA FIRMS). See `PREREGISTRATION.md` for
the full hypotheses, leakage rules, and decision rule.

## S4b1a: study inputs (this slice)

**Scope: INPUTS ONLY.** This slice builds the Van Wagner FWI chain, the
Open-Meteo/ERA5 weather series, and the season-level FWI/`S_dynamic`
features. Per the launch prompt's blinding rule, it never loads the FIRMS
archive and never computes any quantity relating a predictor (FWI,
`S_dynamic`, `S_mapbiomas`) to the FIRMS target.

### Layout

```
src/study/
  config.py            # verifies PREREGISTRATION.md's sha256 against the lock file; every entry point calls this
  fwi.py               # pure-Python parity port of CanadianFwiCalculator.java (Van Wagner & Pickett 1985)
  era5.py              # representative comuna points / full geometries + Open-Meteo Archive (ERA5) fetch + noon-input derivation
  season_fwi.py        # per-season chain (Sep 1 of s-1 startup) + S_dynamic mean aggregation (standard + sensitivity windows)
  firms_target.py      # FIRMS target: comuna assignment, UTC->local date, N_FIRMS/window/filter counting, gates
  season_mapbiomas.py  # leakage-safe per-season Fuego/land-cover features + S_mapbiomas Java-parity port
scripts/
  download_era5.py          # fetches ERA5 hourly weather for all 86 comunas, writes data/era5/
  build_season_fwi.py       # builds data/features/season_fwi.csv from the committed noon-input table
  build_firms_target.py     # builds data/features/firms_target.csv from the committed FIRMS archive
  build_season_mapbiomas.py # builds data/features/season_mapbiomas.csv from the verified Fuego rasters + seed
tests/            # pytest, network-free (fake openers for HTTP, synthetic fixtures for geometry/rasters)
data/era5/        # committed: noon_inputs_<start>_<end>.csv.gz + manifest.json
data/features/    # committed: season_fwi.csv, firms_target.csv, season_mapbiomas.csv
```

### `fwi.py`: Van Wagner FWI parity port

Ported line-for-line from
`Producto/backend/simfat-backend/.../service/fwi/CanadianFwiCalculator.java`.
Validated against the same reference vectors the Java test uses
(`Producto/backend/simfat-backend/src/test/resources/fwi/vanwagner-1985-vectors.csv`,
49 days, Van Wagner & Pickett 1985's own "Sample of Output" table), at the
same tolerances (0.06 for codes/indices, 0.005 for DSR). All edge cases from
`CanadianFwiCalculatorTest.java` are mirrored: the FFMC constant 147.2 (never
147.27723), the DMC rain-recovery literal `0.023`, the DC temperature floor
`-2.8`, `rh > 100` clamping (and the NaN propagation it prevents), and the
`month` range validation.

### `era5.py`: representative points and Open-Meteo Archive fetch

**Representative point method.** One `(lon, lat)` point per comuna,
guaranteed to lie inside the polygon -- never a centroid that can fall
outside (comuna polygons from GADM can be concave, e.g. coastal or
horseshoe shapes). Method: pick the MultiPolygon's largest-area part (the
mainland piece, for comunas whose small offshore islands are separate
parts); try that part's vertex-average; if it is not actually inside
(ray-casting test against the exterior ring minus holes), fall back to a
101x101 grid search over the part's bounding box, keeping the inside point
closest to the vertex-average. Verified for real against all 86 comunas'
`comunas-*.geojson` (`tests/test_era5.py`).

**Download.** Hourly `temperature_2m, relative_humidity_2m, wind_speed_10m,
precipitation` from `https://archive-api.open-meteo.com/v1/archive`, with
`models=era5` (explicit ERA5 reanalysis, not Open-Meteo's multi-model
"best_match" blend) and `timezone=America/Santiago`. Requests are
SEQUENTIAL (one comuna at a time), with retries and backoff; raw per-comuna
JSON responses are cached OUTSIDE the repo (`%TEMP%\study_era5`), atomically
written, and every response's sha256 is recorded in `data/era5/manifest.json`.

**Noon-input derivation (D4).** For each day `D`: `tempC`/`rhPct`/`windKmh`
are the values at `D`'s 12:00 local; `rainMm` is the 24-hour sum from
`D-1`'s 13:00 through `D`'s 12:00 (the Van Wagner noon-rain convention).
Computing `D`'s rain window needs `D-1`'s afternoon/evening hours, so the
raw fetch window starts one day before the nominal start date.

**Gate.** `derive_noon_inputs` raises `MissingDataError` (never
interpolates) naming the comuna and date if any required hourly value is
absent or `null`. `download_era5.py` lets this propagate: a missing day
anywhere aborts the whole run.

### `season_fwi.py`: season-level FWI chain and `S_dynamic`

For each comuna and each pre-registered season `s` in 2017..2025
(`SEASONS`): spin up the chain from the published startup values
(FFMC=85, DMC=6, DC=15) on **Sep 1 of `s-1`**, advance daily through **Apr
30 of `s`** (the sensitivity window's end -- a strict superset of the
standard window, so the chain is built once and never restarted
mid-season), then aggregate:

- **standard window**: Dec 1 of `s-1` through Mar 31 of `s`.
- **sensitivity window**: Nov 1 of `s-1` through Apr 30 of `s`.

`S_dynamic(day) = W_FWI_STD * clamp(fwi(day) / FWI_MAX, 0, 1)` with
`W_FWI_STD = 0.52`, `FWI_MAX = 50.0` -- `ComunaRiskServiceImpl`'s STANDARD
formula with the FIRMS and citizen-report components forced to 0 (Section
8.1). The aggregation is the MEAN of each day's `S_dynamic`/`fwi` over the
window (option B of Section 8.1), not a formula applied to the mean FWI.

### `config.py`: pre-registration integrity gate

Every entry point (`download_era5.py`, `build_season_fwi.py`, and every
future study script) calls `config.verify_preregistration()` first and
aborts loudly on a sha256 mismatch against `PREREGISTRATION.lock.json`
(Section 17). The comparison normalizes CRLF to LF before hashing, since
the recorded hash is of the git-stored (LF) content.

## S4b1b: FIRMS target and MapBiomas season features (this slice)

**Scope: INPUTS ONLY**, same blinding rule as S4b1a, enforced across TWO
separate output files that are never joined: `firms_target.csv` (the
target) and `season_mapbiomas.csv` (predictors). Neither file, nor this
slice's code or tests, computes a quantity relating one to the other.

### `firms_target.py` + `scripts/build_firms_target.py`

Builds `data/features/firms_target.csv` (PREREGISTRATION.md Section 6)
directly from the committed FIRMS archive (`data/firms/DL_FIRE_SV-C2_809648.zip`,
no network). One row per `(comunaId, season, window, filter)` -- 86 x 9 x
2 x 2 = 3096 rows, zero counts included explicitly -- with `count`,
`y_n1`, `y_n3`, `y_n5` (`y_nK = 1` iff `count >= K`).

**Comuna assignment (implementation detail, not fixed by the
pre-registration).** Each detection's `(latitude, longitude)` is tested by
point-in-polygon against every monitored comuna's GeoJSON geometry
(`comunaId` property = the GADM GID_3 code), checking every
`MultiPolygon` part and excluding holes. A detection inside no comuna's
polygon is dropped and counted (18,083 of 208,890 real rows, mostly
offshore/outside-region detections), never assigned to the nearest
comuna.

**UTC -> local date, and a real environment gotcha (disclosed as a
candidate deviation).** `acq_date`/`acq_time` are UTC; season-window
membership is defined in LOCAL (Chile) calendar dates, so every
detection's timestamp is converted before any window check. The launch
prompt asked for `zoneinfo`, but this sandboxed Windows environment has
no system IANA time-zone database, and installing the `tzdata` package
(the usual fix) is outside the "stdlib + numpy + rasterio, no installs"
dependency constraint -- even `zoneinfo.ZoneInfo("UTC")` fails to resolve
here. As a disclosed substitute, the exact Chile DST transition instants
for 2016-2026 were read from the Windows time-zone database ("Pacific SA
Standard Time") and hard-coded in `firms_target.CHILE_DST_INTERVALS_UTC`
(used by `utc_offset_hours`). A weekday rule ("first Sunday of September to
first Sunday of April") was tried first and was wrong for this range: Chile
left DST in mid-May in 2016-2018 and started it on Sep 11 in 2022. Swap in
`zoneinfo` if an environment with `tzdata` becomes available; the two must
agree for every instant from May 2016 to Sep 2026.

**Windows and filters.** `primary` = Dec 1 (s-1)..Mar 31 (s); `sensitivity`
= Nov 1 (s-1)..Apr 30 (s) (reusing `season_fwi.standard_window`/
`sensitivity_window` -- the pre-registered boundaries are identical).
`type0` = `type == 0` AND `confidence` in `{n, h}`; `notype` = same
confidence filter, no `type` restriction. **Gates**: every
comuna-season-window-filter row present; non-negative integer counts;
`sensitivity >= primary` (same filter); `notype >= type0` (same window).

### `season_mapbiomas.py` + `scripts/build_season_mapbiomas.py`

Builds `data/features/season_mapbiomas.csv` (PREREGISTRATION.md Section 7
leakage rules, Section 8.2 formula): one row per comuna-season (86 x 9 =
774 rows) with `fuel`, `burned_union`, `burned_pct`, `burned_log`,
`frequency_mean`, `recurrence_norm`, `history`, `s_mapbiomas`,
`history_log`, `s_mapbiomas_log`.

**Leakage-safe recomputation.** For test season `s`, Fuego inputs use
ONLY years `2013..(s-2)` -- never the committed seed's full 2013-2025
aggregates, which are anchored at 2025. Every cached annual raster's
sha256 is verified against `Producto/analytics/mapbiomas/data/manifest.json`
before use; a missing manifest entry or a mismatch aborts loudly.
`s=2017` uses exactly years 2013-2015 (3 years, the thinnest history, a
disclosed consequence of the leakage rule). Land-cover shares come from
the committed seed's `sharesByYear` for year `s-1` (no leakage).

**`frequencyMean` without a per-season frequency raster (implementation
note).** Production's `frequencyMean` is a pixel-count-weighted mean of a
`frequency_burned_v1` raster. No per-season frequency raster is cached
(only the full 2013-2025 one, explicitly off-limits here), so this module
reproduces the identical definition from the per-year `annual_burned_v1`
rasters via an exact identity: summing each pixel's "years burned" count
over the whole mask equals summing each YEAR's burned-pixel COUNT over
all years, regardless of which pixels overlap across years. See
`frequency_mean_from_zonal_results`'s docstring.

**`yearLastFire`/`yearsSinceLastFire` (implementation note).** Derived
purely from the annual-burned evidence restricted to `<= s-2` (never from
any `year_last_fire_v1` raster, whose only cached windows end in 2017 or
2025 and would leak most seasons). Computed and documented, per the
launch prompt, but with no effect on `history`/`S_mapbiomas` (recency is
disabled, `R_MAX=0`, per Section 8.2) and not persisted to the CSV (whose
header is fixed by the launch prompt without these two columns).

**S_mapbiomas Java parity.** `fuel`/`history`/`S_mapbiomas` mirror
`MapbiomasSusceptibilityServiceImpl.java` exactly (weights 0.65/0.35,
0.60/0.40, `FREQUENCY_CAP=5.0`, class 27 excluded from the fuel sum,
clamp01 everywhere, recency fixed at 1.0). A dedicated parity test
(`tests/test_season_mapbiomas.py::test_s_mapbiomas_parity_against_real_pinned_seed_values`)
feeds the real committed seed's full-window inputs for the three comunas
pinned in `RealSeedRegressionTest.java` and checks `history`/`s_mapbiomas`
match within `1e-6`. `fuel` for `CHL.13.1.1_1` is checked at `1e-4`
instead: the Java source's `0.4716` literal is a short, evidently
human-rounded transcription (the real value is `0.47164`), while the
other two comunas' `fuel` literals (`0.78615`, `0.89208`) and every
`history`/`score` literal carry full double precision.

**`burnedFractionPct`/`burnedFractionLog` (Q27)** are recomputed PER
SEASON over the 86 comunas (never reused across seasons), via the same
production functions (`mb_pipeline.build_stats.percentile_rank_burned_fraction`
/ `log1p_p95_clip_burned_fraction`). `burned_pct` is the primary
`burnedNorm`; `burned_log` is the pre-declared, decision-free sensitivity.

**Gates**: `s=2017` uses exactly Fuego years 2013-2015; `burned_union` is
non-decreasing in `s` for every comuna (a growing year range can only add
burned pixels to the union); every `[0, 1]`-bounded field actually is.
**The s=2019 independent cross-check** (union area vs. the area with
`frequency > 0` in the cached `..._frequency_burned_2013_2017.tif`) is
**skipped with an explicit note**: that file has no `manifest.json`
entry (it predates the manifest's current 17-entry set), so its sha256
cannot be verified -- the launch prompt explicitly sanctions skipping in
that case rather than using an unverified raster.

## Running

```
cd Producto/analytics/study
python -m pip install -r requirements.txt
python -m pytest                       # all tests, network-free

python scripts/download_era5.py           # real network calls, ~20 min, writes data/era5/
python scripts/build_season_fwi.py        # writes data/features/season_fwi.csv
python scripts/build_firms_target.py      # writes data/features/firms_target.csv
python scripts/build_season_mapbiomas.py  # writes data/features/season_mapbiomas.csv
```

## Date range note

The Open-Meteo Archive download covers **2016-09-01 to 2025-04-30**,
extended beyond the pre-registration's working date (2025-03-31) so the
Nov-Apr sensitivity window of the final season (2025) is fully covered by
real data instead of partially built from an incomplete range. This does
not change any fixed parameter of `PREREGISTRATION.md` (margin, season set,
seed, grid) -- see `data/era5/manifest.json`'s `note` field.

## Licence / attribution

Weather data by Open-Meteo.com, ERA5 reanalysis by ECMWF (Copernicus
Climate Change Service). Licence: CC BY 4.0, non-commercial use (project
decision Q24, 2026-09-20). Recorded in full in `data/era5/manifest.json`.
