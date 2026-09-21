"""Pure aggregation over MapBiomas Fuego Collection 1 zonal results.

Every function here takes ``zonal.ZonalResult`` (or plain dicts derived from
one) and returns plain dicts -- no raster I/O, no filesystem access -- so the
aggregation rules are unit-testable without a single GeoTIFF.

Denominator rule (MCS-6b): the Fuego rasters carry no nodata sentinel, so a
pixel value of 0 in ``annual_burned_v1`` is real data ("not burned"), not
"unmapped". Whether a pixel was classified AT ALL that year comes from the
paired ``annual_burned_coverage_v1`` raster instead: it holds a land-cover
class code (matching the MapBiomas legend) for every pixel that was
classified, and 0 for pixels that were not (no legend code is 0). Burned
fraction is therefore always computed against the MAPPED area, never the
raw polygon/pixel-count area.
"""
from __future__ import annotations

from typing import Mapping, Sequence

from .zonal import ZonalResult

UNMAPPED_VALUE = 0
BURNED_VALUE = 1
NO_FIRE_VALUE = 0


def year_fire_stats(
    annual: ZonalResult, coverage: ZonalResult, *, unmapped_value: int = UNMAPPED_VALUE
) -> dict:
    """Coverage and burned fraction for one comuna-year from its raster pair."""
    total_ha = coverage.area_ha
    mapped_ha = total_ha - coverage.value_area_ha.get(unmapped_value, 0.0)
    coverage_fraction = mapped_ha / total_ha if total_ha > 0 else 0.0
    burned_ha = annual.value_area_ha.get(BURNED_VALUE, 0.0)
    burned_fraction = burned_ha / mapped_ha if mapped_ha > 0 else 0.0
    return {
        "mappedHa": mapped_ha,
        "totalHa": total_ha,
        "coverageFraction": coverage_fraction,
        "burnedHa": burned_ha,
        "burnedFraction": burned_fraction,
    }


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
    frequency: Mapping,
    year_last_fire: Mapping,
    threshold: float,
    as_of_year: int,
) -> dict:
    """Combine per-year stats into the ``fire`` section of a comuna document.

    ``available=False`` never hides the underlying numbers (MCS-8): a comuna
    below the coverage threshold still reports its real coverageFraction and
    burnedHaByYear, plus a stated ``reason`` for why it is not usable.
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

    coverage_fraction = min(stats["coverageFraction"] for stats in per_year.values())
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
