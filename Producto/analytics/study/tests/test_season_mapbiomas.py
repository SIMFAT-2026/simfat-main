"""season_mapbiomas.py: leakage-safe, per-season recomputation of the
MapBiomas Fuego/land-cover features and the S_mapbiomas parity port
(PREREGISTRATION.md Section 7, Section 8.2).

BLINDING RULE: this module (and these tests) build/verify PREDICTOR INPUTS
ONLY. No FIRMS data is read and no quantity relating these predictors to
any fire-activity target is computed or asserted anywhere here.
"""
from __future__ import annotations

import json

import numpy as np
import pytest
from rasterio.transform import from_origin

from study import season_mapbiomas


def rect(west, south, east, north):
    return {
        "type": "Polygon",
        "coordinates": [[[west, south], [east, south], [east, north], [west, north], [west, south]]],
    }


# ---- leakage rule: Fuego years <= s-2 --------------------------------------


def test_fire_years_for_season_2017_is_2013_2015():
    assert season_mapbiomas.fire_years_for_season(2017) == [2013, 2014, 2015]


def test_fire_years_for_season_2025_is_2013_2023():
    years = season_mapbiomas.fire_years_for_season(2025)
    assert years[0] == 2013
    assert years[-1] == 2023
    assert years == list(range(2013, 2024))


def test_fire_years_for_season_2019_is_2013_2017():
    assert season_mapbiomas.fire_years_for_season(2019) == [2013, 2014, 2015, 2016, 2017]


# ---- land-cover shares for year s-1 ----------------------------------------


def _shares_by_year(first_year, classes):
    return {"firstYear": first_year, "lastYear": first_year + len(next(iter(classes.values()))) - 1, "unit": "bp", "classes": classes}


def test_shares_for_year_converts_bp_to_fraction_and_drops_zero():
    shares_by_year = _shares_by_year(2016, {"9": [6000, 7000], "18": [4000, 0], "27": [0, 3000]})
    shares = season_mapbiomas.shares_for_year(shares_by_year, 2016)
    assert shares == {"9": 0.6, "18": 0.4}  # class 27 at idx0 is 0bp -> dropped


def test_shares_for_year_second_index():
    shares_by_year = _shares_by_year(2016, {"9": [6000, 7000], "18": [4000, 0], "27": [0, 3000]})
    shares = season_mapbiomas.shares_for_year(shares_by_year, 2017)
    assert shares == {"9": 0.7, "27": 0.3}


def test_shares_for_year_raises_outside_range():
    shares_by_year = _shares_by_year(2016, {"9": [6000, 7000]})
    with pytest.raises(ValueError, match="outside"):
        season_mapbiomas.shares_for_year(shares_by_year, 2020)


# ---- fuel weights / fuel index (Java MapbiomasSusceptibilityServiceImpl parity) --


def _weights():
    return season_mapbiomas.FuelWeights(
        class_weights={"9": 1.0, "18": 0.3, "67": 0.4},
        excluded_classes=frozenset({"27"}),
    )


def test_compute_fuel_index_weighted_sum():
    shares = {"9": 0.6, "18": 0.4}
    fuel = season_mapbiomas.compute_fuel_index(shares, _weights())
    assert fuel == pytest.approx(0.6 * 1.0 + 0.4 * 0.3)


def test_compute_fuel_index_skips_excluded_class_without_counting_its_share():
    shares = {"9": 0.9, "27": 0.1}
    fuel = season_mapbiomas.compute_fuel_index(shares, _weights())
    assert fuel == pytest.approx(0.9 * 1.0)


def test_compute_fuel_index_raises_on_bad_sum():
    shares = {"9": 0.5, "18": 0.3}  # sums to 0.8, not ~1.0
    with pytest.raises(season_mapbiomas.ShareSumMismatchError):
        season_mapbiomas.compute_fuel_index(shares, _weights())


def test_compute_fuel_index_raises_on_unknown_class():
    shares = {"9": 0.5, "99": 0.5}  # class 99 unknown and not excluded
    with pytest.raises(season_mapbiomas.UnknownFuelClassError, match="99"):
        season_mapbiomas.compute_fuel_index(shares, _weights())


def test_compute_fuel_index_clamps_at_1():
    weights = season_mapbiomas.FuelWeights(class_weights={"9": 2.0}, excluded_classes=frozenset())
    fuel = season_mapbiomas.compute_fuel_index({"9": 1.0}, weights)
    assert fuel == 1.0


# ---- history / S_mapbiomas parity port -------------------------------------


def test_compute_recurrence_norm_caps_at_frequency_cap():
    assert season_mapbiomas.compute_recurrence_norm(None) == 0.0
    assert season_mapbiomas.compute_recurrence_norm(2.5) == pytest.approx(0.5)
    assert season_mapbiomas.compute_recurrence_norm(999.0) == 1.0  # capped at FREQUENCY_CAP=5.0


def test_compute_history_index_matches_java_weights():
    history = season_mapbiomas.compute_history_index(burned_norm=0.5, frequency_mean=2.5)
    assert history == pytest.approx(0.60 * 0.5 + 0.40 * 0.5)


def test_compute_history_index_clamps_at_1():
    history = season_mapbiomas.compute_history_index(burned_norm=1.0, frequency_mean=999.0)
    assert history == 1.0


def test_compute_s_mapbiomas_matches_java_weights():
    s = season_mapbiomas.compute_s_mapbiomas(fuel=0.8, history=0.4)
    assert s == pytest.approx(0.65 * 0.8 + 0.35 * 0.4)


# ---- PARITY TEST against RealSeedRegressionTest.java's pinned values ------


SEED_PATH = (
    season_mapbiomas.STUDY_PRODUCTO_ROOT
    / "analytics"
    / "mapbiomas"
    / "data"
    / "comuna-mapbiomas-stats.lulc-col2+fuego-col1-2013-2025.jsonl"
)
FUEL_WEIGHTS_PATH = (
    season_mapbiomas.STUDY_PRODUCTO_ROOT
    / "backend"
    / "simfat-backend"
    / "src"
    / "main"
    / "resources"
    / "mapbiomas"
    / "fuel-weights-v1.json"
)


def _load_seed_doc(comuna_id):
    with open(SEED_PATH, encoding="utf-8") as f:
        for line in f:
            doc = json.loads(line)
            if doc["comunaId"] == comuna_id:
                return doc
    raise AssertionError(f"{comuna_id} not found in real seed")


@pytest.mark.parametrize(
    "comuna_id,expected_fuel,expected_history,expected_score",
    [
        ("CHL.6.1.1_1", 0.78615, 0.38460352096670414, 0.6456087323383464),
        ("CHL.13.1.1_1", 0.4716, 0.26055864156585223, 0.39776152454804825),
        ("CHL.13.2.1_1", 0.89208, 0.1259219059362537, 0.6239246670776888),
    ],
)
def test_s_mapbiomas_parity_against_real_pinned_seed_values(
    comuna_id, expected_fuel, expected_history, expected_score
):
    """Feeds the REAL production full-window inputs (the committed seed's
    sharesByClass / fire.burnedFractionPct / fire.frequencyMean) through
    this module's Python port and checks it reproduces the exact values
    pinned in RealSeedRegressionTest.java -- independent of the
    leakage-safe per-season recomputation (fire_years_for_season etc.),
    which is gated separately.

    Tolerance note: ``history``/``score`` are pinned in the Java source
    with full IEEE double precision (17 significant digits, e.g.
    ``0.38460352096670414``) and are checked here within 1e-6, per the
    launch prompt. ``fuel`` for CHL.13.1.1_1 is pinned as the short
    literal ``0.4716`` (4 decimals) -- independently recomputing it from
    the real seed gives ``0.47164`` (confirmed by hand before writing this
    test), a ~4e-5 difference consistent with Java's own test using
    DELTA=1e-4 for that assertion (RealSeedRegressionTest.java): the short
    literal is evidently a human-rounded transcription, not the full
    double. ``fuel`` is therefore checked at 1e-4 (matching the precision
    the pinned literal itself actually carries), while ``history``/
    ``score`` are checked at 1e-6.
    """
    weights = season_mapbiomas.load_fuel_weights(FUEL_WEIGHTS_PATH)
    doc = _load_seed_doc(comuna_id)

    fuel = season_mapbiomas.compute_fuel_index(doc["landCover"]["sharesByClass"], weights)
    burned_norm = doc["fire"]["burnedFractionPct"]
    frequency_mean = doc["fire"]["frequencyMean"]
    history = season_mapbiomas.compute_history_index(burned_norm, frequency_mean)
    score = season_mapbiomas.compute_s_mapbiomas(fuel, history)

    assert fuel == pytest.approx(expected_fuel, abs=1e-4)
    assert history == pytest.approx(expected_history, abs=1e-6)
    assert score == pytest.approx(expected_score, abs=1e-6)


# ---- frequency mean derived from per-year zonal pixel counts ---------------


def test_frequency_mean_from_zonal_results_matches_pixel_weighted_identity(make_geotiff):
    # 10x10 raster, comuna covers the whole thing (100 pixels). Year A burns
    # a 4x4 block (16 px), year B burns a DIFFERENT 2x2 block inside the
    # same area (4 px, no overlap with A here is irrelevant to the MEAN
    # identity -- see the module docstring: sum of per-year burned-pixel
    # counts / total pixels, regardless of overlap).
    grid_a = np.zeros((10, 10), dtype="uint8")
    grid_a[0:4, 0:4] = 1
    grid_b = np.zeros((10, 10), dtype="uint8")
    grid_b[0:2, 0:2] = 1  # fully inside grid_a's burned block (overlap)
    path_a = make_geotiff(grid_a, -72.0, -33.0, 0.01, 0.01, name="a.tif")
    path_b = make_geotiff(grid_b, -72.0, -33.0, 0.01, 0.01, name="b.tif")

    from mb_pipeline import zonal

    geometry = rect(-72.0, -33.10, -71.90, -33.0)
    zr_a = zonal.zonal_stats(path_a, geometry)
    zr_b = zonal.zonal_stats(path_b, geometry)

    frequency_mean = season_mapbiomas.frequency_mean_from_zonal_results([zr_a, zr_b])
    # pixel-count-weighted mean = (16 + 4) / 100
    assert frequency_mean == pytest.approx((16 + 4) / 100)


def test_frequency_mean_from_zonal_results_is_none_when_no_pixels():
    from mb_pipeline.zonal import ZonalResult

    assert season_mapbiomas.frequency_mean_from_zonal_results([ZonalResult()]) is None
