"""download.py: streamed fetch + sha256 + provenance manifest.

Network-free by design: the happy path uses a real ``file://`` URL (exercising
the real default opener, not a mock), and failure/retry paths inject a fake
opener. No test requires live internet.
"""
import json

import pytest

from mb_pipeline import download


def _file_url(path):
    return path.as_uri()


def test_download_file_writes_content_and_returns_sha256(tmp_path):
    source = tmp_path / "source.bin"
    source.write_bytes(b"hello mapbiomas")
    dest = tmp_path / "dest.bin"

    result = download.download_file(_file_url(source), dest)

    assert dest.read_bytes() == b"hello mapbiomas"
    assert result.bytes == len(b"hello mapbiomas")
    # Assert against an independently computed hash, not a hardcoded guess.
    import hashlib

    assert result.sha256 == hashlib.sha256(b"hello mapbiomas").hexdigest()


def test_download_file_hash_changes_with_content(tmp_path):
    """Triangulation: different content must yield a different real hash."""
    source = tmp_path / "source2.bin"
    source.write_bytes(b"a completely different payload")
    dest = tmp_path / "dest2.bin"

    result = download.download_file(_file_url(source), dest)

    import hashlib

    assert result.sha256 == hashlib.sha256(b"a completely different payload").hexdigest()
    assert dest.read_bytes() == b"a completely different payload"


def test_download_file_retries_then_succeeds(tmp_path):
    dest = tmp_path / "retried.bin"
    calls = []

    class FlakyResponse:
        def __init__(self, payload):
            self._chunks = [payload, b""]

        def read(self, _n):
            return self._chunks.pop(0)

        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

    def opener(url, timeout):
        calls.append(url)
        if len(calls) < 3:
            raise ConnectionError("simulated transient failure")
        return FlakyResponse(b"ok-after-retries")

    sleeps = []
    result = download.download_file(
        "https://example.invalid/file.bin",
        dest,
        opener=opener,
        retries=3,
        sleep=sleeps.append,
    )

    assert len(calls) == 3
    assert dest.read_bytes() == b"ok-after-retries"
    assert len(sleeps) == 2  # slept before attempt 2 and attempt 3, not after success


def test_download_file_raises_after_exhausting_retries(tmp_path):
    dest = tmp_path / "never.bin"

    def always_fails(url, timeout):
        raise ConnectionError("simulated permanent failure")

    with pytest.raises(download.DownloadError, match="after 2 attempts"):
        download.download_file(
            "https://example.invalid/file.bin",
            dest,
            opener=always_fails,
            retries=2,
            sleep=lambda _s: None,
        )


def test_download_file_removes_partial_file_after_exhausting_retries(tmp_path):
    """A response that dies mid-stream on the final attempt must not leave a
    truncated/corrupt file behind at ``dest`` -- callers should never see a
    partial file mistaken for a complete download."""
    dest = tmp_path / "partial.bin"

    class DiesMidStream:
        def __init__(self):
            self._chunks = [b"partial-bytes"]

        def read(self, _n):
            if self._chunks:
                return self._chunks.pop(0)
            raise ConnectionError("simulated mid-stream failure")

        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

    def opener(url, timeout):
        return DiesMidStream()

    with pytest.raises(download.DownloadError):
        download.download_file(
            "https://example.invalid/file.bin",
            dest,
            opener=opener,
            retries=2,
            sleep=lambda _s: None,
        )

    assert not dest.exists()


def test_download_file_preserves_existing_valid_file_when_retries_exhausted(tmp_path):
    """A failed re-download must never destroy a good, already-verified copy
    at ``dest`` -- the download must land in a temp file first and only
    replace ``dest`` once it has fully and successfully completed."""
    dest = tmp_path / "cached.xlsx"
    dest.write_bytes(b"already-verified-content")

    def always_fails(url, timeout):
        raise ConnectionError("simulated permanent failure")

    with pytest.raises(download.DownloadError):
        download.download_file(
            "https://example.invalid/file.bin",
            dest,
            opener=always_fails,
            retries=2,
            sleep=lambda _s: None,
        )

    assert dest.read_bytes() == b"already-verified-content"
    # No leftover temp file either.
    leftovers = [p for p in tmp_path.iterdir() if p != dest]
    assert leftovers == []


def test_download_file_replaces_existing_file_only_on_full_success(tmp_path):
    """A successful download must overwrite a stale ``dest``, but only after
    the transfer completes -- never truncate it up front."""
    dest = tmp_path / "cached.xlsx"
    dest.write_bytes(b"stale-content")
    source = tmp_path / "source.bin"
    source.write_bytes(b"fresh-content")

    result = download.download_file(_file_url(source), dest)

    assert dest.read_bytes() == b"fresh-content"
    assert result.bytes == len(b"fresh-content")


def test_manifest_entry_has_expected_shape(tmp_path):
    dest = tmp_path / "file.xlsx"
    dest.write_bytes(b"x")
    result = download.DownloadResult(path=dest, sha256="deadbeef", bytes=1)

    entry = download.manifest_entry("mapbiomas_lulc_col2", "https://example.org/f.xlsx", result)

    assert entry["dataset"] == "mapbiomas_lulc_col2"
    assert entry["fileName"] == "file.xlsx"
    assert entry["sha256"] == "deadbeef"
    assert entry["bytes"] == 1
    assert "downloadedAt" in entry and entry["downloadedAt"]


def test_write_manifest_creates_file_with_one_entry(tmp_path):
    manifest_path = tmp_path / "data" / "manifest.json"
    entry = {"dataset": "a", "url": "u", "fileName": "a.bin", "sha256": "h", "bytes": 1, "downloadedAt": "t"}

    entries = download.write_manifest(manifest_path, entry)

    assert entries == [entry]
    on_disk = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert on_disk == [entry]


def test_write_manifest_upserts_by_dataset_and_keeps_other_datasets(tmp_path):
    manifest_path = tmp_path / "manifest.json"
    entry_a1 = {"dataset": "a", "url": "u1", "fileName": "a1.bin", "sha256": "h1", "bytes": 1, "downloadedAt": "t1"}
    entry_b = {"dataset": "b", "url": "u2", "fileName": "b.bin", "sha256": "h2", "bytes": 2, "downloadedAt": "t2"}
    entry_a2 = {"dataset": "a", "url": "u3", "fileName": "a2.bin", "sha256": "h3", "bytes": 3, "downloadedAt": "t3"}

    download.write_manifest(manifest_path, entry_a1)
    download.write_manifest(manifest_path, entry_b)
    entries = download.write_manifest(manifest_path, entry_a2)

    by_dataset = {e["dataset"]: e for e in entries}
    assert set(by_dataset) == {"a", "b"}
    assert by_dataset["a"] == entry_a2  # replaced, not duplicated
    assert by_dataset["b"] == entry_b


# --- S1b5 jd-fix CRITICAL-1: write_manifest_batch guard ---------------------
#
# The original S1b5 run downloaded 13 distinct annual_burned_v1 rasters (one
# per year 2013-2025) but wrote every one of them under the SAME dataset key
# (``mapbiomas_fire_chile_col1_annual_burned_v1``). ``write_manifest`` upserts
# by dataset key, so each call silently discarded the previous year's
# provenance -- only 2025 survived in data/manifest.json. write_manifest_batch
# is the guard: it validates a whole batch up front and hard-aborts if any
# two entries in the SAME call share a dataset key but point at different
# files, instead of silently keeping only the last one.


def test_write_manifest_batch_writes_all_entries_when_keys_are_unique(tmp_path):
    """Two distinct annual years, each with its own year-suffixed dataset
    key, must both survive -- the exact case the original bug lost."""
    manifest_path = tmp_path / "manifest.json"
    entry_2013 = {
        "dataset": "mapbiomas_fire_chile_col1_annual_burned_v1_2013",
        "url": "u2013",
        "fileName": "mapbiomas_fire_chile_col1_annual_burned_2013.tif",
        "sha256": "h2013",
        "bytes": 1,
        "downloadedAt": "t2013",
    }
    entry_2014 = {
        "dataset": "mapbiomas_fire_chile_col1_annual_burned_v1_2014",
        "url": "u2014",
        "fileName": "mapbiomas_fire_chile_col1_annual_burned_2014.tif",
        "sha256": "h2014",
        "bytes": 2,
        "downloadedAt": "t2014",
    }

    entries = download.write_manifest_batch(manifest_path, [entry_2013, entry_2014])

    by_dataset = {e["dataset"]: e for e in entries}
    assert set(by_dataset) == {
        "mapbiomas_fire_chile_col1_annual_burned_v1_2013",
        "mapbiomas_fire_chile_col1_annual_burned_v1_2014",
    }
    on_disk = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert len(on_disk) == 2


def test_write_manifest_batch_raises_when_same_dataset_key_used_for_two_different_files(tmp_path):
    """Reproduces the exact S1b5 bug: two different files pushed under one
    shared dataset key in the same batch must hard-abort, never silently
    keep only the last one."""
    manifest_path = tmp_path / "manifest.json"
    entry_2013 = {
        "dataset": "mapbiomas_fire_chile_col1_annual_burned_v1",
        "url": "u2013",
        "fileName": "mapbiomas_fire_chile_col1_annual_burned_2013.tif",
        "sha256": "h2013",
        "bytes": 1,
        "downloadedAt": "t2013",
    }
    entry_2014 = {
        "dataset": "mapbiomas_fire_chile_col1_annual_burned_v1",
        "url": "u2014",
        "fileName": "mapbiomas_fire_chile_col1_annual_burned_2014.tif",
        "sha256": "h2014",
        "bytes": 2,
        "downloadedAt": "t2014",
    }

    with pytest.raises(ValueError, match="annual_burned_v1"):
        download.write_manifest_batch(manifest_path, [entry_2013, entry_2014])

    assert not manifest_path.exists()  # hard-abort BEFORE writing anything
