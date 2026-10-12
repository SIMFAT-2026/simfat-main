"""Runs H1, the Fuego vs FIRMS agreement analysis (PREREGISTRATION.md Section 2).

H1 has NO decision power. It is attempted through 2026-10-12; whatever it
produces is reported as is.

    python scripts/run_h1.py

Writes outputs/h1/h1_scars.csv, h1_secondary.csv and h1_summary.json. The run
happens once: it refuses to start when outputs/h1/h1_summary.json exists
(--allow-overwrite records the overwrite), and, like the backtest, refuses with
exit 3 when the study directory has uncommitted changes or git cannot say
(--allow-dirty is for the test-suite), or when the pre-registration commit is
not an ancestor of HEAD.

Exit codes: 0 success, 2 refused (existing outputs), 3 refused (provenance).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
STUDY_ROOT = SCRIPT_DIR.parent
for path in (STUDY_ROOT / "src", STUDY_ROOT.parent / "mapbiomas" / "src"):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from study import config, dataset, era5, h1, report, season_mapbiomas  # noqa: E402

REPO_ROOT = STUDY_ROOT.parents[2]
DEFAULT_GEOJSON_DIR = REPO_ROOT / "Producto" / "backend" / "simfat-backend" / "src" / "main" / "resources" / "static" / "geojson"
DEFAULT_SEED = (
    REPO_ROOT / "Producto" / "analytics" / "mapbiomas" / "data"
    / "comuna-mapbiomas-stats.lulc-col2+fuego-col1-2013-2025.jsonl"
)
DEFAULT_MANIFEST = REPO_ROOT / "Producto" / "analytics" / "mapbiomas" / "data" / "manifest.json"
DEFAULT_CACHE = Path(os.environ.get("TEMP") or os.environ.get("TMPDIR") or "/tmp") / "mb_fire_col1"
DEFAULT_FIRMS = STUDY_ROOT / "data" / "firms" / "DL_FIRE_SV-C2_809648.zip"
DEFAULT_OUT_DIR = STUDY_ROOT / "outputs" / "h1"


def _parse(argv):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--geojson-dir", type=Path, default=DEFAULT_GEOJSON_DIR)
    p.add_argument("--raster-cache", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    p.add_argument("--seed", type=Path, default=DEFAULT_SEED)
    p.add_argument("--firms-zip", type=Path, default=DEFAULT_FIRMS)
    p.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR)
    p.add_argument("--bootstrap-b", type=int, default=h1.BOOTSTRAP_B,
                   help="resamples (the pre-registered B is the default; smaller values are for tests)")
    p.add_argument("--allow-overwrite", action="store_true")
    p.add_argument("--allow-dirty", action="store_true",
                   help="skip the clean-study-directory check (test-suite only)")
    return p.parse_args(argv)


def main(argv=None) -> int:
    args = _parse(argv)
    started = time.time()
    lock = config.verify_preregistration(STUDY_ROOT)
    print(f"Pre-registration integrity OK (sha256={lock['sha256'][:12]}..., commit={lock['commit'][:12]}...)")

    out_dir = Path(args.out_dir)
    summary_path = out_dir / "h1_summary.json"
    previous_sha256 = None
    if summary_path.exists():
        if not args.allow_overwrite:
            print(f"REFUSED: {summary_path} already exists. H1 runs once; pass --allow-overwrite to run again "
                  "(the overwrite is recorded).", file=sys.stderr)
            return 2
        previous_sha256 = dataset.file_sha256(summary_path)

    try:
        report.verify_preregistration_ancestry(lock["commit"], STUDY_ROOT)
    except report.ProvenanceError as exc:
        print(f"REFUSED: {exc} (PREREGISTRATION.md Section 17).", file=sys.stderr)
        return 3
    dirty = report.git_dirty(STUDY_ROOT, study_dir_only=True)
    if not args.allow_dirty and dirty is not False:
        reason = "has uncommitted changes" if dirty else "cannot be checked (git unavailable or failed)"
        print(f"REFUSED: the study directory {reason}. Commit changes under Producto/analytics/study/ first.",
              file=sys.stderr)
        return 3

    raster_paths = season_mapbiomas.load_verified_annual_rasters(args.raster_cache, args.manifest)
    print(f"Verified {len(raster_paths)} annual Fuego rasters against {args.manifest}")

    polygons = era5.load_comuna_geometries(args.geojson_dir)
    print(f"Loaded {len(polygons)} comuna polygons")

    det = h1.read_filtered_detections(args.firms_zip)
    print(f"FIRMS: {det.n_rows} rows, {det.n_filtered} pass type 0 and confidence n/h")

    scar_rows = h1.analyse_rasters(raster_paths, polygons, det)
    print(f"Scars >= {h1.MIN_SCAR_HA:.0f} ha: {len(scar_rows)}")

    counts, n_unassigned = h1.comuna_year_counts(det, polygons)
    fractions = h1.load_burned_fraction_by_year(args.seed)
    comuna_ids = sorted(polygons)
    secondary = h1.secondary_rows(fractions, counts, comuna_ids=comuna_ids)

    summary = h1.summarize_scars(scar_rows, B=args.bootstrap_b)
    summary["secondary"] = h1.summarize_secondary(secondary, B=args.bootstrap_b)
    sha = dataset.file_sha256
    geojson_files = sorted(Path(args.geojson_dir).glob("comunas-*.geojson"))
    summary = {
        "hypothesis": "H1",
        "decision_power": "none",
        "description": "Agreement between MapBiomas Fuego burn scars and NASA FIRMS active fire",
        "preregistration": {"sha256": lock["sha256"], "commit": lock["commit"]},
        "git_sha": report.current_git_sha(STUDY_ROOT),
        "git_dirty_study_dir": dirty,
        "overwrote_previous": previous_sha256 is not None,
        "previous_summary_sha256": previous_sha256,
        "inputs_sha256": {
            "annual_rasters": {str(y): sha(p) for y, p in sorted(raster_paths.items())},
            "burned_fraction_seed": sha(args.seed),
            "firms_archive": sha(args.firms_zip),
            "fuego_manifest": sha(args.manifest),
            "comuna_geojson": {p.name: sha(p) for p in geojson_files},
        },
        "parameters": {
            "min_scar_ha": h1.MIN_SCAR_HA,
            "buffer_m": h1.BUFFER_M,
            "connectivity": 8,
            "buffer_method": "exact distance from detection to the nearest burned pixel square, local WGS84 metres",
            "area_method": "WGS84 ellipsoidal per-row pixel area (mb_pipeline.zonal.row_pixel_areas_m2)",
            "area_classes_ha": [[lo, None if hi == float("inf") else hi, label] for lo, hi, label in h1.AREA_CLASSES],
            "years": list(h1.YEARS),
            "bootstrap_b": args.bootstrap_b,
            "bootstrap_seed": h1.BOOTSTRAP_SEED,
            "firms_filter": "type == 0 and confidence in {n, h}",
            "year_basis": "America/Santiago local calendar year (DEVIATIONS N2)",
        },
        "detections": {
            "n_rows": det.n_rows,
            "n_filtered": det.n_filtered,
            "n_filtered_unassigned_study_years": n_unassigned,
        },
        **summary,
        "runtime_seconds": round(time.time() - started, 1),
    }

    h1.write_scars_csv(out_dir / "h1_scars.csv", scar_rows)
    h1.write_secondary_csv(out_dir / "h1_secondary.csv", secondary)
    h1.write_summary_json(summary_path, summary)

    p = summary["primary"]
    share = "n/a" if p["share"] is None else f"{p['share']:.3f}"
    print(f"Primary: {p['n_detected']} of {p['n_scars']} scars have a filtered detection in the buffer (share {share})")
    sec = summary["secondary"]
    print(f"Secondary: Spearman rho = {sec['rho']} over {sec['n_pairs']} comuna-years")
    print(f"Wrote {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
