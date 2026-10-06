"""H1 step 4: filtered FIRMS detections by LOCAL calendar year, comuna
assignment, the committed seed's burned fractions, and the secondary table."""
import csv
import io
import json
import zipfile

import numpy as np
import pytest

from study import firms_target, h1

HEADER = ["latitude", "longitude", "acq_date", "acq_time", "confidence", "type"]


def _zip(tmp_path, rows):
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow(HEADER)
    w.writerows(rows)
    path = tmp_path / "firms.zip"
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr("fire.csv", buf.getvalue())
    return path


def test_filter_keeps_type0_and_confidence_n_or_h_only(tmp_path):
    path = _zip(tmp_path, [
        (-37.0, -72.0, "2018-01-10", "1800", "n", 0),   # kept
        (-37.0, -72.0, "2018-01-10", "1800", "h", 0),   # kept
        (-37.0, -72.0, "2018-01-10", "1800", "l", 0),   # low confidence
        (-37.0, -72.0, "2018-01-10", "1800", "n", 2),   # static land source
        (-37.0, -72.0, "2018-01-10", "1800", "h", 3),   # offshore
    ])
    det = h1.read_filtered_detections(path)
    assert det.n_rows == 5
    assert det.n_filtered == 2
    assert len(det.lon) == len(det.lat) == len(det.year) == 2


def test_year_is_the_local_calendar_year_across_new_year(tmp_path):
    path = _zip(tmp_path, [
        # 2020-01-01 02:00 UTC is 2019-12-31 23:00 in Chile (UTC-3 in summer): year 2019.
        (-37.0, -72.0, "2020-01-01", "0200", "n", 0),
        # 2020-01-01 03:00 UTC is exactly local midnight: year 2020.
        (-37.0, -72.0, "2020-01-01", "0300", "n", 0),
        # 2019-12-31 23:30 UTC is 20:30 local: year 2019.
        (-37.0, -72.0, "2019-12-31", "2330", "h", 0),
        # Winter (UTC-4): 2018-07-01 03:30 UTC is 2018-06-30 23:30 local: year 2018.
        (-37.0, -72.0, "2018-07-01", "0330", "h", 0),
    ])
    det = h1.read_filtered_detections(path)
    assert det.year.tolist() == [2019, 2020, 2019, 2018]


def test_year_uses_the_same_local_date_conversion_as_the_target(tmp_path):
    rows = [(-37.0, -72.0, "2019-04-06", "2330", "n", 0), (-37.0, -72.0, "2019-04-07", "0230", "n", 0)]
    det = h1.read_filtered_detections(_zip(tmp_path, rows))
    expected = [
        firms_target.to_local_date(firms_target.parse_acq_datetime_utc(r[2], r[3])).year for r in rows
    ]
    assert det.year.tolist() == expected


def _square(x0, y0, x1, y1, hole=None):
    ring = [[x0, y0], [x1, y0], [x1, y1], [x0, y1], [x0, y0]]
    rings = [ring]
    if hole:
        hx0, hy0, hx1, hy1 = hole
        rings.append([[hx0, hy0], [hx1, hy0], [hx1, hy1], [hx0, hy1], [hx0, hy0]])
    return {"type": "Polygon", "coordinates": rings}


POLYGONS = {
    "CHL.A": _square(-72.0, -38.0, -71.0, -37.0),
    "CHL.B": _square(-71.0, -38.0, -70.0, -37.0, hole=(-70.6, -37.6, -70.4, -37.4)),
}


def test_assign_comunas_matches_the_targets_point_in_polygon_assignment():
    rng = np.random.default_rng(5)
    lons = rng.uniform(-72.5, -69.5, 400)
    lats = rng.uniform(-38.5, -36.5, 400)
    got = h1.assign_comunas(lons, lats, POLYGONS)
    expected = [firms_target.assign_comuna(float(x), float(y), POLYGONS) for x, y in zip(lons, lats)]
    assert got == expected
    assert {"CHL.A", "CHL.B", None} <= set(got)


def test_a_detection_in_a_hole_or_outside_is_unassigned():
    got = h1.assign_comunas(np.array([-70.5, -69.0]), np.array([-37.5, -37.5]), POLYGONS)
    assert got == [None, None]


def test_comuna_year_counts_count_by_comuna_and_year_including_zeros():
    det = h1.FilteredDetections(
        lon=np.array([-71.5, -71.5, -70.9, -70.2, -69.0]),
        lat=np.array([-37.5, -37.5, -37.5, -37.1, -37.5]),
        year=np.array([2018, 2018, 2019, 2018, 2018]),
        n_rows=5, n_filtered=5,
    )
    counts, n_unassigned = h1.comuna_year_counts(det, POLYGONS, years=[2018, 2019, 2020])
    assert counts[("CHL.A", 2018)] == 2
    assert counts[("CHL.A", 2019)] == 0
    assert counts[("CHL.B", 2018)] == 1
    assert counts[("CHL.B", 2019)] == 1
    assert counts[("CHL.A", 2020)] == 0
    assert len(counts) == 2 * 3
    assert n_unassigned == 1


def test_years_outside_the_study_range_are_not_counted():
    det = h1.FilteredDetections(
        lon=np.array([-71.5]), lat=np.array([-37.5]), year=np.array([2012]), n_rows=1, n_filtered=1
    )
    counts, _ = h1.comuna_year_counts(det, POLYGONS, years=[2013, 2014])
    assert sum(counts.values()) == 0


def _seed(tmp_path, docs):
    path = tmp_path / "seed.jsonl"
    path.write_text("\n".join(json.dumps(d) for d in docs) + "\n", encoding="utf-8")
    return path


def test_burned_fractions_come_from_the_seed_by_year(tmp_path):
    seed = _seed(tmp_path, [
        {"comunaId": "CHL.A", "fire": {"available": True, "burnedFractionByYear": {"2013": 0.0, "2014": 0.25}}},
        {"comunaId": "CHL.B", "fire": {"available": True, "burnedFractionByYear": {"2013": 0.5, "2014": 0.0}}},
    ])
    got = h1.load_burned_fraction_by_year(seed)
    assert got == {"CHL.A": {2013: 0.0, 2014: 0.25}, "CHL.B": {2013: 0.5, 2014: 0.0}}


def test_a_comuna_without_fire_data_is_an_error_not_a_zero(tmp_path):
    seed = _seed(tmp_path, [{"comunaId": "CHL.A", "fire": {"available": False}}])
    with pytest.raises(h1.H1InputError):
        h1.load_burned_fraction_by_year(seed)


def test_secondary_rows_pair_fraction_and_count_per_comuna_year():
    fractions = {"CHL.A": {2013: 0.1, 2014: 0.2}, "CHL.B": {2013: 0.0, 2014: 0.4}}
    counts = {("CHL.A", 2013): 5, ("CHL.A", 2014): 6, ("CHL.B", 2013): 0, ("CHL.B", 2014): 9}
    rows = h1.secondary_rows(fractions, counts, comuna_ids=["CHL.A", "CHL.B"], years=[2013, 2014])
    assert len(rows) == 4
    assert rows[0] == {"comunaId": "CHL.A", "year": 2013, "burned_fraction": 0.1, "firms_count": 5}
    assert rows[3] == {"comunaId": "CHL.B", "year": 2014, "burned_fraction": 0.4, "firms_count": 9}


def test_secondary_rows_refuse_a_missing_pair():
    with pytest.raises(h1.H1InputError):
        h1.secondary_rows({"CHL.A": {2013: 0.1}}, {("CHL.A", 2013): 1}, comuna_ids=["CHL.A"], years=[2013, 2014])
