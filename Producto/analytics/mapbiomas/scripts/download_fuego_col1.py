"""Idempotent, committed downloader for the full Fuego Collection 1 history.

This is the CRITICAL S1b5 jd-fix: the original run that produced the 13
``annual_burned_v1`` rasters (2013-2025) used one ad hoc, uninspectable
process and wrote every file's provenance under the SAME dataset key
(``mapbiomas_fire_chile_col1_annual_burned_v1``), so ``data/manifest.json``
only ever kept the LAST upsert (2025) and silently lost provenance for
2013-2024 -- see ``download.write_manifest_batch`` for the guard that now
makes this impossible to repeat silently.

This script gives every file its own, year/window-suffixed dataset key and
downloads (or re-downloads, to VERIFY) all 16 Fuego Collection 1 files S1b5
depends on:

- ``annual_burned_v1`` for every year 2013-2025 (13 files)
- ``frequency_burned_v1`` 2013-2025 (1 file)
- ``year_last_fire_v1`` 2025 (1 file)
- ``accumulated_burned_v1`` 2013-2025 (1 file, cross-check only)

Files are cached OUTSIDE the repo -- default ``%TEMP%\\mb_fire_col1``,
override with ``MB_FIRE_RASTER_DIR`` (same convention as
``scripts/generate_fire_report_2017.py`` / ``tests/test_build_stats_real.py``)
-- never committed; only ``data/manifest.json``'s provenance record is.

Idempotent and honest about provenance: for each file, this script always
attempts a fresh download (``download.download_file``, atomic, hashed on
the way through) and compares the new sha256 against whatever was already
cached at that path, reporting ``matchedOnRedownload`` per file. If a
pre-existing cached copy exists but the re-download FAILS (no network),
the cached copy is never touched or invented a new hash for: its own
sha256/bytes are kept and ``downloadedAt`` is set to the cache file's real
mtime, with an explicit ``note`` saying so. A file with no cached copy and
a failed download is a real, unrecovered failure and raises.

Usage (from ``Producto/analytics/mapbiomas``):

    PYTHONPATH=src python scripts/download_fuego_col1.py
"""
from __future__ import annotations

import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path

from mb_pipeline import download

_BASE_URL = "https://storage.googleapis.com/mapbiomas-public/initiatives/chile/fire/collection1"
_ROOT = Path(__file__).resolve().parents[1]
_MANIFEST_PATH = _ROOT / "data" / "manifest.json"

# The exact legacy, unsuffixed key the original buggy run used for every
# annual year -- pruned from the manifest once the year-suffixed keys below
# have all been written, so no stale duplicate survives.
_LEGACY_ANNUAL_KEY = "mapbiomas_fire_chile_col1_annual_burned_v1"


def _cache_dir() -> Path:
    """Same env var / default-directory lookup as generate_fire_report_2017.py."""
    env = os.environ.get("MB_FIRE_RASTER_DIR")
    if env:
        return Path(env)
    return Path(os.environ.get("TEMP", "/tmp")) / "mb_fire_col1"


def targets() -> list[tuple[str, str, str]]:
    """Return ``(dataset key, url, file name)`` for all 16 Fuego Col 1 files."""
    result: list[tuple[str, str, str]] = []
    for year in range(2013, 2026):
        file_name = f"mapbiomas_fire_chile_col1_annual_burned_{year}.tif"
        result.append((
            f"{_LEGACY_ANNUAL_KEY}_{year}",
            f"{_BASE_URL}/annual_burned_v1/{file_name}",
            file_name,
        ))

    freq_name = "mapbiomas_fire_chile_col1_frequency_burned_2013_2025.tif"
    result.append((
        "mapbiomas_fire_chile_col1_frequency_burned_v1_2013_2025",
        f"{_BASE_URL}/frequency_burned_v1/{freq_name}",
        freq_name,
    ))

    ylf_name = "mapbiomas_fire_chile_col1_year_last_fire_2025.tif"
    result.append((
        "mapbiomas_fire_chile_col1_year_last_fire_v1_2025",
        f"{_BASE_URL}/year_last_fire_v1/{ylf_name}",
        ylf_name,
    ))

    acc_name = "mapbiomas_fire_chile_col1_accumulated_burned_2013_2025.tif"
    result.append((
        "mapbiomas_fire_chile_col1_accumulated_burned_v1_2013_2025",
        f"{_BASE_URL}/accumulated_burned_v1/{acc_name}",
        acc_name,
    ))
    return result


def _sha256_of(path: Path) -> str:
    hasher = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(1 << 20):
            hasher.update(chunk)
    return hasher.hexdigest()


def _prune_legacy_annual_key(manifest_path: Path) -> None:
    """Drop the stale, unsuffixed legacy annual key if still present.

    Only ever removes the EXACT legacy key (never a year-suffixed one), so
    this cannot accidentally drop real provenance.
    """
    if not manifest_path.exists():
        return
    entries = json.loads(manifest_path.read_text(encoding="utf-8"))
    pruned = [e for e in entries if e["dataset"] != _LEGACY_ANNUAL_KEY]
    if len(pruned) != len(entries):
        manifest_path.write_text(json.dumps(pruned, indent=2) + "\n", encoding="utf-8")


def run(cache_dir: Path | None = None, manifest_path: Path | None = None) -> list[dict]:
    """Download (or verify) every Fuego Col 1 file; return a per-file report row for each."""
    cache_dir = cache_dir or _cache_dir()
    manifest_path = manifest_path or _MANIFEST_PATH
    cache_dir.mkdir(parents=True, exist_ok=True)

    report: list[dict] = []
    entries: list[dict] = []

    for dataset, url, file_name in targets():
        dest = cache_dir / file_name
        previous_sha256 = _sha256_of(dest) if dest.exists() else None

        try:
            result = download.download_file(url, dest, timeout=120.0, retries=3)
            entry = download.manifest_entry(dataset, url, result)
            matched = (previous_sha256 == result.sha256) if previous_sha256 is not None else None
            row = {
                "dataset": dataset,
                "fileName": file_name,
                "sha256": result.sha256,
                "bytes": result.bytes,
                "matchedOnRedownload": matched,
            }
        except download.DownloadError as exc:
            if previous_sha256 is None:
                raise  # no cached fallback -- a real, unrecovered failure
            stat = dest.stat()
            downloaded_at = datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc).isoformat(
                timespec="seconds"
            )
            entry = download.manifest_entry(
                dataset,
                url,
                download.DownloadResult(path=dest, sha256=previous_sha256, bytes=stat.st_size),
                downloaded_at=downloaded_at,
            )
            entry["note"] = (
                f"re-download failed ({exc}); kept the cached copy's sha256/bytes, "
                "downloadedAt is the cache file's own mtime, not a live capture"
            )
            row = {
                "dataset": dataset,
                "fileName": file_name,
                "sha256": previous_sha256,
                "bytes": stat.st_size,
                "matchedOnRedownload": False,
            }

        entries.append(entry)
        report.append(row)

    download.write_manifest_batch(manifest_path, entries)
    _prune_legacy_annual_key(manifest_path)
    return report


if __name__ == "__main__":
    for row in run():
        print(row)
