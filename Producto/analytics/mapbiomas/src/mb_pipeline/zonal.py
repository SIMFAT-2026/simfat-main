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


_POLYGONAL = ("Polygon", "MultiPolygon")


def _as_polygonal(geometry: dict) -> dict:
    """Unwrap a GeoJSON Feature and accept only Polygon / MultiPolygon."""
    if not isinstance(geometry, dict):
        raise ValueError(f"Geometry must be a GeoJSON dict, got {type(geometry).__name__}")
    if geometry.get("type") == "Feature":
        geometry = geometry.get("geometry")
        if not isinstance(geometry, dict):
            raise ValueError("GeoJSON Feature has no geometry")
    kind = geometry.get("type")
    if kind not in _POLYGONAL:
        raise ValueError(
            f"Unsupported geometry type {kind!r}: expected Polygon, MultiPolygon "
            "or a Feature wrapping one"
        )
    return geometry


def _validate_raster(src, band: int) -> None:
    t = src.transform
    if t.b != 0 or t.d != 0 or t.e >= 0 or t.a <= 0:
        raise ValueError(
            "Raster must be north-up and unrotated (b == d == 0, e < 0); got "
            f"transform {tuple(t)[:6]}"
        )
    if not np.issubdtype(np.dtype(src.dtypes[band - 1]), np.integer):
        raise ValueError(
            f"Expected an integer raster of class codes, got dtype {src.dtypes[band - 1]}"
        )
    if src.crs is None or src.crs.to_epsg() != 4326:
        raise ValueError(f"Raster CRS must be EPSG:4326, got {src.crs}")


def union_zonal_stats(
    raster_paths, geometry: dict, *, band: int = 1, union_value: int = 1
) -> ZonalResult:
    """Pixel-level union ('value == union_value in ANY of ``raster_paths``')
    zonal stats across multiple rasters that share an IDENTICAL grid (same
    transform) -- e.g. the 13 MapBiomas Fuego Collection 1 ``annual_burned_v1``
    rasters for 2013..2025 (verified same origin/size/pixel-size for 2013,
    2017 and 2025, S1b5).

    A pixel that is ``union_value`` in MORE THAN ONE raster counts ONCE, never
    summed -- this is what makes it a real pixel-level union rather than
    ``sum(zonal_stats(year).value_area_ha[union_value] for year in years)``,
    which double-counts any pixel that burned more than once (design D1/MCS-10:
    ``max(annual) <= union <= sum(annual)``).

    Raises ``ValueError`` if the rasters do not share an identical transform:
    OR-ing misaligned pixel grids would silently produce a meaningless result.
    """
    geometry = _as_polygonal(geometry)
    reference_transform = None
    mask = None
    pixel_ha = None
    ever = None

    for path in raster_paths:
        with rasterio.open(path) as src:
            _validate_raster(src, band)
            if reference_transform is None:
                reference_transform = src.transform
            elif tuple(src.transform)[:6] != tuple(reference_transform)[:6]:
                raise ValueError(
                    f"raster grid mismatch: {path} has a different transform than "
                    "the first raster -- union_zonal_stats requires an identical grid"
                )
            window = _window_for(src, geometry)
            if window is None or window.width < 1 or window.height < 1:
                return ZonalResult()
            data = src.read(band, window=window, masked=False)
            transform = src.window_transform(window)

        if mask is None:
            mask = geometry_mask(
                [geometry], out_shape=data.shape, transform=transform, all_touched=False, invert=True
            )
            if not mask.any():
                return ZonalResult()
            areas_ha = (
                row_pixel_areas_m2(transform.f, transform.a, -transform.e, data.shape[0]) / M2_PER_HA
            )
            pixel_ha = np.broadcast_to(areas_ha[:, None], data.shape)[mask]
            ever = np.zeros(pixel_ha.shape, dtype=bool)
        elif data.shape != mask.shape:
            raise ValueError(
                f"raster grid mismatch: {path} has a different shape than the first raster"
            )

        ever |= data[mask] == union_value

    if ever is None:
        return ZonalResult()

    total_pixels = int(mask.sum())
    total_ha = float(pixel_ha.sum())
    burned_pixels = int(ever.sum())
    burned_ha = float(pixel_ha[ever].sum())
    return ZonalResult(
        pixel_count=total_pixels,
        area_ha=total_ha,
        value_pixels={0: total_pixels - burned_pixels, union_value: burned_pixels},
        value_area_ha={0: total_ha - burned_ha, union_value: burned_ha},
    )


def zonal_stats(raster_path, geometry: dict, band: int = 1) -> ZonalResult:
    """Pixel counts and corrected area per raster value inside ``geometry``."""
    geometry = _as_polygonal(geometry)
    with rasterio.open(raster_path) as src:
        _validate_raster(src, band)
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
