"""Wiring checks for scripts/build_firms_target.py: the pure counting/gate
logic itself is covered by tests/test_firms_target.py.

BLINDING NOTE: no predictor-vs-target relation is read, computed, or
asserted here.
"""
from __future__ import annotations

import build_firms_target


def test_geojson_dir_resolves_to_the_real_repo_directory():
    assert build_firms_target.GEOJSON_DIR.is_dir()
    assert list(build_firms_target.GEOJSON_DIR.glob("comunas-*.geojson"))


def test_firms_zip_resolves_to_the_committed_archive():
    assert build_firms_target.FIRMS_ZIP.is_file()
    assert build_firms_target.FIRMS_ZIP.name == "DL_FIRE_SV-C2_809648.zip"


def test_output_path_is_under_data_features():
    assert build_firms_target.OUTPUT_PATH.parent.name == "features"
    assert build_firms_target.OUTPUT_PATH.name == "firms_target.csv"
