"""Fetch pipeline source files and record a provenance manifest.

Downloads are streamed straight to disk and hashed on the way through, so
nothing large is held in memory. Callers inject the HTTP opener, so the
happy path can be exercised for real with a ``file://`` URL and failure
paths with a fake opener -- no test needs live internet.

The manifest (``data/manifest.json``) records provenance -- dataset name,
source URL, file name, sha256 and size -- for every large source file the
pipeline depends on. Raw files themselves (xlsx, GeoTIFFs) are never
committed to git; only this metadata is.
"""
from __future__ import annotations

import hashlib
import json
import os
import time
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Mapping


class DownloadError(RuntimeError):
    """Raised when a download exhausts all retry attempts."""


@dataclass(frozen=True)
class DownloadResult:
    path: Path
    sha256: str
    bytes: int


def _default_opener(url: str, timeout: float):
    return urllib.request.urlopen(url, timeout=timeout)


def download_file(
    url: str,
    dest: Path,
    *,
    opener: Callable[[str, float], object] | None = None,
    timeout: float = 30.0,
    retries: int = 3,
    retry_backoff: float = 0.5,
    sleep: Callable[[float], None] = time.sleep,
) -> DownloadResult:
    """Stream ``url`` to ``dest``, retrying on failure, and hash the result.

    ``opener`` must be a callable ``(url, timeout) -> context manager`` whose
    ``__enter__`` result exposes ``.read(n)``. Defaults to
    ``urllib.request.urlopen``, which also handles ``file://`` URLs.

    Writes go to a temp file in ``dest``'s own directory first and are only
    moved onto ``dest`` (via ``os.replace``, atomic on both POSIX and
    Windows) once the transfer has fully and successfully completed. This
    means a failing download -- including a re-download of a file that
    already has a good, previously-verified copy at ``dest`` -- NEVER
    truncates or deletes that existing copy; only the temp file is removed
    on failure.
    """
    if retries < 1:
        raise ValueError("retries must be >= 1")
    opener = opener or _default_opener
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp_dest = dest.with_name(dest.name + ".tmp")
    last_exc: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            hasher = hashlib.sha256()
            size = 0
            with opener(url, timeout) as response, open(tmp_dest, "wb") as out:
                while True:
                    chunk = response.read(1 << 20)
                    if not chunk:
                        break
                    out.write(chunk)
                    hasher.update(chunk)
                    size += len(chunk)
            os.replace(tmp_dest, dest)
            return DownloadResult(path=dest, sha256=hasher.hexdigest(), bytes=size)
        except Exception as exc:  # retried below; wrapped and re-raised once exhausted
            last_exc = exc
            if attempt < retries:
                sleep(retry_backoff * attempt)
    # All retries exhausted: remove only the temp file. A pre-existing,
    # verified copy at dest (if any) was never touched and must survive.
    tmp_dest.unlink(missing_ok=True)
    raise DownloadError(f"Failed to download {url} after {retries} attempts") from last_exc


def manifest_entry(
    dataset: str,
    url: str,
    result: DownloadResult,
    *,
    downloaded_at: str | None = None,
) -> dict:
    """Build a provenance record for one downloaded source file."""
    return {
        "dataset": dataset,
        "url": url,
        "fileName": result.path.name,
        "sha256": result.sha256,
        "bytes": result.bytes,
        "downloadedAt": downloaded_at or datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }


def write_manifest(manifest_path: Path, entry: Mapping) -> list[dict]:
    """Upsert ``entry`` into the manifest file, keyed by ``dataset``."""
    manifest_path = Path(manifest_path)
    if manifest_path.exists():
        entries = json.loads(manifest_path.read_text(encoding="utf-8"))
    else:
        entries = []
    entries = [e for e in entries if e["dataset"] != entry["dataset"]]
    entries.append(dict(entry))
    entries.sort(key=lambda e: e["dataset"])
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(entries, indent=2) + "\n", encoding="utf-8")
    return entries


def write_manifest_batch(manifest_path: Path, entries: list[Mapping]) -> list[dict]:
    """Upsert every entry in ``entries`` into the manifest, guarded.

    This is the S1b5 jd-fix guard for the CRITICAL provenance-loss bug: the
    original run downloaded 13 distinct ``annual_burned_v1`` rasters (one
    per year) but called ``write_manifest`` for every one of them under the
    exact SAME dataset key, so each call silently overwrote the previous
    year's provenance and only the last (2025) survived.

    Before writing anything, this function validates the WHOLE batch: if two
    entries share a ``dataset`` key but have different ``fileName`` values,
    it raises ``ValueError`` naming the offending key -- each physically
    distinct source file must get its own dataset key (e.g. suffix it with
    the year). Entries that legitimately share both the same dataset key
    and the same fileName (e.g. a re-download that refreshes one file's
    hash/timestamp) are not an error.
    """
    seen: dict[str, str] = {}
    for entry in entries:
        dataset = entry["dataset"]
        file_name = entry["fileName"]
        if dataset in seen and seen[dataset] != file_name:
            raise ValueError(
                f"write_manifest_batch: dataset key {dataset!r} reused for two "
                f"different files ({seen[dataset]!r} and {file_name!r}) in the "
                "same batch -- give each distinct file its own dataset key "
                "(e.g. suffix it with the year)."
            )
        seen[dataset] = file_name

    result: list[dict] = []
    for entry in entries:
        result = write_manifest(manifest_path, entry)
    return result
