"""Dependency-free WGS84 geodesic polygon area, for the D2 independent area
gate (S1b4): sanity-checking the LULC-classified total hectares of a comuna
against the area of that comuna's own GeoJSON polygon, as an independent
cross-check that cannot be fooled by a double-counted LULC row.

No pyproj/shapely dependency is added -- ``requirements.txt`` only carries
numpy, rasterio and openpyxl, and this pipeline avoids adding a new heavy
geodesy dependency for one sanity gate. Instead this module uses the
standard line-integral formula for the area of a polygon on a sphere
(the same algorithm used by turf.js's ``area`` module, originally from
Chris Veness' geodesy library), evaluated on the WGS84 ELLIPSOID's
*authalic* radius (the radius of the sphere with the same total surface
area as the WGS84 ellipsoid, ``WGS84_AUTHALIC_RADIUS_M`` below). This is a
well-known, widely used lightweight substitute for a full geodesic library
(e.g. ``pyproj.Geod``) and matches the true ellipsoidal geodesic area to
within a small fraction of a percent for regional (comuna-sized) polygons
-- see ``tests/test_area.py``, which cross-checks it against
``zonal.pixel_area_m2`` (the pipeline's existing, already-tested WGS84
ellipsoidal raster pixel-area code) for a tiny rectangle.
"""
from __future__ import annotations

import math
from typing import Sequence

# Radius (m) of the sphere with the same total surface area as the WGS84
# ellipsoid (a well-known published constant; see e.g. IUGG/EPSG:7043).
WGS84_AUTHALIC_RADIUS_M = 6_371_007.1809

M2_PER_HA = 10_000.0

_POLYGONAL = ("Polygon", "MultiPolygon")


def _unwrap_feature(geometry: dict) -> dict:
    if not isinstance(geometry, dict):
        raise ValueError(f"Geometry must be a GeoJSON dict, got {type(geometry).__name__}")
    if geometry.get("type") == "Feature":
        geometry = geometry.get("geometry")
        if not isinstance(geometry, dict):
            raise ValueError("GeoJSON Feature has no geometry")
    if geometry.get("type") not in _POLYGONAL:
        raise ValueError(
            f"Unsupported geometry type {geometry.get('type')!r}: expected Polygon, "
            "MultiPolygon or a Feature wrapping one"
        )
    return geometry


def _ring_area_m2(ring: Sequence[Sequence[float]]) -> float:
    """Unsigned spherical area (m^2) of one linear ring of ``[lon, lat]`` pairs.

    A GeoJSON ring is closed (first point == last); the duplicate closing
    point is dropped before the sum so it is not double-weighted.
    """
    if len(ring) > 1 and ring[0][0] == ring[-1][0] and ring[0][1] == ring[-1][1]:
        ring = ring[:-1]
    n = len(ring)
    if n < 3:
        return 0.0
    total = 0.0
    for i in range(n):
        lon_prev = ring[(i - 1) % n][0]
        lat_mid = ring[i][1]
        lon_next = ring[(i + 1) % n][0]
        total += math.radians(lon_next - lon_prev) * math.sin(math.radians(lat_mid))
    return abs(total) * WGS84_AUTHALIC_RADIUS_M**2 / 2.0


def polygon_area_m2(geometry: dict) -> float:
    """Geodesic area (m^2) of a GeoJSON Polygon/MultiPolygon (or Feature).

    Every ring after the first (exterior) one in a Polygon's ``coordinates``
    is a hole and its area is SUBTRACTED. A MultiPolygon's parts are summed.
    """
    geometry = _unwrap_feature(geometry)
    polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
    total = 0.0
    for rings in polygons:
        exterior, *holes = rings
        total += _ring_area_m2(exterior)
        for hole in holes:
            total -= _ring_area_m2(hole)
    return total


def polygon_area_ha(geometry: dict) -> float:
    """Geodesic area (hectares) of a GeoJSON Polygon/MultiPolygon (or Feature)."""
    return polygon_area_m2(geometry) / M2_PER_HA
