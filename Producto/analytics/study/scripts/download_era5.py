"""Downloads Open-Meteo Archive (ERA5) hourly weather for all 86 monitored
comunas and derives the committed daily-noon-input table (PREREGISTRATION.md
Section 8.1, design D4).

Usage (from Producto/analytics/study, with ``src`` and ``scripts`` on
PYTHONPATH -- see pytest.ini / README.md):

    python scripts/download_era5.py

Behaviour:
- Verifies PREREGISTRATION.md's sha256 against PREREGISTRATION.lock.json
  before doing anything else (``study.config.verify_preregistration``).
- One representative point per comuna, read from the repo's own GeoJSON
  (``study.era5.load_comuna_points`` -- guaranteed inside the polygon, never
  a possibly-outside centroid). See era5.py's ``representative_point``
  docstring for the method.
- Fetches hourly ``temperature_2m, relative_humidity_2m, wind_speed_10m,
  precipitation`` from the Open-Meteo Archive, explicit ``models=era5``,
  ``timezone=America/Santiago``, SEQUENTIALLY (one comuna at a time, never
  in parallel) with retries and exponential-ish backoff -- see
  ``study.era5.fetch_hourly``.
- The raw hourly request actually starts ONE DAY BEFORE ``START_DATE``
  (Aug 31 instead of Sep 1): the noon-rain window for the very first day
  (Sep 1) needs D-1's hours 13:00-23:00, which do not exist for Sep 1
  itself. This is a necessary consequence of the rain-window formula, not a
  change to the pre-registered date range.
- Caches the raw per-comuna JSON response OUTSIDE the repo (under
  ``%TEMP%\\study_era5`` / ``$TMPDIR/study_era5``), atomically, and records
  every response's sha256 in the manifest.
- Derives one noon-input row per comuna per day (noon temp/RH/wind, 24h rain
  ending at noon) and GATES: if any comuna or day is missing after retries,
  this script ABORTS loudly (``MissingDataError`` propagates) -- it never
  interpolates a missing day.
- Writes the committed, deterministic-gzip CSV
  (``data/era5/noon_inputs_<start>_<end>.csv.gz``, ``mtime=0`` so re-running
  on identical input bytes produces an identical committed file) and
  ``data/era5/manifest.json`` (request parameters, per-comuna point,
  retrieval date, each raw response's sha256, the committed file's sha256,
  and the Open-Meteo/ERA5 attribution).

Date range note (S4b1a launch prompt, scope item 3): the pre-registration's
Section 8.1 says the Open-Meteo Archive series has not yet been downloaded
and will be fetched "before any backtest result runs" without fixing an end
date in the contract text itself; the end date is extended from
2025-03-31 to 2025-04-30 here so the Nov-Apr SENSITIVITY window (also scope
item 3) is fully covered by real data instead of being partially built from
an incomplete range. This is a data-collection decision, not a change to
PREREGISTRATION.md's fixed parameters (margin, season set, seed, grid),
and is disclosed in the manifest's ``note`` field.
"""
from __future__ import annotations

import gzip
import hashlib
import io
import json
import os
import sys
from datetime import date, datetime, timedelta
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
STUDY_ROOT = SCRIPT_DIR.parent
SRC_DIR = STUDY_ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from study import config, era5  # noqa: E402

REPO_ROOT = STUDY_ROOT.parents[2]  # Producto/analytics/study -> Producto/analytics -> Producto -> repo root
GEOJSON_DIR = (
    REPO_ROOT
    / "Producto"
    / "backend"
    / "simfat-backend"
    / "src"
    / "main"
    / "resources"
    / "static"
    / "geojson"
)

START_DATE = date(2016, 9, 1)
END_DATE = date(2025, 4, 30)
RAW_FETCH_START = START_DATE - timedelta(days=1)  # D-1 coverage for Sep 1's noon rain window

RAW_CACHE_DIR = Path(os.environ.get("TEMP") or os.environ.get("TMPDIR") or "/tmp") / "study_era5"

OUTPUT_DIR = STUDY_ROOT / "data" / "era5"
OUTPUT_CSV = OUTPUT_DIR / f"noon_inputs_{START_DATE.isoformat()}_{END_DATE.isoformat()}.csv.gz"
MANIFEST_PATH = OUTPUT_DIR / "manifest.json"

ATTRIBUTION = (
    "Weather data by Open-Meteo.com (https://open-meteo.com/), ERA5 reanalysis by "
    "ECMWF (Copernicus Climate Change Service). Licence: CC BY 4.0, non-commercial "
    "use (project decision Q24, 2026-09-20)."
)

CSV_HEADER = "comunaId,date,tempC,rhPct,windKmh,rainMm\n"


def _atomic_write_bytes(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_bytes(data)
    os.replace(tmp, path)


def _gzip_deterministic(data: bytes) -> bytes:
    """gzips ``data`` with ``mtime=0`` so re-running on identical input bytes
    produces a byte-identical committed file (no timestamp noise in diffs)."""
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as gz:
        gz.write(data)
    return buf.getvalue()


def main() -> int:
    lock = config.verify_preregistration(STUDY_ROOT)
    print(f"Pre-registration integrity OK (sha256={lock['sha256'][:12]}..., commit={lock['commit'][:12]}...)")

    comuna_points = era5.load_comuna_points(GEOJSON_DIR)
    print(f"Loaded {len(comuna_points)} representative comuna points from {GEOJSON_DIR}")

    manifest_entries = []
    csv_rows = [CSV_HEADER]
    retrieval_date = datetime.utcnow().isoformat(timespec="seconds") + "Z"

    for i, comuna_id in enumerate(sorted(comuna_points), start=1):
        lon, lat = comuna_points[comuna_id]
        raw_path = RAW_CACHE_DIR / f"{comuna_id}.json"

        if raw_path.exists():
            raw_bytes = raw_path.read_bytes()
            print(f"[{i}/{len(comuna_points)}] {comuna_id}: using cached raw response")
        else:
            print(f"[{i}/{len(comuna_points)}] {comuna_id}: fetching ({lat:.4f}, {lon:.4f})...")
            payload = era5.fetch_hourly(lat, lon, RAW_FETCH_START.isoformat(), END_DATE.isoformat())
            raw_bytes = json.dumps(payload).encode("utf-8")
            _atomic_write_bytes(raw_path, raw_bytes)

        payload = json.loads(raw_bytes.decode("utf-8"))
        raw_sha256 = hashlib.sha256(raw_bytes).hexdigest()

        # Gate: raises MissingDataError (comuna + date named) if any noon
        # value or rain-window hour is missing. Never interpolated.
        records = era5.derive_noon_inputs(
            payload, START_DATE.isoformat(), END_DATE.isoformat(), comuna_id=comuna_id
        )
        expected_days = (END_DATE - START_DATE).days + 1
        if len(records) != expected_days:
            raise era5.MissingDataError(
                f"{comuna_id}: expected {expected_days} noon-input days, got {len(records)}"
            )

        for r in records:
            csv_rows.append(f"{comuna_id},{r.date},{r.temp_c},{r.rh_pct},{r.wind_kmh},{r.rain_mm}\n")

        manifest_entries.append(
            {
                "comunaId": comuna_id,
                "representativePoint": {"lon": lon, "lat": lat},
                "rawResponseSha256": raw_sha256,
                "rawResponseBytes": len(raw_bytes),
                "noonInputDays": len(records),
            }
        )

    csv_bytes = "".join(csv_rows).encode("utf-8")
    gz_bytes = _gzip_deterministic(csv_bytes)
    _atomic_write_bytes(OUTPUT_CSV, gz_bytes)
    committed_sha256 = hashlib.sha256(gz_bytes).hexdigest()

    manifest = {
        "source": "Open-Meteo Archive API (ERA5 reanalysis)",
        "url": era5.OPEN_METEO_ARCHIVE_URL,
        "model": era5.MODEL,
        "hourlyVariables": era5.HOURLY_VARS.split(","),
        "timezone": era5.TIMEZONE,
        "requestedRange": {"startDate": START_DATE.isoformat(), "endDate": END_DATE.isoformat()},
        "rawFetchRange": {
            "startDate": RAW_FETCH_START.isoformat(),
            "endDate": END_DATE.isoformat(),
            "note": "starts 1 day before requestedRange.startDate so Sep 1's 24h noon-rain window (13:00 D-1 .. 12:00 D) has D-1 data",
        },
        "note": (
            "End date extended from the pre-registration's working draft (2025-03-31) to "
            "2025-04-30 so the Nov-Apr sensitivity window (PREREGISTRATION.md Section 6 / "
            "D4) is fully covered by real data. Does not change any fixed parameter in "
            "PREREGISTRATION.md (margin, season set, seed, grid)."
        ),
        "representativePointMethod": (
            "One point per comuna, read from the repo's comunas-*.geojson. The largest "
            "polygon part's vertex-average is used if it tests inside the polygon "
            "(ray-casting, holes excluded); otherwise a 101x101 grid search over that "
            "part's bounding box finds the inside point closest to the vertex-average. "
            "See study.era5.representative_point."
        ),
        "retrievalDate": retrieval_date,
        "comunas": manifest_entries,
        "committedFile": {
            "path": str(OUTPUT_CSV.relative_to(STUDY_ROOT)).replace("\\", "/"),
            "sha256": committed_sha256,
            "bytes": len(gz_bytes),
            "rows": len(csv_rows) - 1,
        },
        "attribution": ATTRIBUTION,
        "preregistration": {"sha256": lock["sha256"], "commit": lock["commit"]},
    }
    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(f"Wrote {OUTPUT_CSV} ({len(gz_bytes)} bytes, {len(csv_rows) - 1} rows)")
    print(f"Wrote {MANIFEST_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
