"""Real-raster path: exercises zonal_stats + fire_stats against the actual
2017 Fuego Collection 1 GeoTIFFs and a real comuna polygon.

Raw rasters are never committed (see README), so these tests SKIP on a
fresh checkout instead of failing. Point ``MB_FIRE_RASTER_DIR`` at a
directory holding:

  mapbiomas_fire_chile_col1_annual_burned_2017.tif
  mapbiomas_fire_chile_col1_annual_burned_coverage_2017.tif
  mapbiomas_fire_chile_col1_year_last_fire_2017.tif
  mapbiomas_fire_chile_col1_frequency_burned_2013_2017.tif

to run them for real. This suite was exercised for real during S1b apply
(see data/coverage_report.csv and the apply-progress report for the actual
numbers found for all 86 comunas).

Note: ``annual_burned_coverage_v1`` is downloaded and required to be
present (see ``_RASTER_NAMES``) but is NOT used as a mapped-area
denominator here -- see fire_stats.py's module docstring for the empirical
finding that it encodes burned-pixel land cover, not a coverage mask.
"""
from __future__ import annotations

import os
from pathlib import Path

import pytest
import rasterio

from mb_pipeline import fire_stats, lulc, zonal

_RASTER_NAMES = {
    "annual": "mapbiomas_fire_chile_col1_annual_burned_2017.tif",
    "coverage": "mapbiomas_fire_chile_col1_annual_burned_coverage_2017.tif",
    "year_last_fire": "mapbiomas_fire_chile_col1_year_last_fire_2017.tif",
    "frequency": "mapbiomas_fire_chile_col1_frequency_burned_2013_2017.tif",
}


def _raster_dir() -> Path | None:
    env = os.environ.get("MB_FIRE_RASTER_DIR")
    candidates = [Path(env)] if env else []
    candidates.append(Path(os.environ.get("TEMP", "/tmp")) / "mb_fire_col1")
    for candidate in candidates:
        if all((candidate / name).exists() for name in _RASTER_NAMES.values()):
            return candidate
    return None


pytestmark = pytest.mark.skipif(
    _raster_dir() is None,
    reason="real Fuego Col 1 rasters not present locally (set MB_FIRE_RASTER_DIR); see README for the download step",
)


def _arauco_geometry() -> dict:
    # Reuse lulc's own repo-root-relative path so this test does not need to
    # recompute a parents[N] depth of its own (test files and lulc.py live
    # at different depths from the repo root).
    biobio_path = next(r.geojson_path for r in lulc.DEFAULT_REGIONS if r.xlsx_region == "Biobío")
    features = lulc.load_geojson_features(biobio_path)
    arauco = next(f for f in features if f["properties"]["nombre"] == "Arauco")
    return arauco["geometry"]


def test_real_2017_zonal_stats_for_arauco_are_sane_and_non_trivial():
    raster_dir = _raster_dir()
    geometry = _arauco_geometry()

    annual = zonal.zonal_stats(raster_dir / _RASTER_NAMES["annual"], geometry)
    assert annual.pixel_count > 0  # the polygon is really inside the raster
    result = fire_stats.annual_burned_fraction(annual)
    assert result["totalHa"] > 0.0
    assert 0.0 <= result["burnedFraction"] <= 1.0

    with rasterio.open(raster_dir / _RASTER_NAMES["annual"]) as src:
        raster_bounds = tuple(src.bounds)
    coverage_fraction = fire_stats.bbox_coverage_fraction(geometry, raster_bounds)
    # Arauco is squarely inside the Fuego Col 1 extent (-74.85..-69.77 lon,
    # -44.07..-32.02 lat): real bbox coverage must be complete, not partial.
    assert coverage_fraction == pytest.approx(1.0)


def test_real_frequency_and_year_last_fire_for_arauco():
    raster_dir = _raster_dir()
    geometry = _arauco_geometry()

    freq = zonal.zonal_stats(raster_dir / _RASTER_NAMES["frequency"], geometry)
    ylf = zonal.zonal_stats(raster_dir / _RASTER_NAMES["year_last_fire"], geometry)

    freq_result = fire_stats.frequency_stats(freq)
    ylf_result = fire_stats.year_last_fire_stats(ylf)

    assert freq_result["frequencyMean"] is not None
    assert freq_result["frequencyMean"] >= 0.0
    assert freq_result["frequencyMax"] is None or freq_result["frequencyMax"] >= 0
    # yearLastFire, if any, must be within the raster's own year range (<=2017).
    if ylf_result["yearLastFire"] is not None:
        assert 2013 <= ylf_result["yearLastFire"] <= 2017
