"""Builds data/features/firms_target.csv (PREREGISTRATION.md Section 6).

Reads the committed FIRMS archive directly (no network), assigns every
detection to a comuna by point-in-polygon against the repo's own GeoJSON,
converts each detection's UTC timestamp to a local (Chile) calendar date
(see study.firms_target's module docstring for the DST implementation
note), and writes one row per (comunaId, season, window, filter): 86
comunas x 9 seasons x 2 windows (primary/sensitivity) x 2 filters
(type0/notype) = 3096 rows, zero counts included explicitly.

BLINDING RULE: this script builds the FIRMS TARGET ONLY. It never loads or
computes any predictor (FWI, S_dynamic, S_mapbiomas) and never prints or
writes any quantity relating this target to one -- only row counts,
detections dropped by each filter/outside every comuna, and the overall
per-season prevalence (N=3, primary settings), all of which
PREREGISTRATION.md Section 9 already allows reporting on their own.

Usage (from Producto/analytics/study):

    python scripts/build_firms_target.py
"""
from __future__ import annotations

import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
STUDY_ROOT = SCRIPT_DIR.parent
SRC_DIR = STUDY_ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from study import config, firms_target  # noqa: E402
from study.season_fwi import SEASONS  # noqa: E402

REPO_ROOT = STUDY_ROOT.parents[2]  # study -> analytics -> Producto -> repo root
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
FIRMS_ZIP = STUDY_ROOT / "data" / "firms" / "DL_FIRE_SV-C2_809648.zip"
OUTPUT_PATH = STUDY_ROOT / "data" / "features" / "firms_target.csv"


def main() -> int:
    lock = config.verify_preregistration(STUDY_ROOT)
    print(f"Pre-registration integrity OK (sha256={lock['sha256'][:12]}..., commit={lock['commit'][:12]}...)")

    polygons = firms_target.load_comuna_polygons(GEOJSON_DIR)
    print(f"Loaded {len(polygons)} comuna polygons from {GEOJSON_DIR}")

    print(f"Reading {FIRMS_ZIP}")
    detections, dropped_outside = firms_target.read_detections(FIRMS_ZIP, polygons)
    summary = firms_target.filter_summary(detections)

    comuna_ids = sorted(polygons)
    rows = firms_target.build_counts(detections, comuna_ids, seasons=SEASONS)
    firms_target.validate_gates(rows, comuna_ids, seasons=SEASONS)
    firms_target.write_csv(OUTPUT_PATH, rows)

    print(f"Wrote {OUTPUT_PATH} ({len(rows)} rows)")
    print(f"Detections read (assigned to some comuna): {summary['total']}")
    print(f"Detections dropped (outside every monitored comuna's polygon): {dropped_outside}")
    print(f"Detections dropped by confidence filter (not n/h): {summary['dropped_by_confidence']}")
    print(
        "Detections dropped by the type filter (confidence ok, type != 0): "
        f"{summary['dropped_by_type_among_confidence_passing']}"
    )
    print(f"Final type0+n/h count (primary filter, any window): {summary['final_type0_count']}")
    print(f"Final notype+n/h count (sensitivity filter, any window): {summary['final_notype_count']}")

    print("Overall prevalence per season (N=3, primary window, type0 filter):")
    for season in SEASONS:
        prevalence = firms_target.prevalence_for_season(
            rows, season, window="primary", filter_name="type0", n=3
        )
        print(f"  {season}: {prevalence:.4f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
