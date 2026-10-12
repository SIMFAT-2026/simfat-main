"""area.py: dependency-free WGS84 geodesic polygon area (D2 independent
area gate).

No pyproj/shapely dependency is added (see requirements.txt); the ring-area
formula is cross-checked below against ``zonal.pixel_area_m2``, the
pipeline's existing, already-tested WGS84 ellipsoidal area code, for a tiny
rectangle where the two methods must agree closely.
"""
import math

import pytest

from mb_pipeline import area, zonal


def _rectangle(lon0, lat0, dlon, dlat):
    """A small closed rectangular ring (counterclockwise), GeoJSON [lon, lat]."""
    ring = [
        [lon0, lat0],
        [lon0 + dlon, lat0],
        [lon0 + dlon, lat0 + dlat],
        [lon0, lat0 + dlat],
        [lon0, lat0],
    ]
    return {"type": "Polygon", "coordinates": [ring]}


def test_polygon_area_ha_matches_existing_ellipsoidal_pixel_area_for_a_tiny_rectangle():
    """Cross-check against the already-trusted ``zonal.pixel_area_m2``: a
    tiny (0.01deg) rectangle is close enough to flat that the two
    independently-derived WGS84 area methods must agree to within 0.1%."""
    lat0, dlon, dlat = -37.0, 0.01, 0.01
    geometry = _rectangle(-73.0, lat0, dlon, dlat)

    got_m2 = area.polygon_area_m2(geometry)
    expected_m2 = zonal.pixel_area_m2(lat0, dlon, dlat)

    assert got_m2 == pytest.approx(expected_m2, rel=1e-3)


def test_polygon_area_ha_is_dlon_dlat_symmetric_scaling_sanity_check():
    """Triangulation: doubling both dlon and dlat should ~quadruple the area
    (flat-earth sanity check at these small scales)."""
    lat0 = -37.0
    small = area.polygon_area_m2(_rectangle(-73.0, lat0, 0.01, 0.01))
    big = area.polygon_area_m2(_rectangle(-73.0, lat0, 0.02, 0.02))

    assert big == pytest.approx(small * 4, rel=1e-2)


def test_polygon_area_ha_subtracts_holes():
    outer = [
        [0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0], [0.0, 0.0],
    ]
    hole = [
        [0.4, 0.4], [0.6, 0.4], [0.6, 0.6], [0.4, 0.6], [0.4, 0.4],
    ]
    with_hole = {"type": "Polygon", "coordinates": [outer, hole]}
    without_hole = {"type": "Polygon", "coordinates": [outer]}

    area_with_hole = area.polygon_area_m2(with_hole)
    area_without_hole = area.polygon_area_m2(without_hole)

    assert area_with_hole < area_without_hole


def test_polygon_area_ha_sums_multipolygon_parts():
    part1 = _rectangle(-73.0, -37.0, 0.01, 0.01)
    part2 = _rectangle(-72.0, -38.0, 0.02, 0.02)
    multi = {
        "type": "MultiPolygon",
        "coordinates": [part1["coordinates"], part2["coordinates"]],
    }

    got = area.polygon_area_m2(multi)
    expected = area.polygon_area_m2(part1) + area.polygon_area_m2(part2)

    assert got == pytest.approx(expected, rel=1e-9)


def test_polygon_area_ha_unwraps_a_geojson_feature():
    feature = {
        "type": "Feature",
        "properties": {},
        "geometry": _rectangle(-73.0, -37.0, 0.01, 0.01),
    }
    plain = _rectangle(-73.0, -37.0, 0.01, 0.01)

    assert area.polygon_area_m2(feature) == area.polygon_area_m2(plain)


def test_polygon_area_ha_returns_hectares():
    geometry = _rectangle(-73.0, -37.0, 0.01, 0.01)

    m2 = area.polygon_area_m2(geometry)
    ha = area.polygon_area_ha(geometry)

    assert ha == pytest.approx(m2 / 10_000.0)
