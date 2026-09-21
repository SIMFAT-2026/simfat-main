import math

import numpy as np
import pytest

from mb_pipeline import zonal

M_PER_DEG_LON_EQ = 111320.0
M_PER_DEG_LAT = 110540.0


def rect(west, south, east, north):
    return {
        "type": "Polygon",
        "coordinates": [[[west, south], [east, south], [east, north], [west, north], [west, south]]],
    }


def analytic_rect_ha(west, south, east, north):
    mid = math.radians((south + north) / 2)
    return (east - west) * M_PER_DEG_LON_EQ * math.cos(mid) * (north - south) * M_PER_DEG_LAT / 1e4


# --- analytic pixel area -----------------------------------------------------


def test_pixel_area_at_equator():
    assert zonal.pixel_area_m2(0.0, 0.001, 0.001) == pytest.approx(
        0.001 * M_PER_DEG_LON_EQ * 0.001 * M_PER_DEG_LAT
    )


def test_pixel_area_at_60_degrees_is_half_of_equator():
    eq = zonal.pixel_area_m2(0.0, 0.0005, 0.0005)
    assert zonal.pixel_area_m2(-60.0, 0.0005, 0.0005) == pytest.approx(eq / 2, rel=1e-9)


def test_row_areas_shrink_towards_the_pole():
    transform_rows = zonal.row_pixel_areas_m2(top=-33.0, dlon=0.001, dlat=0.001, n_rows=5)
    assert len(transform_rows) == 5
    assert all(a > b for a, b in zip(transform_rows, transform_rows[1:]))


# --- known-area rectangle at two latitudes (MCS-7a) --------------------------


@pytest.mark.parametrize("north", [-33.0, -44.0])
def test_known_block_area_within_half_percent(make_geotiff, north):
    west, size, n = -72.0, 0.001, 100
    path = make_geotiff(np.ones((n, n), dtype="uint8"), west, north, size, size)
    result = zonal.zonal_stats(path, rect(west, north - n * size, west + n * size, north))
    expected = analytic_rect_ha(west, north - n * size, west + n * size, north)
    assert result.pixel_count == n * n
    assert result.area_ha == pytest.approx(expected, rel=0.005)


def test_area_ratio_between_latitudes_follows_cosine(make_geotiff):
    size, n = 0.001, 100
    areas = {}
    for north in (-33.0, -44.0):
        path = make_geotiff(
            np.ones((n, n), dtype="uint8"), -72.0, north, size, size, name=f"lat{abs(north)}.tif"
        )
        areas[north] = zonal.zonal_stats(path, rect(-72.0, north - n * size, -72.0 + n * size, north)).area_ha
    expected_ratio = math.cos(math.radians(-44.05)) / math.cos(math.radians(-33.05))
    assert areas[-44.0] / areas[-33.0] == pytest.approx(expected_ratio, rel=0.005)


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
