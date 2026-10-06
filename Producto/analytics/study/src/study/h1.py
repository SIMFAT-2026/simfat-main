"""H1: agreement between MapBiomas Fuego (burn scars) and NASA FIRMS (active
fire) -- PREREGISTRATION.md Section 2, H1.

H1 has NO decision power. It characterizes agreement and is reported as is.

- PRIMARY: share of Fuego scars >= 250 ha (connected burned pixels in an annual
  raster) with >= 1 filtered FIRMS detection (type 0, confidence n/h) within the
  scar plus a 375 m buffer (one VIIRS pixel) in the same calendar year.
- SECONDARY: Spearman correlation between per-comuna annual burned fraction and
  the filtered FIRMS count (86 comunas x 13 years, 2013-2025).

Stdlib + numpy + rasterio only (no scipy). Every choice the pre-registration
leaves open is recorded in DEVIATIONS.md (N18 onward).
"""
from __future__ import annotations

import numpy as np


# ---------------------------------------------------------------------------
# Connected components of burned pixels (8-connectivity), numpy only
# ---------------------------------------------------------------------------

# Forward neighbours only: every 8-connected pair appears exactly once.
_FORWARD_OFFSETS = ((0, 1), (1, -1), (1, 0), (1, 1))


def connected_components(rows, cols, width: int) -> np.ndarray:
    """Labels the 8-connected components of a set of pixels.

    ``rows``/``cols`` are the coordinates of the pixels (``np.nonzero`` order is
    not required, but any order is accepted); ``width`` is the number of columns
    of the grid they live in. Returns one compact label (0..k-1) per input
    pixel, in input order.

    Method: the pixels are sorted by their flattened index, every forward
    neighbour (E, SW, S, SE) is found by binary search, and the resulting edge
    list is reduced by vectorised hook-and-compress union-find: each round
    hooks the larger root of every unresolved edge onto the smaller one
    (``minimum.at``), then fully compresses the parent pointers. Roots only ever
    hook to a smaller root, so no cycle can form, and the unresolved edge set
    shrinks every round.
    """
    rows = np.asarray(rows, dtype=np.int64)
    cols = np.asarray(cols, dtype=np.int64)
    n = rows.shape[0]
    if n == 0:
        return np.zeros(0, dtype=np.int64)

    keys = rows * width + cols
    order = np.argsort(keys, kind="stable")
    skeys = keys[order]
    scols = cols[order]

    edge_a: list[np.ndarray] = []
    edge_b: list[np.ndarray] = []
    for dr, dc in _FORWARD_OFFSETS:
        ok = np.ones(n, dtype=bool)
        if dc == 1:
            ok &= scols + 1 < width
        elif dc == -1:
            ok &= scols >= 1
        src = np.flatnonzero(ok)
        target = skeys[src] + dr * width + dc
        pos = np.searchsorted(skeys, target)
        pos_clipped = np.minimum(pos, n - 1)
        hit = (pos < n) & (skeys[pos_clipped] == target)
        edge_a.append(src[hit])
        edge_b.append(pos_clipped[hit])

    a = np.concatenate(edge_a)
    b = np.concatenate(edge_b)
    parent = np.arange(n, dtype=np.int64)
    while a.size:
        pa, pb = parent[a], parent[b]
        unresolved = pa != pb
        if not unresolved.any():
            break
        a, b, pa, pb = a[unresolved], b[unresolved], pa[unresolved], pb[unresolved]
        np.minimum.at(parent, np.maximum(pa, pb), np.minimum(pa, pb))
        while True:
            grand = parent[parent]
            if np.array_equal(grand, parent):
                break
            parent = grand

    _, compact = np.unique(parent, return_inverse=True)
    labels = np.empty(n, dtype=np.int64)
    labels[order] = compact
    return labels
