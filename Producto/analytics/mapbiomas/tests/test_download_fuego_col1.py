"""scripts/download_fuego_col1.py: the committed, idempotent Fuego Col 1 downloader.

The unit-level tests here are network-free by design, matching
``tests/test_download.py``'s convention -- they exercise ``targets()`` and
the legacy-key pruning helper directly, with no HTTP call. The one
end-to-end test that actually downloads all 16 real files is opt-in (set
``MB_RUN_NETWORK_TESTS=1``) since it needs live internet and ~120MB of
transfer; it is how this fix's honest manifest.json was actually produced
and verified (see the S1b5 jd-fix report for the real sha256/match table).
"""
from __future__ import annotations

import json
import os

import pytest

import download_fuego_col1 as script
from mb_pipeline import download


def test_targets_has_16_entries_with_unique_dataset_keys_and_file_names():
    all_targets = script.targets()

    assert len(all_targets) == 16
    datasets = [t[0] for t in all_targets]
    file_names = [t[2] for t in all_targets]
    assert len(set(datasets)) == 16, "every file must get its own dataset key"
    assert len(set(file_names)) == 16, "every file must be a distinct raster"


def test_targets_covers_every_annual_year_2013_to_2025():
    annual_datasets = {t[0] for t in script.targets() if "annual_burned_v1_" in t[0]}
    expected = {f"mapbiomas_fire_chile_col1_annual_burned_v1_{year}" for year in range(2013, 2026)}
    assert annual_datasets == expected


def test_targets_includes_frequency_year_last_fire_and_accumulated():
    datasets = {t[0] for t in script.targets()}
    assert "mapbiomas_fire_chile_col1_frequency_burned_v1_2013_2025" in datasets
    assert "mapbiomas_fire_chile_col1_year_last_fire_v1_2025" in datasets
    assert "mapbiomas_fire_chile_col1_accumulated_burned_v1_2013_2025" in datasets


def test_prune_legacy_annual_key_removes_only_the_exact_legacy_key(tmp_path):
    manifest_path = tmp_path / "manifest.json"
    manifest_path.write_text(
        json.dumps(
            [
                {"dataset": "mapbiomas_fire_chile_col1_annual_burned_v1", "fileName": "stale.tif"},
                {"dataset": "mapbiomas_fire_chile_col1_annual_burned_v1_2013", "fileName": "2013.tif"},
                {"dataset": "mapbiomas_lulc_col2_coverage", "fileName": "lulc.xlsx"},
            ]
        ),
        encoding="utf-8",
    )

    script._prune_legacy_annual_key(manifest_path)

    remaining = json.loads(manifest_path.read_text(encoding="utf-8"))
    remaining_datasets = {e["dataset"] for e in remaining}
    assert remaining_datasets == {
        "mapbiomas_fire_chile_col1_annual_burned_v1_2013",
        "mapbiomas_lulc_col2_coverage",
    }


def test_prune_legacy_annual_key_is_a_noop_when_manifest_is_absent(tmp_path):
    manifest_path = tmp_path / "does_not_exist.json"
    script._prune_legacy_annual_key(manifest_path)  # must not raise
    assert not manifest_path.exists()


@pytest.mark.skipif(
    os.environ.get("MB_RUN_NETWORK_TESTS") != "1",
    reason="downloads ~120MB of real Fuego Col 1 rasters; set MB_RUN_NETWORK_TESTS=1 to opt in",
)
def test_run_downloads_all_16_files_and_writes_an_honest_manifest(tmp_path):
    cache_dir = tmp_path / "cache"
    manifest_path = tmp_path / "manifest.json"

    report = script.run(cache_dir=cache_dir, manifest_path=manifest_path)

    assert len(report) == 16
    entries = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert len(entries) == 16
    for row in report:
        assert row["bytes"] > 0
        assert len(row["sha256"]) == 64
