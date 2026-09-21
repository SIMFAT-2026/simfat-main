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
