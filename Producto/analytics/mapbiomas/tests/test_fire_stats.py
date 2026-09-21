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

# --- year_fire_stats: coverage/burned fraction from a single year's pair -----


def test_fully_mapped_comuna_with_no_burn():
    annual = ZonalResult(pixel_count=100, area_ha=100.0, value_pixels={0: 100}, value_area_ha={0: 100.0})
    coverage = ZonalResult(
        pixel_count=100, area_ha=100.0, value_pixels={3: 100}, value_area_ha={3: 100.0}
    )
    result = fire_stats.year_fire_stats(annual, coverage)
    assert result["coverageFraction"] == pytest.approx(1.0)
    assert result["mappedHa"] == pytest.approx(100.0)
    assert result["burnedHa"] == pytest.approx(0.0)
    assert result["burnedFraction"] == pytest.approx(0.0)


def test_burned_fraction_uses_mapped_area_not_total_pixel_count():
    # Half the comuna was never classified (coverage value 0); of the mapped
    # half, a quarter of ITS area burned. burnedFraction must be computed
    # against the 50 mapped ha, not the 100 total ha.
    annual = ZonalResult(
        pixel_count=100, area_ha=100.0, value_pixels={0: 87, 1: 13}, value_area_ha={0: 87.0, 1: 13.0}
    )
    coverage = ZonalResult(
        pixel_count=100, area_ha=100.0, value_pixels={0: 50, 3: 50}, value_area_ha={0: 50.0, 3: 50.0}
    )
    result = fire_stats.year_fire_stats(annual, coverage)
    assert result["mappedHa"] == pytest.approx(50.0)
    assert result["coverageFraction"] == pytest.approx(0.5)
    assert result["burnedHa"] == pytest.approx(13.0)
    # 13 / 50 (mapped), NOT 13 / 100 (total)
    assert result["burnedFraction"] == pytest.approx(0.26)


def test_comuna_fully_outside_mapped_area_gives_zero_coverage_not_a_crash():
    # Polygon does not intersect the raster at all: zonal_stats returns the
    # empty ZonalResult() (area_ha=0). Division by zero must not happen.
    annual = ZonalResult()
    coverage = ZonalResult()
    result = fire_stats.year_fire_stats(annual, coverage)
    assert result["coverageFraction"] == 0.0
    assert result["mappedHa"] == 0.0
    assert result["burnedHa"] == 0.0
    assert result["burnedFraction"] == 0.0


def test_partially_mapped_comuna_reports_the_exact_fraction():
    coverage = ZonalResult(
        pixel_count=4, area_ha=4.0, value_pixels={0: 1, 3: 3}, value_area_ha={0: 1.0, 3: 3.0}
    )
    annual = ZonalResult(pixel_count=4, area_ha=4.0, value_pixels={0: 4}, value_area_ha={0: 4.0})
    result = fire_stats.year_fire_stats(annual, coverage)
    assert result["coverageFraction"] == pytest.approx(0.75)


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
        2016: {"coverageFraction": 0.99, "mappedHa": 99.0, "burnedHa": 0.0, "burnedFraction": 0.0},
        2017: {"coverageFraction": 0.97, "mappedHa": 97.0, "burnedHa": 20.0, "burnedFraction": 20 / 97},
    }
    section = fire_stats.build_fire_section(
        per_year,
        frequency={"frequencyMean": 0.3, "frequencyMax": 2},
        year_last_fire={"yearLastFire": 2017},
        threshold=0.95,
        as_of_year=2017,
    )
    assert section["available"] is True
    assert section["coverageFraction"] == pytest.approx(0.97)  # the min across processed years
    assert section["burnedHaByYear"] == {"2016": 0.0, "2017": 20.0}
    assert section["burnedFractionByYear"]["2017"] == pytest.approx(20 / 97)
    assert section["frequencyMean"] == pytest.approx(0.3)
    assert section["frequencyMax"] == 2
    assert section["yearLastFire"] == 2017
    assert section["yearsSinceLastFire"] == 0


def test_build_fire_section_burned_only_in_one_year_leaves_the_other_at_zero():
    per_year = {
        2015: {"coverageFraction": 0.99, "mappedHa": 99.0, "burnedHa": 40.0, "burnedFraction": 40 / 99},
        2016: {"coverageFraction": 0.99, "mappedHa": 99.0, "burnedHa": 0.0, "burnedFraction": 0.0},
    }
    section = fire_stats.build_fire_section(
        per_year,
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
    per_year = {2017: {"coverageFraction": 0.40, "mappedHa": 40.0, "burnedHa": 5.0, "burnedFraction": 0.125}}
    section = fire_stats.build_fire_section(
        per_year,
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
    )
    assert section["available"] is False
    assert section["burnedHaByYear"] == {}
    assert section["reason"] is not None
