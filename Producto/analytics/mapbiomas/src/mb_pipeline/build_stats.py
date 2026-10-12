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


def land_cover_section(
    by_year: Mapping[int, Mapping[int, float]],
    *,
    reference_year: int,
    mean_years: Sequence[int],
) -> dict:
    """Build the ``landCover`` section for one comuna from its per-year hectares.

    ``sharesByClass`` (the reference year's shares) and ``sharesByClassMean5y``
    (the mean of ``mean_years`` hectares) are FRACTIONS summing to ~1.0 (design
    D1) -- basis points are reserved for ``sharesByYear.classes`` only (not
    implemented in this slice). Internally the largest-remainder bp rounding
    (``to_basis_points``) is still used so the emitted fractions are exact
    multiples of 1/10000, deterministic and reproducible, then divided by
    ``BP_TOTAL`` before being returned.
    """
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
