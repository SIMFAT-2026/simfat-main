"""Windowed masked zonal statistics with per-row pixel-area correction.

Rasters are EPSG:4326 with square-in-degrees pixels, so the ground area of a
pixel depends on its latitude. The area is applied per raster ROW:

    A(phi) = (M(phi) * dlat) * (N(phi) * cos(phi) * dlon)   [m^2, angles in rad]

with M the meridian and N the prime-vertical radii of curvature of the WGS84
ellipsoid, evaluated at the row-centre latitude.

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

WGS84_A = 6378137.0
WGS84_F = 1 / 298.257223563
WGS84_E2 = WGS84_F * (2 - WGS84_F)
M2_PER_HA = 10_000.0


@dataclass(frozen=True)
class ZonalResult:
    pixel_count: int = 0
    area_ha: float = 0.0
    value_pixels: dict[int, int] = field(default_factory=dict)
    value_area_ha: dict[int, float] = field(default_factory=dict)


def row_pixel_areas_m2(top: float, dlon: float, dlat: float, n_rows: int) -> np.ndarray:
    """WGS84 ellipsoidal pixel area for each of ``n_rows`` rows below ``top`` (north-up)."""
    phi = np.radians(top - (np.arange(n_rows) + 0.5) * dlat)
    w = 1.0 - WGS84_E2 * np.sin(phi) ** 2
    meridian = WGS84_A * (1.0 - WGS84_E2) / w**1.5
    prime_vertical = WGS84_A / np.sqrt(w)
    return (meridian * math.radians(dlat)) * (prime_vertical * np.cos(phi) * math.radians(dlon))


def pixel_area_m2(lat_deg: float, dlon: float, dlat: float) -> float:
    """Ground area of one pixel centred at ``lat_deg`` (single source: the row formula)."""
    return float(row_pixel_areas_m2(lat_deg + dlat / 2, dlon, dlat, 1)[0])


def _window_for(src, geometry: dict) -> Window | None:
    coords = np.array(_flatten(geometry["coordinates"]))
    bounds = (coords[:, 0].min(), coords[:, 1].min(), coords[:, 0].max(), coords[:, 1].max())
    exact = from_bounds(*bounds, transform=src.transform)
    # Round the near and far edges independently so the window always covers
    # every pixel the polygon can reach, whatever fraction the offset has.
    col0, row0 = math.floor(exact.col_off), math.floor(exact.row_off)
    col1 = math.ceil(exact.col_off + exact.width)
    row1 = math.ceil(exact.row_off + exact.height)
    window = Window(col0, row0, col1 - col0, row1 - row0)
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
