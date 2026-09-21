"""Windowed masked zonal statistics with per-row pixel-area correction.

Rasters are EPSG:4326 with square-in-degrees pixels, so the ground area of a
pixel depends on its latitude. The area is applied per raster ROW:

    A(phi) = (dlon * 111320 * cos(phi)) * (dlat * 110540)   [m^2]

Zero is data, not nodata: rasters are read unmasked and any declared nodata
value is ignored on purpose (burned-area products use 0 for "not burned").
A pixel belongs to a polygon iff its center is inside (``all_touched=False``).
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np
import rasterio
from rasterio.errors import WindowError
from rasterio.features import geometry_mask
from rasterio.windows import Window, from_bounds

M_PER_DEG_LON_EQUATOR = 111320.0
M_PER_DEG_LAT = 110540.0
M2_PER_HA = 10_000.0


@dataclass(frozen=True)
class ZonalResult:
    pixel_count: int = 0
    area_ha: float = 0.0
    value_pixels: dict[int, int] = field(default_factory=dict)
    value_area_ha: dict[int, float] = field(default_factory=dict)


def pixel_area_m2(lat_deg: float, dlon: float, dlat: float) -> float:
    """Ground area of one pixel centered at ``lat_deg`` (deg pixel sizes)."""
    width = dlon * M_PER_DEG_LON_EQUATOR * math.cos(math.radians(lat_deg))
    return width * (dlat * M_PER_DEG_LAT)


def row_pixel_areas_m2(top: float, dlon: float, dlat: float, n_rows: int) -> np.ndarray:
    """Pixel area for each of ``n_rows`` rows below latitude ``top`` (north-up)."""
    centers = top - (np.arange(n_rows) + 0.5) * dlat
    return (dlon * M_PER_DEG_LON_EQUATOR * np.cos(np.radians(centers))) * (dlat * M_PER_DEG_LAT)


def _window_for(src, geometry: dict) -> Window | None:
    coords = np.array(_flatten(geometry["coordinates"]))
    bounds = (coords[:, 0].min(), coords[:, 1].min(), coords[:, 0].max(), coords[:, 1].max())
    window = from_bounds(*bounds, transform=src.transform)
    window = window.round_offsets(op="floor").round_lengths(op="ceil")
    try:
        return window.intersection(Window(0, 0, src.width, src.height))
    except WindowError:
        return None


def _flatten(coords):
    if coords and isinstance(coords[0], (int, float)):
        return [coords]
    return [pt for part in coords for pt in _flatten(part)]


def zonal_stats(raster_path, geometry: dict, band: int = 1) -> ZonalResult:
    """Pixel counts and corrected area per raster value inside ``geometry``."""
    with rasterio.open(raster_path) as src:
        window = _window_for(src, geometry)
        if window is None or window.width < 1 or window.height < 1:
            return ZonalResult()
        data = src.read(band, window=window, masked=False)
        transform = src.window_transform(window)
    inside = geometry_mask(
        [geometry], out_shape=data.shape, transform=transform, all_touched=False, invert=True
    )
    if not inside.any():
        return ZonalResult()
    areas_ha = (
        row_pixel_areas_m2(transform.f, transform.a, -transform.e, data.shape[0]) / M2_PER_HA
    )
    pixel_ha = np.broadcast_to(areas_ha[:, None], data.shape)[inside]
    values = data[inside]
    value_pixels: dict[int, int] = {}
    value_area: dict[int, float] = {}
    for value in np.unique(values):
        sel = values == value
        value_pixels[int(value)] = int(sel.sum())
        value_area[int(value)] = float(pixel_ha[sel].sum())
    return ZonalResult(
        pixel_count=int(inside.sum()),
        area_ha=float(pixel_ha.sum()),
        value_pixels=value_pixels,
        value_area_ha=value_area,
    )
