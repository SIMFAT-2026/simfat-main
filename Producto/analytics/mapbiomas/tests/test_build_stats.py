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


# --- land_cover_section: reference year + 5y mean, both FRACTIONS (design D1) ----
#
# sharesByClass/sharesByClassMean5y hold fractions summing to ~1.0, NOT basis
# points -- only sharesByYear.classes (not implemented in this slice) is
# documented as bp. Internally the largest-remainder bp rounding is still
# used (so the emitted fractions are exact multiples of 1/10000, reproducible
# and free of naive floating-point rounding drift), then divided by 10000
# before being returned.


def _by_year():
    return {
        2020: {59: 60.0, 60: 30.0, 15: 10.0},
        2021: {59: 55.0, 60: 35.0, 15: 10.0},
        2022: {59: 50.0, 60: 40.0, 15: 10.0},
        2023: {59: 45.0, 60: 45.0, 15: 10.0},
        2024: {59: 40.0, 60: 50.0, 15: 10.0},
    }


def test_land_cover_section_reference_year_shares_sum_to_one():
    section = build_stats.land_cover_section(_by_year(), reference_year=2024, mean_years=range(2020, 2025))
    assert sum(section["sharesByClass"].values()) == pytest.approx(1.0, abs=1e-9)
    assert section["referenceYear"] == 2024


def test_land_cover_section_reference_shares_match_ratio_within_1e4():
    section = build_stats.land_cover_section(_by_year(), reference_year=2024, mean_years=range(2020, 2025))
    # 2024: 59=40/100, 60=50/100, 15=10/100
    assert section["sharesByClass"][59] == pytest.approx(0.40, abs=1e-4)
    assert section["sharesByClass"][60] == pytest.approx(0.50, abs=1e-4)


def test_land_cover_section_mean_5y_sums_to_one_and_is_the_average():
    section = build_stats.land_cover_section(_by_year(), reference_year=2024, mean_years=range(2020, 2025))
    assert sum(section["sharesByClassMean5y"].values()) == pytest.approx(1.0, abs=1e-9)
    # mean of 59 shares across 2020..2024 (each year sums to 100 ha):
    # 60,55,50,45,40 -> mean 50.0/100 = 0.50
    assert section["sharesByClassMean5y"][59] == pytest.approx(0.50, abs=1e-4)


def test_land_cover_section_different_reference_year_is_reflected():
    section_2020 = build_stats.land_cover_section(_by_year(), reference_year=2020, mean_years=range(2020, 2025))
    assert section_2020["sharesByClass"][59] == pytest.approx(0.60, abs=1e-4)


def test_land_cover_section_fractions_are_exact_bp_over_10000():
    # The fraction shape must never drift from the largest-remainder bp
    # rounding: an independently computed to_basis_points() call on the same
    # hectares must equal sharesByClass * 10000 exactly (not just "close").
    by_year = _by_year()
    section = build_stats.land_cover_section(by_year, reference_year=2024, mean_years=range(2020, 2025))
    expected_bp = build_stats.to_basis_points(by_year[2024])
    assert sum(expected_bp.values()) == 10000
    for code, bp in expected_bp.items():
        assert section["sharesByClass"][code] == bp / build_stats.BP_TOTAL


# --- shares_by_year_bp: per-year class-major sparse bp series (design D1, S1b4) ---


def test_shares_by_year_bp_class_major_sparse_int_arrays():
    by_year = {
        1999: {59: 90.0, 60: 10.0},
        2000: {59: 80.0, 60: 20.0},
    }
    series = build_stats.shares_by_year_bp(by_year, first_year=1999, last_year=2000)
    assert series["firstYear"] == 1999
    assert series["lastYear"] == 2000
    assert series["unit"] == "bp"
    assert series["classes"][59] == [9000, 8000]
    assert series["classes"][60] == [1000, 2000]


def test_shares_by_year_bp_every_year_sums_to_exactly_10000():
    by_year = {
        1999: {59: 33.333, 60: 33.333, 15: 33.334},
        2000: {59: 100.0},
    }
    series = build_stats.shares_by_year_bp(by_year, first_year=1999, last_year=2000)
    for i in range(2):
        year_total = sum(values[i] for values in series["classes"].values())
        assert year_total == 10000


def test_shares_by_year_bp_class_absent_from_a_year_is_zero_not_missing():
    # class 15 only appears in 1999; the 2000 slot must still exist, as 0,
    # not be missing from the array (fixed-length arrays, index = year offset).
    by_year = {
        1999: {59: 90.0, 15: 10.0},
        2000: {59: 100.0},
    }
    series = build_stats.shares_by_year_bp(by_year, first_year=1999, last_year=2000)
    assert series["classes"][15] == [1000, 0]


def test_shares_by_year_bp_class_never_present_in_any_year_is_absent_sparse():
    by_year = {1999: {59: 100.0}, 2000: {59: 100.0}}
    series = build_stats.shares_by_year_bp(by_year, first_year=1999, last_year=2000)
    assert 15 not in series["classes"]


def test_shares_by_year_bp_year_with_no_data_at_all_is_all_zero_that_year():
    # A year missing entirely from by_year (e.g. gap in the source) must not
    # crash -- every known class gets a 0 at that index, and the year still
    # sums to 0 (not silently skipped -- caller-level gates decide what a
    # zero-total year means, this function just encodes what it is given).
    by_year = {1999: {59: 100.0}}
    series = build_stats.shares_by_year_bp(by_year, first_year=1999, last_year=2000)
    assert series["classes"][59] == [10000, 0]


def test_shares_by_year_bp_real_length_matches_1999_2024_range():
    by_year = {year: {59: 100.0} for year in range(1999, 2025)}
    series = build_stats.shares_by_year_bp(by_year, first_year=1999, last_year=2024)
    assert len(series["classes"][59]) == 26


# --- validate_comuna_year_shares: per comuna-YEAR hard gate (design D2, MCS-3e) ---
#
# The gate compares each year's total classified hectares against the SAME
# comuna's reference-year (2024) total: a real comuna's classified area is
# physically stable year to year (verified against the real xlsx, S1b4 --
# every one of the 86 target comunas' yearly total is stable to within a
# fraction of a hectare across 1999-2024), so a year whose total drifts by
# more than 0.5% from the reference year is a genuine data problem (e.g. a
# missing row/class for that year), not a rounding artifact.


def test_validate_comuna_year_shares_passes_when_totals_are_stable():
    by_year = {2020: {59: 60.0, 60: 40.0}, 2024: {59: 55.0, 60: 45.0}}  # both sum to 100.0
    build_stats.validate_comuna_year_shares(by_year, comuna_id="CHL.6.1.1_1", reference_year=2024)


def test_validate_comuna_year_shares_fails_loudly_and_names_comuna_and_year():
    by_year = {2024: {59: 60.0, 60: 40.0}, 2020: {59: 60.0, 60: 30.0}}  # 2020 totals 90 vs ref 100
    with pytest.raises(build_stats.ShareSumError, match=r"CHL\.6\.1\.1_1.*2020"):
        build_stats.validate_comuna_year_shares(by_year, comuna_id="CHL.6.1.1_1", reference_year=2024)


def test_validate_comuna_year_shares_tolerance_is_half_a_percentage_point():
    # 99.4/100 = 0.994 -> off by 0.006, just OUTSIDE the 0.005 tolerance -> fails.
    with pytest.raises(build_stats.ShareSumError):
        build_stats.validate_comuna_year_shares(
            {2024: {59: 100.0}, 2020: {59: 99.4}}, comuna_id="X", reference_year=2024
        )
    # 99.6/100 = 0.996 -> off by 0.004, INSIDE tolerance -> passes.
    build_stats.validate_comuna_year_shares(
        {2024: {59: 100.0}, 2020: {59: 99.6}}, comuna_id="X", reference_year=2024
    )


def test_validate_comuna_year_shares_checks_every_year_independently():
    # 2021 is fine, 2020 is broken -- the error must name 2020, not 2021.
    by_year = {2024: {59: 100.0}, 2021: {59: 100.0}, 2020: {59: 50.0}}
    with pytest.raises(build_stats.ShareSumError, match="2020"):
        build_stats.validate_comuna_year_shares(by_year, comuna_id="X", reference_year=2024)


# --- land_cover_section: sharesByYear integration (design D1 rev2, S1b4) ----


def test_land_cover_section_includes_shares_by_year():
    section = build_stats.land_cover_section(
        _by_year(), reference_year=2024, mean_years=range(2020, 2025), first_year=2020, last_year=2024
    )
    assert "sharesByYear" in section
    assert section["sharesByYear"]["firstYear"] == 2020
    assert section["sharesByYear"]["lastYear"] == 2024


def test_land_cover_section_reference_year_consistency_with_shares_by_year():
    """sharesByClass[code] must equal sharesByYear.classes[code][idx(2024)]/10000
    within 1e-4 (design D1/MCS-1d) -- the two representations of the SAME
    reference year must never drift apart."""
    section = build_stats.land_cover_section(
        _by_year(), reference_year=2024, mean_years=range(2020, 2025), first_year=2020, last_year=2024
    )
    idx_2024 = 2024 - section["sharesByYear"]["firstYear"]
    for code, fraction in section["sharesByClass"].items():
        bp_2024 = section["sharesByYear"]["classes"][code][idx_2024]
        assert fraction == pytest.approx(bp_2024 / build_stats.BP_TOTAL, abs=1e-4)


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
    assert sum(section["sharesByClass"].values()) == pytest.approx(1.0, abs=1e-9)
    assert section["sharesByClass"][60] == pytest.approx(0.60, abs=1e-4)


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


# --- percentile_rank_burned_fraction: design D3's burnedFractionPct ---------
#
# Empirical percentile rank of a burned-fraction value across all comunas,
# frozen with dataVersion (design D1/D3): ties take the mean rank EXCEPT
# exact zeros, which are pinned to 0.0 (the distribution is zero-inflated);
# comunas excluded from the rank (no usable fire data) map to None and do
# not count toward N.


def test_percentile_rank_single_nonzero_max_is_exactly_one():
    result = build_stats.percentile_rank_burned_fraction({"a": 0.1, "b": 0.5})
    assert result["b"] == pytest.approx(1.0)
    assert result["a"] == pytest.approx(0.5)  # unique smaller value: rank 1/2


def test_percentile_rank_exact_zeros_are_pinned_to_zero_not_tie_averaged():
    result = build_stats.percentile_rank_burned_fraction({"a": 0.0, "b": 0.0, "c": 0.5})
    assert result["a"] == 0.0
    assert result["b"] == 0.0
    assert result["c"] == pytest.approx(1.0)  # unique max, rank 3/3


def test_percentile_rank_all_zero_are_all_pinned_to_zero():
    result = build_stats.percentile_rank_burned_fraction({"a": 0.0, "b": 0.0, "c": 0.0})
    assert result == {"a": 0.0, "b": 0.0, "c": 0.0}


def test_percentile_rank_nonzero_ties_share_the_mean_rank():
    # sorted [0.2, 0.2, 0.6] -> ranks 1,2 average to 1.5 for the tied pair.
    result = build_stats.percentile_rank_burned_fraction({"a": 0.2, "b": 0.2, "c": 0.6})
    assert result["a"] == pytest.approx(0.5)  # 1.5 / 3
    assert result["b"] == pytest.approx(0.5)
    assert result["c"] == pytest.approx(1.0)  # 3 / 3


def test_percentile_rank_none_values_are_excluded_and_do_not_count_toward_n():
    result = build_stats.percentile_rank_burned_fraction({"a": 0.5, "b": None, "c": 0.5})
    assert result["b"] is None
    # a and c tie as the only two ranked comunas (n=2, b excluded): 1.5/2.
    assert result["a"] == pytest.approx(0.75)
    assert result["c"] == pytest.approx(0.75)


def test_percentile_rank_everything_excluded_returns_all_none():
    result = build_stats.percentile_rank_burned_fraction({"a": None, "b": None})
    assert result == {"a": None, "b": None}


# --- add_burned_fraction_pct: wires the rank into the fire section ---------


def test_add_burned_fraction_pct_mutates_every_docs_fire_section():
    docs = [
        {"comunaId": "A", "fire": {"available": True, "burnedFractionByYear": {"2017": 0.0}}},
        {"comunaId": "B", "fire": {"available": True, "burnedFractionByYear": {"2017": 0.5}}},
        {"comunaId": "C", "fire": {"available": False, "burnedFractionByYear": {"2017": 0.9}}},
    ]

    build_stats.add_burned_fraction_pct(docs)

    assert docs[0]["fire"]["burnedFractionPct"] == 0.0
    assert docs[1]["fire"]["burnedFractionPct"] == pytest.approx(1.0)
    assert docs[2]["fire"]["burnedFractionPct"] is None
