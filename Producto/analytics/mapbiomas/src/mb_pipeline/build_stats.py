"""Assemble per-comuna documents from LULC shares and Fuego zonal stats.

This module has two independent halves:

- pure assembly (``to_basis_points``, ``land_cover_section``,
  ``build_comuna_document``) -- no I/O, fully covered by synthetic fixtures
  in ``tests/test_build_stats.py``.
- writers (``write_coverage_report``, ``write_seed_jsonl``) -- plain file
  I/O with a fixed, tested shape.

Land-cover shares are ROUNDED internally in basis points (bp, 1/100 of a
percent) as integers, largest-remainder rounded so every comuna-year's shares
sum to EXACTLY 10000 -- never 9999 or 10001 from naive per-class rounding.
The two fields exposed on the seed document, ``sharesByClass`` and
``sharesByClassMean5y``, are FRACTIONS (bp / 10000) summing to ~1.0 (design
D1); only the (not yet implemented) ``sharesByYear.classes`` field is
documented to hold raw basis points.
"""
from __future__ import annotations

import csv
import json
import math
from pathlib import Path
from typing import Mapping, MutableMapping, Sequence

from . import fire_stats

BP_TOTAL = 10_000


def to_basis_points(shares_ha: Mapping[int, float]) -> dict[int, int]:
    """Largest-remainder round a {classCode: hectares} map to bp summing to 10000.

    Classes with zero hectares are dropped (a zero share carries no
    information and would just be noise in the sparse encoding). Ties in the
    remainder are broken by ascending class code, making the result
    deterministic.
    """
    total_ha = sum(shares_ha.values())
    if total_ha <= 0:
        return {}
    exact = {code: ha / total_ha * BP_TOTAL for code, ha in shares_ha.items() if ha > 0}
    floored = {code: int(value) for code, value in exact.items()}
    remainder = BP_TOTAL - sum(floored.values())
    # Distribute the remaining bp, largest fractional remainder first,
    # ascending class code as the deterministic tie-break.
    order = sorted(exact, key=lambda code: (-(exact[code] - floored[code]), code))
    for code in order[:remainder]:
        floored[code] += 1
    return floored


def shares_by_year_bp(
    by_year: Mapping[int, Mapping[int, float]], *, first_year: int, last_year: int
) -> dict:
    """Class-major, sparse, per-year basis-point series (design D1 rev2).

    Returns ``{firstYear, lastYear, unit: "bp", classes: {code: [int, ...]}}``
    where ``classes[code][i]`` is the basis-point share of ``code`` in year
    ``first_year + i``. Each year's bp values (across every class present
    that year, via ``to_basis_points``) sum to EXACTLY 10000 -- the same
    largest-remainder rounding used by ``land_cover_section``, applied
    independently per year. A class absent from a given year (zero hectares,
    dropped by ``to_basis_points``) reads 0 at that year's index rather than
    being missing from the array (fixed-length arrays: length ==
    ``last_year - first_year + 1``). A class that never has a non-zero share
    in ANY year in range is not a key in ``classes`` at all (sparse).
    """
    n_years = last_year - first_year + 1
    classes: dict[int, list[int]] = {}
    for year in range(first_year, last_year + 1):
        idx = year - first_year
        year_bp = to_basis_points(by_year.get(year, {}))
        for code, bp in year_bp.items():
            classes.setdefault(code, [0] * n_years)[idx] = bp
    return {"firstYear": first_year, "lastYear": last_year, "unit": "bp", "classes": classes}


class ShareSumError(ValueError):
    """Raised when a comuna-year's classified total drifts from its reference year (MCS-3e)."""


def validate_comuna_year_shares(
    by_year: Mapping[int, Mapping[int, float]],
    *,
    comuna_id: str,
    reference_year: int,
    tolerance: float = 0.005,
) -> None:
    """Hard gate (design D2, MCS-3e): abort if any comuna-year's classified
    total hectares drifts by more than ``tolerance`` (relative) from the
    SAME comuna's reference-year total.

    A comuna's classified area is physically stable across years (verified
    against the real xlsx, S1b4: every one of the 86 target comunas' yearly
    total is stable to a fraction of a hectare across 1999-2024), so this
    catches genuine per-year data problems (e.g. a missing class/row for one
    year) rather than being a cosmetic rounding check. Names ``(comuna_id,
    year)`` in the raised error, never renormalizes silently.

    IMPORTANT (S1b4 review): this is a DRIFT detector for row dropouts, NOT
    a double-count gate. It compares each year against the SAME comuna's
    OWN reference-year total, so a double count that is stable across every
    year (e.g. one class summed twice, every year alike) shifts both sides
    of the comparison equally and passes silently -- proven empirically by
    a judge during S1b4 review. Catching a double count needs a source
    independent of the xlsx itself; see
    ``validate_comuna_polygon_area_ratio``, which compares against the
    comuna's own GeoJSON polygon area instead.
    """
    reference_total = sum(by_year[reference_year].values())
    for year, classes in by_year.items():
        total = sum(classes.values())
        fraction = (total / reference_total) if reference_total else 0.0
        if abs(fraction - 1.0) > tolerance:
            raise ShareSumError(
                f"comuna={comuna_id!r} year={year}: classified total {total} ha is "
                f"{fraction:.4f}x the reference year {reference_year}'s total "
                f"{reference_total} ha (tolerance {tolerance})"
            )


class AreaRatioError(ValueError):
    """Raised when a comuna's LULC classified total drifts too far from its
    own GeoJSON polygon's geodesic area (design D2 independent area gate)."""


def validate_comuna_polygon_area_ratio(
    lulc_total_ha: float,
    polygon_ha: float,
    *,
    comuna_id: str,
    tolerance: float = 0.15,
) -> float:
    """Hard gate (design D2): abort if a comuna's LULC classified total
    hectares (reference year, ALL classes including 27/unobserved) drifts by
    more than ``tolerance`` (relative) from that SAME comuna's own GeoJSON
    polygon area (``area.polygon_area_ha``, computed independently of the
    xlsx from the repo's comuna GeoJSON).

    This is INDEPENDENT of ``validate_comuna_year_shares``: that gate can
    only catch a drift that is unstable across years for one comuna, so it
    is blind to a double count that is stable across every year alike (a
    judge proved this empirically during S1b4 review by doubling one
    class's rows in every year and showing the year-drift gate still
    passed). Comparing against the polygon's own area instead catches that
    case directly, because a genuinely doubled class pushes the ratio well
    above 1.0 regardless of year-to-year stability.

    Tolerance (default 15%, i.e. ratio in ``[0.85, 1.15]``): real evidence
    across all 86 comunas in our 3 target regions (S1b4, 2024 reference
    year) gives a ratio distribution of min 0.9009 (Coronel), max 1.0118
    (San Pedro de la Paz), median 1.0007 -- the two comunas below 0.95 are
    both coastal/multi-part (Coronel, Lebu), consistent with the MapBiomas
    political-boundary polygon differing from the GADM-derived polygon at
    the coastline/estuary. 15% gives ~1.5x headroom over that worst real
    deviation (10%) while staying decisively tighter than the ~50-100%+
    deviation a genuine double count of any non-trivial class would produce
    -- it is a principled bound on plausible boundary-source disagreement,
    not fitted to make every comuna pass.
    """
    if polygon_ha <= 0:
        raise AreaRatioError(
            f"comuna={comuna_id!r}: polygon area is {polygon_ha} ha (must be positive)"
        )
    ratio = lulc_total_ha / polygon_ha
    if abs(ratio - 1.0) > tolerance:
        raise AreaRatioError(
            f"comuna={comuna_id!r}: LULC classified total {lulc_total_ha:.1f} ha is "
            f"{ratio:.4f}x its GeoJSON polygon's geodesic area {polygon_ha:.1f} ha "
            f"(tolerance {tolerance:.0%}) -- possible double count or join error"
        )
    return ratio


def land_cover_section(
    by_year: Mapping[int, Mapping[int, float]],
    *,
    reference_year: int,
    mean_years: Sequence[int],
    first_year: int | None = None,
    last_year: int | None = None,
) -> dict:
    """Build the ``landCover`` section for one comuna from its per-year hectares.

    ``sharesByClass`` (the reference year's shares) and ``sharesByClassMean5y``
    (the mean of ``mean_years`` hectares) are FRACTIONS summing to ~1.0
    (design D1) -- basis points are reserved for ``sharesByYear.classes``
    (design D1 rev2, ``shares_by_year_bp``, spanning ``first_year..last_year``
    inclusive, defaulting to the full range of years present in ``by_year``
    when not given explicitly). Internally the largest-remainder bp rounding
    (``to_basis_points``) is used for BOTH representations so the emitted
    fractions are exact multiples of 1/10000, deterministic, reproducible,
    and never drift from the bp series (MCS-1d reference-year consistency).
    """
    if first_year is None:
        first_year = min(by_year)
    if last_year is None:
        last_year = max(by_year)
    reference_bp = to_basis_points(by_year[reference_year])

    total_ha_by_class: dict[int, float] = {}
    for year in mean_years:
        for code, ha in by_year[year].items():
            total_ha_by_class[code] = total_ha_by_class.get(code, 0.0) + ha
    mean_ha_by_class = {code: total / len(mean_years) for code, total in total_ha_by_class.items()}
    mean_bp = to_basis_points(mean_ha_by_class)

    return {
        "referenceYear": reference_year,
        "sharesByClass": {code: bp / BP_TOTAL for code, bp in reference_bp.items()},
        "sharesByClassMean5y": {code: bp / BP_TOTAL for code, bp in mean_bp.items()},
        "sharesByYear": shares_by_year_bp(by_year, first_year=first_year, last_year=last_year),
    }


def build_comuna_document(
    comuna_id: str,
    *,
    land_cover: dict | None,
    fire: dict,
    provenance: dict,
    computed_at: str,
    land_cover_reason: str | None = None,
    partial: bool | None = None,
) -> dict:
    """Assemble one comuna's seed document from its landCover and fire sections.

    ``partial`` is omitted from the document unless explicitly set, so a
    full production seed's shape is unchanged; a caller writing a partial
    seed (e.g. the fire-only 2017 subset, see
    ``scripts/generate_fire_report_2017.py``) passes ``partial=True`` so a
    downstream loader can machine-detect partial status instead of relying
    only on filename/prose.
    """
    doc = {
        "comunaId": comuna_id,
        "landCover": land_cover,
        "fire": fire,
        "provenance": provenance,
        "computedAt": computed_at,
    }
    if land_cover is None:
        doc["landCoverReason"] = land_cover_reason
    if partial is not None:
        doc["partial"] = partial
    return doc


def percentile_rank_burned_fraction(values: Mapping[str, float | None]) -> dict[str, float | None]:
    """Empirical percentile rank of a burned-fraction value across all comunas (design D3).

    ``values`` maps ``comunaId -> the burned-fraction value to rank`` (see
    ``fire_stats.select_burned_fraction_for_pct``), or ``None`` for a comuna
    excluded from the ranking (no usable fire data for that comuna).

    Definition (frozen with ``dataVersion`` once computed -- design D1/D3):

    - A comuna mapped to ``None`` is excluded entirely: it does not count
      toward N (the number of ranked comunas) and its own result is ``None``.
    - Among the remaining N comunas, each value receives the standard 1-based
      "average rank": ties share the mean of the ranks they would occupy if
      every ranked value were sorted ascending (e.g. two tied values at
      positions 1 and 2 both get rank 1.5); a unique maximum gets rank N.
    - The burned-fraction distribution is zero-inflated (most comunas have
      never burned), so an EXACT zero is a special case: it is PINNED to
      0.0 regardless of its average rank, rather than sharing the
      tie-averaged rank of every other zero comuna. Without this pin, a
      dataset that is mostly zeros would push every zero comuna's rank close
      to 0.5, which would misrepresent "no fire at all" as "moderate risk".
    - Every other (non-zero, non-excluded) value's result is its average
      rank divided by N, so the unique maximum in the dataset is exactly 1.0.
    """
    ranked_items = [(comuna_id, value) for comuna_id, value in values.items() if value is not None]
    result: dict[str, float | None] = {
        comuna_id: None for comuna_id, value in values.items() if value is None
    }
    n = len(ranked_items)
    if n == 0:
        return result

    sorted_values = sorted(value for _, value in ranked_items)
    average_rank_by_value: dict[float, float] = {}
    i = 0
    while i < n:
        j = i
        while j < n and sorted_values[j] == sorted_values[i]:
            j += 1
        # 1-based ranks i+1..j (inclusive) are occupied by this tie group;
        # their mean is (i+1+j)/2 regardless of how many values tie.
        average_rank_by_value[sorted_values[i]] = (i + 1 + j) / 2
        i = j

    for comuna_id, value in ranked_items:
        result[comuna_id] = 0.0 if value == 0 else average_rank_by_value[value] / n
    return result


def add_burned_fraction_pct(docs: Sequence[MutableMapping]) -> None:
    """Mutate every doc's ``fire.burnedFractionPct`` in place (design D1/D3).

    The percentile rank is a CROSS-comuna statistic (frozen per
    ``dataVersion``), so this must be called once with the full batch of
    comuna documents being written to a single seed -- never per-comuna,
    which would make every comuna rank 1.0 against itself alone.
    """
    values = {
        doc["comunaId"]: fire_stats.select_burned_fraction_for_pct(doc["fire"]) for doc in docs
    }
    pct_by_comuna = percentile_rank_burned_fraction(values)
    for doc in docs:
        doc["fire"]["burnedFractionPct"] = pct_by_comuna[doc["comunaId"]]


def _linear_interpolation_percentile(sorted_values: Sequence[float], q: float) -> float:
    """The q-th percentile (0<=q<=1) of an already-sorted sequence, linear
    interpolation between closest ranks (numpy's default ``'linear'``
    method) -- used by ``log1p_p95_clip_burned_fraction`` for the p95 cut
    point. No numpy dependency: this pipeline's other modules are
    numpy-free by design (build_stats.py's own module docstring).
    """
    n = len(sorted_values)
    if n == 1:
        return sorted_values[0]
    idx = q * (n - 1)
    lo = int(idx)
    hi = min(lo + 1, n - 1)
    frac = idx - lo
    return sorted_values[lo] + frac * (sorted_values[hi] - sorted_values[lo])


def log1p_p95_clip_burned_fraction(
    values: Mapping[str, float | None], *, p: float = 0.95
) -> dict[str, float | None]:
    """``log1p`` + p95-clip variant of the burned-fraction normalization
    (design D1/D3's ``burnedFractionLog``, a config-switchable alternative
    to ``percentile_rank_burned_fraction`` that the pre-registered backtest
    may select instead).

    Definition: ``log1p`` every ranked (non-excluded) comuna's value, take
    the empirical p-th percentile (linear interpolation, default p=0.95) of
    the LOGGED values, then return ``min(logged, p_value) / p_value`` for
    every ranked comuna -- so the p-th-percentile comuna (and anything
    above it) maps to exactly 1.0, and the mapping is monotonic in the raw
    fraction. Comunas mapped to ``None`` in ``values`` (no usable fire data,
    see ``fire_stats.select_burned_fraction_for_pct``) are excluded from
    the percentile computation entirely and themselves map to ``None`` --
    mirrors ``percentile_rank_burned_fraction``'s exclusion rule exactly, so
    the same ``values`` mapping can feed either function.

    When every ranked value is exactly 0.0 (an all-zero batch), the p-value
    is 0.0 and every ranked comuna maps to 0.0 rather than dividing by zero.
    """
    ranked_items = [(comuna_id, value) for comuna_id, value in values.items() if value is not None]
    result: dict[str, float | None] = {
        comuna_id: None for comuna_id, value in values.items() if value is None
    }
    if not ranked_items:
        return result

    logged = {comuna_id: math.log1p(value) for comuna_id, value in ranked_items}
    p_value = _linear_interpolation_percentile(sorted(logged.values()), p)
    if p_value <= 0:
        for comuna_id in logged:
            result[comuna_id] = 0.0
        return result

    for comuna_id, logged_value in logged.items():
        result[comuna_id] = min(logged_value, p_value) / p_value
    return result


def add_burned_fraction_log(docs: Sequence[MutableMapping]) -> None:
    """Mutate every doc's ``fire.burnedFractionLog`` in place (design D1/D3).

    Uses the SAME underlying value as ``add_burned_fraction_pct``
    (``fire_stats.select_burned_fraction_for_pct`` -- the cumulative/union
    fraction once available, per S1b5), so both normalizations describe the
    same physical quantity through different transforms. A CROSS-comuna
    statistic (the p95 cut point), so this must be called once with the
    full batch being written to a single seed, same rule as
    ``add_burned_fraction_pct``.
    """
    values = {
        doc["comunaId"]: fire_stats.select_burned_fraction_for_pct(doc["fire"]) for doc in docs
    }
    log_by_comuna = log1p_p95_clip_burned_fraction(values)
    for doc in docs:
        doc["fire"]["burnedFractionLog"] = log_by_comuna[doc["comunaId"]]


def write_coverage_report(path: Path, rows: Sequence[Mapping]) -> None:
    """Write the Biobio-gate CSV: one row per processed comuna."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = list(rows[0].keys()) if rows else []
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def write_seed_jsonl(path: Path, docs: Sequence[Mapping]) -> None:
    """Write the seed as one minified JSON document per line."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for doc in docs:
            f.write(json.dumps(doc, separators=(",", ":"), ensure_ascii=False))
            f.write("\n")
