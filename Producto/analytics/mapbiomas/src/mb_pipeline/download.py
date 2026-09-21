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
    """
    if retries < 1:
        raise ValueError("retries must be >= 1")
    opener = opener or _default_opener
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    last_exc: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            hasher = hashlib.sha256()
            size = 0
            with opener(url, timeout) as response, open(dest, "wb") as out:
                while True:
                    chunk = response.read(1 << 20)
                    if not chunk:
                        break
                    out.write(chunk)
                    hasher.update(chunk)
                    size += len(chunk)
            return DownloadResult(path=dest, sha256=hasher.hexdigest(), bytes=size)
        except Exception as exc:  # retried below; wrapped and re-raised once exhausted
            last_exc = exc
            if attempt < retries:
                sleep(retry_backoff * attempt)
    # All retries exhausted: don't leave a partial/corrupt file at dest for a
    # caller to mistake for a complete download.
    dest.unlink(missing_ok=True)
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
