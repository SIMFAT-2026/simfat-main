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


@pytest.mark.skipif(
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

    # design D1/D3: every comuna with usable fire data gets a burnedFractionPct
    # (empirical percentile rank across all 86 comunas, computed from this
    # dataset's only Fuego year, 2017 -- see build_stats.add_burned_fraction_pct
    # and fire_stats.select_burned_fraction_for_pct). Florida burned the most
    # of any comuna in 2017, so it must be at the top of the rank.
    assert florida["fire"]["burnedFractionPct"] == pytest.approx(1.0)
    pct_values = [doc["fire"]["burnedFractionPct"] for doc in docs.values() if doc["fire"]["available"]]
    assert all(0.0 <= v <= 1.0 for v in pct_values)
    # At least one comuna had zero burned area in 2017 and must be pinned to
    # exactly 0.0, not a small positive tie-averaged rank.
    assert 0.0 in pct_values


# --- S1b5: full 2013-2025 history real-raster run --------------------------

_FULL_HISTORY_RASTER_NAMES = tuple(
    f"mapbiomas_fire_chile_col1_annual_burned_{year}.tif" for year in range(2013, 2026)
) + (
    "mapbiomas_fire_chile_col1_frequency_burned_2013_2025.tif",
    "mapbiomas_fire_chile_col1_year_last_fire_2025.tif",
    "mapbiomas_fire_chile_col1_accumulated_burned_2013_2025.tif",
)


def _full_history_raster_dir() -> Path | None:
    env = os.environ.get("MB_FIRE_RASTER_DIR")
    candidates = [Path(env)] if env else []
    candidates.append(Path(os.environ.get("TEMP", "/tmp")) / "mb_fire_col1")
    for candidate in candidates:
        if all((candidate / name).exists() for name in _FULL_HISTORY_RASTER_NAMES):
            return candidate
    return None


@pytest.mark.skipif(
    _full_history_raster_dir() is None,
    reason="real 2013-2025 Fuego Col 1 rasters not present locally; see README for the download step",
)
def test_generate_full_history_writes_86_comunas_with_union_and_cross_check(tmp_path):
    raster_dir = _full_history_raster_dir()
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
    assert seed_path.name == "fire_stats_2013_2025_fireonly.jsonl"

    # every year 2013..2025 is present for every comuna.
    for doc in docs.values():
        assert set(doc["fire"]["burnedHaByYear"]) == {str(y) for y in range(2013, 2026)} or not doc["fire"]["available"]

    # union (cumulative) fraction is populated and respects the D1/MCS-10b gates.
    for doc in docs.values():
        fire = doc["fire"]
        if fire["burnedFraction"] is None:
            continue
        assert 0.0 <= fire["burnedFraction"] <= 1.0
        annual_values = [v for v in fire["burnedFractionByYear"].values() if v is not None]
        if annual_values:
            assert max(annual_values) <= fire["burnedFraction"] + 1e-6
            assert fire["burnedFraction"] <= sum(annual_values) + 1e-6

    # cross-check column exists and is small (our OR-derived union should be
    # close to MapBiomas' own accumulated_burned_v1 product).
    cross_check_diffs = [
        float(row["unionCrossCheckAbsDiff"])
        for row in rows
        if row["unionCrossCheckAbsDiff"] not in ("", None)
    ]
    assert cross_check_diffs, "expected at least one comuna with a cross-check value"
    assert max(cross_check_diffs) < 0.05  # informative bound, not a hard gate

    # burnedFractionPct and burnedFractionLog are both populated for every
    # available comuna and stay in [0, 1].
    for doc in docs.values():
        if doc["fire"]["available"]:
            assert doc["fire"]["burnedFractionPct"] is not None
            assert doc["fire"]["burnedFractionLog"] is not None
            assert 0.0 <= doc["fire"]["burnedFractionPct"] <= 1.0
            assert 0.0 <= doc["fire"]["burnedFractionLog"] <= 1.0


@pytest.mark.skipif(
    _full_history_raster_dir() is None,
    reason="real 2013-2025 Fuego Col 1 rasters not present locally; see README for the download step",
)
def test_generate_full_history_matches_the_2017_only_run_for_2017_values(tmp_path):
    """S1b5 REGRESSION gate: recomputing 2017 with the full 2013-2025 run must
    reproduce the SAME per-year 2017 burnedHa/burnedFraction as the original
    2017-only run, for every comuna -- proving the method did not change,
    only its scope grew."""
    raster_dir = _full_history_raster_dir()

    # 2017-only run: restrict discovery to a directory holding ONLY the
    # original 4 2017 files (symlink/copy avoided -- instead call generate()
    # against a filtered temp dir).
    only_2017_dir = tmp_path / "only_2017"
    only_2017_dir.mkdir()
    import shutil

    for name in (
        "mapbiomas_fire_chile_col1_annual_burned_2017.tif",
        "mapbiomas_fire_chile_col1_frequency_burned_2013_2017.tif",
        "mapbiomas_fire_chile_col1_year_last_fire_2017.tif",
    ):
        shutil.copy(raster_dir / name, only_2017_dir / name)

    _, seed_2017_only = script.generate(raster_dir=only_2017_dir, data_dir=tmp_path / "out_2017")
    _, seed_full = script.generate(raster_dir=raster_dir, data_dir=tmp_path / "out_full")

    docs_2017_only = {}
    with open(seed_2017_only, encoding="utf-8") as f:
        for line in f:
            doc = json.loads(line)
            docs_2017_only[doc["comunaId"]] = doc

    docs_full = {}
    with open(seed_full, encoding="utf-8") as f:
        for line in f:
            doc = json.loads(line)
            docs_full[doc["comunaId"]] = doc

    max_abs_diff_ha = 0.0
    max_abs_diff_fraction = 0.0
    for comuna_id, doc_2017 in docs_2017_only.items():
        doc_full = docs_full[comuna_id]
        ha_2017_only = doc_2017["fire"]["burnedHaByYear"]["2017"]
        ha_full = doc_full["fire"]["burnedHaByYear"]["2017"]
        frac_2017_only = doc_2017["fire"]["burnedFractionByYear"]["2017"]
        frac_full = doc_full["fire"]["burnedFractionByYear"]["2017"]
        max_abs_diff_ha = max(max_abs_diff_ha, abs(ha_2017_only - ha_full))
        max_abs_diff_fraction = max(max_abs_diff_fraction, abs(frac_2017_only - frac_full))

    assert max_abs_diff_ha < 1e-6, f"max abs diff in 2017 burnedHa across 86 comunas: {max_abs_diff_ha}"
    assert max_abs_diff_fraction < 1e-9, f"max abs diff in 2017 burnedFraction: {max_abs_diff_fraction}"
