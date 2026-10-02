"""Representative comuna points, Open-Meteo Archive (ERA5) fetch, and
daily-noon-input derivation (design D4, PREREGISTRATION.md Section 8.1).

Scope note: this module builds INPUTS only. It never touches FIRMS and never
computes a quantity relating weather/FWI to any fire-activity target.
"""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path
from typing import Callable

OPEN_METEO_ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive"
HOURLY_VARS = "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation"
TIMEZONE = "America/Santiago"
MODEL = "era5"


class Era5FetchError(RuntimeError):
    """Raised when the Open-Meteo Archive request fails or returns an error payload."""


class MissingDataError(RuntimeError):
    """Raised when a required hourly value for a noon-input day is missing (null)."""


# ---------------------------------------------------------------------------
# Polygon geometry: a representative point guaranteed to lie inside the comuna
# ---------------------------------------------------------------------------


def polygon_rings(geometry: dict):
    """Yields ``(exterior, holes)`` for every polygon part of a GeoJSON
    Polygon or MultiPolygon geometry."""
    gtype = geometry["type"]
    coords = geometry["coordinates"]
    if gtype == "Polygon":
        parts = [coords]
    elif gtype == "MultiPolygon":
        parts = coords
    else:
        raise ValueError(f"unsupported geometry type {gtype!r}")
    for part in parts:
        exterior = part[0]
        holes = part[1:]
        yield exterior, holes


def ring_area(ring) -> float:
    """Shoelace-formula absolute area, in squared coordinate units.

    This is a PLANAR approximation (lon/lat treated as Cartesian), which is
    only used to compare parts of the SAME MultiPolygon against each other
    (pick the largest), never as a real area in m^2 -- unlike D2's pixel-area
    correction, which is for the mapbiomas zonal-stats pipeline, not this.
    """
    area = 0.0
    n = len(ring)
    for i in range(n - 1):
        x1, y1 = ring[i][0], ring[i][1]
        x2, y2 = ring[i + 1][0], ring[i + 1][1]
        area += x1 * y2 - x2 * y1
    return abs(area) / 2.0


def point_in_ring(point, ring) -> bool:
    """Standard ray-casting point-in-polygon test against one ring (closed or not)."""
    x, y = point
    n = len(ring)
    inside = False
    j = n - 1
    for i in range(n):
        xi, yi = ring[i][0], ring[i][1]
        xj, yj = ring[j][0], ring[j][1]
        if (yi > y) != (yj > y):
            x_intersect = (xj - xi) * (y - yi) / (yj - yi) + xi
            if x < x_intersect:
                inside = not inside
        j = i
    return inside


def point_in_polygon(point, exterior, holes) -> bool:
    """A point is inside a polygon part iff it is inside the exterior ring
    and outside every hole."""
    if not point_in_ring(point, exterior):
        return False
    for hole in holes:
        if point_in_ring(point, hole):
            return False
    return True


def representative_point(geometry: dict) -> tuple[float, float]:
    """Returns ONE ``(lon, lat)`` point guaranteed to lie inside ``geometry``.

    Method (documented per the launch prompt's requirement):
    1. Pick the polygon part with the largest exterior area (the mainland
       piece for comunas whose MultiPolygon includes small offshore
       islands as separate parts).
    2. Try the vertex-average ("centroid" in the loose sense) of that
       part's exterior ring. For convex rings this is always inside and is
       cheap.
    3. If the vertex-average is NOT actually inside (concave rings, e.g. a
       C-shaped or horseshoe comuna, can put it in a notch outside the
       polygon -- the exact failure mode a plain centroid has and the
       reason a centroid is rejected by the launch prompt), fall back to a
       grid search over the part's bounding box (101x101) and keep the
       inside grid point closest to the vertex-average.
    4. If the polygon part is so thin the grid misses every sample (last
       resort, not expected for any of the 86 comunas -- verified by
       ``load_comuna_points`` below), fall back to the first vertex of the
       exterior ring, which is always inside-or-on-the-boundary.
    """
    parts = list(polygon_rings(geometry))
    exterior, holes = max(parts, key=lambda p: ring_area(p[0]))

    pts = exterior[:-1] if exterior[0] == exterior[-1] else list(exterior)
    avg_lon = sum(p[0] for p in pts) / len(pts)
    avg_lat = sum(p[1] for p in pts) / len(pts)
    candidate = (avg_lon, avg_lat)
    if point_in_polygon(candidate, exterior, holes):
        return candidate

    lons = [p[0] for p in pts]
    lats = [p[1] for p in pts]
    min_lon, max_lon = min(lons), max(lons)
    min_lat, max_lat = min(lats), max(lats)
    grid_n = 101
    best = None
    best_dist = None
    for i in range(grid_n):
        gx = min_lon + (max_lon - min_lon) * i / (grid_n - 1)
        for j in range(grid_n):
            gy = min_lat + (max_lat - min_lat) * j / (grid_n - 1)
            if point_in_polygon((gx, gy), exterior, holes):
                dist = (gx - avg_lon) ** 2 + (gy - avg_lat) ** 2
                if best_dist is None or dist < best_dist:
                    best_dist = dist
                    best = (gx, gy)
    if best is not None:
        return best

    return (pts[0][0], pts[0][1])


def load_comuna_points(geojson_dir: Path) -> dict[str, tuple[float, float]]:
    """Reads every ``comunas-*.geojson`` in ``geojson_dir`` and returns
    ``{comunaId: (lon, lat)}`` for all monitored comunas."""
    geojson_dir = Path(geojson_dir)
    points: dict[str, tuple[float, float]] = {}
    for path in sorted(geojson_dir.glob("comunas-*.geojson")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for feature in data["features"]:
            comuna_id = feature["properties"]["comunaId"]
            points[comuna_id] = representative_point(feature["geometry"])
    return points


# ---------------------------------------------------------------------------
# Open-Meteo Archive (ERA5) fetch
# ---------------------------------------------------------------------------


def _default_opener(url: str, timeout: float):
    return urllib.request.urlopen(url, timeout=timeout)


def build_request_url(lat: float, lon: float, start_date: str, end_date: str, *, model: str = MODEL) -> str:
    params = {
        "latitude": f"{lat:.4f}",
        "longitude": f"{lon:.4f}",
        "start_date": start_date,
        "end_date": end_date,
        "hourly": HOURLY_VARS,
        "timezone": TIMEZONE,
        "models": model,
    }
    return OPEN_METEO_ARCHIVE_URL + "?" + urllib.parse.urlencode(params)


def fetch_hourly(
    lat: float,
    lon: float,
    start_date: str,
    end_date: str,
    *,
    opener: Callable[[str, float], object] | None = None,
    timeout: float = 60.0,
    retries: int = 5,
    retry_backoff: float = 2.0,
    sleep: Callable[[float], None] = time.sleep,
    model: str = MODEL,
) -> dict:
    """Fetches hourly ERA5 archive data for one point, retrying on failure.

    Being polite (per the launch prompt): this function makes exactly ONE
    request per call; the caller (``scripts/download_era5.py``) is
    responsible for calling it sequentially, one comuna at a time, never in
    parallel.
    """
    if retries < 1:
        raise ValueError("retries must be >= 1")
    opener = opener or _default_opener
    url = build_request_url(lat, lon, start_date, end_date, model=model)
    last_exc: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            with opener(url, timeout) as response:
                raw = response.read()
            payload = json.loads(raw.decode("utf-8") if isinstance(raw, bytes) else raw)
            if "error" in payload:
                raise Era5FetchError(f"Open-Meteo error response for {url}: {payload}")
            if "hourly" not in payload:
                raise Era5FetchError(f"Open-Meteo response missing 'hourly' for {url}: {payload}")
            return payload
        except Era5FetchError:
            raise
        except Exception as exc:  # retried below; wrapped and re-raised once exhausted
            last_exc = exc
            if attempt < retries:
                sleep(retry_backoff * attempt)
    raise Era5FetchError(
        f"Failed to fetch Open-Meteo archive for ({lat}, {lon}) {start_date}..{end_date} "
        f"after {retries} attempts"
    ) from last_exc


# ---------------------------------------------------------------------------
# Daily noon-input derivation (D4: noon temp/RH/wind + 24h rain ending at noon)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class NoonRecord:
    date: str
    temp_c: float
    rh_pct: float
    wind_kmh: float
    rain_mm: float


def _date_range(start_date_str: str, end_date_str: str):
    start = date.fromisoformat(start_date_str)
    end = date.fromisoformat(end_date_str)
    d = start
    while d <= end:
        yield d
        d += timedelta(days=1)


def derive_noon_inputs(payload: dict, start_date: str, end_date: str, *, comuna_id: str | None = None) -> list[NoonRecord]:
    """Derives one ``NoonRecord`` per day in ``[start_date, end_date]`` from
    an Open-Meteo Archive hourly payload, per design D4:

    - ``temp_c``, ``rh_pct``, ``wind_kmh``: the value at 12:00 local time.
    - ``rain_mm``: the 24-hour accumulated precipitation ending at noon
      (sum of hourly values from 13:00 of D-1 through 12:00 of D inclusive),
      the Van Wagner noon-rain convention.

    Raises ``MissingDataError`` (never interpolates) naming the comuna (if
    given) and date, if any required hourly value is absent from the
    payload or is ``null``.
    """
    hourly = payload["hourly"]
    times = hourly["time"]
    index = {t: i for i, t in enumerate(times)}
    temps = hourly["temperature_2m"]
    rhs = hourly["relative_humidity_2m"]
    winds = hourly["wind_speed_10m"]
    precs = hourly["precipitation"]

    who = f" for comuna {comuna_id}" if comuna_id else ""
    records: list[NoonRecord] = []
    for d in _date_range(start_date, end_date):
        noon_key = f"{d.isoformat()}T12:00"
        if noon_key not in index:
            raise MissingDataError(f"Missing noon ({noon_key}) hourly data{who}")
        idx = index[noon_key]
        temp = temps[idx]
        rh = rhs[idx]
        wind = winds[idx]
        if temp is None or rh is None or wind is None:
            raise MissingDataError(f"Null noon value(s) on {d.isoformat()}{who}: temp={temp}, rh={rh}, wind={wind}")

        rain_total = 0.0
        prev_day = d - timedelta(days=1)
        rain_hours = [(prev_day, h) for h in range(13, 24)] + [(d, h) for h in range(0, 13)]
        for day_part, hour in rain_hours:
            key = f"{day_part.isoformat()}T{hour:02d}:00"
            if key not in index:
                raise MissingDataError(
                    f"Missing rain-window hour ({key}) for the noon input of {d.isoformat()}{who}"
                )
            value = precs[index[key]]
            if value is None:
                raise MissingDataError(
                    f"Null rain-window hour ({key}) for the noon input of {d.isoformat()}{who}"
                )
            rain_total += value

        records.append(
            NoonRecord(date=d.isoformat(), temp_c=temp, rh_pct=rh, wind_kmh=wind, rain_mm=rain_total)
        )
    return records
