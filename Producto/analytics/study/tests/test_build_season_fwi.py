"""Unit tests for the pure-logic helpers in scripts/build_season_fwi.py:
reading the committed noon_inputs csv.gz and writing data/features/season_fwi.csv.

BLINDING NOTE: no FIRMS data and no predictor-vs-target relation is read,
computed, or asserted here.
"""
from __future__ import annotations

import gzip
from datetime import date

import build_season_fwi
from study.season_fwi import SeasonFeatureRow


def _write_gz_csv(path, rows):
    header = "comunaId,date,tempC,rhPct,windKmh,rainMm\n"
    body = "".join(f"{r[0]},{r[1]},{r[2]},{r[3]},{r[4]},{r[5]}\n" for r in rows)
    with gzip.open(path, "wb") as f:
        f.write((header + body).encode("utf-8"))


def test_load_noon_inputs_groups_by_comuna_and_parses_dates(tmp_path):
    path = tmp_path / "noon_inputs_test.csv.gz"
    _write_gz_csv(
        path,
        [
            ("CHL.6.1.1_1", "2016-09-01", 18.0, 40.0, 10.0, 0.0),
            ("CHL.6.1.1_1", "2016-09-02", 19.0, 45.0, 12.0, 2.0),
            ("CHL.6.2.1_1", "2016-09-01", 15.0, 50.0, 8.0, 0.0),
        ],
    )

    weather = build_season_fwi.load_noon_inputs(path)

    assert set(weather) == {"CHL.6.1.1_1", "CHL.6.2.1_1"}
    assert weather["CHL.6.1.1_1"][date(2016, 9, 1)] == (18.0, 40.0, 10.0, 0.0)
    assert weather["CHL.6.1.1_1"][date(2016, 9, 2)] == (19.0, 45.0, 12.0, 2.0)
    assert weather["CHL.6.2.1_1"][date(2016, 9, 1)] == (15.0, 50.0, 8.0, 0.0)


def test_write_season_fwi_csv_writes_header_and_rows(tmp_path):
    out_path = tmp_path / "season_fwi.csv"
    rows = [
        SeasonFeatureRow("CHL.6.1.1_1", 2017, "standard", 0.1234, 12.34, 121),
        SeasonFeatureRow("CHL.6.1.1_1", 2017, "sensitivity", 0.2345, 23.45, 181),
    ]

    build_season_fwi.write_season_fwi_csv(rows, out_path)

    content = out_path.read_text(encoding="utf-8")
    lines = content.strip().split("\n")
    assert lines[0] == "comunaId,season,window,sdyn_mean,fwi_mean,n_days"
    assert lines[1] == "CHL.6.1.1_1,2017,standard,0.1234,12.34,121"
    assert lines[2] == "CHL.6.1.1_1,2017,sensitivity,0.2345,23.45,181"
