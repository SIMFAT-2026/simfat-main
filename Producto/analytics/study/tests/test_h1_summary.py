"""H1 step 6: shares, intervals and the Spearman result, with known answers."""
import math

import pytest

from study import h1


def _scar(year, detected, area=300.0, edge=False, sid=None):
    return {
        "year": year, "scar_id": sid or f"{year}-x", "area_ha": area, "n_pixels": 1,
        "n_detections_in_buffer": 2 if detected else 0, "detected": detected,
        "edge_flag": edge, "truncated": edge, "comunas": ["CHL.A"], "lat": -37.0, "lon": -72.0,
    }


def _rows():
    rows = []
    # 2015: 4 scars, 3 detected; 2016: 6 scars, 3 detected; one big, one huge scar.
    rows += [_scar(2015, d, sid=f"2015-{i}") for i, d in enumerate([True, True, True, False])]
    rows += [_scar(2016, d, sid=f"2016-{i}") for i, d in enumerate([True, True, True, False, False, False])]
    rows[0]["area_ha"] = 800.0
    rows[1]["area_ha"] = 2500.0
    rows[4]["area_ha"] = 9000.0
    rows[5]["edge_flag"] = rows[5]["truncated"] = True
    return rows


def _sec(pairs):
    return [{"comunaId": c, "year": y, "burned_fraction": x, "firms_count": n} for c, y, x, n in pairs]


def test_primary_share_counts_scars_with_at_least_one_detection():
    s = h1.summarize_scars(_rows(), B=200, seed=1)
    assert s["primary"]["n_scars"] == 10
    assert s["primary"]["n_detected"] == 6
    assert s["primary"]["share"] == pytest.approx(0.6)


def test_per_year_shares_and_counts():
    s = h1.summarize_scars(_rows(), B=200, seed=1)
    by_year = {r["year"]: r for r in s["per_year"]}
    assert (by_year[2015]["n_scars"], by_year[2015]["n_detected"]) == (4, 3)
    assert by_year[2015]["share"] == pytest.approx(0.75)
    assert by_year[2016]["share"] == pytest.approx(0.5)


def test_years_without_scars_are_listed_with_no_share():
    s = h1.summarize_scars(_rows(), B=200, seed=1, years=[2014, 2015, 2016])
    by_year = {r["year"]: r for r in s["per_year"]}
    assert by_year[2014]["n_scars"] == 0 and by_year[2014]["share"] is None


def test_by_area_class_uses_the_fixed_half_open_classes():
    s = h1.summarize_scars(_rows(), B=200, seed=1)
    by_class = {r["class"]: r for r in s["by_area_class"]}
    assert by_class["250-500 ha"]["n_scars"] == 7
    assert by_class["500-1,000 ha"]["n_scars"] == 1
    assert by_class["1,000-5,000 ha"]["n_scars"] == 1
    assert by_class[">= 5,000 ha"]["n_scars"] == 1
    assert sum(r["n_scars"] for r in s["by_area_class"]) == 10
    assert by_class[">= 5,000 ha"]["share"] == pytest.approx(1.0)  # 2016-0 is the huge one, detected


def test_excluding_edge_scars_is_reported_next_to_the_primary():
    s = h1.summarize_scars(_rows(), B=200, seed=1)
    assert s["primary"]["n_edge_flag"] == 1
    ex = s["excluding_edge_flag"]
    assert ex["n_scars"] == 9 and ex["n_detected"] == 5  # the edge scar (2016-1) was detected


def test_year_clustered_interval_is_wide_when_years_differ_completely():
    # Year A: every scar detected; year B: none. Resampling YEARS gives shares in {0, .5, 1}.
    rows = [_scar(2015, True, sid=f"a{i}") for i in range(20)] + [_scar(2016, False, sid=f"b{i}") for i in range(20)]
    s = h1.summarize_scars(rows, B=500, seed=7)
    lo, hi = s["primary"]["ci_by_year"]["lower"], s["primary"]["ci_by_year"]["upper"]
    assert (lo, hi) == (0.0, 1.0)
    # The naive scar-level interval ignores that dependence and is much narrower.
    assert s["primary"]["ci_by_scar"]["upper"] - s["primary"]["ci_by_scar"]["lower"] < 0.5


def test_intervals_bracket_the_estimate_and_stay_in_unit_range():
    s = h1.summarize_scars(_rows(), B=500, seed=3)
    for ci in (s["primary"]["ci_by_year"], s["primary"]["ci_by_scar"]):
        assert 0.0 <= ci["lower"] <= s["primary"]["share"] <= ci["upper"] <= 1.0
    assert s["primary"]["ci_by_year"]["n_valid"] == 500


def test_the_same_seed_gives_the_same_intervals():
    a = h1.summarize_scars(_rows(), B=300, seed=20261027)
    b = h1.summarize_scars(_rows(), B=300, seed=20261027)
    assert a == b


def test_no_scars_at_all_gives_empty_primary_not_a_crash():
    s = h1.summarize_scars([], B=50, seed=1)
    assert s["primary"]["n_scars"] == 0 and s["primary"]["share"] is None
    assert s["primary"]["ci_by_year"] is None


def test_a_single_scar_year_has_no_interval():
    s = h1.summarize_scars([_scar(2015, True)], B=50, seed=1)
    by_year = {r["year"]: r for r in s["per_year"]}
    assert by_year[2015]["ci_lower"] is None


def test_spearman_known_value():
    # x ranks 1..5, y = [2, 1, 4, 3, 5]: rho = 1 - 6 * 4 / (5 * 24) = 0.8
    rows = _sec([("A", 2013, 1, 2), ("B", 2013, 2, 1), ("C", 2013, 3, 4), ("D", 2013, 4, 3), ("E", 2013, 5, 5)])
    s = h1.summarize_secondary(rows, B=200, seed=1)
    assert s["rho"] == pytest.approx(0.8)
    assert s["n_pairs"] == 5 and s["n_comunas"] == 5


def test_spearman_ties_use_average_ranks():
    # hand computation on average ranks: 6.25 / sqrt(7.5 * 9) = 0.76073
    rows = _sec([("A", 2013, 0, 0), ("B", 2013, 0, 0), ("C", 2013, 0, 5), ("D", 2013, 1, 5), ("E", 2013, 1, 9)])
    s = h1.summarize_secondary(rows, B=100, seed=1)
    assert s["rho"] == pytest.approx(6.25 / math.sqrt(67.5), rel=1e-9)


def test_perfect_monotone_relation_gives_a_degenerate_interval_at_one():
    rows = _sec([(f"C{i}", y, i + 0.1 * y, 10 * i + y) for i in range(12) for y in (2013, 2014)])
    s = h1.summarize_secondary(rows, B=200, seed=1)
    assert s["rho"] == pytest.approx(1.0)
    assert s["ci_lower"] == pytest.approx(1.0) and s["ci_upper"] == pytest.approx(1.0)


def test_perfect_inverse_relation_gives_minus_one():
    rows = _sec([(f"C{i}", 2013, i, 100 - i) for i in range(15)])
    assert h1.summarize_secondary(rows, B=100, seed=1)["rho"] == pytest.approx(-1.0)


def test_the_secondary_bootstrap_resamples_comunas_with_all_their_years():
    rows = _sec([(f"C{i}", y, (i * 7 + y) % 11, (i * 5 + y * 3) % 13) for i in range(20) for y in range(2013, 2018)])
    s = h1.summarize_secondary(rows, B=300, seed=20261027)
    assert s["ci_method"].startswith("percentile bootstrap, comunas resampled")
    assert s["n_valid"] == 300
    assert s["ci_lower"] < s["ci_upper"]
    assert s == h1.summarize_secondary(rows, B=300, seed=20261027)


def test_constant_burned_fraction_gives_no_correlation():
    rows = _sec([(f"C{i}", 2013, 0.0, i) for i in range(6)])
    s = h1.summarize_secondary(rows, B=50, seed=1)
    assert s["rho"] is None and s["ci_lower"] is None
