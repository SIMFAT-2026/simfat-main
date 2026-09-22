"""Real-raster path for scripts/generate_fire_report_2017.py.

WARNING-2/3 fix (S1b jd-fix review): before this script existed, no
committed code reproduced ``data/coverage_report.csv`` /
``data/fire_stats_2017_partial.jsonl`` from the real 86 comuna GeoJSONs and
the real Fuego rasters -- this test proves the script does, for real,
against the actual rasters on disk. Skips on a fresh checkout, same pattern
as ``test_build_stats_real.py``.
"""
from __future__ import annotations

import csv
import json
import os
from pathlib import Path

import pytest

import generate_fire_report_2017 as script

_RASTER_NAMES = (
    "mapbiomas_fire_chile_col1_annual_burned_2017.tif",
    "mapbiomas_fire_chile_col1_annual_burned_coverage_2017.tif",
    "mapbiomas_fire_chile_col1_year_last_fire_2017.tif",
    "mapbiomas_fire_chile_col1_frequency_burned_2013_2017.tif",
)


def _raster_dir() -> Path | None:
    env = os.environ.get("MB_FIRE_RASTER_DIR")
    candidates = [Path(env)] if env else []
    candidates.append(Path(os.environ.get("TEMP", "/tmp")) / "mb_fire_col1")
    for candidate in candidates:
        if all((candidate / name).exists() for name in _RASTER_NAMES):
            return candidate
    return None


pytestmark = pytest.mark.skipif(
    _raster_dir() is None,
    reason="real Fuego Col 1 rasters not present locally (set MB_FIRE_RASTER_DIR); see README",
)


def test_generate_writes_86_comunas_with_no_year_last_fire_contradiction(tmp_path):
    raster_dir = _raster_dir()
    coverage_report_path, seed_path = script.generate(raster_dir=raster_dir, data_dir=tmp_path)

    with open(coverage_report_path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    assert len(rows) == 86

    docs = {}
    with open(seed_path, encoding="utf-8") as f:
        for line in f:
            doc = json.loads(line)
            docs[doc["comunaId"]] = doc
    assert len(docs) == 86

    # The CRITICAL 1 regression check, exercised through the real script:
    # Florida's yearLastFire must never contradict its own 2017 burned
    # fraction, whatever the raw year_last_fire_v1 raster says.
    florida = docs["CHL.6.3.4_1"]
    assert florida["fire"]["burnedFractionByYear"]["2017"] > 0.4
    assert florida["fire"]["yearLastFire"] >= 2017

    # landCover is out of scope for this slice; every document must say so.
    assert florida["landCover"] is None
    assert florida["landCoverReason"] is not None
    assert florida["partial"] is True
