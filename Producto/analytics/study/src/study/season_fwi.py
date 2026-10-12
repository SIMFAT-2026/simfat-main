"""Season-level FWI chain and S_dynamic aggregation (PREREGISTRATION.md
Section 8.1, design D4).

For each comuna and each pre-registered test season ``s`` in 2017..2025,
the Van Wagner chain is spun up from the published startup values
(FFMC=85, DMC=6, DC=15) on Sep 1 of ``s-1`` and advanced one day at a time
through the end of the SENSITIVITY window (Apr 30 of ``s``), since that
window is a strict superset of the standard window (Dec 1 of ``s-1``
through Mar 31 of ``s``) and the chain must never be restarted mid-season.
``S_dynamic(day) = W_FWI_STD * clamp(fwi(day) / FWI_MAX, 0, 1)``
(``ComunaRiskServiceImpl``'s STANDARD formula with FIRMS and citizen
reports forced to 0, per Section 8.1).

BLINDING RULE: this module computes FWI/S_dynamic INPUTS only. It never
loads FIRMS data and never computes a quantity relating a predictor to any
fire-activity target -- not here, not in its tests.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta
from typing import Mapping

from study import fwi

# ComunaRiskServiceImpl constants (Section 8.1): W_FWI_STD = 0.52, FWI_MAX = 50.0.
W_FWI_STD = 0.52
FWI_MAX = 50.0

# Van Wagner & Pickett (1985) published startup values, per design D4 /
# ComunaFwiStateService.STARTUP_FFMC/DMC/DC.
STARTUP_FFMC = 85.0
STARTUP_DMC = 6.0
STARTUP_DC = 15.0

# The 9 pre-registered, pooled test seasons (PREREGISTRATION.md Section 5).
SEASONS: tuple[int, ...] = tuple(range(2017, 2026))


class MissingWeatherDayError(RuntimeError):
    """Raised when a day required by the chain or an aggregation window has
    no weather input. The chain is never filled by interpolation."""


@dataclass(frozen=True)
class SeasonFeatureRow:
    comuna_id: str
    season: int
    window: str  # "standard" | "sensitivity"
    sdyn_mean: float
    fwi_mean: float
    n_days: int


def spinup_start(season: int) -> date:
    """Sep 1 of ``season - 1``, fixed across all 9 seasons (not tuned per season)."""
    return date(season - 1, 9, 1)


def standard_window(season: int) -> tuple[date, date]:
    """Dec 1 of ``season - 1`` through Mar 31 of ``season`` (the pre-registered season)."""
    return date(season - 1, 12, 1), date(season, 3, 31)


def sensitivity_window(season: int) -> tuple[date, date]:
    """Nov 1 of ``season - 1`` through Apr 30 of ``season`` (pre-declared sensitivity window)."""
    return date(season - 1, 11, 1), date(season, 4, 30)


def s_dynamic(fwi_value: float) -> float:
    """``W_FWI_STD * clamp(fwi_value / FWI_MAX, 0, 1)`` -- the backtest's
    STANDARD formula with FIRMS and citizen reports forced to 0."""
    norm = min(1.0, max(0.0, fwi_value / FWI_MAX))
    return W_FWI_STD * norm


def run_chain(
    weather_by_date: Mapping[date, tuple[float, float, float, float]],
    start: date,
    end: date,
    *,
    comuna_id: str | None = None,
) -> dict[date, fwi.FwiOutputs]:
    """Advances the Van Wagner chain from the published startup state,
    one day at a time, from ``start`` through ``end`` inclusive.

    ``weather_by_date[d]`` must be a ``(temp_c, rh_pct, wind_kmh, precip_mm)``
    tuple for the noon input of day ``d``. Raises ``MissingWeatherDayError``
    (never interpolates) if a day in range is absent.
    """
    who = f" for comuna {comuna_id}" if comuna_id else ""
    state = fwi.FwiState(STARTUP_FFMC, STARTUP_DMC, STARTUP_DC)
    outputs: dict[date, fwi.FwiOutputs] = {}
    d = start
    while d <= end:
        if d not in weather_by_date:
            raise MissingWeatherDayError(f"Missing weather input for {d.isoformat()}{who}")
        temp, rh, wind, rain = weather_by_date[d]
        inputs = fwi.FwiInputs(temp, rh, wind, rain, d.month)
        out = fwi.advance(state, inputs)
        outputs[d] = out
        state = fwi.FwiState(out.ffmc, out.dmc, out.dc)
        d += timedelta(days=1)
    return outputs


def aggregate_window(
    outputs: Mapping[date, fwi.FwiOutputs],
    window_start: date,
    window_end: date,
    *,
    comuna_id: str | None = None,
) -> tuple[float, float, int]:
    """Returns ``(sdyn_mean, fwi_mean, n_days)``: the mean, over the window's
    days, of each day's ``S_dynamic`` and raw ``fwi`` (aggregation option B
    of Section 8.1 -- the mean of daily blended scores, not a snapshot)."""
    who = f" for comuna {comuna_id}" if comuna_id else ""
    sdyn_values = []
    fwi_values = []
    d = window_start
    while d <= window_end:
        if d not in outputs:
            raise MissingWeatherDayError(f"Missing FWI output for {d.isoformat()}{who}")
        out = outputs[d]
        fwi_values.append(out.fwi)
        sdyn_values.append(s_dynamic(out.fwi))
        d += timedelta(days=1)
    n_days = len(fwi_values)
    return sum(sdyn_values) / n_days, sum(fwi_values) / n_days, n_days


def build_season_features(
    comuna_id: str,
    weather_by_date: Mapping[date, tuple[float, float, float, float]],
    season: int,
) -> list[SeasonFeatureRow]:
    """Runs the chain for one comuna-season from ``spinup_start`` through the
    end of the sensitivity window, then aggregates both the standard and the
    sensitivity windows from that single continuous chain."""
    start = spinup_start(season)
    end = sensitivity_window(season)[1]
    outputs = run_chain(weather_by_date, start, end, comuna_id=comuna_id)

    rows = []
    for window_name, (window_start, window_end) in (
        ("standard", standard_window(season)),
        ("sensitivity", sensitivity_window(season)),
    ):
        sdyn_mean, fwi_mean, n_days = aggregate_window(
            outputs, window_start, window_end, comuna_id=comuna_id
        )
        rows.append(
            SeasonFeatureRow(
                comuna_id=comuna_id,
                season=season,
                window=window_name,
                sdyn_mean=sdyn_mean,
                fwi_mean=fwi_mean,
                n_days=n_days,
            )
        )
    return rows
