"""Assemble per-comuna documents from LULC shares and Fuego zonal stats.

This module has two independent halves:

- pure assembly (``to_basis_points``, ``land_cover_section``,
  ``build_comuna_document``) -- no I/O, fully covered by synthetic fixtures
  in ``tests/test_build_stats.py``.
- writers (``write_coverage_report``, ``write_seed_jsonl``) -- plain file
  I/O with a fixed, tested shape.

Land-cover shares are stored in basis points (bp, 1/100 of a percent) as
integers, largest-remainder rounded so every comuna-year's shares sum to
EXACTLY 10000 -- never 9999 or 10001 from naive per-class rounding.
"""
from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import Mapping, Sequence

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

    ``sharesByClass`` is the reference year's shares in bp; ``sharesByClassMean5y``
    is the mean of ``mean_years`` hectares, converted to bp the same way.
    """
    reference_shares = to_basis_points(by_year[reference_year])

    total_ha_by_class: dict[int, float] = {}
    for year in mean_years:
        for code, ha in by_year[year].items():
            total_ha_by_class[code] = total_ha_by_class.get(code, 0.0) + ha
    mean_ha_by_class = {code: total / len(mean_years) for code, total in total_ha_by_class.items()}
    mean_shares = to_basis_points(mean_ha_by_class)

    return {
        "referenceYear": reference_year,
        "sharesByClass": reference_shares,
        "sharesByClassMean5y": mean_shares,
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
