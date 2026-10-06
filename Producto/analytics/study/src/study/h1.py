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

import math
from dataclasses import dataclass

import numpy as np

from mb_pipeline import zonal

MIN_SCAR_HA = 250.0  # Section 2, H1
BUFFER_M = 375.0  # Section 2, H1: one VIIRS pixel
M2_PER_HA = zonal.M2_PER_HA


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


# ---------------------------------------------------------------------------
# Local metric scale and the raster grid
# ---------------------------------------------------------------------------


def metres_per_degree(lat_deg: float) -> tuple[float, float]:
    """``(metres per degree of longitude, metres per degree of latitude)`` on the
    WGS84 ellipsoid at ``lat_deg``: ``N cos(phi) pi/180`` and ``M pi/180`` with
    the same ``M``/``N`` radii of curvature the pipeline's pixel-area formula
    uses (``mb_pipeline.zonal.row_pixel_areas_m2``)."""
    phi = math.radians(lat_deg)
    w = 1.0 - zonal.WGS84_E2 * math.sin(phi) ** 2
    meridian = zonal.WGS84_A * (1.0 - zonal.WGS84_E2) / w**1.5
    prime_vertical = zonal.WGS84_A / math.sqrt(w)
    return prime_vertical * math.cos(phi) * math.pi / 180.0, meridian * math.pi / 180.0


@dataclass(frozen=True)
class Grid:
    """North-up EPSG:4326 grid: ``west``/``north`` are the outer corner of pixel
    (0, 0); ``dlon``/``dlat`` are positive pixel sizes in degrees."""

    west: float
    north: float
    dlon: float
    dlat: float


# ---------------------------------------------------------------------------
# Scars: connected burned pixels inside the union of the monitored comunas
# ---------------------------------------------------------------------------

_NEIGHBOURS = tuple((dr, dc) for dr in (-1, 0, 1) for dc in (-1, 0, 1) if (dr, dc) != (0, 0))


@dataclass
class Scars:
    """Scars kept by the area filter. ``table`` has one dict per scar; ``rows``,
    ``cols`` and ``scar`` list the pixels of the kept scars (``scar`` indexes
    ``table``)."""

    table: list
    rows: np.ndarray
    cols: np.ndarray
    scar: np.ndarray


def find_scars(burned, inside, comuna_idx, comuna_ids, grid: Grid, *, min_ha: float = MIN_SCAR_HA) -> Scars:
    """Labels the 8-connected components of ``burned & inside`` and keeps those
    whose area is ``>= min_ha``.

    ``comuna_idx`` holds 0 outside every comuna and ``i + 1`` for comuna
    ``comuna_ids[i]``. A scar that crosses a comuna border stays one scar; the
    clip is to the union of the monitored comunas only.

    Per scar: ``area_ha`` (sum of the pipeline's WGS84 per-row pixel areas),
    ``n_pixels``, ``comunas`` (sorted ids touched), ``edge_flag`` (a scar pixel
    has an 8-neighbour outside the union or outside the window), ``truncated``
    (a scar pixel has a burned 8-neighbour outside the union, so the true scar
    continues beyond the clip) and the pixel-mean ``lat``/``lon``.
    """
    burned = np.asarray(burned, dtype=bool)
    inside = np.asarray(inside, dtype=bool)
    h, w = burned.shape
    rows, cols = np.nonzero(burned & inside)
    empty = Scars([], np.zeros(0, dtype=np.int64), np.zeros(0, dtype=np.int64), np.zeros(0, dtype=np.int64))
    if rows.size == 0:
        return empty

    labels = connected_components(rows, cols, w)
    n_labels = int(labels.max()) + 1
    row_ha = zonal.row_pixel_areas_m2(grid.north, grid.dlon, grid.dlat, h) / M2_PER_HA
    area = np.bincount(labels, weights=row_ha[rows], minlength=n_labels)
    keep = area >= min_ha
    if not keep.any():
        return empty

    new_id = np.cumsum(keep) - 1
    pixel_kept = keep[labels]
    rows, cols = rows[pixel_kept], cols[pixel_kept]
    scar = new_id[labels[pixel_kept]]
    k = int(keep.sum())

    edge = np.zeros(k, dtype=bool)
    truncated = np.zeros(k, dtype=bool)
    for dr, dc in _NEIGHBOURS:
        nr, nc = rows + dr, cols + dc
        in_window = (nr >= 0) & (nr < h) & (nc >= 0) & (nc < w)
        nr_c, nc_c = np.clip(nr, 0, h - 1), np.clip(nc, 0, w - 1)
        nb_inside = in_window & inside[nr_c, nc_c]
        edge[scar[~nb_inside]] = True
        truncated[scar[in_window & ~inside[nr_c, nc_c] & burned[nr_c, nc_c]]] = True

    pairs = np.unique(scar * 256 + comuna_idx[rows, cols].astype(np.int64))
    comunas: list[list[str]] = [[] for _ in range(k)]
    for pair in pairs.tolist():
        scar_i, idx = divmod(pair, 256)
        if idx > 0:
            comunas[scar_i].append(comuna_ids[idx - 1])

    n_pixels = np.bincount(scar, minlength=k)
    mean_r = np.bincount(scar, weights=rows, minlength=k) / n_pixels
    mean_c = np.bincount(scar, weights=cols, minlength=k) / n_pixels
    table = [
        {
            "area_ha": float(area[keep][i]),
            "n_pixels": int(n_pixels[i]),
            "comunas": sorted(comunas[i]),
            "edge_flag": bool(edge[i]),
            "truncated": bool(truncated[i]),
            "lat": float(grid.north - (mean_r[i] + 0.5) * grid.dlat),
            "lon": float(grid.west + (mean_c[i] + 0.5) * grid.dlon),
        }
        for i in range(k)
    ]
    return Scars(table=table, rows=rows, cols=cols, scar=scar)
