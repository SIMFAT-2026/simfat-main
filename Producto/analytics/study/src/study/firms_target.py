"""FIRMS target construction (PREREGISTRATION.md Section 6, D7 "H2
circularity resolved").

``y(c, s) = 1`` if comuna ``c`` has at least ``N_FIRMS`` detections during
season ``s`` with ``type == 0`` AND ``confidence`` in ``{n, h}``. This module
builds that target (and its pre-declared sensitivities: ``N_FIRMS`` in
{1, 3, 5}, the Nov-Apr sensitivity window, and the no-``type``-filter
variant) directly from the committed FIRMS archive.

BLINDING RULE (hard, non-negotiable): this module builds the FIRMS TARGET
ONLY. It never computes, prints, or returns any quantity that relates a
predictor (FWI, S_dynamic, S_mapbiomas, fuel, history) to this target -- not
here, not in its tests, not in build_firms_target.py's printed output. The
target and the predictors are written to SEPARATE files
(data/features/firms_target.csv vs. data/features/season_mapbiomas.csv /
season_fwi.csv), never joined in one file by this slice.

IMPLEMENTATION NOTE -- comuna assignment and DST handling (not fixed by the
pre-registration, documented here per the launch prompt):

- Comuna assignment: FIRMS detections carry only (lat, lon); this module
  assigns each detection to a comuna by point-in-polygon against the repo's
  own ``comunas-*.geojson`` (``comunaId`` property, the GADM GID_3 code).
  Every polygon PART of a comuna's MultiPolygon is checked (not just the
  largest, unlike ``era5.representative_point``'s "pick one point" use
  case) and holes are excluded. A detection that falls inside no comuna's
  polygon at all is DROPPED and counted (never silently assigned to the
  nearest comuna).
- UTC -> America/Santiago local date, and the DST gotcha: ``acq_date`` +
  ``acq_time`` in the FIRMS archive are UTC; season-window membership is
  defined in LOCAL (Chile) calendar dates (PREREGISTRATION.md Section 5),
  so every detection's UTC timestamp is converted to a local CALENDAR DATE
  before any window check. This sandboxed Windows environment has no
  system IANA time-zone database and installing the ``tzdata`` package is
  disallowed by the launch prompt's dependency constraint (stdlib + numpy +
  rasterio only, no installs) -- so Python's ``zoneinfo.ZoneInfo("America/
  Santiago")`` cannot resolve here (``ZoneInfoNotFoundError``), even though
  it is stdlib. As a disclosed substitute, Chile's mainland DST rule stable
  since the 2016 decree (DST from the first Sunday of September through
  the day before the first Sunday of April the following year, UTC-3;
  UTC-4 otherwise) is implemented directly in stdlib ``datetime`` (see
  ``is_dst_chile``). This was NOT independently re-verified against an
  official source in this environment (no internet access here either);
  it is this module's best-effort reproduction of the publicly documented
  rule. **Candidate deviation from the literal launch-prompt instruction
  ("use zoneinfo")**, reported rather than silently worked around -- if an
  environment with ``tzdata`` installed (or a live system clock database)
  is available later, swap ``is_dst_chile``/``to_local_date`` for
  ``zoneinfo.ZoneInfo("America/Santiago")`` and the two should agree for
  every date in this study's range (Nov 2016 .. Apr 2025), since none of
  the pre-registered window boundaries fall within a few hours of a
  transition instant (Nov 1 is deep in DST; Apr 30 is always well after the
  April transition, which always falls in the first week of April).

  A second, narrower consequence of the same missing tzdata: even
  ``zoneinfo.ZoneInfo("UTC")`` fails to resolve in this environment (it is
  not special-cased by CPython; it is just another IANA key backed by the
  same missing database), so the UTC timestamps below use
  ``datetime.timezone.utc`` (always available, no database lookup needed)
  instead of ``zoneinfo.ZoneInfo("UTC")``.
"""
from __future__ import annotations

import csv
import io
import zipfile
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Iterable, Mapping, Sequence

from study import era5
from study.season_fwi import SEASONS, sensitivity_window, standard_window

UTC = timezone.utc

PRIMARY_TYPE_VALUES = {0}
CONFIDENCE_FILTER = {"n", "h"}
N_THRESHOLDS = (1, 3, 5)

WINDOWS: dict[str, "callable"] = {"primary": standard_window, "sensitivity": sensitivity_window}
FILTERS: tuple[str, ...] = ("type0", "notype")

CSV_HEADER = ["comunaId", "season", "window", "filter", "count", "y_n1", "y_n3", "y_n5"]


class FirmsTargetGateError(ValueError):
    """Raised when the built target rows violate one of the hard gates
    (every comuna-season-window-filter present; counts non-negative;
    sensitivity >= primary; notype >= type0)."""


# ---------------------------------------------------------------------------
# UTC -> America/Santiago local date (see module docstring)
# ---------------------------------------------------------------------------


def _nth_weekday_of_month(year: int, month: int, weekday: int, n: int) -> date:
    """The n-th (1-based) occurrence of ``weekday`` (Mon=0..Sun=6) in
    ``year-month``."""
    first_of_month = date(year, month, 1)
    offset = (weekday - first_of_month.weekday()) % 7
    first_occurrence = first_of_month + timedelta(days=offset)
    return first_occurrence + timedelta(weeks=n - 1)


def is_dst_chile(d: date) -> bool:
    """Chile mainland DST rule stable since 2016: DST (UTC-3) from the first
    Sunday of September through the day before the first Sunday of the
    following April; UTC-4 (standard) otherwise. See the module docstring's
    IMPLEMENTATION NOTE for why this is implemented directly instead of via
    ``zoneinfo``."""
    SUNDAY = 6
    dst_start = _nth_weekday_of_month(d.year, 9, SUNDAY, 1)
    dst_end = _nth_weekday_of_month(d.year, 4, SUNDAY, 1)
    return d >= dst_start or d < dst_end


def parse_acq_datetime_utc(acq_date: str, acq_time: str) -> datetime:
    """Combines FIRMS' ``acq_date`` (``YYYY-MM-DD``) and ``acq_time``
    (``HMM``/``HHMM``, zero-padded here) into a UTC-aware ``datetime``."""
    hhmm = acq_time.strip().zfill(4)
    hour, minute = int(hhmm[:2]), int(hhmm[2:])
    d = date.fromisoformat(acq_date)
    return datetime(d.year, d.month, d.day, hour, minute, tzinfo=UTC)


def to_local_date(dt_utc: datetime) -> date:
    """UTC datetime -> America/Santiago local CALENDAR date.

    Two-pass fixed point: the DST regime is first guessed from the UTC
    calendar date, then re-derived from the resulting candidate local date,
    closing the narrow one-day gap that can occur right at the Sept/April
    transition instants (irrelevant to this study's actual season windows,
    see the module docstring, but kept correct regardless).
    """
    candidate_offset = -3 if is_dst_chile(dt_utc.date()) else -4
    candidate_local = (dt_utc + timedelta(hours=candidate_offset)).date()
    offset = -3 if is_dst_chile(candidate_local) else -4
    return (dt_utc + timedelta(hours=offset)).date()


# ---------------------------------------------------------------------------
# Point-in-polygon comuna assignment
# ---------------------------------------------------------------------------


def point_in_geometry(point: tuple[float, float], geometry: dict) -> bool:
    """True iff ``point`` is inside ANY polygon part of ``geometry``
    (Polygon or MultiPolygon), holes excluded. Unlike
    ``era5.representative_point`` (which only needs the single largest
    part), comuna assignment must check every part -- a detection over a
    small offshore-island part must still be assigned to that comuna."""
    for exterior, holes in era5.polygon_rings(geometry):
        if era5.point_in_polygon(point, exterior, holes):
            return True
    return False


def load_comuna_polygons(geojson_dir: Path) -> dict[str, dict]:
    """``{comunaId: geometry}`` for all 86 monitored comunas."""
    return era5.load_comuna_geometries(geojson_dir)


def assign_comuna(lon: float, lat: float, polygons: Mapping[str, dict]) -> str | None:
    """Returns the first comuna whose polygon contains ``(lon, lat)``, or
    ``None`` if the point is outside every monitored comuna's polygon
    (dropped by the caller, never assigned to the nearest one)."""
    for comuna_id, geometry in polygons.items():
        if point_in_geometry((lon, lat), geometry):
            return comuna_id
    return None


# ---------------------------------------------------------------------------
# Reading the FIRMS archive
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class FirmsDetection:
    comuna_id: str
    local_date: date
    type_code: int
    confidence: str


def iter_firms_rows(zip_path: Path) -> Iterable[dict]:
    """Yields each row of the FIRMS archive's CSV as a ``dict`` (raw string
    values, as read by ``csv.DictReader``)."""
    with zipfile.ZipFile(zip_path) as zf:
        csv_name = next(name for name in zf.namelist() if name.lower().endswith(".csv"))
        with zf.open(csv_name) as raw:
            text = io.TextIOWrapper(raw, encoding="utf-8", newline="")
            yield from csv.DictReader(text)


def read_detections(zip_path: Path, polygons: Mapping[str, dict]) -> tuple[list[FirmsDetection], int]:
    """Reads every row of the FIRMS archive, assigns it to a comuna, and
    converts its UTC timestamp to a local date.

    Returns ``(detections, dropped_outside_comunas)``: rows whose point
    falls outside every monitored comuna's polygon are dropped and counted,
    never silently assigned.
    """
    detections: list[FirmsDetection] = []
    dropped_outside = 0
    for row in iter_firms_rows(zip_path):
        lon, lat = float(row["longitude"]), float(row["latitude"])
        comuna_id = assign_comuna(lon, lat, polygons)
        if comuna_id is None:
            dropped_outside += 1
            continue
        dt_utc = parse_acq_datetime_utc(row["acq_date"], row["acq_time"])
        detections.append(
            FirmsDetection(
                comuna_id=comuna_id,
                local_date=to_local_date(dt_utc),
                type_code=int(row["type"]),
                confidence=row["confidence"],
            )
        )
    return detections, dropped_outside


# ---------------------------------------------------------------------------
# Filtering, counting, and the target itself
# ---------------------------------------------------------------------------


def _in_window(d: date, window: tuple[date, date]) -> bool:
    return window[0] <= d <= window[1]


def _passes_filter(det: FirmsDetection, filter_name: str) -> bool:
    if det.confidence not in CONFIDENCE_FILTER:
        return False
    if filter_name == "type0":
        return det.type_code in PRIMARY_TYPE_VALUES
    if filter_name == "notype":
        return True
    raise ValueError(f"unknown filter {filter_name!r}")


def build_counts(
    detections: Sequence[FirmsDetection],
    comuna_ids: Iterable[str],
    *,
    seasons: Sequence[int] = SEASONS,
) -> list[dict]:
    """One row per (comunaId, season, window, filter) -- 86 x 9 x 2 x 2 in
    the real run -- with zero counts included explicitly, never missing."""
    by_comuna: dict[str, list[FirmsDetection]] = {}
    for det in detections:
        by_comuna.setdefault(det.comuna_id, []).append(det)

    rows: list[dict] = []
    for comuna_id in comuna_ids:
        dets = by_comuna.get(comuna_id, [])
        for season in seasons:
            for window_name, window_fn in WINDOWS.items():
                window = window_fn(season)
                for filter_name in FILTERS:
                    count = sum(
                        1
                        for det in dets
                        if _in_window(det.local_date, window) and _passes_filter(det, filter_name)
                    )
                    rows.append(
                        {
                            "comunaId": comuna_id,
                            "season": season,
                            "window": window_name,
                            "filter": filter_name,
                            "count": count,
                            "y_n1": int(count >= 1),
                            "y_n3": int(count >= 3),
                            "y_n5": int(count >= 5),
                        }
                    )
    return rows


def validate_gates(
    rows: Sequence[Mapping], comuna_ids: Iterable[str], *, seasons: Sequence[int] = SEASONS
) -> None:
    """Hard gates (launch prompt scope item 1):

    - every (comunaId, season, window, filter) combination is present;
    - every count is a non-negative integer;
    - the sensitivity window's count >= the primary window's (same filter);
    - the no-type-filter ("notype") count >= the type-filtered ("type0")
      count (same window).
    """
    comuna_ids = list(comuna_ids)
    by_key: dict[tuple, dict] = {}
    for r in rows:
        key = (r["comunaId"], r["season"], r["window"], r["filter"])
        by_key[key] = r

    for comuna_id in comuna_ids:
        for season in seasons:
            for window_name in WINDOWS:
                for filter_name in FILTERS:
                    key = (comuna_id, season, window_name, filter_name)
                    if key not in by_key:
                        raise FirmsTargetGateError(f"missing row for {key}")
                    count = by_key[key]["count"]
                    if not isinstance(count, int) or count < 0:
                        raise FirmsTargetGateError(f"count must be a non-negative integer, got {count!r} for {key}")

    for comuna_id in comuna_ids:
        for season in seasons:
            for filter_name in FILTERS:
                primary = by_key[(comuna_id, season, "primary", filter_name)]["count"]
                sensitivity = by_key[(comuna_id, season, "sensitivity", filter_name)]["count"]
                if sensitivity < primary:
                    raise FirmsTargetGateError(
                        f"sensitivity count ({sensitivity}) < primary count ({primary}) for "
                        f"comuna={comuna_id!r} season={season} filter={filter_name!r}"
                    )
            for window_name in WINDOWS:
                type0 = by_key[(comuna_id, season, window_name, "type0")]["count"]
                notype = by_key[(comuna_id, season, window_name, "notype")]["count"]
                if notype < type0:
                    raise FirmsTargetGateError(
                        f"notype count ({notype}) < type0 count ({type0}) for "
                        f"comuna={comuna_id!r} season={season} window={window_name!r}"
                    )


# ---------------------------------------------------------------------------
# Descriptive summaries (counts only -- never a predictor-vs-target relation)
# ---------------------------------------------------------------------------


def filter_summary(detections: Sequence[FirmsDetection]) -> dict:
    """Counts of detections dropped by each filter, among detections already
    assigned to a comuna (``read_detections``'s ``dropped_outside`` is
    reported separately -- it happens before this summary even sees a row).
    """
    total = len(detections)
    dropped_by_confidence = sum(1 for d in detections if d.confidence not in CONFIDENCE_FILTER)
    confidence_passing = [d for d in detections if d.confidence in CONFIDENCE_FILTER]
    dropped_by_type = sum(1 for d in confidence_passing if d.type_code not in PRIMARY_TYPE_VALUES)
    final_type0_count = len(confidence_passing) - dropped_by_type
    return {
        "total": total,
        "dropped_by_confidence": dropped_by_confidence,
        "dropped_by_type_among_confidence_passing": dropped_by_type,
        "final_type0_count": final_type0_count,
        "final_notype_count": len(confidence_passing),
    }


def prevalence_for_season(
    rows: Sequence[Mapping], season: int, *, window: str, filter_name: str, n: int
) -> float:
    """Mean of ``y_n{n}`` over every comuna for ``season`` under the given
    window/filter -- the overall positive rate, which Section 9 of the
    pre-registration explicitly allows reporting (it is not a
    predictor-vs-target relation)."""
    y_key = f"y_n{n}"
    matching = [r[y_key] for r in rows if r["season"] == season and r["window"] == window and r["filter"] == filter_name]
    if not matching:
        raise ValueError(f"no rows for season={season} window={window!r} filter={filter_name!r}")
    return sum(matching) / len(matching)


# ---------------------------------------------------------------------------
# CSV writer
# ---------------------------------------------------------------------------


def write_csv(path: Path, rows: Sequence[Mapping]) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_HEADER)
        writer.writeheader()
        for r in rows:
            writer.writerow({k: r[k] for k in CSV_HEADER})
