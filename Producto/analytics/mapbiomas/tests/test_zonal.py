import math

import numpy as np
import pytest

from mb_pipeline import zonal

# WGS84 constants, restated here on purpose: the oracle below must not import
# anything from the production module.
WGS84_A = 6378137.0
WGS84_F = 1 / 298.257223563
WGS84_E2 = WGS84_F * (2 - WGS84_F)


def rect(west, south, east, north):
    return {
        "type": "Polygon",
        "coordinates": [[[west, south], [east, south], [east, north], [west, north], [west, south]]],
    }


def _authalic_f(phi_deg):
    e = math.sqrt(WGS84_E2)
    s = math.sin(math.radians(phi_deg))
    return s / (2 * (1 - WGS84_E2 * s * s)) + math.log((1 + e * s) / (1 - e * s)) / (4 * e)


def analytic_rect_ha(west, south, east, north):
    """Exact WGS84 area of a lat/lon cell, in hectares.

    Formula-independent oracle: it uses the closed-form authalic integral
    (b^2 * dlam * [F(phi2) - F(phi1)]), not the radii-of-curvature product used
    by the production code, so the two cannot share a mistake.
    """
    b2 = WGS84_A**2 * (1 - WGS84_E2)
    dlam = math.radians(east - west)
    return dlam * b2 * (_authalic_f(north) - _authalic_f(south)) / 1e4


# --- oracle sanity: pinned values computed once from the closed form ----------


def test_oracle_pinned_values():
    assert analytic_rect_ha(-72.0, -33.1, -71.9, -33.0) == pytest.approx(10358.609956, rel=1e-9)
    assert analytic_rect_ha(-72.0, -40.0, -71.9, -39.9) == pytest.approx(9488.504212, rel=1e-9)
    assert analytic_rect_ha(-72.0, -34.0, -71.9, -33.0) == pytest.approx(103062.225707, rel=1e-9)


# --- pixel area vs the independent oracle -------------------------------------


@pytest.mark.parametrize("lat", [-33.0, -37.0, -39.7])
def test_pixel_area_matches_ellipsoidal_oracle(lat):
    d = 0.0005
    expected = analytic_rect_ha(-72.0, lat - d / 2, -72.0 + d, lat + d / 2) * 1e4
    assert zonal.pixel_area_m2(lat, d, d) == pytest.approx(expected, rel=1e-4)


@pytest.mark.parametrize("lat", [-33.0, -39.7])
def test_row_areas_match_ellipsoidal_oracle(lat):
    d = 0.0005
    top = lat + d / 2
    areas = zonal.row_pixel_areas_m2(top=top, dlon=d, dlat=d, n_rows=3)
    for i, area in enumerate(areas):
        south = top - (i + 1) * d
        expected = analytic_rect_ha(-72.0, south, -72.0 + d, south + d) * 1e4
        assert area == pytest.approx(expected, rel=1e-4)


def test_pixel_area_delegates_to_row_formula():
    row = zonal.row_pixel_areas_m2(top=-33.0 + 0.0005, dlon=0.001, dlat=0.001, n_rows=1)[0]
    assert zonal.pixel_area_m2(-33.0, 0.001, 0.001) == pytest.approx(row, rel=1e-12)


def test_row_areas_shrink_towards_the_pole():
    transform_rows = zonal.row_pixel_areas_m2(top=-33.0, dlon=0.001, dlat=0.001, n_rows=5)
    assert len(transform_rows) == 5
    assert all(a > b for a, b in zip(transform_rows, transform_rows[1:]))


# --- known-area rectangle at study latitudes (MCS-7a) -------------------------


@pytest.mark.parametrize("north", [-33.0, -37.0, -39.7, -44.0])
def test_known_block_area_matches_oracle(make_geotiff, north):
    west, size, n = -72.0, 0.001, 100
    path = make_geotiff(np.ones((n, n), dtype="uint8"), west, north, size, size)
    result = zonal.zonal_stats(path, rect(west, north - n * size, west + n * size, north))
    expected = analytic_rect_ha(west, north - n * size, west + n * size, north)
    assert result.pixel_count == n * n
    assert result.area_ha == pytest.approx(expected, rel=1e-4)


@pytest.mark.parametrize("north", [-33.0, -39.7])
def test_tall_raster_proves_per_row_correction(make_geotiff, north):
    # ~1 degree of latitude: a constant (top-row) area per row is off by ~0.5%,
    # far above the 1e-4 tolerance, so that mutation must fail this test.
    west, dlon, dlat, rows, cols = -72.0, 0.01, 0.001, 1000, 10
    path = make_geotiff(np.ones((rows, cols), dtype="uint8"), west, north, dlon, dlat)
    poly = rect(west, north - rows * dlat, west + cols * dlon, north)
    result = zonal.zonal_stats(path, poly)
    assert result.pixel_count == rows * cols
    expected = analytic_rect_ha(west, north - rows * dlat, west + cols * dlon, north)
    assert result.area_ha == pytest.approx(expected, rel=1e-4)


# --- exact counts on a 10x10 raster (MCS-5a) ---------------------------------


def _grid():
    grid = np.zeros((10, 10), dtype="uint8")
    grid[3:7, 2:6] = 1  # 4x4 block of value 1
    grid[3:5, 2:4] = 2  # 2x2 sub-block of value 2 inside it
    return grid


def test_exact_value_counts_for_square_polygon(make_geotiff):
    # pixel 0.01 deg, origin (-72.0, -33.0); polygon = cols 2..5, rows 3..6 exactly
    path = make_geotiff(_grid(), -72.0, -33.0, 0.01, 0.01)
    poly = rect(-72.0 + 0.02, -33.0 - 0.07, -72.0 + 0.06, -33.0 - 0.03)
    result = zonal.zonal_stats(path, poly)
    assert result.pixel_count == 16
    assert result.value_pixels == {1: 12, 2: 4}
    assert sum(result.value_area_ha.values()) == pytest.approx(result.area_ha)
    assert result.value_area_ha[2] == pytest.approx(result.area_ha / 4, rel=0.01)


def test_pixel_is_included_only_when_its_center_is_inside(make_geotiff):
    path = make_geotiff(_grid(), -72.0, -33.0, 0.01, 0.01)
    # east edge at 5.4 pixels: column 5 center (5.5) is outside -> excluded
    poly = rect(-72.0 + 0.02, -33.0 - 0.07, -72.0 + 0.054, -33.0 - 0.03)
    assert zonal.zonal_stats(path, poly).pixel_count == 12
    # east edge at 5.6 pixels: column 5 center is inside -> included
    poly = rect(-72.0 + 0.02, -33.0 - 0.07, -72.0 + 0.056, -33.0 - 0.03)
    assert zonal.zonal_stats(path, poly).pixel_count == 16


def test_interior_ring_is_excluded(make_geotiff):
    path = make_geotiff(np.ones((10, 10), dtype="uint8"), -72.0, -33.0, 0.01, 0.01)
    outer = rect(-72.0, -33.10, -71.90, -33.0)["coordinates"][0]
    hole = rect(-72.0 + 0.04, -33.0 - 0.06, -72.0 + 0.06, -33.0 - 0.04)["coordinates"][0]
    result = zonal.zonal_stats(path, {"type": "Polygon", "coordinates": [outer, hole]})
    assert result.pixel_count == 100 - 4


def test_polygon_partially_outside_raster_is_clipped(make_geotiff):
    path = make_geotiff(np.ones((10, 10), dtype="uint8"), -72.0, -33.0, 0.01, 0.01)
    poly = rect(-72.0 + 0.08, -33.10, -71.0, -33.0)  # extends far east of the raster
    assert zonal.zonal_stats(path, poly).pixel_count == 20


def test_polygon_outside_raster_gives_empty_result(make_geotiff):
    path = make_geotiff(np.ones((10, 10), dtype="uint8"), -72.0, -33.0, 0.01, 0.01)
    result = zonal.zonal_stats(path, rect(10.0, 10.0, 10.1, 10.1))
    assert result.pixel_count == 0
    assert result.area_ha == 0.0
    assert result.value_pixels == {}


# --- zero is data, not nodata (MCS-6a) ---------------------------------------


def test_zeros_are_counted_when_raster_has_no_nodata(make_geotiff):
    grid = np.zeros((10, 10), dtype="uint8")
    grid[0, 0] = 1
    path = make_geotiff(grid, -72.0, -33.0, 0.01, 0.01)
    result = zonal.zonal_stats(path, rect(-72.0, -33.10, -71.90, -33.0))
    assert result.pixel_count == 100
    assert result.value_pixels == {0: 99, 1: 1}


def test_zeros_are_counted_even_if_raster_declares_nodata_zero(make_geotiff):
    grid = np.zeros((10, 10), dtype="uint8")
    grid[0, 0] = 1
    path = make_geotiff(grid, -72.0, -33.0, 0.01, 0.01, nodata=0)
    result = zonal.zonal_stats(path, rect(-72.0, -33.10, -71.90, -33.0))
    assert result.value_pixels == {0: 99, 1: 1}


def test_window_covers_far_edge_pixels_when_polygon_starts_mid_pixel(make_geotiff):
    path = make_geotiff(np.ones((10, 10), dtype="uint8"), -72.0, -33.0, 0.01, 0.01)
    # x from 2.7 to 5.6 pixels, y from 3.0 to 7.0: centers of columns 3, 4, 5 are inside
    poly = rect(-72.0 + 0.027, -33.0 - 0.07, -72.0 + 0.056, -33.0 - 0.03)
    assert zonal.zonal_stats(path, poly).pixel_count == 3 * 4


def test_all_touched_false_excludes_partially_covered_pixels(make_geotiff):
    path = make_geotiff(np.ones((10, 10), dtype="uint8"), -72.0, -33.0, 0.01, 0.01)
    # thin sliver covering 40% of column 5 but not its center
    poly = rect(-72.0 + 0.05, -33.0 - 0.07, -72.0 + 0.054, -33.0 - 0.03)
    assert zonal.zonal_stats(path, poly).pixel_count == 0
