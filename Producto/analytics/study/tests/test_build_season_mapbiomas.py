"""Wiring and gate checks for scripts/build_season_mapbiomas.py: the pure
feature computation itself is covered by tests/test_season_mapbiomas.py.

BLINDING NOTE: no FIRMS data and no predictor-vs-target relation is read,
computed, or asserted here.
"""
from __future__ import annotations

import build_season_mapbiomas
from study.season_mapbiomas import SeasonMapbiomasRow


def _row(comuna_id, season, **overrides):
    base = dict(
        comuna_id=comuna_id, season=season, fuel=0.5, burned_union=0.1, burned_pct=0.2,
        burned_log=0.2, frequency_mean=1.0, recurrence_norm=0.2, history=0.3, s_mapbiomas=0.4,
        history_log=0.3, s_mapbiomas_log=0.4,
    )
    base.update(overrides)
    return SeasonMapbiomasRow(**base)


def test_paths_resolve_to_real_repo_files():
    assert build_season_mapbiomas.GEOJSON_DIR.is_dir()
    assert build_season_mapbiomas.SEED_PATH.is_file()
    assert build_season_mapbiomas.FUEL_WEIGHTS_PATH.is_file()
    assert build_season_mapbiomas.MANIFEST_PATH.is_file()


def test_validate_gates_passes_on_monotonic_bounded_rows():
    rows = [_row("A", 2017, burned_union=0.1), _row("A", 2018, burned_union=0.2)]
    build_season_mapbiomas.validate_gates(rows)  # must not raise


def test_validate_gates_raises_on_decreasing_union():
    rows = [_row("A", 2017, burned_union=0.3), _row("A", 2018, burned_union=0.1)]
    try:
        build_season_mapbiomas.validate_gates(rows)
        assert False, "expected AssertionError"
    except AssertionError as exc:
        assert "decreased" in str(exc)


def test_validate_gates_raises_on_out_of_bounds_value():
    rows = [_row("A", 2017, fuel=1.5)]
    try:
        build_season_mapbiomas.validate_gates(rows)
        assert False, "expected AssertionError"
    except AssertionError as exc:
        assert "fuel" in str(exc)


def test_write_csv_writes_header_and_rows(tmp_path):
    rows = [_row("A", 2017)]
    out = tmp_path / "season_mapbiomas.csv"
    build_season_mapbiomas.write_csv(rows, out)
    lines = out.read_text(encoding="utf-8").strip().split("\n")
    assert lines[0] == ",".join(build_season_mapbiomas.CSV_HEADER)
    assert lines[1].startswith("A,2017,0.5,0.1,0.2,0.2,1.0,0.2,0.3,0.4,0.3,0.4")
