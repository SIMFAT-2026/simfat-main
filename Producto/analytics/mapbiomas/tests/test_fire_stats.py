"""Pure zonal-aggregation logic for the Fuego Collection 1 rasters.

All tests here work on synthetic ``zonal.ZonalResult`` objects directly --
no raster I/O -- so the aggregation rules (denominator choice, edge cases)
are proven independently of ``zonal_stats`` itself (see test_zonal.py) and
independently of any real download.
"""
from __future__ import annotations

import pytest

from mb_pipeline import fire_stats
from mb_pipeline.zonal import ZonalResult

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
