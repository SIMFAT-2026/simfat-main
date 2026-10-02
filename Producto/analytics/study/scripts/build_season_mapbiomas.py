"""Builds data/features/season_mapbiomas.csv (PREREGISTRATION.md Section 7,
Section 8.2).

For every comuna and every pre-registered test season (``study.
season_mapbiomas.SEASONS``, 2017..2025), recomputes the leakage-safe Fuego
features (burned union, frequency, recency inputs) from the verified
annual Fuego rasters restricted to years ``<= s-2``, reads land-cover
shares for year ``s-1`` from the committed seed, and computes
``fuel``/``history``/``S_mapbiomas`` via the Java-parity port in
``study.season_mapbiomas``.

BLINDING RULE: this script builds PREDICTOR INPUTS ONLY. It never loads
FIRMS data and never prints or computes any quantity relating these
predictors to a fire-activity target -- only row counts, value ranges, and
the gate checks described in the launch prompt.

Usage (from Producto/analytics/study):

    python scripts/build_season_mapbiomas.py
"""
from __future__ import annotations

import csv
import os
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
STUDY_ROOT = SCRIPT_DIR.parent
SRC_DIR = STUDY_ROOT / "src"
MAPBIOMAS_SRC_DIR = STUDY_ROOT.parent / "mapbiomas" / "src"
for path in (SRC_DIR, MAPBIOMAS_SRC_DIR):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from study import config, era5, season_mapbiomas  # noqa: E402
from study.season_mapbiomas import SeasonMapbiomasRow  # noqa: E402

REPO_ROOT = STUDY_ROOT.parents[2]  # study -> analytics -> Producto -> repo root
GEOJSON_DIR = (
    REPO_ROOT
    / "Producto"
    / "backend"
    / "simfat-backend"
    / "src"
    / "main"
    / "resources"
    / "static"
    / "geojson"
)
SEED_PATH = (
    REPO_ROOT
    / "Producto"
    / "analytics"
    / "mapbiomas"
    / "data"
    / "comuna-mapbiomas-stats.lulc-col2+fuego-col1-2013-2025.jsonl"
)
FUEL_WEIGHTS_PATH = (
    REPO_ROOT
    / "Producto"
    / "backend"
    / "simfat-backend"
    / "src"
    / "main"
    / "resources"
    / "mapbiomas"
    / "fuel-weights-v1.json"
)
MANIFEST_PATH = REPO_ROOT / "Producto" / "analytics" / "mapbiomas" / "data" / "manifest.json"
RASTER_CACHE_DIR = Path(os.environ.get("TEMP") or os.environ.get("TMPDIR") or "/tmp") / "mb_fire_col1"
OUTPUT_PATH = STUDY_ROOT / "data" / "features" / "season_mapbiomas.csv"

CSV_HEADER = [
    "comunaId", "season", "fuel", "burned_union", "burned_pct", "burned_log",
    "frequency_mean", "recurrence_norm", "history", "s_mapbiomas", "history_log", "s_mapbiomas_log",
]


def write_csv(rows: list[SeasonMapbiomasRow], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(CSV_HEADER)
        for r in rows:
            writer.writerow(
                [
                    r.comuna_id, r.season, r.fuel, r.burned_union, r.burned_pct, r.burned_log,
                    r.frequency_mean, r.recurrence_norm, r.history, r.s_mapbiomas,
                    r.history_log, r.s_mapbiomas_log,
                ]
            )


def validate_gates(rows: list[SeasonMapbiomasRow]) -> None:
    """Launch-prompt gates: s=2017 uses exactly Fuego years 2013-2015;
    burned_union is non-decreasing in season for every comuna; every
    [0,1]-bounded field actually is."""
    assert season_mapbiomas.fire_years_for_season(2017) == [2013, 2014, 2015]

    by_comuna: dict[str, list[SeasonMapbiomasRow]] = {}
    for r in rows:
        by_comuna.setdefault(r.comuna_id, []).append(r)
    for comuna_id, comuna_rows in by_comuna.items():
        ordered = sorted(comuna_rows, key=lambda r: r.season)
        unions = [r.burned_union for r in ordered]
        for prev, nxt in zip(unions, unions[1:]):
            if nxt < prev - 1e-9:
                raise AssertionError(
                    f"{comuna_id}: burned_union decreased across seasons ({prev} -> {nxt})"
                )

    bounded_fields = ("fuel", "burned_union", "burned_pct", "burned_log", "recurrence_norm",
                       "history", "s_mapbiomas", "history_log", "s_mapbiomas_log")
    for r in rows:
        for field in bounded_fields:
            value = getattr(r, field)
            if not (0.0 - 1e-9 <= value <= 1.0 + 1e-9):
                raise AssertionError(f"{r.comuna_id} season={r.season}: {field}={value} outside [0, 1]")
        if r.frequency_mean is not None and r.frequency_mean < 0:
            raise AssertionError(f"{r.comuna_id} season={r.season}: frequency_mean={r.frequency_mean} is negative")


def check_s2019_frequency_cross_check() -> None:
    """Independent cross-check (launch prompt scope item 2): for s=2019
    (Fuego years 2013-2017), the union area should equal the area with
    frequency > 0 in the cached
    ``mapbiomas_fire_chile_col1_frequency_burned_2013_2017.tif``.

    SKIPPED here: that file has no entry in manifest.json (see
    study.season_mapbiomas's module docstring), so its sha256 cannot be
    verified before use -- the launch prompt explicitly allows skipping
    with an explicit note in that case.
    """
    print(
        "NOTE: s=2019 independent frequency cross-check SKIPPED -- "
        "mapbiomas_fire_chile_col1_frequency_burned_2013_2017.tif has no "
        "manifest.json entry (unverified sha256), per the launch prompt's "
        "explicit skip-with-note allowance. See season_mapbiomas.py's module "
        "docstring."
    )


def main() -> int:
    lock = config.verify_preregistration(STUDY_ROOT)
    print(f"Pre-registration integrity OK (sha256={lock['sha256'][:12]}..., commit={lock['commit'][:12]}...)")

    check_s2019_frequency_cross_check()

    geometries = era5.load_comuna_geometries(GEOJSON_DIR)
    print(f"Loaded {len(geometries)} comuna geometries from {GEOJSON_DIR}")

    land_cover_shares = season_mapbiomas.load_land_cover_shares_by_year(SEED_PATH)
    print(f"Loaded land-cover sharesByYear for {len(land_cover_shares)} comunas from {SEED_PATH}")

    fuel_weights = season_mapbiomas.load_fuel_weights(FUEL_WEIGHTS_PATH)
    print(f"Loaded fuel weights from {FUEL_WEIGHTS_PATH}")

    annual_raster_paths = season_mapbiomas.load_verified_annual_rasters(RASTER_CACHE_DIR, MANIFEST_PATH)
    print(f"Verified {len(annual_raster_paths)} annual Fuego rasters against {MANIFEST_PATH}")

    rows = season_mapbiomas.build_season_mapbiomas_features(
        geometries, land_cover_shares, annual_raster_paths, fuel_weights
    )
    validate_gates(rows)
    write_csv(rows, OUTPUT_PATH)

    print(f"Wrote {OUTPUT_PATH} ({len(rows)} rows)")
    for field in ("fuel", "burned_union", "burned_pct", "burned_log", "history", "s_mapbiomas"):
        values = [getattr(r, field) for r in rows]
        print(f"{field} range: [{min(values):.4f}, {max(values):.4f}]")
    freq_values = [r.frequency_mean for r in rows if r.frequency_mean is not None]
    if freq_values:
        print(f"frequency_mean range: [{min(freq_values):.4f}, {max(freq_values):.4f}]")
    return 0


if __name__ == "__main__":
    sys.exit(main())
