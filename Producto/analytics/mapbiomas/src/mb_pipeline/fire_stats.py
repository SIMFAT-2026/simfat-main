"""Pure aggregation over MapBiomas Fuego Collection 1 zonal results.

Every function here takes ``zonal.ZonalResult`` (or plain dicts derived from
one) and returns plain dicts -- no raster I/O, no filesystem access -- so the
aggregation rules are unit-testable without a single GeoTIFF.

Denominator rule (MCS-6b, AMENDED after real-data evidence): the Fuego
rasters carry no nodata sentinel, so a pixel value of 0 in
``annual_burned_v1`` is real data ("not burned"), not "unmapped" -- burned
fraction is computed against the raster's OWN total polygon area.

EMPIRICAL FINDING (2026-09-21, real 2017 rasters): the original plan was to
use ``annual_burned_coverage_v1`` as a "was this pixel classified/mapped at
all this year" mask, dividing burned area by that mapped area instead of
the raw polygon area. Downloading the real 2017 file and comparing it
pixel-for-pixel against ``annual_burned_v1`` for every Biobío comuna shows
this is WRONG: ``annual_burned_coverage_v1``'s nonzero pixel count is
EXACTLY EQUAL to ``annual_burned_v1``'s burned-pixel count in every comuna
tested (Arauco 0==0, Cañete 474==474, Curanilahue 850==850, Florida
401472==401472, ...). The dataset holds the LAND-COVER CLASS OF PIXELS THAT
BURNED that year (useful for fuel-type-of-burn analysis), not a general
mapped/observed-area indicator; using it as a "mapped area" denominator
would make burnedFraction == 1.0 for every comuna with any fire at all,
which is a degenerate, useless gate. ``bbox_coverage_fraction`` below is
the real replacement for the "is this comuna's Fuego data usable" gate.
"""
from __future__ import annotations

from typing import Mapping, Sequence

from .zonal import ZonalResult

BURNED_VALUE = 1
NO_FIRE_VALUE = 0


def annual_burned_fraction(annual: ZonalResult) -> dict:
    """Burned hectares and fraction for one comuna-year, from the annual raster alone.

    ``annual_burned_v1`` has no nodata, so its own zonal area IS the
    comuna's observed total for that year; no second raster is needed.
    """
    total_ha = annual.area_ha
    burned_ha = annual.value_area_ha.get(BURNED_VALUE, 0.0)
    burned_fraction = burned_ha / total_ha if total_ha > 0 else 0.0
    return {"totalHa": total_ha, "burnedHa": burned_ha, "burnedFraction": burned_fraction}


def _flatten_coords(coords):
    if coords and isinstance(coords[0], (int, float)):
        return [coords]
    return [pt for part in coords for pt in _flatten_coords(part)]


def _geometry_bbox(geometry: dict) -> tuple[float, float, float, float]:
    """(west, south, east, north) of a Polygon/MultiPolygon or Feature wrapping one."""
    if geometry.get("type") == "Feature":
        geometry = geometry["geometry"]
    points = _flatten_coords(geometry["coordinates"])
    lons = [p[0] for p in points]
    lats = [p[1] for p in points]
    return min(lons), min(lats), max(lons), max(lats)


def bbox_coverage_fraction(geometry: dict, raster_bounds: tuple[float, float, float, float]) -> float:
    """Fraction of ``geometry``'s bounding box that overlaps ``raster_bounds``.

    Replaces the falsified "coverage raster as mapped-area mask" plan (see
    module docstring): this checks the comuna's geometry against the
    raster's own declared spatial extent, independent of any pixel value.
    1.0 means the comuna is fully inside the raster's footprint; a value
    below the gate threshold flags a comuna that straddles or falls outside
    the collection's coverage area.
    """
    west, south, east, north = _geometry_bbox(geometry)
    rwest, rsouth, reast, rnorth = raster_bounds
    inter_w, inter_s = max(west, rwest), max(south, rsouth)
    inter_e, inter_n = min(east, reast), min(north, rnorth)
    inter_area = max(0.0, inter_e - inter_w) * max(0.0, inter_n - inter_s)
    geom_area = max(0.0, east - west) * max(0.0, north - south)
    return inter_area / geom_area if geom_area > 0 else 0.0


def available_flag(coverage_fraction: float, *, threshold: float) -> bool:
    """MCS-8 gate: a comuna-year is usable only at or above ``threshold``."""
    return coverage_fraction >= threshold


def frequency_stats(freq: ZonalResult) -> dict:
    """Pixel-count-weighted mean and max of a ``frequency_burned_v1`` window."""
    total_pixels = freq.pixel_count
    if total_pixels == 0:
        return {"frequencyMean": None, "frequencyMax": None}
    weighted_sum = sum(value * count for value, count in freq.value_pixels.items())
    return {
        "frequencyMean": weighted_sum / total_pixels,
        "frequencyMax": max(freq.value_pixels),
    }


def year_last_fire_stats(ylf: ZonalResult, *, no_fire_value: int = NO_FIRE_VALUE) -> dict:
    """Most recent burn year present in a ``year_last_fire_v1`` window, or None."""
    burned_years = [year for year in ylf.value_pixels if year != no_fire_value]
    return {"yearLastFire": max(burned_years) if burned_years else None}


def build_fire_section(
    per_year: Mapping[int, dict],
    *,
    coverage_fraction: float,
    frequency: Mapping,
    year_last_fire: Mapping,
    threshold: float,
    as_of_year: int,
) -> dict:
    """Combine per-year stats into the ``fire`` section of a comuna document.

    ``available=False`` never hides the underlying numbers (MCS-8): a comuna
    below the coverage threshold still reports its real coverageFraction and
    burnedHaByYear, plus a stated ``reason`` for why it is not usable.

    ``coverage_fraction`` is an EXPLICIT, per-comuna parameter (computed once
    via ``bbox_coverage_fraction``), not read out of each per-year dict.
    Coverage is a property of the comuna's geometry vs. the raster's extent,
    not of any individual fire-year -- and ``annual_burned_fraction`` (the
    only production source of per-year stats) never returns a
    ``coverageFraction`` key, so reading it from ``per_year`` values raised a
    ``KeyError`` on every real call (CRITICAL 2 in the S1b jd-fix review; see
    ``tests/test_fire_stats.py::test_build_fire_section_composes_with_real_annual_burned_fraction_no_key_error``).
    """
    if not per_year:
        return {
            "available": False,
            "coverageFraction": None,
            "burnedHaByYear": {},
            "burnedFractionByYear": {},
            "frequencyMean": frequency.get("frequencyMean"),
            "frequencyMax": frequency.get("frequencyMax"),
            "yearLastFire": year_last_fire.get("yearLastFire"),
            "yearsSinceLastFire": None,
            "reason": "no fire years processed for this comuna",
        }

    burned_ha_by_year = {str(year): stats["burnedHa"] for year, stats in per_year.items()}
    burned_fraction_by_year = {str(year): stats["burnedFraction"] for year, stats in per_year.items()}
    available = available_flag(coverage_fraction, threshold=threshold)
    year_last_fire_value = year_last_fire.get("yearLastFire")
    years_since = (as_of_year - year_last_fire_value) if year_last_fire_value is not None else None

    return {
        "available": available,
        "coverageFraction": coverage_fraction,
        "burnedHaByYear": burned_ha_by_year,
        "burnedFractionByYear": burned_fraction_by_year,
        "frequencyMean": frequency.get("frequencyMean"),
        "frequencyMax": frequency.get("frequencyMax"),
        "yearLastFire": year_last_fire_value,
        "yearsSinceLastFire": years_since,
        "reason": None
        if available
        else f"coverageFraction {coverage_fraction:.4f} below threshold {threshold}",
    }
