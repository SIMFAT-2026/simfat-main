"""MapBiomas Chile Collection 2 legend as an explicit parent -> children tree.

Only LEAF classes may be aggregated. A code that is an ancestor of another code
in the same aggregation would double count area, so the pipeline aborts
(``LegendError``) instead of silently renormalizing.
"""
from __future__ import annotations

from typing import Iterable

UNOBSERVED_CODE = 27


class LegendError(ValueError):
    """Raised when the legend invariants are violated."""


# parent code -> ordered children
_TREE: dict[int, tuple[int, ...]] = {
    1: (3,),
    3: (59, 60, 67),
    10: (11, 12, 63, 66, 29),
    14: (9, 18, 15),
    22: (24, 23, 61, 25),
    26: (33, 34),
}

_LABELS: dict[int, str] = {
    1: "Forest formation",
    3: "Forest",
    59: "Primary forest",
    60: "Secondary forest",
    67: "Dwarf forest",
    10: "Natural non-forest",
    11: "Wetland",
    12: "Grassland",
    63: "Steppe",
    66: "Shrubland",
    29: "Rocky outcrop",
    14: "Farming and silviculture",
    9: "Silviculture",
    18: "Agriculture",
    15: "Pasture",
    22: "Non-vegetated",
    24: "Infrastructure",
    23: "Beach, dune and sand",
    61: "Salt flat",
    25: "Other non-vegetated",
    26: "Water bodies",
    33: "River, lake and ocean",
    34: "Ice and snow",
    UNOBSERVED_CODE: "Not observed",
}

_PARENT: dict[int, int] = {c: p for p, kids in _TREE.items() for c in kids}


def children(code: int) -> tuple[int, ...]:
    return _TREE.get(code, ())


def is_leaf(code: int) -> bool:
    return code not in _TREE


def ancestors(code: int) -> tuple[int, ...]:
    """Ancestors from the direct parent up to the root."""
    chain = []
    while code in _PARENT:
        code = _PARENT[code]
        chain.append(code)
    return tuple(chain)


def label(code: int) -> str:
    require_known([code])
    return _LABELS[code]


def is_observed(code: int) -> bool:
    return code != UNOBSERVED_CODE


def require_known(codes: Iterable[int]) -> None:
    unknown = sorted(set(codes) - _LABELS.keys())
    if unknown:
        raise LegendError(f"Class codes not in the legend: {unknown}")


def overlapping_pairs(codes: Iterable[int]) -> list[tuple[int, int]]:
    """Return sorted (ancestor, descendant) pairs present in the set."""
    used = set(codes)
    require_known(used)
    return sorted(
        (ancestor, code)
        for code in used
        for ancestor in ancestors(code)
        if ancestor in used
    )


def assert_disjoint(codes: Iterable[int]) -> None:
    """Fail loudly if any code is an ancestor of another code in the set."""
    pairs = overlapping_pairs(codes)
    if pairs:
        ancestor, code = pairs[0]
        raise LegendError(
            f"Class {ancestor} is an ancestor of class {code}; aggregating "
            f"both would double count area (all overlapping pairs: {pairs})"
        )
