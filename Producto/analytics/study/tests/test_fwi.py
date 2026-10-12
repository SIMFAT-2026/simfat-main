"""Reference-value tests for study.fwi, the pure-Python port of
CanadianFwiCalculator.java (Van Wagner & Pickett 1985).

Mirrors Producto/backend/simfat-backend/src/test/java/com/simfat/backend/
service/fwi/CanadianFwiCalculatorTest.java: same reference CSV (read directly
from the backend's test resources -- not duplicated), same tolerances, same
edge cases (FFMC constant 147.2, DMC literal 0.023, DC temperature floor
-2.8, rh > 100 handling, NaN guards).
"""
from __future__ import annotations

import math
from pathlib import Path

import pytest

from study import fwi

CODE_TOLERANCE = 0.06
DSR_TOLERANCE = 0.005

# Producto/analytics/study/tests -> Producto/analytics/study -> Producto/analytics -> Producto
_REPO_PRODUCTO_DIR = Path(__file__).resolve().parents[3]
_VECTORS_CSV = (
    _REPO_PRODUCTO_DIR
    / "backend"
    / "simfat-backend"
    / "src"
    / "test"
    / "resources"
    / "fwi"
    / "vanwagner-1985-vectors.csv"
)


def _load_reference_days():
    start = None
    days = []
    with open(_VECTORS_CSV, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("START,"):
                parts = line.split(",")
                start = fwi.FwiState(float(parts[1]), float(parts[2]), float(parts[3]))
                continue
            parts = line.split(",")
            days.append(
                {
                    "month": int(parts[0]),
                    "day": int(parts[1]),
                    "temp": float(parts[2]),
                    "rh": float(parts[3]),
                    "ws": float(parts[4]),
                    "prec": float(parts[5]),
                    "ffmc": float(parts[6]),
                    "dmc": float(parts[7]),
                    "dc": float(parts[8]),
                    "isi": float(parts[9]),
                    "bui": float(parts[10]),
                    "fwi": float(parts[11]),
                    "dsr": float(parts[12]),
                }
            )
    assert start is not None, "vanwagner-1985-vectors.csv is missing its START line"
    return start, days


@pytest.fixture(scope="module")
def reference():
    return _load_reference_days()


def test_vectors_csv_has_49_reference_days(reference):
    _, days = reference
    assert len(days) == 49


def test_full_replay_49_days_from_published_startup_state(reference):
    start, days = reference
    state = start

    for d in days:
        inputs = fwi.FwiInputs(d["temp"], d["rh"], d["ws"], d["prec"], d["month"])
        out = fwi.advance(state, inputs)

        label = f"day {d['month']}/{d['day']}"
        assert out.ffmc == pytest.approx(d["ffmc"], abs=CODE_TOLERANCE), f"FFMC {label}"
        assert out.dmc == pytest.approx(d["dmc"], abs=CODE_TOLERANCE), f"DMC {label}"
        assert out.dc == pytest.approx(d["dc"], abs=CODE_TOLERANCE), f"DC {label}"
        assert out.isi == pytest.approx(d["isi"], abs=CODE_TOLERANCE), f"ISI {label}"
        assert out.bui == pytest.approx(d["bui"], abs=CODE_TOLERANCE), f"BUI {label}"
        assert out.fwi == pytest.approx(d["fwi"], abs=CODE_TOLERANCE), f"FWI {label}"
        assert out.dsr == pytest.approx(d["dsr"], abs=DSR_TOLERANCE), f"DSR {label}"

        # Carry state forward at full float precision, per the report.
        state = fwi.FwiState(out.ffmc, out.dmc, out.dc)


def test_ffmc_mo_above_150_applies_heavy_moisture_carry_term():
    # Mirrors CanadianFwiCalculatorTest.ffmc_moAbove150_appliesHeavyMoistureCarryTerm.
    previous_state = fwi.FwiState(10.0, 6.0, 15.0)
    rainy_noon = fwi.FwiInputs(20.0, 50.0, 10.0, 5.0, 6)

    out = fwi.advance(previous_state, rainy_noon)

    assert out.ffmc == pytest.approx(51.97010884982895, abs=1e-6)


def test_ffmc_mr_above_250_clamps_to_250():
    # Mirrors CanadianFwiCalculatorTest.ffmc_mrAbove250_clampsTo250.
    previous_state = fwi.FwiState(10.0, 6.0, 15.0)
    heavy_rain_noon = fwi.FwiInputs(20.0, 50.0, 10.0, 30.0, 6)

    out = fwi.advance(previous_state, heavy_rain_noon)

    assert out.ffmc == pytest.approx(48.95210203908465, abs=1e-6)


def test_dc_sub_freezing_temp_floors_temp_at_minus_2_8_before_v_term():
    # Mirrors CanadianFwiCalculatorTest.dc_subFreezingTemp_floorsTempAt_minus2_8BeforeVTerm.
    previous_state = fwi.FwiState(85.0, 6.0, 100.0)
    cold_july_noon = fwi.FwiInputs(-10.0, 50.0, 10.0, 0.0, 7)

    out = fwi.advance(previous_state, cold_july_noon)

    assert out.dc == pytest.approx(103.2, abs=1e-9)


def test_fwi_inputs_relative_humidity_above_100_is_clamped_to_100():
    inputs = fwi.FwiInputs(15.0, 101.0, 10.0, 0.0, 6)
    assert inputs.rh_pct == 100.0


def test_fwi_inputs_relative_humidity_below_0_is_clamped_to_0():
    inputs = fwi.FwiInputs(15.0, -5.0, 10.0, 0.0, 6)
    assert inputs.rh_pct == 0.0


def test_fwi_inputs_negative_wind_is_clamped_to_zero():
    inputs = fwi.FwiInputs(15.0, 50.0, -3.0, 0.0, 6)
    assert inputs.wind_kmh == 0.0


def test_fwi_inputs_negative_precip_is_clamped_to_zero():
    inputs = fwi.FwiInputs(15.0, 50.0, 10.0, -1.0, 6)
    assert inputs.precip_mm == 0.0


def test_fwi_inputs_relative_humidity_above_100_does_not_propagate_nan():
    previous_state = fwi.FwiState(85.0, 6.0, 15.0)
    inputs = fwi.FwiInputs(15.0, 101.0, 10.0, 0.0, 6)

    out = fwi.advance(previous_state, inputs)

    assert math.isfinite(out.ffmc)
    assert math.isfinite(out.fwi)


def test_fwi_inputs_month_out_of_range_raises_value_error():
    with pytest.raises(ValueError):
        fwi.FwiInputs(15.0, 50.0, 10.0, 0.0, 13)
    with pytest.raises(ValueError):
        fwi.FwiInputs(15.0, 50.0, 10.0, 0.0, 0)
