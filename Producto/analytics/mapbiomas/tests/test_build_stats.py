"""Synthetic-fixture tests for the S1b document/report assembly.

These tests never touch the network or a real raster; they combine a tiny
synthetic LULC COVERAGE workbook (same pattern as test_lulc.py) with
synthetic fire-year stats to prove the ASSEMBLY logic -- basis-point
rounding, document shape, CSV/JSONL writers -- independently of any real
download. The real-raster path is exercised separately in
test_build_stats_real.py.
"""
from __future__ import annotations

import csv
import json

import pytest

from mb_pipeline import build_stats, lulc


# --- to_basis_points: largest-remainder rounding, always sums to 10000 ------


def test_to_basis_points_sums_to_exactly_10000():
    shares = {1: 33.333, 2: 33.333, 3: 33.334}
    bp = build_stats.to_basis_points(shares)
    assert sum(bp.values()) == 10000


def test_to_basis_points_matches_ratios_within_tolerance():
    shares = {1: 75.0, 2: 25.0}
    bp = build_stats.to_basis_points(shares)
    assert bp[1] == pytest.approx(7500, abs=1)
    assert bp[2] == pytest.approx(2500, abs=1)
    assert sum(bp.values()) == 10000


def test_to_basis_points_empty_input_is_empty_not_an_error():
    assert build_stats.to_basis_points({}) == {}


def test_to_basis_points_all_zero_hectares_is_empty():
    assert build_stats.to_basis_points({1: 0.0, 2: 0.0}) == {}


def test_to_basis_points_largest_remainder_breaks_ties_by_class_code():
    # 3 classes splitting 100 ha into thirds: floor(3333.33)*3 = 9999, one
    # class must get the extra bp. Largest remainder (all equal here) is
    # broken by ascending class code, so it is deterministic.
    bp = build_stats.to_basis_points({1: 1.0, 2: 1.0, 3: 1.0})
    assert sum(bp.values()) == 10000
    assert bp[1] == 3334  # ascending class code wins the extra bp on an exact tie


# --- land_cover_section: reference year + 5y mean, both in bp --------------


def _by_year():
    return {
        2020: {59: 60.0, 60: 30.0, 15: 10.0},
        2021: {59: 55.0, 60: 35.0, 15: 10.0},
        2022: {59: 50.0, 60: 40.0, 15: 10.0},
        2023: {59: 45.0, 60: 45.0, 15: 10.0},
        2024: {59: 40.0, 60: 50.0, 15: 10.0},
    }


def test_land_cover_section_reference_year_shares_sum_to_10000():
    section = build_stats.land_cover_section(_by_year(), reference_year=2024, mean_years=range(2020, 2025))
    assert sum(section["sharesByClass"].values()) == 10000
    assert section["referenceYear"] == 2024


def test_land_cover_section_reference_shares_match_ratio_within_1e4():
    section = build_stats.land_cover_section(_by_year(), reference_year=2024, mean_years=range(2020, 2025))
    # 2024: 59=40/100, 60=50/100, 15=10/100
    assert section["sharesByClass"][59] / 10000 == pytest.approx(0.40, abs=1e-4)
    assert section["sharesByClass"][60] / 10000 == pytest.approx(0.50, abs=1e-4)


def test_land_cover_section_mean_5y_sums_to_10000_and_is_the_average():
    section = build_stats.land_cover_section(_by_year(), reference_year=2024, mean_years=range(2020, 2025))
    assert sum(section["sharesByClassMean5y"].values()) == 10000
    # mean of 59 shares across 2020..2024 (each year sums to 100 ha):
    # 60,55,50,45,40 -> mean 50.0/100 = 0.50
    assert section["sharesByClassMean5y"][59] / 10000 == pytest.approx(0.50, abs=1e-4)


def test_land_cover_section_different_reference_year_is_reflected():
    section_2020 = build_stats.land_cover_section(_by_year(), reference_year=2020, mean_years=range(2020, 2025))
    assert section_2020["sharesByClass"][59] / 10000 == pytest.approx(0.60, abs=1e-4)


# --- build_comuna_document: assembles landCover + fire + provenance --------


def test_build_comuna_document_shape():
    land_cover = {"referenceYear": 2024, "sharesByClass": {59: 10000}, "sharesByClassMean5y": {59: 10000}}
    fire = {"available": True, "coverageFraction": 0.97, "burnedHaByYear": {"2017": 5.0}}
    doc = build_stats.build_comuna_document(
        "CHL.6.1.1_1",
        land_cover=land_cover,
        fire=fire,
        provenance={"sources": ["manifest-entry"]},
        computed_at="2026-09-21T00:00:00+00:00",
    )
    assert doc["comunaId"] == "CHL.6.1.1_1"
    assert doc["landCover"] is land_cover
    assert doc["fire"] is fire
    assert doc["provenance"] == {"sources": ["manifest-entry"]}
    assert doc["computedAt"] == "2026-09-21T00:00:00+00:00"


def test_build_comuna_document_land_cover_can_be_none_with_a_reason():
    doc = build_stats.build_comuna_document(
        "CHL.6.1.1_1",
        land_cover=None,
        land_cover_reason="LULC xlsx not processed in this slice",
        fire={"available": False},
        provenance={},
        computed_at="2026-09-21T00:00:00+00:00",
    )
    assert doc["landCover"] is None
    assert doc["landCoverReason"] == "LULC xlsx not processed in this slice"


def test_build_comuna_document_omits_partial_flag_by_default():
    doc = build_stats.build_comuna_document(
        "CHL.6.1.1_1",
        land_cover=None,
        land_cover_reason="LULC xlsx not processed in this slice",
        fire={"available": False},
        provenance={},
        computed_at="2026-09-21T00:00:00+00:00",
    )
    assert "partial" not in doc


def test_build_comuna_document_can_be_marked_partial():
    doc = build_stats.build_comuna_document(
        "CHL.6.1.1_1",
        land_cover=None,
        land_cover_reason="fire-only 2017 partial seed",
        fire={"available": False},
        provenance={},
        computed_at="2026-09-21T00:00:00+00:00",
        partial=True,
    )
    assert doc["partial"] is True


# --- combining a REAL join_coverage() output (still synthetic xlsx) --------


def test_land_cover_section_from_a_real_join_coverage_output(make_coverage_xlsx):
    xlsx_path = make_coverage_xlsx(
        rows=[
            {"territory_level_2": "Biobío", "territory_level_4": "Arauco", "class": 59, "years": {1999: 10.0, 2024: 40.0}},
            {"territory_level_2": "Biobío", "territory_level_4": "Arauco", "class": 60, "years": {1999: 90.0, 2024: 60.0}},
        ],
        year_columns=("y1999", "y2024"),
    )
    rows = lulc.read_coverage_sheet(xlsx_path)
    index = {("Biobío", lulc.normalize_name("Arauco")): "CHL.6.1.1_1"}
    joined = lulc.join_coverage(rows, index, expected_comuna_ids=["CHL.6.1.1_1"])
    section = build_stats.land_cover_section(joined["CHL.6.1.1_1"], reference_year=2024, mean_years=[2024])
    assert sum(section["sharesByClass"].values()) == 10000
    assert section["sharesByClass"][60] / 10000 == pytest.approx(0.60, abs=1e-4)


# --- CSV coverage report (the Biobio gate artifact) -------------------------


def test_write_coverage_report_writes_a_row_per_comuna(tmp_path):
    rows = [
        {"comunaId": "CHL.6.1.1_1", "coverageFraction": 0.97, "available": True, "years": "2017"},
        {"comunaId": "CHL.6.1.2_1", "coverageFraction": 0.40, "available": False, "years": "2017"},
    ]
    path = tmp_path / "coverage_report.csv"
    build_stats.write_coverage_report(path, rows)
    with open(path, newline="", encoding="utf-8") as f:
        reader = list(csv.DictReader(f))
    assert len(reader) == 2
    assert reader[0]["comunaId"] == "CHL.6.1.1_1"
    assert reader[1]["available"] == "False"


# --- JSONL seed writer: one document per line -------------------------------


def test_write_seed_jsonl_one_document_per_line(tmp_path):
    docs = [
        {"comunaId": "A", "fire": {"available": True}},
        {"comunaId": "B", "fire": {"available": False}},
    ]
    path = tmp_path / "seed.jsonl"
    build_stats.write_seed_jsonl(path, docs)
    lines = path.read_text(encoding="utf-8").strip("\n").split("\n")
    assert len(lines) == 2
    assert json.loads(lines[0])["comunaId"] == "A"
    assert json.loads(lines[1])["comunaId"] == "B"
