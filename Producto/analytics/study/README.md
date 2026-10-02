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
  config.py       # verifies PREREGISTRATION.md's sha256 against the lock file; every entry point calls this
  fwi.py          # pure-Python parity port of CanadianFwiCalculator.java (Van Wagner & Pickett 1985)
  era5.py         # representative comuna points + Open-Meteo Archive (ERA5) fetch + noon-input derivation
  season_fwi.py   # per-season chain (Sep 1 of s-1 startup) + S_dynamic mean aggregation (standard + sensitivity windows)
scripts/
  download_era5.py    # fetches ERA5 hourly weather for all 86 comunas, writes data/era5/
  build_season_fwi.py # builds data/features/season_fwi.csv from the committed noon-input table
tests/            # pytest, network-free (fake openers for HTTP, synthetic fixtures for geometry)
data/era5/        # committed: noon_inputs_<start>_<end>.csv.gz + manifest.json
data/features/    # committed: season_fwi.csv
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

## Running

```
cd Producto/analytics/study
python -m pip install -r requirements.txt
python -m pytest                       # all tests, network-free

python scripts/download_era5.py         # real network calls, ~20 min, writes data/era5/
python scripts/build_season_fwi.py      # writes data/features/season_fwi.csv
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
