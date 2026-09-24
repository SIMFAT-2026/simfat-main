"""Pure zonal-aggregation logic for the Fuego Collection 1 rasters.

All tests here work on synthetic ``zonal.ZonalResult`` objects directly --
no raster I/O -- so the aggregation rules (denominator choice, edge cases)
are proven independently of ``zonal_stats`` itself (see test_zonal.py) and
independently of any real download.
"""
from __future__ import annotations

import os
from pathlib import Path

import pytest

from mb_pipeline import fire_stats, lulc, zonal
from mb_pipeline.zonal import ZonalResult

_REAL_YLF_RASTER_NAMES = (
    "mapbiomas_fire_chile_col1_annual_burned_2017.tif",
    "mapbiomas_fire_chile_col1_year_last_fire_2017.tif",
)


def _real_raster_dir() -> Path | None:
    # Mirrors test_build_stats_real.py's env var / default-dir lookup.
    env = os.environ.get("MB_FIRE_RASTER_DIR")
    candidates = [Path(env)] if env else []
    candidates.append(Path(os.environ.get("TEMP", "/tmp")) / "mb_fire_col1")
    for candidate in candidates:
        if all((candidate / name).exists() for name in _REAL_YLF_RASTER_NAMES):
            return candidate
    return None


def _florida_geometry() -> dict:
    biobio_path = next(r.geojson_path for r in lulc.DEFAULT_REGIONS if r.xlsx_region == "Biobío")
    features = lulc.load_geojson_features(biobio_path)
    florida = next(f for f in features if f["properties"]["nombre"] == "Florida")
    return florida["geometry"]

# --- annual_burned_fraction: burned/total from the annual raster alone ------
#
# EMPIRICAL FINDING (real 2017 data, see README "annual_burned_coverage_v1
# discovery"): annual_burned_coverage_v1's nonzero pixel count is EXACTLY
# equal to annual_burned_v1's burned-pixel count in every comuna checked
# (confirmed for Arauco, Cañete, Contulmo, Curanilahue, Lebu, Florida). It
# encodes the LAND-COVER CLASS of pixels that burned, not "was this pixel
# classified/mapped at all" -- so it cannot be used as a mapped-area
# denominator. annual_burned_v1 itself has no nodata, so burned fraction is
# computed against its OWN total polygon area.


def test_no_burn_gives_zero_fraction():
    annual = ZonalResult(pixel_count=100, area_ha=100.0, value_pixels={0: 100}, value_area_ha={0: 100.0})
    result = fire_stats.annual_burned_fraction(annual)
    assert result["totalHa"] == pytest.approx(100.0)
    assert result["burnedHa"] == pytest.approx(0.0)
    assert result["burnedFraction"] == pytest.approx(0.0)


def test_partial_burn_fraction_is_burned_over_total():
    annual = ZonalResult(
        pixel_count=100, area_ha=100.0, value_pixels={0: 87, 1: 13}, value_area_ha={0: 87.0, 1: 13.0}
    )
    result = fire_stats.annual_burned_fraction(annual)
    assert result["burnedHa"] == pytest.approx(13.0)
    assert result["burnedFraction"] == pytest.approx(0.13)


def test_comuna_outside_raster_gives_zero_not_a_crash():
    result = fire_stats.annual_burned_fraction(ZonalResult())
    assert result["totalHa"] == 0.0
    assert result["burnedHa"] == 0.0
    assert result["burnedFraction"] == 0.0


# --- bbox_coverage_fraction: is the comuna's geometry inside the raster? ----
#
# This replaces the originally-planned "coverage raster as mapped-area mask"
# gate (falsified above). It measures how much of the comuna's bounding box
# overlaps the raster's own declared bounding box -- a real geometry+raster
# extent check, not derived from pixel values at all. For our 3 target
# regions this is 1.0 for every comuna (Fuego Col 1's bbox comfortably
# encloses Biobío/Ñuble/Araucanía); the function stays generic so it also
# catches a comuna that would straddle the raster edge.


def _rect(west, south, east, north):
    return {
        "type": "Polygon",
        "coordinates": [[[west, south], [east, south], [east, north], [west, north], [west, south]]],
    }


def test_bbox_coverage_fraction_full_containment_is_1():
    geometry = _rect(-73.0, -38.0, -72.5, -37.5)
    raster_bounds = (-74.85, -44.07, -69.77, -32.02)
    assert fire_stats.bbox_coverage_fraction(geometry, raster_bounds) == pytest.approx(1.0)


def test_bbox_coverage_fraction_partial_overlap_is_the_intersection_ratio():
    # geometry spans lon -70..-68 (width 2); only -70..-69.77 (width 0.23) is
    # inside the raster's east bound (-69.77) -> 0.23 / 2 = 0.115.
    geometry = _rect(-70.0, -38.0, -68.0, -37.0)
    raster_bounds = (-74.85, -44.07, -69.77, -32.02)
    assert fire_stats.bbox_coverage_fraction(geometry, raster_bounds) == pytest.approx(0.115, abs=1e-3)


def test_bbox_coverage_fraction_no_overlap_is_0():
    geometry = _rect(10.0, 10.0, 11.0, 11.0)
    raster_bounds = (-74.85, -44.07, -69.77, -32.02)
    assert fire_stats.bbox_coverage_fraction(geometry, raster_bounds) == 0.0


def test_bbox_coverage_fraction_accepts_a_feature_wrapper():
    geometry = _rect(-73.0, -38.0, -72.5, -37.5)
    feature = {"type": "Feature", "properties": {}, "geometry": geometry}
    raster_bounds = (-74.85, -44.07, -69.77, -32.02)
    assert fire_stats.bbox_coverage_fraction(feature, raster_bounds) == pytest.approx(1.0)


# --- available_flag (MCS-8 gate) --------------------------------------------


def test_available_flag_true_above_threshold():
    assert fire_stats.available_flag(0.97, threshold=0.95) is True


def test_available_flag_false_below_threshold():
    assert fire_stats.available_flag(0.80, threshold=0.95) is False


def test_available_flag_boundary_is_inclusive():
    assert fire_stats.available_flag(0.95, threshold=0.95) is True


# --- frequency_stats (area-weighted... pixel-weighted mean/max) -------------


def test_frequency_stats_mean_and_max_from_pixel_counts():
    # 3 pixels never burned (0), 2 pixels burned once, 1 pixel burned twice.
    freq = ZonalResult(pixel_count=6, area_ha=6.0, value_pixels={0: 3, 1: 2, 2: 1}, value_area_ha={})
    result = fire_stats.frequency_stats(freq)
    # mean = (0*3 + 1*2 + 2*1) / 6 = 4/6
    assert result["frequencyMean"] == pytest.approx(4 / 6)
    assert result["frequencyMax"] == 2


def test_frequency_stats_empty_result_is_none_not_zero():
    result = fire_stats.frequency_stats(ZonalResult())
    assert result["frequencyMean"] is None
    assert result["frequencyMax"] is None


# --- year_last_fire_stats -----------------------------------------------------


def test_year_last_fire_takes_the_most_recent_year_present():
    ylf = ZonalResult(pixel_count=3, area_ha=3.0, value_pixels={0: 1, 2014: 1, 2016: 1}, value_area_ha={})
    assert fire_stats.year_last_fire_stats(ylf) == {"yearLastFire": 2016}


def test_year_last_fire_none_when_never_burned():
    ylf = ZonalResult(pixel_count=5, area_ha=5.0, value_pixels={0: 5}, value_area_ha={})
    assert fire_stats.year_last_fire_stats(ylf) == {"yearLastFire": None}


def test_year_last_fire_none_when_polygon_outside_raster():
    assert fire_stats.year_last_fire_stats(ZonalResult()) == {"yearLastFire": None}


# --- build_fire_section's yearLastFire correction (CRITICAL 1) -------------
#
# Real evidence (Florida comuna, CHL.6.3.4_1): the raw year_last_fire_v1
# raster's own maximum pixel value anywhere in Florida's bounding box is
# 2016 -- never 2017 -- yet annual_burned_v1 for 2017 shows 47.6% of
# Florida burned that same year. The two MapBiomas products disagree; a
# shipped document must never assert a yearLastFire that CONTRADICTS its
# own burnedFractionByYear. build_fire_section corrects for this by taking
# the max of the raw year-last-fire value and the most recent year with
# burnedHa > 0 in the per-year data actually processed.


def test_build_fire_section_year_last_fire_is_corrected_by_annual_burned_evidence():
    # Raw year-last-fire raster says 2016, but 2017 shows real burned area:
    # the corrected yearLastFire must be 2017, not the raw 2016.
    per_year = {
        2017: {"mappedHa": 100.0, "burnedHa": 47.6, "burnedFraction": 0.476},
    }
    section = fire_stats.build_fire_section(
        per_year,
        coverage_fraction=1.0,
        frequency={"frequencyMean": 0.5, "frequencyMax": 3},
        year_last_fire={"yearLastFire": 2016},
        threshold=0.95,
        as_of_year=2017,
    )
    assert section["yearLastFire"] == 2017
    assert section["yearsSinceLastFire"] == 0


def test_build_fire_section_year_last_fire_keeps_raw_value_when_it_is_more_recent():
    # Raw value already agrees with (or exceeds) the years actually burned;
    # the correction must not go backwards.
    per_year = {
        2015: {"mappedHa": 100.0, "burnedHa": 10.0, "burnedFraction": 0.10},
    }
    section = fire_stats.build_fire_section(
        per_year,
        coverage_fraction=1.0,
        frequency={"frequencyMean": 0.1, "frequencyMax": 1},
        year_last_fire={"yearLastFire": 2018},
        threshold=0.95,
        as_of_year=2018,
    )
    assert section["yearLastFire"] == 2018


def test_build_fire_section_year_last_fire_correction_ignores_zero_burned_years():
    # A processed year with burnedHa == 0 must not push yearLastFire forward.
    per_year = {
        2016: {"mappedHa": 100.0, "burnedHa": 0.0, "burnedFraction": 0.0},
        2017: {"mappedHa": 100.0, "burnedHa": 0.0, "burnedFraction": 0.0},
    }
    section = fire_stats.build_fire_section(
        per_year,
        coverage_fraction=1.0,
        frequency={"frequencyMean": 0.0, "frequencyMax": 0},
        year_last_fire={"yearLastFire": 2014},
        threshold=0.95,
        as_of_year=2017,
    )
    assert section["yearLastFire"] == 2014


@pytest.mark.skipif(
    _real_raster_dir() is None,
    reason="real Fuego Col 1 rasters not present locally (set MB_FIRE_RASTER_DIR); see README for the download step",
)
def test_build_fire_section_real_florida_year_last_fire_matches_its_own_2017_burn():
    # Regression test with the REAL Florida (CHL.6.3.4_1) numbers: the raw
    # year_last_fire_v1 raster caps out at 2016 for Florida's bbox, but the
    # real annual_burned_v1 2017 raster shows 47.6% burned that year. This
    # would have caught the original CRITICAL 1 bug directly against real
    # rasters, not just a synthetic fixture.
    raster_dir = _real_raster_dir()
    geometry = _florida_geometry()

    annual = zonal.zonal_stats(
        raster_dir / "mapbiomas_fire_chile_col1_annual_burned_2017.tif", geometry
    )
    ylf = zonal.zonal_stats(
        raster_dir / "mapbiomas_fire_chile_col1_year_last_fire_2017.tif", geometry
    )

    per_year = {2017: fire_stats.annual_burned_fraction(annual)}
    year_last_fire = fire_stats.year_last_fire_stats(ylf)
    assert year_last_fire["yearLastFire"] == 2016  # raw raster evidence, capped at 2016

    section = fire_stats.build_fire_section(
        per_year,
        coverage_fraction=1.0,
        frequency={"frequencyMean": None, "frequencyMax": None},
        year_last_fire=year_last_fire,
        threshold=0.95,
        as_of_year=2017,
    )
    assert section["yearLastFire"] >= 2017


# --- build_fire_section: combines years, applies the gate, no hidden zeros --


def test_build_fire_section_combines_multiple_years():
    per_year = {
        2016: {"mappedHa": 99.0, "burnedHa": 0.0, "burnedFraction": 0.0},
        2017: {"mappedHa": 97.0, "burnedHa": 20.0, "burnedFraction": 20 / 97},
    }
    section = fire_stats.build_fire_section(
        per_year,
        coverage_fraction=0.97,
        frequency={"frequencyMean": 0.3, "frequencyMax": 2},
        year_last_fire={"yearLastFire": 2017},
        threshold=0.95,
        as_of_year=2017,
    )
    assert section["available"] is True
    assert section["coverageFraction"] == pytest.approx(0.97)  # the explicit per-comuna coverage_fraction
    assert section["burnedHaByYear"] == {"2016": 0.0, "2017": 20.0}
    assert section["burnedFractionByYear"]["2017"] == pytest.approx(20 / 97)
    assert section["frequencyMean"] == pytest.approx(0.3)
    assert section["frequencyMax"] == 2
    assert section["yearLastFire"] == 2017
    assert section["yearsSinceLastFire"] == 0


def test_build_fire_section_burned_only_in_one_year_leaves_the_other_at_zero():
    per_year = {
        2015: {"mappedHa": 99.0, "burnedHa": 40.0, "burnedFraction": 40 / 99},
        2016: {"mappedHa": 99.0, "burnedHa": 0.0, "burnedFraction": 0.0},
    }
    section = fire_stats.build_fire_section(
        per_year,
        coverage_fraction=0.99,
        frequency={"frequencyMean": 0.5, "frequencyMax": 1},
        year_last_fire={"yearLastFire": 2015},
        threshold=0.95,
        as_of_year=2016,
    )
    assert section["burnedHaByYear"]["2015"] == pytest.approx(40.0)
    assert section["burnedHaByYear"]["2016"] == pytest.approx(0.0)
    assert section["yearsSinceLastFire"] == 1


def test_build_fire_section_below_threshold_is_unavailable_but_keeps_real_numbers():
    # Biobio gate failure case: available=False, but burnedHaByYear/coverageFraction
    # are the REAL computed values, not hidden or zeroed out (design rule: no
    # hidden zero, a documented reason instead).
    per_year = {2017: {"mappedHa": 40.0, "burnedHa": 5.0, "burnedFraction": 0.125}}
    section = fire_stats.build_fire_section(
        per_year,
        coverage_fraction=0.40,
        frequency={"frequencyMean": None, "frequencyMax": None},
        year_last_fire={"yearLastFire": None},
        threshold=0.95,
        as_of_year=2017,
    )
    assert section["available"] is False
    assert section["coverageFraction"] == pytest.approx(0.40)
    assert section["burnedHaByYear"]["2017"] == pytest.approx(5.0)
    assert "below threshold" in section["reason"]


def test_build_fire_section_no_processed_years_is_unavailable_with_a_reason():
    section = fire_stats.build_fire_section(
        {}, frequency={"frequencyMean": None, "frequencyMax": None},
        year_last_fire={"yearLastFire": None}, threshold=0.95, as_of_year=2017,
        coverage_fraction=1.0,
    )
    assert section["available"] is False
    assert section["burnedHaByYear"] == {}
    assert section["reason"] is not None


# --- build_fire_section composes with the REAL per-year functions (CRITICAL 2)
#
# Regression test for a real KeyError: build_fire_section used to read
# "coverageFraction" out of each per-year dict, but annual_burned_fraction()
# (the only production source of per-year stats) never puts that key there
# -- coverage is a per-comuna property, not a per-year one. This chains the
# real functions, with no hand-patched per_year dict, to prove the two
# compose without exploding.


def test_build_fire_section_composes_with_real_annual_burned_fraction_no_key_error():
    geometry = _rect(-73.0, -38.0, -72.5, -37.5)
    raster_bounds = (-74.85, -44.07, -69.77, -32.02)
    coverage_fraction = fire_stats.bbox_coverage_fraction(geometry, raster_bounds)

    annual_2017 = ZonalResult(
        pixel_count=100, area_ha=100.0, value_pixels={0: 87, 1: 13}, value_area_ha={0: 87.0, 1: 13.0}
    )
    per_year = {2017: fire_stats.annual_burned_fraction(annual_2017)}

    section = fire_stats.build_fire_section(
        per_year,
        coverage_fraction=coverage_fraction,
        frequency={"frequencyMean": 0.3, "frequencyMax": 1},
        year_last_fire={"yearLastFire": 2017},
        threshold=0.95,
        as_of_year=2017,
    )

    assert section["available"] is True
    assert section["coverageFraction"] == pytest.approx(1.0)
    assert section["burnedFractionByYear"]["2017"] == pytest.approx(0.13)


# --- select_burned_fraction_for_pct: which value feeds burnedFractionPct ----
#
# Design D1/D3: burnedFractionPct ranks the cumulative/union burnedFraction
# once multiple Fuego years are merged; this S1b3 partial dataset only has
# one Fuego year (2017), so it falls back to that single year's
# burnedFractionByYear value.


def test_select_burned_fraction_for_pct_prefers_cumulative_when_present():
    fire = {"available": True, "burnedFraction": 0.08, "burnedFractionByYear": {"2020": 0.5}}
    assert fire_stats.select_burned_fraction_for_pct(fire) == 0.08


def test_select_burned_fraction_for_pct_falls_back_to_the_single_available_year():
    fire = {"available": True, "burnedFractionByYear": {"2017": 0.02}}
    assert fire_stats.select_burned_fraction_for_pct(fire) == 0.02


def test_select_burned_fraction_for_pct_none_when_fire_unavailable():
    # available=False must be excluded from the rank even though a raw
    # burnedFractionByYear value is still present (MCS-8: available=False
    # never hides the underlying numbers, but it does mean "not usable").
    fire = {"available": False, "burnedFractionByYear": {"2017": 0.5}}
    assert fire_stats.select_burned_fraction_for_pct(fire) is None


def test_select_burned_fraction_for_pct_none_when_no_value_at_all():
    fire = {"available": True, "burnedFractionByYear": {}}
    assert fire_stats.select_burned_fraction_for_pct(fire) is None


def test_select_burned_fraction_for_pct_raises_when_multiple_years_and_no_cumulative():
    # Summing/averaging partial years without a real cumulative (union)
    # computation would silently double-count overlapping burns -- fail
    # loudly instead of guessing.
    fire = {"available": True, "burnedFractionByYear": {"2017": 0.1, "2018": 0.2}}
    with pytest.raises(ValueError, match="cumulative"):
        fire_stats.select_burned_fraction_for_pct(fire)
