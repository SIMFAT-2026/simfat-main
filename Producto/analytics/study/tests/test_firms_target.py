"""firms_target.py: FIRMS target construction (PREREGISTRATION.md Section 6).

BLINDING RULE: this module builds the FIRMS TARGET ONLY. It never computes
any quantity relating a predictor (FWI, S_dynamic, S_mapbiomas) to FIRMS --
not here, not in these tests, not in build_firms_target.py's printed output.
"""
from __future__ import annotations

import csv
import zipfile
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

import pytest

from study import firms_target


def _rect(west, south, east, north):
    return {
        "type": "Polygon",
        "coordinates": [[[west, south], [east, south], [east, north], [west, north], [west, south]]],
    }


# ---- UTC -> America/Santiago local date conversion ------------------------


def test_parse_acq_datetime_utc_zero_pads_time():
    dt = firms_target.parse_acq_datetime_utc("2017-12-15", "640")
    assert dt == datetime(2017, 12, 15, 6, 40, tzinfo=timezone.utc)


def test_parse_acq_datetime_utc_handles_already_padded_time():
    dt = firms_target.parse_acq_datetime_utc("2017-12-15", "0000")
    assert dt == datetime(2017, 12, 15, 0, 0, tzinfo=timezone.utc)


def test_to_local_date_shifts_back_one_day_near_midnight_in_summer_dst():
    # Mid-December is deep in Chile's DST window (UTC-3): 02:30 UTC -> 23:30
    # the previous local day.
    dt_utc = datetime(2024, 12, 15, 2, 30, tzinfo=timezone.utc)
    assert firms_target.to_local_date(dt_utc) == date(2024, 12, 14)


def test_to_local_date_uses_standard_offset_in_late_april():
    # Late April is after Chile's DST-end transition (first Sunday of
    # April): offset is UTC-4, so 03:30 UTC -> 23:30 the previous local day.
    dt_utc = datetime(2024, 4, 20, 3, 30, tzinfo=timezone.utc)
    assert firms_target.to_local_date(dt_utc) == date(2024, 4, 19)


def test_is_dst_chile_true_in_december_false_in_august():
    assert firms_target.is_dst_chile(date(2024, 12, 15)) is True
    assert firms_target.is_dst_chile(date(2024, 8, 15)) is False


def test_is_dst_chile_false_on_and_after_first_sunday_of_april():
    first_sunday = firms_target._nth_weekday_of_month(2024, 4, 6, 1)
    assert firms_target.is_dst_chile(first_sunday) is False
    assert firms_target.is_dst_chile(first_sunday - timedelta(days=1)) is True


def test_is_dst_chile_true_on_and_after_first_sunday_of_september():
    first_sunday = firms_target._nth_weekday_of_month(2024, 9, 6, 1)
    assert firms_target.is_dst_chile(first_sunday) is True
    assert firms_target.is_dst_chile(first_sunday - timedelta(days=1)) is False


# ---- point-in-polygon comuna assignment ------------------------------------


def test_point_in_geometry_checks_every_multipolygon_part():
    small = _rect(0, 0, 1, 1)["coordinates"]
    big = _rect(20, 20, 30, 30)["coordinates"]
    geometry = {"type": "MultiPolygon", "coordinates": [small, big]}
    assert firms_target.point_in_geometry((0.5, 0.5), geometry) is True
    assert firms_target.point_in_geometry((25, 25), geometry) is True
    assert firms_target.point_in_geometry((50, 50), geometry) is False


def test_point_in_geometry_excludes_holes():
    outer = _rect(0, 0, 10, 10)["coordinates"][0]
    hole = _rect(4, 4, 6, 6)["coordinates"][0]
    geometry = {"type": "Polygon", "coordinates": [outer, hole]}
    assert firms_target.point_in_geometry((5, 5), geometry) is False  # inside the hole
    assert firms_target.point_in_geometry((1, 1), geometry) is True


def test_assign_comuna_returns_none_when_outside_every_polygon():
    polygons = {"A": _rect(0, 0, 1, 1), "B": _rect(10, 10, 11, 11)}
    assert firms_target.assign_comuna(0.5, 0.5, polygons) == "A"
    assert firms_target.assign_comuna(50, 50, polygons) is None


# ---- reading the real FIRMS archive (structural only, no target-vs-predictor) ---


REPO_ROOT = Path(__file__).resolve().parents[4]
FIRMS_ZIP = REPO_ROOT / "Producto" / "analytics" / "study" / "data" / "firms" / "DL_FIRE_SV-C2_809648.zip"


def test_iter_firms_rows_reads_real_archive_header_fields():
    rows = firms_target.iter_firms_rows(FIRMS_ZIP)
    first = next(rows)
    assert set(["latitude", "longitude", "acq_date", "acq_time", "confidence", "type"]).issubset(first)


# ---- counting / filtering ---------------------------------------------------


def _det(comuna_id, local_date, type_code, confidence):
    return firms_target.FirmsDetection(
        comuna_id=comuna_id, local_date=local_date, type_code=type_code, confidence=confidence
    )


def test_build_counts_counts_within_window_and_respects_type0_filter():
    detections = [
        _det("A", date(2016, 12, 10), 0, "n"),  # inside primary window, type0 ok
        _det("A", date(2016, 11, 15), 0, "n"),  # inside sensitivity only
        _det("A", date(2016, 12, 11), 2, "n"),  # wrong type -> excluded from type0
        _det("A", date(2016, 12, 12), 0, "l"),  # low confidence -> excluded from both filters
    ]
    rows = firms_target.build_counts(detections, ["A"], seasons=(2017,))
    by_key = {(r["window"], r["filter"]): r for r in rows}

    assert by_key[("primary", "type0")]["count"] == 1
    assert by_key[("primary", "notype")]["count"] == 2
    assert by_key[("sensitivity", "type0")]["count"] == 2
    assert by_key[("sensitivity", "notype")]["count"] == 3


def test_build_counts_includes_zero_rows_for_every_comuna_season_window_filter():
    rows = firms_target.build_counts([], ["A", "B"], seasons=(2017, 2018))
    assert len(rows) == 2 * 2 * 2 * 2  # comunas x seasons x windows x filters
    assert all(r["count"] == 0 for r in rows)
    assert all(r["y_n1"] == 0 and r["y_n3"] == 0 and r["y_n5"] == 0 for r in rows)


def test_build_counts_y_thresholds_match_count():
    detections = [_det("A", date(2016, 12, i), 0, "n") for i in range(10, 15)]  # 5 detections
    rows = firms_target.build_counts(detections, ["A"], seasons=(2017,))
    row = next(r for r in rows if r["window"] == "primary" and r["filter"] == "type0")
    assert row["count"] == 5
    assert row["y_n1"] == 1 and row["y_n3"] == 1 and row["y_n5"] == 1


# ---- gates -------------------------------------------------------------------


def test_validate_gates_passes_on_consistent_rows():
    rows = firms_target.build_counts([], ["A"], seasons=(2017,))
    firms_target.validate_gates(rows, ["A"], seasons=(2017,))  # must not raise


def test_validate_gates_raises_on_missing_row():
    rows = firms_target.build_counts([], ["A"], seasons=(2017,))
    rows.pop()
    with pytest.raises(firms_target.FirmsTargetGateError, match="missing row"):
        firms_target.validate_gates(rows, ["A"], seasons=(2017,))


def test_validate_gates_raises_when_sensitivity_less_than_primary():
    rows = firms_target.build_counts([], ["A"], seasons=(2017,))
    for r in rows:
        if r["window"] == "sensitivity" and r["filter"] == "type0":
            r["count"] = -1  # force a violation (sensitivity < primary's 0)
    with pytest.raises(firms_target.FirmsTargetGateError, match="sensitivity"):
        firms_target.validate_gates(rows, ["A"], seasons=(2017,))


def test_validate_gates_raises_when_notype_less_than_type0():
    rows = firms_target.build_counts([], ["A"], seasons=(2017,))
    for r in rows:
        if r["filter"] == "type0" and r["window"] == "primary":
            r["count"] = 5
        if r["filter"] == "type0" and r["window"] == "sensitivity":
            r["count"] = 5  # keep sensitivity >= primary satisfied for type0
    with pytest.raises(firms_target.FirmsTargetGateError, match="notype"):
        firms_target.validate_gates(rows, ["A"], seasons=(2017,))


# ---- descriptive summary (no predictor involved) ----------------------------


def test_filter_summary_counts_dropped_by_confidence_and_type():
    detections = [
        _det("A", date(2016, 12, 10), 0, "n"),  # passes both
        _det("A", date(2016, 12, 10), 2, "h"),  # confidence ok, type != 0
        _det("A", date(2016, 12, 10), 0, "l"),  # confidence fails
    ]
    summary = firms_target.filter_summary(detections)
    assert summary["total"] == 3
    assert summary["dropped_by_confidence"] == 1
    assert summary["dropped_by_type_among_confidence_passing"] == 1
    assert summary["final_type0_count"] == 1


def test_prevalence_for_season_is_mean_of_y_n3_over_comunas():
    detections = [_det("A", date(2016, 12, d), 0, "n") for d in range(10, 13)]  # 3 detections, A positive
    rows = firms_target.build_counts(detections, ["A", "B"], seasons=(2017,))
    prevalence = firms_target.prevalence_for_season(rows, 2017, window="primary", filter_name="type0", n=3)
    assert prevalence == pytest.approx(0.5)  # A=1, B=0 -> mean 0.5


# ---- CSV writer --------------------------------------------------------------


def test_write_csv_writes_header_and_rows(tmp_path):
    rows = firms_target.build_counts([], ["A"], seasons=(2017,))
    out = tmp_path / "firms_target.csv"
    firms_target.write_csv(out, rows)
    with open(out, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        assert reader.fieldnames == [
            "comunaId", "season", "window", "filter", "count", "y_n1", "y_n3", "y_n5",
        ]
        written_rows = list(reader)
    assert len(written_rows) == len(rows)
