"""Runs the pre-registered backtest (PREREGISTRATION.md Sections 10-13).

GATED. The real run happens exactly once, after review. This script refuses to
touch the real study inputs unless ``--confirm-preregistered-run`` is passed:

    python scripts/run_backtest.py --dry-run
        validates the three input files' shapes and join keys only (row and
        key counts); computes no metric and prints no value that relates a
        predictor to the target.

    python scripts/run_backtest.py --confirm-preregistered-run
        the real run: writes outputs/tables/*.csv and outputs/decision.json.

Inputs that are not the real files (any other path, with different bytes) run
without the flag; that is how the test-suite exercises the whole engine on
synthetic data.

Usage (from Producto/analytics/study):

    python scripts/run_backtest.py [--target P] [--fwi P] [--mapbiomas P]
                                   [--out-dir D] [--dry-run]
                                   [--confirm-preregistered-run]
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
STUDY_ROOT = SCRIPT_DIR.parent
SRC_DIR = STUDY_ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from study import config, dataset, report  # noqa: E402

FEATURES_DIR = STUDY_ROOT / "data" / "features"
REAL_INPUTS = {
    "target": FEATURES_DIR / "firms_target.csv",
    "fwi": FEATURES_DIR / "season_fwi.csv",
    "mapbiomas": FEATURES_DIR / "season_mapbiomas.csv",
}
INPUT_NAMES = {"target": "firms_target.csv", "fwi": "season_fwi.csv", "mapbiomas": "season_mapbiomas.csv"}
DEFAULT_OUT_DIR = STUDY_ROOT / "outputs"


def is_real_input(key: str, path: Path) -> bool:
    """True if ``path`` is the real input file, by location or by content, so a
    renamed copy of a real file cannot bypass the gate."""
    real = Path(REAL_INPUTS[key])
    path = Path(path)
    try:
        if path.resolve() == real.resolve():
            return True
    except OSError:
        pass
    if real.is_file() and path.is_file():
        return dataset.file_sha256(path) == dataset.file_sha256(real)
    return False


def _parse(argv):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--target", type=Path, default=REAL_INPUTS["target"])
    p.add_argument("--fwi", type=Path, default=REAL_INPUTS["fwi"])
    p.add_argument("--mapbiomas", type=Path, default=REAL_INPUTS["mapbiomas"])
    p.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR)
    p.add_argument("--dry-run", action="store_true",
                   help="validate file shapes and join keys only; compute no metric")
    p.add_argument("--confirm-preregistered-run", action="store_true",
                   help="required to run on the real study inputs")
    return p.parse_args(argv)


def main(argv=None, *, params: config.StudyParameters | None = None) -> int:
    """``params`` lets tests shrink the bootstrap; the command line never sets it."""
    args = _parse(argv)
    lock = config.verify_preregistration(STUDY_ROOT)
    print(f"Pre-registration integrity OK (sha256={lock['sha256'][:12]}..., commit={lock['commit'][:12]}...)")

    paths = {"target": args.target, "fwi": args.fwi, "mapbiomas": args.mapbiomas}

    if not args.dry_run and not args.confirm_preregistered_run:
        real = [INPUT_NAMES[k] for k, p in paths.items() if is_real_input(k, p)]
        if real:
            print(
                "REFUSED: the inputs include the real study files "
                f"({', '.join(real)}). The pre-registered run happens exactly once, after review; "
                "pass --confirm-preregistered-run to perform it, or --dry-run to validate the join keys only.",
                file=sys.stderr,
            )
            return 2

    target_rows = dataset.read_csv_rows(paths["target"])
    fwi_rows = dataset.read_csv_rows(paths["fwi"])
    mb_rows = dataset.read_csv_rows(paths["mapbiomas"])

    if args.dry_run:
        rep = dataset.validate_keys(target_rows, fwi_rows, mb_rows)
        print(f"target keys: {rep.n_target_keys}")
        print(f"comunas: {rep.n_comunas}")
        print(f"seasons: {rep.n_seasons}")
        print(f"missing season_fwi keys: {rep.missing_fwi}")
        print(f"missing season_mapbiomas keys: {rep.missing_mapbiomas}")
        print("Dry run complete: no metric was computed.")
        return 0 if rep.ok else 1

    effective = params if params is not None else config.PARAMETERS
    result = report.run_backtest(target_rows, fwi_rows, mb_rows, effective)

    warnings = report.degeneracy_warnings(result, effective)
    for message in warnings:
        print(f"WARNING: {message}", file=sys.stderr)

    out_dir = Path(args.out_dir)
    report.write_tables(result, out_dir / "tables")
    report.write_decision_json(
        result, out_dir / "decision.json", lock=lock,
        input_hashes={INPUT_NAMES[k]: dataset.file_sha256(p) for k, p in paths.items()},
        git_sha=report.current_git_sha(STUDY_ROOT), params=effective,
        warnings=warnings,
    )
    o = result.primary.outcome
    print(f"Primary decision: shipped wM = {o.shipped_wm} ({o.claim})")
    print(f"Wrote {out_dir / 'tables'} and {out_dir / 'decision.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
