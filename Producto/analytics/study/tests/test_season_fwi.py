"""season_fwi.py: per-season FWI chain (Sep 1 of s-1 startup) and the
S_dynamic mean aggregation over the standard and sensitivity windows
(PREREGISTRATION.md Section 8.1).

BLINDING NOTE: these tests only exercise FWI/S_dynamic aggregation over
synthetic weather. No FIRMS data, no predictor-vs-target relation, is
computed or asserted anywhere in this module or its tests.
"""
from __future__ import annotations

from datetime import date, timedelta

import pytest

from study import fwi, season_fwi


def _constant_weather(start: date, end: date, *, temp=18.0, rh=40.0, wind=10.0, rain=0.0):
    weather = {}
    d = start
    while d <= end:
        weather[d] = (temp, rh, wind, rain)
        d += timedelta(days=1)
    return weather


# ---- window boundaries ------------------------------------------------


def test_spinup_start_is_sep_1_of_s_minus_1():
    assert season_fwi.spinup_start(2017) == date(2016, 9, 1)
    assert season_fwi.spinup_start(2025) == date(2024, 9, 1)


def test_standard_window_is_dec_1_to_mar_31():
    assert season_fwi.standard_window(2017) == (date(2016, 12, 1), date(2017, 3, 31))


def test_sensitivity_window_is_nov_1_to_apr_30():
    assert season_fwi.sensitivity_window(2017) == (date(2016, 11, 1), date(2017, 4, 30))


def test_seasons_constant_has_the_9_pre_registered_seasons():
    assert season_fwi.SEASONS == tuple(range(2017, 2026))
    assert len(season_fwi.SEASONS) == 9


# ---- S_dynamic normalization (ComunaRiskServiceImpl parity) -----------


def test_s_dynamic_matches_w_fwi_std_times_clamped_fwi_over_50():
    assert season_fwi.s_dynamic(0.0) == 0.0
    assert season_fwi.s_dynamic(25.0) == pytest.approx(0.52 * 0.5)
    assert season_fwi.s_dynamic(50.0) == pytest.approx(0.52 * 1.0)
    # Clamped above FWI_MAX: must not exceed W_FWI_STD.
    assert season_fwi.s_dynamic(999.0) == pytest.approx(0.52)
    # Clamped below 0 (a defensive case; fwi should never be negative in
    # practice, but the production normalize() clamps symmetrically).
    assert season_fwi.s_dynamic(-5.0) == 0.0


# ---- chain execution ----------------------------------------------------


def test_run_chain_starts_from_published_startup_values():
    start = date(2016, 9, 1)
    end = date(2016, 9, 1)
    weather = _constant_weather(start, end)

    outputs = season_fwi.run_chain(weather, start, end)

    startup = fwi.FwiState(season_fwi.STARTUP_FFMC, season_fwi.STARTUP_DMC, season_fwi.STARTUP_DC)
    expected = fwi.advance(startup, fwi.FwiInputs(18.0, 40.0, 10.0, 0.0, 9))
    assert outputs[start] == expected


def test_run_chain_carries_state_day_to_day():
    start = date(2016, 9, 1)
    end = date(2016, 9, 3)
    weather = _constant_weather(start, end)

    outputs = season_fwi.run_chain(weather, start, end)

    assert len(outputs) == 3
    startup = fwi.FwiState(season_fwi.STARTUP_FFMC, season_fwi.STARTUP_DMC, season_fwi.STARTUP_DC)
    day1_inputs = fwi.FwiInputs(18.0, 40.0, 10.0, 0.0, 9)
    day1 = fwi.advance(startup, day1_inputs)
    day2 = fwi.advance(fwi.FwiState(day1.ffmc, day1.dmc, day1.dc), day1_inputs)
    assert outputs[start] == day1
    assert outputs[start + timedelta(days=1)] == day2


def test_run_chain_raises_on_missing_day():
    start = date(2016, 9, 1)
    end = date(2016, 9, 3)
    weather = _constant_weather(start, date(2016, 9, 2))  # day 3 missing

    with pytest.raises(season_fwi.MissingWeatherDayError, match="2016-09-03"):
        season_fwi.run_chain(weather, start, end, comuna_id="CHL.6.1.1_1")


# ---- feature aggregation ------------------------------------------------


def test_build_season_features_returns_standard_and_sensitivity_rows():
    season = 2017
    start = season_fwi.spinup_start(season)
    end = season_fwi.sensitivity_window(season)[1]
    weather = _constant_weather(start, end)

    rows = season_fwi.build_season_features("CHL.6.1.1_1", weather, season)

    assert len(rows) == 2
    by_window = {r.window: r for r in rows}
    assert set(by_window) == {"standard", "sensitivity"}

    standard = by_window["standard"]
    assert standard.comuna_id == "CHL.6.1.1_1"
    assert standard.season == 2017
    assert standard.n_days == (date(2017, 3, 31) - date(2016, 12, 1)).days + 1
    assert 0.0 <= standard.sdyn_mean <= 0.52
    assert standard.fwi_mean >= 0.0

    sensitivity = by_window["sensitivity"]
    assert sensitivity.n_days == (date(2017, 4, 30) - date(2016, 11, 1)).days + 1
    # The sensitivity window strictly contains the standard window, so it has
    # more days under constant weather.
    assert sensitivity.n_days > standard.n_days


def test_build_season_features_sdyn_mean_is_mean_of_daily_s_dynamic_not_formula_of_mean_fwi():
    # Constant weather makes mean(S_dynamic(day)) == S_dynamic(mean(fwi(day)))
    # trivially (all days equal), so this test uses VARYING rain to make the
    # two differ if the aggregation were implemented wrong (e.g. clamping the
    # mean FWI instead of averaging the clamped per-day scores).
    season = 2017
    start = season_fwi.spinup_start(season)
    end = season_fwi.sensitivity_window(season)[1]
    weather = {}
    d = start
    toggle = False
    while d <= end:
        weather[d] = (18.0, 40.0, 10.0, 6.0 if toggle else 0.0)
        toggle = not toggle
        d += timedelta(days=1)

    rows = season_fwi.build_season_features("CHL.6.1.1_1", weather, season)
    standard = {r.window: r for r in rows}["standard"]

    outputs = season_fwi.run_chain(weather, start, end)
    ws, we = season_fwi.standard_window(season)
    daily = []
    dd = ws
    while dd <= we:
        daily.append(season_fwi.s_dynamic(outputs[dd].fwi))
        dd += timedelta(days=1)
    expected_mean = sum(daily) / len(daily)

    assert standard.sdyn_mean == pytest.approx(expected_mean)
