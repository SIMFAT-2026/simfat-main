"""Generate the real Fuego Collection 1 coverage report and fire-only seed.

Reads the 86 target comuna GeoJSONs (Biobío, Ñuble, La Araucanía) via
``lulc.load_region_features``, reads the real MapBiomas Fuego Collection 1
rasters from a directory (never committed -- see README), calls the
corrected ``fire_stats``/``build_stats`` functions, and writes:

- ``data/coverage_report.csv`` (the Biobio-gate artifact)
- ``data/fire_stats_<as-of-year>_partial.jsonl`` (fire-only per-comuna
  seed; ``landCover`` is null with a stated reason, since the LULC join is
  out of scope for this slice)

This is a real, runnable reproduction of the committed data files -- it is
the WARNING-2/3 fix from the S1b jd-fix review: previously no committed
script produced ``data/coverage_report.csv``/``data/fire_stats_2017_partial.jsonl``
from source, so whatever ad hoc process did was not inspectable.

The script is GENERIC over whatever ``annual_burned``/``year_last_fire``/
``frequency_burned`` years are actually present in the raster directory --
it discovers them from filenames instead of hardcoding 2017, even though
2017 is the only year currently downloaded.

Usage (from ``Producto/analytics/mapbiomas``), with the real rasters at
``%TEMP%\\mb_fire_col1`` (or point ``MB_FIRE_RASTER_DIR`` elsewhere):

    PYTHONPATH=src python scripts/generate_fire_report_2017.py

See ``tests/test_build_stats_real.py`` for the exact raster file names
expected and the same ``MB_FIRE_RASTER_DIR`` / default-directory lookup.
"""
from __future__ import annotations

import os
import re
from datetime import datetime, timezone
from pathlib import Path

import rasterio

from mb_pipeline import build_stats, fire_stats, lulc, zonal

_ANNUAL_RE = re.compile(r"^mapbiomas_fire_chile_col1_annual_burned_(\d{4})\.tif$")
_COVERAGE_RE = re.compile(r"^mapbiomas_fire_chile_col1_annual_burned_coverage_(\d{4})\.tif$")
_FREQUENCY_RE = re.compile(r"^mapbiomas_fire_chile_col1_frequency_burned_(\d{4})_(\d{4})\.tif$")
_YEAR_LAST_FIRE_RE = re.compile(r"^mapbiomas_fire_chile_col1_year_last_fire_(\d{4})\.tif$")

_DATA_DIR = Path(__file__).resolve().parents[1] / "data"
_THRESHOLD = 0.95


def _raster_dir() -> Path:
    """Same env var / default-directory lookup as tests/test_build_stats_real.py."""
    env = os.environ.get("MB_FIRE_RASTER_DIR")
    if env:
        return Path(env)
    return Path(os.environ.get("TEMP", "/tmp")) / "mb_fire_col1"


def _discover_rasters(raster_dir: Path) -> dict:
    """Find every annual/coverage/frequency/year-last-fire raster present, by year."""
    annual_years: dict[int, Path] = {}
    coverage_years: dict[int, Path] = {}
    frequency_windows: dict[tuple[int, int], Path] = {}
    year_last_fire_years: dict[int, Path] = {}

    for entry in raster_dir.iterdir():
        if match := _ANNUAL_RE.match(entry.name):
            annual_years[int(match.group(1))] = entry
        elif match := _COVERAGE_RE.match(entry.name):
            coverage_years[int(match.group(1))] = entry
        elif match := _FREQUENCY_RE.match(entry.name):
            frequency_windows[(int(match.group(1)), int(match.group(2)))] = entry
        elif match := _YEAR_LAST_FIRE_RE.match(entry.name):
            year_last_fire_years[int(match.group(1))] = entry

    if not annual_years:
        raise SystemExit(f"No annual_burned rasters found in {raster_dir}")
    as_of_year = max(annual_years)
    year_last_fire_year = as_of_year if as_of_year in year_last_fire_years else max(year_last_fire_years)
    frequency_window = max(frequency_windows) if frequency_windows else None

    return {
        "as_of_year": as_of_year,
        "annual_years": sorted(annual_years),
        "annual_paths": annual_years,
        "coverage_paths": coverage_years,
        "year_last_fire_path": year_last_fire_years[year_last_fire_year],
        "year_last_fire_year": year_last_fire_year,
        "frequency_path": frequency_windows[frequency_window] if frequency_window else None,
        "frequency_window": frequency_window,
    }


def _build_provenance(raster_info: dict) -> dict:
    sources = [path.name for _, path in sorted(raster_info["annual_paths"].items())]
    if raster_info["coverage_paths"]:
        coverage_names = ", ".join(p.name for _, p in sorted(raster_info["coverage_paths"].items()))
        sources.append(f"{coverage_names} (bbox extent only, see fire_stats.py finding)")
    if raster_info["frequency_path"] is not None:
        sources.append(raster_info["frequency_path"].name)
    sources.append(raster_info["year_last_fire_path"].name)
    scope_years = ", ".join(str(y) for y in raster_info["annual_years"])
    window = raster_info["frequency_window"]
    window_desc = f"{window[0]}-{window[1]}" if window else "n/a"
    return {
        "sources": sources,
        "scope": f"S1b real-data subset: fire year(s) {scope_years}; frequency window {window_desc}",
        "downloadDate": datetime.now(timezone.utc).strftime("%Y-%m-%d"),
    }


def generate(raster_dir: Path | None = None, data_dir: Path | None = None) -> tuple[Path, Path]:
    raster_dir = raster_dir or _raster_dir()
    data_dir = data_dir or _DATA_DIR
    raster_info = _discover_rasters(raster_dir)
    as_of_year = raster_info["as_of_year"]

    with rasterio.open(raster_info["annual_paths"][as_of_year]) as src:
        raster_bounds = tuple(src.bounds)

    ylf_zonal_cache: dict[str, zonal.ZonalResult] = {}
    freq_zonal_cache: dict[str, zonal.ZonalResult] = {}

    region_features = lulc.load_region_features(lulc.DEFAULT_REGIONS)
    computed_at = datetime.now(timezone.utc).isoformat()

    csv_rows = []
    docs = []

    for region_name, features in region_features.items():
        for feature in features:
            comuna_id = feature["properties"]["comunaId"]
            nombre = feature["properties"]["nombre"]
            geometry = feature["geometry"]

            coverage_fraction = fire_stats.bbox_coverage_fraction(geometry, raster_bounds)

            per_year = {}
            for year in raster_info["annual_years"]:
                annual_result = zonal.zonal_stats(raster_info["annual_paths"][year], geometry)
                per_year[year] = fire_stats.annual_burned_fraction(annual_result)

            if raster_info["frequency_path"] is not None:
                freq_result = zonal.zonal_stats(raster_info["frequency_path"], geometry)
                frequency = fire_stats.frequency_stats(freq_result)
            else:
                frequency = {"frequencyMean": None, "frequencyMax": None}

            ylf_result = zonal.zonal_stats(raster_info["year_last_fire_path"], geometry)
            year_last_fire = fire_stats.year_last_fire_stats(ylf_result)

            fire_section = fire_stats.build_fire_section(
                per_year,
                coverage_fraction=coverage_fraction,
                frequency=frequency,
                year_last_fire=year_last_fire,
                threshold=_THRESHOLD,
                as_of_year=as_of_year,
            )

            doc = build_stats.build_comuna_document(
                comuna_id,
                land_cover=None,
                land_cover_reason=(
                    "LULC xlsx not processed in this S1b slice (fire-only real-data "
                    "subset); see sdd apply-progress for scope."
                ),
                fire=fire_section,
                provenance=_build_provenance(raster_info),
                computed_at=computed_at,
                partial=True,
            )
            docs.append(doc)

            window = raster_info["frequency_window"]
            window_suffix = f"{window[0]}_{window[1]}" if window else "na"
            row = {
                "comunaId": comuna_id,
                "nombre": nombre,
                "region": region_name,
                "coverageFraction": coverage_fraction,
                "available": fire_section["available"],
            }
            for year in raster_info["annual_years"]:
                row[f"totalHa{year}"] = per_year[year]["totalHa"]
                row[f"burnedHa{year}"] = per_year[year]["burnedHa"]
                row[f"burnedFraction{year}"] = per_year[year]["burnedFraction"]
            row[f"frequencyMean{window_suffix}"] = frequency["frequencyMean"]
            row[f"frequencyMax{window_suffix}"] = frequency["frequencyMax"]
            row[f"yearLastFireThrough{as_of_year}"] = fire_section["yearLastFire"]
            csv_rows.append(row)

    coverage_report_path = data_dir / "coverage_report.csv"
    seed_path = data_dir / f"fire_stats_{as_of_year}_partial.jsonl"
    build_stats.write_coverage_report(coverage_report_path, csv_rows)
    build_stats.write_seed_jsonl(seed_path, docs)
    return coverage_report_path, seed_path


if __name__ == "__main__":
    coverage_report_path, seed_path = generate()
    print(f"Wrote {coverage_report_path}")
    print(f"Wrote {seed_path}")
