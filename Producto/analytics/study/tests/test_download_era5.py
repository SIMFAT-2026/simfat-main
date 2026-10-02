"""Unit tests for the pure-logic helpers in scripts/download_era5.py.

The script's ``main()`` does real network I/O and writes committed files, so
it is exercised manually (see the apply report), not from pytest. These
tests cover the deterministic-gzip helper and the script's fixed
configuration constants (date range, output path, attribution string).
"""
from __future__ import annotations

import gzip
from datetime import date

import download_era5


def test_gzip_deterministic_is_byte_identical_across_runs():
    payload = b"comunaId,date,tempC,rhPct,windKmh,rainMm\nCHL.6.1.1_1,2016-09-01,18.0,40.0,10.0,0.0\n"

    first = download_era5._gzip_deterministic(payload)
    second = download_era5._gzip_deterministic(payload)

    assert first == second


def test_gzip_deterministic_round_trips_and_strips_mtime():
    payload = b"hello study"

    gz_bytes = download_era5._gzip_deterministic(payload)

    assert gzip.decompress(gz_bytes) == payload
    # mtime field (bytes 4-7 of the gzip header) must be zero.
    assert gz_bytes[4:8] == b"\x00\x00\x00\x00"


def test_date_range_covers_the_full_nov_apr_sensitivity_window_for_every_season():
    # Season 2025's sensitivity window ends Apr 30, 2025 (D4 scope item 3) --
    # must be <= END_DATE, or the committed noon-input table would be
    # missing data for the final season's sensitivity window.
    from study import season_fwi

    last_season = season_fwi.SEASONS[-1]
    _, sensitivity_end = season_fwi.sensitivity_window(last_season)

    assert sensitivity_end <= download_era5.END_DATE


def test_raw_fetch_start_is_one_day_before_start_date():
    assert download_era5.RAW_FETCH_START == date(2016, 8, 31)
    assert download_era5.START_DATE == date(2016, 9, 1)


def test_output_csv_filename_encodes_the_requested_date_range():
    name = download_era5.OUTPUT_CSV.name
    assert name == f"noon_inputs_{download_era5.START_DATE.isoformat()}_{download_era5.END_DATE.isoformat()}.csv.gz"


def test_attribution_mentions_open_meteo_era5_and_cc_by_licence():
    assert "Open-Meteo" in download_era5.ATTRIBUTION
    assert "ERA5" in download_era5.ATTRIBUTION
    assert "CC BY 4.0" in download_era5.ATTRIBUTION
