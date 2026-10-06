"""H1 step 2: local metric scale, scar areas with latitude-dependent pixels,
the >= 250 ha filter, the union-edge flag and the comunas a scar touches."""
import math

import numpy as np
import pytest

from mb_pipeline import zonal
from study import h1

DLON = DLAT = 0.0002694945852358564  # the real Fuego pixel size (~30 m)


def _bowring_m_per_deg(lat_deg):
    """Independent series (Bowring/NIMA) for the length of one degree."""
    p = math.radians(lat_deg)
    lat_len = 111132.92 - 559.82 * math.cos(2 * p) + 1.175 * math.cos(4 * p) - 0.0023 * math.cos(6 * p)
    lon_len = 111412.84 * math.cos(p) - 93.5 * math.cos(3 * p) + 0.118 * math.cos(5 * p)
    return lon_len, lat_len


@pytest.mark.parametrize("lat", [0.0, -36.0, -38.0, -39.6, 45.0])
def test_metres_per_degree_matches_an_independent_series(lat):
    mx, my = h1.metres_per_degree(lat)
    ex, ey = _bowring_m_per_deg(lat)
    assert mx == pytest.approx(ex, rel=2e-5)
    assert my == pytest.approx(ey, rel=2e-5)


def test_a_pixel_is_narrower_than_tall_in_metres_at_chilean_latitudes():
    mx, my = h1.metres_per_degree(-38.0)
    assert mx * DLON == pytest.approx(23.6, abs=0.2)  # width
    assert my * DLAT == pytest.approx(29.8, abs=0.2)  # height


def _grid(north=-37.0, west=-72.0):
    return h1.Grid(west=west, north=north, dlon=DLON, dlat=DLAT)


def _find(burned, *, inside=None, comuna_idx=None, comuna_ids=("A",), grid=None, min_ha=0.0):
    burned = np.asarray(burned, dtype=bool)
    inside = np.ones_like(burned) if inside is None else np.asarray(inside, dtype=bool)
    comuna_idx = (
        np.where(inside, 1, 0).astype(np.uint8) if comuna_idx is None else np.asarray(comuna_idx, dtype=np.uint8)
    )
    return h1.find_scars(burned, inside, comuna_idx, list(comuna_ids), grid or _grid(), min_ha=min_ha)


def _expected_ha(grid, pixels):
    areas = zonal.row_pixel_areas_m2(grid.north, grid.dlon, grid.dlat, 100) / 10_000.0
    return float(sum(areas[r] for r, _ in pixels))


def test_area_uses_the_pipelines_per_row_wgs84_pixel_area():
    a = np.zeros((10, 10), dtype=bool)
    a[2:5, 3:7] = True  # 3 rows x 4 columns
    scars = _find(a)
    assert len(scars.table) == 1
    pixels = [(r, c) for r in range(2, 5) for c in range(3, 7)]
    assert scars.table[0]["area_ha"] == pytest.approx(_expected_ha(_grid(), pixels), rel=1e-12)
    assert scars.table[0]["n_pixels"] == 12


def test_pixel_area_depends_on_latitude():
    a = np.zeros((6, 6), dtype=bool)
    a[1:4, 1:4] = True
    equator = _find(a, grid=_grid(north=0.5)).table[0]["area_ha"]
    south = _find(a, grid=_grid(north=-60.0)).table[0]["area_ha"]
    # Independent expectation: 9 pixels of (lon_len * dlon) x (lat_len * dlat) at the
    # scar's mean row latitude (row centre 2.5 pixels below the window top).
    def expected_ha(north):
        lat = north - 2.5 * DLAT
        lon_len, lat_len = _bowring_m_per_deg(lat)
        return 9 * (lon_len * DLON) * (lat_len * DLAT) / 10_000.0

    assert equator == pytest.approx(expected_ha(0.5), rel=1e-3)
    assert south == pytest.approx(expected_ha(-60.0), rel=1e-3)
    assert south == pytest.approx(equator * 0.5, rel=0.03)  # roughly cos(60 deg)
    assert equator > south


def test_threshold_is_inclusive_and_measured_in_hectares_not_pixels():
    a = np.zeros((20, 20), dtype=bool)
    a[0:10, 0:10] = True  # 100 pixels, ~8 ha at -37 degrees
    ha = _find(a).table[0]["area_ha"]
    assert 5.0 < ha < 10.0
    assert len(_find(a, min_ha=ha * (1 - 1e-9)).table) == 1
    assert len(_find(a, min_ha=ha * (1 + 1e-9)).table) == 0


def test_the_same_pixel_count_passes_at_one_latitude_and_fails_at_another():
    a = np.zeros((20, 20), dtype=bool)
    a[0:10, 0:10] = True
    ha_equator = _find(a, grid=_grid(north=0.5)).table[0]["area_ha"]
    ha_south = _find(a, grid=_grid(north=-60.0)).table[0]["area_ha"]
    cut = (ha_equator + ha_south) / 2
    assert len(_find(a, grid=_grid(north=0.5), min_ha=cut).table) == 1
    assert len(_find(a, grid=_grid(north=-60.0), min_ha=cut).table) == 0


def test_two_scars_are_reported_separately_with_their_own_area():
    a = np.zeros((12, 12), dtype=bool)
    a[0:2, 0:2] = True
    a[6:10, 6:10] = True
    scars = _find(a)
    assert sorted(t["n_pixels"] for t in scars.table) == [4, 16]
    assert len(scars.rows) == 20
    assert set(scars.scar.tolist()) == {0, 1}


def test_only_pixels_of_kept_scars_are_returned():
    a = np.zeros((12, 12), dtype=bool)
    a[0:2, 0:2] = True
    a[6:10, 6:10] = True
    ha_big = _find(a).table
    big = max(t["area_ha"] for t in ha_big)
    scars = _find(a, min_ha=big * 0.99)
    assert [t["n_pixels"] for t in scars.table] == [16]
    assert len(scars.rows) == 16


def test_burned_pixels_outside_the_union_are_clipped_not_counted():
    burned = np.zeros((8, 8), dtype=bool)
    burned[2:6, 1:7] = True
    inside = np.zeros((8, 8), dtype=bool)
    inside[:, :4] = True  # the union covers columns 0-3 only
    scars = _find(burned, inside=inside)
    assert scars.table[0]["n_pixels"] == 4 * 3  # columns 1-3 only


def test_scar_touching_the_union_edge_is_flagged_and_marked_truncated_if_it_continues():
    burned = np.zeros((8, 8), dtype=bool)
    burned[2:6, 1:7] = True
    inside = np.zeros((8, 8), dtype=bool)
    inside[:, :4] = True
    row = _find(burned, inside=inside).table[0]
    assert row["edge_flag"] is True
    assert row["truncated"] is True  # burned pixels continue outside the union


def test_edge_flag_without_truncation_when_the_burn_stops_at_the_union_edge():
    burned = np.zeros((8, 8), dtype=bool)
    burned[2:6, 1:4] = True  # stops exactly at the last inside column
    inside = np.zeros((8, 8), dtype=bool)
    inside[:, :4] = True
    row = _find(burned, inside=inside).table[0]
    assert row["edge_flag"] is True
    assert row["truncated"] is False


def test_interior_scar_has_no_flags():
    burned = np.zeros((9, 9), dtype=bool)
    burned[3:6, 3:6] = True
    row = _find(burned).table[0]
    assert row["edge_flag"] is False and row["truncated"] is False


def test_scar_at_the_window_border_counts_as_touching_the_edge():
    burned = np.zeros((6, 6), dtype=bool)
    burned[0:3, 0:3] = True
    assert _find(burned).table[0]["edge_flag"] is True


def test_a_scar_crossing_a_comuna_border_stays_one_scar_and_lists_both_comunas():
    burned = np.zeros((6, 8), dtype=bool)
    burned[1:5, 1:7] = True
    inside = np.ones_like(burned)
    idx = np.ones((6, 8), dtype=np.uint8)
    idx[:, 4:] = 2
    scars = _find(burned, inside=inside, comuna_idx=idx, comuna_ids=("CHL.A", "CHL.B"))
    assert len(scars.table) == 1
    assert scars.table[0]["comunas"] == ["CHL.A", "CHL.B"]


def test_centroid_lies_inside_the_scars_bounding_box():
    burned = np.zeros((10, 10), dtype=bool)
    burned[2:6, 3:7] = True
    row = _find(burned).table[0]
    g = _grid()
    assert g.west + 3 * DLON < row["lon"] < g.west + 7 * DLON
    assert g.north - 6 * DLAT < row["lat"] < g.north - 2 * DLAT
