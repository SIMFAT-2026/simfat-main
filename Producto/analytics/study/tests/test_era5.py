"""era5.py: representative points, Open-Meteo Archive fetch, noon-input derivation.

Network-free by design: ``fetch_hourly`` is exercised with an injected fake
opener (mirrors mb_pipeline/download.py's pattern); no test requires live
internet. The representative-point tests use small synthetic polygons AND
the real repo GeoJSON (86 comunas), so the guarantee ("always inside, never
a centroid that falls outside") is checked for real data too.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from study import era5

REPO_PRODUCTO_DIR = Path(__file__).resolve().parents[3]
GEOJSON_DIR = (
    REPO_PRODUCTO_DIR
    / "backend"
    / "simfat-backend"
    / "src"
    / "main"
    / "resources"
    / "static"
    / "geojson"
)


# ---- polygon geometry helpers -------------------------------------------


def test_ring_area_unit_square():
    ring = [[0, 0], [1, 0], [1, 1], [0, 1], [0, 0]]
    assert era5.ring_area(ring) == pytest.approx(1.0)


def test_point_in_ring_inside_and_outside_square():
    square = [[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]]
    assert era5.point_in_ring((5, 5), square) is True
    assert era5.point_in_ring((15, 5), square) is False


def test_point_in_polygon_respects_holes():
    outer = [[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]]
    hole = [[4, 4], [6, 4], [6, 6], [4, 6], [4, 4]]
    assert era5.point_in_polygon((5, 5), outer, [hole]) is False  # inside the hole
    assert era5.point_in_polygon((1, 1), outer, [hole]) is True  # inside donut


def test_representative_point_convex_square_returns_the_centroid():
    geometry = {"type": "Polygon", "coordinates": [[[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]]]}

    point = era5.representative_point(geometry)

    assert era5.point_in_polygon(point, geometry["coordinates"][0], []) is True
    assert point == pytest.approx((5.0, 5.0))


def test_representative_point_concave_c_shape_falls_back_to_grid_search():
    # A "C"/staple shape whose vertex-average lands in the notch, OUTSIDE the
    # polygon -- exactly the failure mode a plain centroid has.
    c_shape = [
        [0, 0], [10, 0], [10, 3], [3, 3], [3, 7], [10, 7], [10, 10], [0, 10], [0, 0],
    ]
    geometry = {"type": "Polygon", "coordinates": [c_shape]}
    vertex_avg_x = sum(p[0] for p in c_shape[:-1]) / (len(c_shape) - 1)
    vertex_avg_y = sum(p[1] for p in c_shape[:-1]) / (len(c_shape) - 1)
    assert era5.point_in_polygon((vertex_avg_x, vertex_avg_y), c_shape, []) is False

    point = era5.representative_point(geometry)

    assert era5.point_in_polygon(point, c_shape, []) is True


def test_representative_point_multipolygon_picks_the_largest_part():
    small_island = [[0, 0], [1, 0], [1, 1], [0, 1], [0, 0]]
    mainland = [[20, 20], [30, 20], [30, 30], [20, 30], [20, 20]]
    geometry = {"type": "MultiPolygon", "coordinates": [[small_island], [mainland]]}

    point = era5.representative_point(geometry)

    assert era5.point_in_polygon(point, mainland, []) is True
    assert era5.point_in_polygon(point, small_island, []) is False


def test_load_comuna_geometries_covers_all_86_comunas_and_returns_raw_geometry():
    geometries = era5.load_comuna_geometries(GEOJSON_DIR)

    assert len(geometries) == 86
    for path in GEOJSON_DIR.glob("comunas-*.geojson"):
        data = json.loads(path.read_text(encoding="utf-8"))
        for feature in data["features"]:
            comuna_id = feature["properties"]["comunaId"]
            assert geometries[comuna_id] == feature["geometry"]


def test_load_comuna_points_covers_all_86_comunas_and_every_point_is_inside_its_polygon():
    points = era5.load_comuna_points(GEOJSON_DIR)

    assert len(points) == 86

    for path in GEOJSON_DIR.glob("comunas-*.geojson"):
        data = json.loads(path.read_text(encoding="utf-8"))
        for feature in data["features"]:
            comuna_id = feature["properties"]["comunaId"]
            lon, lat = points[comuna_id]
            parts = list(era5.polygon_rings(feature["geometry"]))
            inside_some_part = any(
                era5.point_in_polygon((lon, lat), exterior, holes) for exterior, holes in parts
            )
            assert inside_some_part, f"representative point for {comuna_id} is outside every polygon part"


# ---- Open-Meteo Archive fetch ---------------------------------------------


def test_build_request_url_includes_era5_model_and_hourly_vars():
    url = era5.build_request_url(-37.29, -73.43, "2016-08-31", "2025-04-30")

    assert url.startswith(era5.OPEN_METEO_ARCHIVE_URL + "?")
    assert "models=era5" in url
    assert "hourly=temperature_2m%2Crelative_humidity_2m%2Cwind_speed_10m%2Cprecipitation" in url
    assert "timezone=America%2FSantiago" in url
    assert "start_date=2016-08-31" in url
    assert "end_date=2025-04-30" in url


class _FakeResponse:
    def __init__(self, payload_bytes):
        self._payload = payload_bytes
        self._read = False

    def read(self):
        return self._payload

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


def _payload_bytes(obj):
    return json.dumps(obj).encode("utf-8")


def test_fetch_hourly_happy_path_returns_parsed_payload():
    body = {"hourly": {"time": ["2024-01-01T00:00"], "temperature_2m": [10.0]}}

    def opener(url, timeout):
        return _FakeResponse(_payload_bytes(body))

    result = era5.fetch_hourly(-37.29, -73.43, "2024-01-01", "2024-01-01", opener=opener, sleep=lambda _s: None)

    assert result == body


def test_fetch_hourly_retries_then_succeeds():
    body = {"hourly": {"time": [], "temperature_2m": []}}
    calls = []

    def opener(url, timeout):
        calls.append(url)
        if len(calls) < 3:
            raise ConnectionError("simulated transient failure")
        return _FakeResponse(_payload_bytes(body))

    sleeps = []
    result = era5.fetch_hourly(
        -37.29, -73.43, "2024-01-01", "2024-01-01", opener=opener, retries=3, sleep=sleeps.append
    )

    assert result == body
    assert len(calls) == 3
    assert len(sleeps) == 2


def test_fetch_hourly_raises_after_exhausting_retries():
    def always_fails(url, timeout):
        raise ConnectionError("simulated permanent failure")

    with pytest.raises(era5.Era5FetchError):
        era5.fetch_hourly(
            -37.29, -73.43, "2024-01-01", "2024-01-01", opener=always_fails, retries=2, sleep=lambda _s: None
        )


def test_fetch_hourly_raises_on_error_response_without_retrying_forever():
    def opener(url, timeout):
        return _FakeResponse(_payload_bytes({"error": True, "reason": "bad params"}))

    with pytest.raises(era5.Era5FetchError, match="bad params"):
        era5.fetch_hourly(
            -37.29, -73.43, "2024-01-01", "2024-01-01", opener=opener, retries=2, sleep=lambda _s: None
        )


# ---- noon-input derivation -------------------------------------------------


def _hours(date_str, start_hour, end_hour, values_by_hour):
    """Builds (time, value) pairs for hours [start_hour, end_hour] of date_str."""
    return [
        (f"{date_str}T{h:02d}:00", values_by_hour.get(h))
        for h in range(start_hour, end_hour + 1)
    ]


def test_derive_noon_inputs_extracts_noon_values_and_24h_rain_sum():
    # D-1 = 2024-01-01 (hours 13..23), D = 2024-01-02 (hours 0..12).
    times = []
    temps = []
    rhs = []
    winds = []
    precs = []

    def add(date_str, hour, temp, rh, wind, prec):
        times.append(f"{date_str}T{hour:02d}:00")
        temps.append(temp)
        rhs.append(rh)
        winds.append(wind)
        precs.append(prec)

    for h in range(13, 24):
        add("2024-01-01", h, 10.0, 50.0, 5.0, 0.1)  # 11 hours, 1.1mm
    for h in range(0, 13):
        add("2024-01-02", h, 20.0 if h == 12 else 15.0, 40.0 if h == 12 else 45.0, 8.0 if h == 12 else 6.0, 0.2)  # 13 hours, 2.6mm

    payload = {
        "hourly": {
            "time": times,
            "temperature_2m": temps,
            "relative_humidity_2m": rhs,
            "wind_speed_10m": winds,
            "precipitation": precs,
        }
    }

    records = era5.derive_noon_inputs(payload, "2024-01-02", "2024-01-02")

    assert len(records) == 1
    record = records[0]
    assert record.date == "2024-01-02"
    assert record.temp_c == 20.0
    assert record.rh_pct == 40.0
    assert record.wind_kmh == 8.0
    assert record.rain_mm == pytest.approx(1.1 + 2.6)


def test_derive_noon_inputs_raises_when_noon_value_is_missing():
    times = [f"2024-01-01T{h:02d}:00" for h in range(24)] + [f"2024-01-02T{h:02d}:00" for h in range(13)]
    temps = [10.0] * len(times)
    temps[-1] = None  # 2024-01-02T12:00 missing
    rhs = [50.0] * len(times)
    winds = [5.0] * len(times)
    precs = [0.0] * len(times)
    payload = {
        "hourly": {
            "time": times,
            "temperature_2m": temps,
            "relative_humidity_2m": rhs,
            "wind_speed_10m": winds,
            "precipitation": precs,
        }
    }

    with pytest.raises(era5.MissingDataError, match="2024-01-02"):
        era5.derive_noon_inputs(payload, "2024-01-02", "2024-01-02")


def test_derive_noon_inputs_raises_when_a_rain_window_hour_is_missing():
    times = [f"2024-01-01T{h:02d}:00" for h in range(24)] + [f"2024-01-02T{h:02d}:00" for h in range(13)]
    temps = [10.0] * len(times)
    rhs = [50.0] * len(times)
    winds = [5.0] * len(times)
    precs = [0.0] * len(times)
    precs[15] = None  # 2024-01-01T15:00 (hour 15, inside the 13:00-23:00 D-1 rain window)
    payload = {
        "hourly": {
            "time": times,
            "temperature_2m": temps,
            "relative_humidity_2m": rhs,
            "wind_speed_10m": winds,
            "precipitation": precs,
        }
    }

    with pytest.raises(era5.MissingDataError, match="2024-01-02"):
        era5.derive_noon_inputs(payload, "2024-01-02", "2024-01-02")


def test_derive_noon_inputs_raises_when_date_minus_one_is_entirely_absent():
    # D-1 hours not present at all in the payload.
    times = [f"2024-01-02T{h:02d}:00" for h in range(13)]
    temps = [10.0] * len(times)
    rhs = [50.0] * len(times)
    winds = [5.0] * len(times)
    precs = [0.0] * len(times)
    payload = {
        "hourly": {
            "time": times,
            "temperature_2m": temps,
            "relative_humidity_2m": rhs,
            "wind_speed_10m": winds,
            "precipitation": precs,
        }
    }

    with pytest.raises(era5.MissingDataError):
        era5.derive_noon_inputs(payload, "2024-01-02", "2024-01-02")
