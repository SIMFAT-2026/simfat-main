"""H1 step 3: detections within 375 m of a scar. The buffer is the exact
Euclidean distance, in local WGS84 metres, from the detection to the nearest
burned pixel SQUARE of the scar (so a detection inside a burned pixel is at
distance 0). Hand-built geometry with known answers."""
import numpy as np
import pytest

from study import h1

DLON = DLAT = 0.0002694945852358564
SHAPE = (60, 60)
R0, R1, C0, C1 = 20, 25, 20, 25  # burned block: rows 20-24, cols 20-24


def _setup(north=-38.0, west=-72.0, block=(R0, R1, C0, C1)):
    grid = h1.Grid(west=west, north=north, dlon=DLON, dlat=DLAT)
    burned = np.zeros(SHAPE, dtype=bool)
    r0, r1, c0, c1 = block
    burned[r0:r1, c0:c1] = True
    inside = np.ones(SHAPE, dtype=bool)
    idx = np.ones(SHAPE, dtype=np.uint8)
    scars = h1.find_scars(burned, inside, idx, ["A"], grid, min_ha=0.0)
    return grid, scars


def _count(grid, scars, lons, lats, **kw):
    return h1.detections_in_buffer(scars, grid, SHAPE, np.asarray(lons, float), np.asarray(lats, float), **kw)


def _east_edge(grid):
    return grid.west + C1 * grid.dlon


def _north_edge(grid):
    return grid.north - R0 * grid.dlat


def _mid_lat(grid):
    return grid.north - (R0 + R1) / 2 * grid.dlat


@pytest.mark.parametrize("north", [-36.0, -38.0, -39.6, 0.0])
def test_detection_exactly_375_m_east_is_in_and_400_m_is_out(north):
    grid, scars = _setup(north=north)
    lat = _mid_lat(grid)
    mx, _ = h1.metres_per_degree(lat)
    at_375 = _east_edge(grid) + 375.0 / mx
    at_400 = _east_edge(grid) + 400.0 / mx
    at_374 = _east_edge(grid) + 374.0 / mx
    assert _count(grid, scars, [at_375], [lat]).tolist() == [1]
    assert _count(grid, scars, [at_374], [lat]).tolist() == [1]
    assert _count(grid, scars, [at_400], [lat]).tolist() == [0]


@pytest.mark.parametrize("north", [-36.0, -38.0, -39.6])
def test_detection_exactly_375_m_north_is_in_and_400_m_is_out(north):
    grid, scars = _setup(north=north)
    lon = grid.west + (C0 + C1) / 2 * grid.dlon
    lat_edge = _north_edge(grid)

    def lat_at(distance_m):
        # the metric scale is taken at the detection's own latitude, so solve for it
        lat = lat_edge
        for _ in range(5):
            lat = lat_edge + distance_m / h1.metres_per_degree(lat)[1]
        return lat

    assert _count(grid, scars, [lon], [lat_at(375.0)]).tolist() == [1]
    assert _count(grid, scars, [lon], [lat_at(400.0)]).tolist() == [0]


def test_the_buffer_is_metric_not_a_fixed_number_of_pixels():
    # At -38 degrees a pixel is ~23.6 m wide and ~29.8 m tall: 15 pixels east of
    # the scar is ~354 m (in), 15 pixels north is ~447 m (out).
    grid, scars = _setup(north=-38.0)
    lat = _mid_lat(grid)
    lon = _east_edge(grid) + 15 * DLON
    assert _count(grid, scars, [lon], [lat]).tolist() == [1]
    lon_mid = grid.west + (C0 + C1) / 2 * DLON
    lat_n = _north_edge(grid) + 15 * DLAT
    assert _count(grid, scars, [lon_mid], [lat_n]).tolist() == [0]
    # 17 pixels east is ~401 m: out.
    assert _count(grid, scars, [_east_edge(grid) + 17 * DLON], [lat]).tolist() == [0]


def test_the_buffer_is_euclidean_not_a_square():
    # 265 m east and 265 m north of the NE corner: hypot = 374.8 m (in);
    # 280 m each: hypot = 396 m (out) although each leg alone is < 375 m.
    grid, scars = _setup(north=-38.0)
    corner_lon, corner_lat = _east_edge(grid), _north_edge(grid)
    mx, my = h1.metres_per_degree(corner_lat)
    inside = (corner_lon + 265.0 / mx, corner_lat + 265.0 / my)
    outside = (corner_lon + 280.0 / mx, corner_lat + 280.0 / my)
    assert _count(grid, scars, [inside[0]], [inside[1]]).tolist() == [1]
    assert _count(grid, scars, [outside[0]], [outside[1]]).tolist() == [0]


def test_detection_inside_a_burned_pixel_counts():
    grid, scars = _setup()
    lon = grid.west + (C0 + 2.3) * DLON
    lat = grid.north - (R0 + 1.7) * DLAT
    assert _count(grid, scars, [lon], [lat]).tolist() == [1]


def test_detection_in_an_unburned_pixel_next_to_the_scar_counts():
    grid, scars = _setup()
    lon = grid.west + (C1 + 0.5) * DLON
    lat = grid.north - (R0 + 2.5) * DLAT
    assert _count(grid, scars, [lon], [lat]).tolist() == [1]


def test_detection_far_away_does_not_count():
    grid, scars = _setup()
    assert _count(grid, scars, [grid.west + 55 * DLON], [grid.north - 5 * DLAT]).tolist() == [0]


def test_detections_outside_the_window_are_ignored_without_error():
    grid, scars = _setup()
    assert _count(grid, scars, [grid.west - 1.0, grid.west + 5.0, 10.0], [grid.north + 1.0, -80.0, 0.0]).tolist() == [0]


def test_counts_are_distinct_detections_per_scar():
    grid, scars = _setup()
    lat = _mid_lat(grid)
    lons = [grid.west + (C0 + k + 0.5) * DLON for k in range(3)]
    assert _count(grid, scars, lons, [lat] * 3).tolist() == [3]


def test_one_detection_between_two_scars_counts_for_both():
    grid = h1.Grid(west=-72.0, north=-38.0, dlon=DLON, dlat=DLAT)
    burned = np.zeros(SHAPE, dtype=bool)
    burned[20:25, 5:10] = True
    burned[20:25, 30:35] = True
    scars = h1.find_scars(burned, np.ones(SHAPE, bool), np.ones(SHAPE, np.uint8), ["A"], grid, min_ha=0.0)
    assert len(scars.table) == 2
    lat = grid.north - 22.5 * DLAT
    lon = grid.west + 20.0 * DLON  # 10 px (236 m) from each scar
    assert _count(grid, scars, [lon], [lat]).tolist() == [1, 1]


def test_chunking_does_not_change_the_result():
    grid, scars = _setup()
    rng = np.random.default_rng(3)
    lons = grid.west + rng.uniform(0, 60, 500) * DLON
    lats = grid.north - rng.uniform(0, 60, 500) * DLAT
    whole = _count(grid, scars, lons, lats, chunk=10_000)
    small = _count(grid, scars, lons, lats, chunk=7)
    assert whole.tolist() == small.tolist()


def test_matches_brute_force_distance_on_random_points():
    grid, scars = _setup(north=-37.5)
    rng = np.random.default_rng(11)
    lons = grid.west + rng.uniform(8, 38, 400) * DLON
    lats = grid.north - rng.uniform(8, 38, 400) * DLAT
    expected = 0
    for lon, lat in zip(lons, lats):
        mx, my = h1.metres_per_degree(lat)
        west, east = grid.west + C0 * DLON, grid.west + C1 * DLON
        north, south = grid.north - R0 * DLAT, grid.north - R1 * DLAT
        dx = max(west - lon, lon - east, 0.0) * mx
        dy = max(south - lat, lat - north, 0.0) * my
        expected += (dx * dx + dy * dy) ** 0.5 <= 375.0 + 1e-6
    assert _count(grid, scars, lons, lats).tolist() == [expected]
    assert 0 < expected < 400


def test_no_scars_or_no_detections_gives_zero_counts():
    grid, scars = _setup()
    assert _count(grid, scars, [], []).tolist() == [0]
    empty = h1.Scars([], np.zeros(0, int), np.zeros(0, int), np.zeros(0, int))
    assert h1.detections_in_buffer(empty, grid, SHAPE, np.array([1.0]), np.array([1.0])).tolist() == []
