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

import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence

import numpy as np

from mb_pipeline import zonal
from study import firms_target

MIN_SCAR_HA = 250.0  # Section 2, H1
BUFFER_M = 375.0  # Section 2, H1: one VIIRS pixel
M2_PER_HA = zonal.M2_PER_HA
YEARS: tuple[int, ...] = tuple(range(2013, 2026))  # the 13 annual Fuego rasters


class H1InputError(ValueError):
    """An H1 input is missing or inconsistent; the run aborts rather than guess."""


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


def metres_per_degree(lat_deg):
    """``(metres per degree of longitude, metres per degree of latitude)`` on the
    WGS84 ellipsoid at ``lat_deg``: ``N cos(phi) pi/180`` and ``M pi/180`` with
    the same ``M``/``N`` radii of curvature the pipeline's pixel-area formula
    uses (``mb_pipeline.zonal.row_pixel_areas_m2``). Works on scalars and arrays."""
    phi = np.radians(lat_deg)
    w = 1.0 - zonal.WGS84_E2 * np.sin(phi) ** 2
    meridian = zonal.WGS84_A * (1.0 - zonal.WGS84_E2) / w**1.5
    prime_vertical = zonal.WGS84_A / np.sqrt(w)
    return prime_vertical * np.cos(phi) * math.pi / 180.0, meridian * math.pi / 180.0


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


# ---------------------------------------------------------------------------
# Detections within the buffer of a scar
# ---------------------------------------------------------------------------

BUFFER_TOLERANCE_M = 1e-6  # float slack so a detection built at exactly 375 m is in


def detections_in_buffer(
    scars: Scars,
    grid: Grid,
    shape: tuple[int, int],
    det_lon,
    det_lat,
    *,
    buffer_m: float = BUFFER_M,
    chunk: int = 1000,
) -> np.ndarray:
    """Number of distinct detections within ``buffer_m`` of each scar.

    A detection is within the buffer of a scar iff its Euclidean distance to the
    nearest burned pixel SQUARE of the scar is ``<= buffer_m`` (distance 0 when
    it lies inside a burned pixel), i.e. it lies inside the scar polygon
    buffered by ``buffer_m``. Distances are in local WGS84 metres: degree offsets
    are scaled by ``metres_per_degree`` at the detection's own latitude, so the
    buffer is longer in pixels along rows than along columns and shrinks in
    columns toward the poles.

    Method (exact, no dilation): for every detection the burned pixels of the
    kept scars in a window of pixels that surely covers the buffer are looked
    up by binary search on the sorted pixel keys, and the point-to-square
    distance is computed for each hit. ``shape`` is the (rows, cols) of the grid
    the scar pixels live in; detections outside it (beyond the buffer) match
    nothing. ``chunk`` bounds memory only.
    """
    k = len(scars.table)
    counts = np.zeros(k, dtype=np.int64)
    det_lon = np.asarray(det_lon, dtype=float)
    det_lat = np.asarray(det_lat, dtype=float)
    if k == 0 or det_lon.size == 0:
        return counts

    h, w = shape
    keys = scars.rows * w + scars.cols
    order = np.argsort(keys, kind="stable")
    skeys = keys[order]
    sscar = scars.scar[order]

    # Window half-sizes in pixels, from the smallest pixel (in metres) of the grid.
    lat_extreme = max(abs(grid.north), abs(grid.north - h * grid.dlat))
    mx_edge, _ = metres_per_degree(lat_extreme)
    _, my_edge = metres_per_degree(0.0)
    half_c = int(math.ceil(buffer_m / (float(mx_edge) * grid.dlon))) + 1
    half_r = int(math.ceil(buffer_m / (float(my_edge) * grid.dlat))) + 1
    dr = np.arange(-half_r, half_r + 1)
    dc = np.arange(-half_c, half_c + 1)

    for start in range(0, det_lon.size, chunk):
        lon = det_lon[start:start + chunk]
        lat = det_lat[start:start + chunk]
        row0 = np.floor((grid.north - lat) / grid.dlat).astype(np.int64)
        col0 = np.floor((lon - grid.west) / grid.dlon).astype(np.int64)
        near = (row0 >= -half_r) & (row0 < h + half_r) & (col0 >= -half_c) & (col0 < w + half_c)
        if not near.any():
            continue
        lon, lat, row0, col0 = lon[near], lat[near], row0[near], col0[near]
        det_i = np.arange(lon.size)

        rr = row0[:, None, None] + dr[None, :, None]
        cc = col0[:, None, None] + dc[None, None, :]
        rr, cc = np.broadcast_arrays(rr, cc)
        valid = (rr >= 0) & (rr < h) & (cc >= 0) & (cc < w)
        key = np.where(valid, rr * w + cc, -1)
        pos = np.searchsorted(skeys, key.ravel())
        pos_c = np.minimum(pos, skeys.size - 1)
        hit = (skeys[pos_c] == key.ravel()) & valid.ravel()
        if not hit.any():
            continue
        d_idx = np.broadcast_to(det_i[:, None, None], rr.shape).ravel()[hit]
        r_hit = rr.ravel()[hit]
        c_hit = cc.ravel()[hit]
        scar_hit = sscar[pos_c[hit]]

        mx, my = metres_per_degree(lat[d_idx])
        cx = grid.west + (c_hit + 0.5) * grid.dlon
        cy = grid.north - (r_hit + 0.5) * grid.dlat
        dx = np.maximum(np.abs(lon[d_idx] - cx) - grid.dlon / 2.0, 0.0) * mx
        dy = np.maximum(np.abs(lat[d_idx] - cy) - grid.dlat / 2.0, 0.0) * my
        within = np.hypot(dx, dy) <= buffer_m + BUFFER_TOLERANCE_M
        if not within.any():
            continue
        pairs = np.unique(d_idx[within] * k + scar_hit[within])
        counts += np.bincount(pairs % k, minlength=k)
    return counts


# ---------------------------------------------------------------------------
# FIRMS: filtered detections by LOCAL calendar year, comuna assignment
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class FilteredDetections:
    """Detections passing the pre-registered filter (type 0, confidence n/h):
    longitude, latitude and America/Santiago LOCAL calendar year. ``n_rows`` is
    the size of the archive, ``n_filtered`` the number kept."""

    lon: np.ndarray
    lat: np.ndarray
    year: np.ndarray
    n_rows: int
    n_filtered: int


def read_filtered_detections(zip_path: Path) -> FilteredDetections:
    """Reads the FIRMS archive and keeps ``type == 0`` and confidence in
    ``{n, h}`` (the same filter as the backtest target,
    ``firms_target.PRIMARY_TYPE_VALUES`` / ``CONFIDENCE_FILTER``). The year is the
    local calendar year after the same UTC -> Chile conversion the target uses
    (``firms_target.to_local_date``, DEVIATIONS N2). No detection is dropped for
    lying outside the comunas: the buffer test needs those near the border."""
    lon: list[float] = []
    lat: list[float] = []
    year: list[int] = []
    n_rows = 0
    for row in firms_target.iter_firms_rows(Path(zip_path)):
        n_rows += 1
        if row["confidence"] not in firms_target.CONFIDENCE_FILTER:
            continue
        if int(row["type"]) not in firms_target.PRIMARY_TYPE_VALUES:
            continue
        dt_utc = firms_target.parse_acq_datetime_utc(row["acq_date"], row["acq_time"])
        lon.append(float(row["longitude"]))
        lat.append(float(row["latitude"]))
        year.append(firms_target.to_local_date(dt_utc).year)
    return FilteredDetections(
        lon=np.asarray(lon, dtype=float),
        lat=np.asarray(lat, dtype=float),
        year=np.asarray(year, dtype=np.int64),
        n_rows=n_rows,
        n_filtered=len(lon),
    )


def _bbox(geometry: dict) -> tuple[float, float, float, float]:
    xs, ys = [], []
    for exterior, _ in firms_target.era5.polygon_rings(geometry):
        xs.extend(p[0] for p in exterior)
        ys.extend(p[1] for p in exterior)
    return min(xs), min(ys), max(xs), max(ys)


def assign_comunas(lons, lats, polygons: Mapping[str, dict]) -> list:
    """``firms_target.assign_comuna`` for many points: the first comuna (in
    ``polygons`` order) whose polygon contains the point, holes excluded, or
    ``None``. A bounding-box test skips polygons that cannot contain the point;
    it never changes the answer (a test compares both on random points)."""
    boxes = {cid: _bbox(g) for cid, g in polygons.items()}
    out: list = []
    for x, y in zip(np.asarray(lons, float).tolist(), np.asarray(lats, float).tolist()):
        found = None
        for cid, geometry in polygons.items():
            x0, y0, x1, y1 = boxes[cid]
            if x0 <= x <= x1 and y0 <= y <= y1 and firms_target.point_in_geometry((x, y), geometry):
                found = cid
                break
        out.append(found)
    return out


def comuna_year_counts(
    det: FilteredDetections, polygons: Mapping[str, dict], *, years: Sequence[int] = YEARS
) -> tuple[dict, int]:
    """``({(comunaId, year): count}, n_unassigned)`` of filtered detections by
    local calendar year inside each comuna, zeros included for every comuna and
    year. Detections of years outside ``years`` are ignored; detections inside no
    comuna are counted in ``n_unassigned`` (among the in-range years)."""
    counts = {(cid, y): 0 for cid in polygons for y in years}
    in_range = np.isin(det.year, np.asarray(list(years)))
    lons, lats, yrs = det.lon[in_range], det.lat[in_range], det.year[in_range]
    n_unassigned = 0
    for cid, y in zip(assign_comunas(lons, lats, polygons), yrs.tolist()):
        if cid is None:
            n_unassigned += 1
        else:
            counts[(cid, y)] += 1
    return counts, n_unassigned


# ---------------------------------------------------------------------------
# Burned fractions from the committed seed, and the secondary table
# ---------------------------------------------------------------------------


def load_burned_fraction_by_year(seed_path: Path) -> dict[str, dict[int, float]]:
    """``{comunaId: {year: burnedFractionByYear}}`` from the committed seed (the
    same file the backtest's predictors came from)."""
    out: dict[str, dict[int, float]] = {}
    with open(seed_path, encoding="utf-8") as f:
        for line in f:
            doc = json.loads(line)
            by_year = (doc.get("fire") or {}).get("burnedFractionByYear")
            if not by_year:
                raise H1InputError(f"{doc.get('comunaId')}: seed has no fire.burnedFractionByYear")
            out[doc["comunaId"]] = {int(y): float(v) for y, v in by_year.items()}
    return out


def secondary_rows(
    fractions: Mapping[str, Mapping[int, float]],
    counts: Mapping[tuple, int],
    *,
    comuna_ids: Sequence[str],
    years: Sequence[int] = YEARS,
) -> list[dict]:
    """One row per comuna and year: annual burned fraction and filtered FIRMS
    count. A missing pair is an error, never a silent zero."""
    rows = []
    for cid in comuna_ids:
        for y in years:
            if cid not in fractions or y not in fractions[cid]:
                raise H1InputError(f"no burned fraction for {cid} {y}")
            if (cid, y) not in counts:
                raise H1InputError(f"no FIRMS count for {cid} {y}")
            rows.append(
                {"comunaId": cid, "year": y, "burned_fraction": fractions[cid][y], "firms_count": counts[(cid, y)]}
            )
    return rows
