"""H1 step 5: from annual rasters + comuna polygons + filtered detections to one
row per scar, on small synthetic GeoTIFFs with known answers."""
import numpy as np
import pytest

from study import h1

DLON = DLAT = 0.0002694945852358564
WEST, NORTH = -72.0, -37.0
H = W = 100


def _poly(c0, r0, c1, r1):
    """Rectangle in pixel coordinates (outer pixel edges) as a GeoJSON polygon."""
    x0, x1 = WEST + c0 * DLON, WEST + c1 * DLON
    y0, y1 = NORTH - r1 * DLAT, NORTH - r0 * DLAT
    return {"type": "Polygon", "coordinates": [[[x0, y0], [x1, y0], [x1, y1], [x0, y1], [x0, y0]]]}


# Comuna A: columns 10-49; comuna B: columns 50-89; both rows 10-89.
POLYGONS = {"CHL.A": _poly(10, 10, 50, 90), "CHL.B": _poly(50, 10, 90, 90)}


def _raster(make_geotiff, name, blocks):
    arr = np.zeros((H, W), dtype=np.uint8)
    for r0, r1, c0, c1 in blocks:
        arr[r0:r1, c0:c1] = 1
    return make_geotiff(arr, WEST, NORTH, DLON, DLAT, name=name)


def _det(points):
    """points: list of (col, row, year) in fractional pixel coordinates."""
    return h1.FilteredDetections(
        lon=np.array([WEST + c * DLON for c, r, y in points]),
        lat=np.array([NORTH - r * DLAT for c, r, y in points]),
        year=np.array([y for c, r, y in points], dtype=np.int64),
        n_rows=len(points),
        n_filtered=len(points),
    )


def _run(rasters, det, **kw):
    kw.setdefault("min_ha", 3.0)
    return h1.analyse_rasters(rasters, POLYGONS, det, **kw)


def test_scar_with_a_detection_inside_in_the_same_year_is_detected(make_geotiff):
    rasters = {2015: _raster(make_geotiff, "y2015.tif", [(30, 42, 20, 32)])}
    rows = _run(rasters, _det([(25.5, 35.5, 2015)]))
    assert len(rows) == 1
    r = rows[0]
    assert r["year"] == 2015 and r["n_detections_in_buffer"] == 1 and r["detected"] is True
    assert r["comunas"] == ["CHL.A"] and r["edge_flag"] is False


def test_detection_of_a_different_calendar_year_is_not_a_match(make_geotiff):
    rasters = {2015: _raster(make_geotiff, "y2015.tif", [(30, 42, 20, 32)])}
    rows = _run(rasters, _det([(25.5, 35.5, 2016), (25.5, 35.5, 2014)]))
    assert rows[0]["n_detections_in_buffer"] == 0 and rows[0]["detected"] is False


def test_each_year_uses_its_own_raster_and_its_own_detections(make_geotiff):
    rasters = {
        2015: _raster(make_geotiff, "y2015.tif", [(30, 42, 20, 32)]),
        2016: _raster(make_geotiff, "y2016.tif", [(60, 72, 60, 72)]),
    }
    det = _det([(25.5, 35.5, 2015), (65.5, 65.5, 2015)])  # second one: 2016's scar, but year 2015
    rows = {r["year"]: r for r in _run(rasters, det)}
    assert rows[2015]["detected"] is True
    assert rows[2016]["detected"] is False


def test_detection_in_the_buffer_outside_the_union_still_counts(make_geotiff):
    # Scar against the west edge of comuna A (columns 10-14); detection 6 pixels
    # (~140 m) west of the union edge, outside every comuna.
    rasters = {2018: _raster(make_geotiff, "y2018.tif", [(30, 42, 10, 15)])}
    # 3 ha threshold is not met by 12x5 px (4.2 ha is): keep it low
    rows = _run(rasters, _det([(4.0, 36.0, 2018)]), min_ha=2.0)
    assert rows[0]["n_detections_in_buffer"] == 1
    assert rows[0]["edge_flag"] is True


def test_scar_crossing_the_comuna_border_is_one_scar_listing_both(make_geotiff):
    rasters = {2019: _raster(make_geotiff, "y2019.tif", [(40, 52, 44, 56)])}
    rows = _run(rasters, _det([]))
    assert len(rows) == 1
    assert rows[0]["comunas"] == ["CHL.A", "CHL.B"]
    assert rows[0]["n_pixels"] == 12 * 12


def test_burned_pixels_outside_every_comuna_are_ignored(make_geotiff):
    rasters = {2020: _raster(make_geotiff, "y2020.tif", [(2, 8, 2, 8), (30, 42, 20, 32)])}
    rows = _run(rasters, _det([]))
    assert len(rows) == 1 and rows[0]["n_pixels"] == 12 * 12


def test_scar_continuing_beyond_the_union_is_flagged_truncated(make_geotiff):
    rasters = {2021: _raster(make_geotiff, "y2021.tif", [(30, 42, 5, 20)])}  # columns 5-9 are outside
    rows = _run(rasters, _det([]))
    assert rows[0]["edge_flag"] is True and rows[0]["truncated"] is True
    assert rows[0]["n_pixels"] == 12 * 10  # only columns 10-19 are inside


def test_rows_carry_year_class_and_a_stable_scar_id(make_geotiff):
    rasters = {2015: _raster(make_geotiff, "y2015.tif", [(30, 42, 20, 32), (60, 72, 60, 72)])}
    rows = _run(rasters, _det([]))
    assert [r["scar_id"] for r in rows] == ["2015-001", "2015-002"]
    assert all(r["year"] == 2015 for r in rows)


def test_a_raster_with_a_different_grid_is_refused(make_geotiff):
    a = _raster(make_geotiff, "a.tif", [(30, 42, 20, 32)])
    b = make_geotiff(np.zeros((H, W), dtype=np.uint8), WEST + 0.01, NORTH, DLON, DLAT, name="b.tif")
    with pytest.raises(h1.H1InputError):
        _run({2015: a, 2016: b}, _det([]))


@pytest.mark.parametrize(
    "ha, label",
    [(250.0, "250-500 ha"), (499.99, "250-500 ha"), (500.0, "500-1,000 ha"), (999.0, "500-1,000 ha"),
     (1000.0, "1,000-5,000 ha"), (4999.9, "1,000-5,000 ha"), (5000.0, ">= 5,000 ha"), (120000.0, ">= 5,000 ha")],
)
def test_area_classes_are_fixed_and_half_open(ha, label):
    assert h1.area_class(ha) == label


def test_area_class_below_the_threshold_is_an_error():
    with pytest.raises(ValueError):
        h1.area_class(249.9)
