"""Runs the pre-registered backtest (PREREGISTRATION.md Sections 10-13).

GATED. The real run happens exactly once, after review. This script refuses to
touch the real study inputs unless ``--confirm-preregistered-run`` is passed:

    python scripts/run_backtest.py --dry-run
        validates the three input files' shapes and join keys only (row and
        key counts); computes no metric and prints no value that relates a
        predictor to the target.

    python scripts/run_backtest.py --confirm-preregistered-run
        the real run: writes outputs/tables/*.csv and outputs/decision.json.
        The primary result and the per-season table are written first; each
        rerun is guarded and a failure is recorded, not hidden. The run refuses
        to start if outputs/decision.json already exists (add --allow-overwrite
        to override; the overwrite and the previous file's sha256 are recorded)
        or if the pre-registration commit is not an ancestor of HEAD
        (PREREGISTRATION.md Section 17). decision.json records whether the git
        working tree was dirty.

Exit codes:

    0  success (or a dry run whose join keys are complete)
    1  dry run: join keys are missing
    2  refused: real inputs without --confirm-preregistered-run, or an existing
       outputs/decision.json without --allow-overwrite
    3  refused: provenance (pre-registration commit not an ancestor of HEAD, or
       git unavailable)
    4  the run finished and wrote every output, but an analysis failed or a
       per-season interval is single-class / bootstrap-failed; the final line
       lists them

Inputs that are not the real files (any other path, with different bytes) run
without the flag; that is how the test-suite exercises the whole engine on
synthetic data.

Usage (from Producto/analytics/study):

    python scripts/run_backtest.py [--target P] [--fwi P] [--mapbiomas P]
                                   [--out-dir D] [--dry-run]
                                   [--confirm-preregistered-run] [--allow-overwrite]
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
    p.add_argument("--allow-overwrite", action="store_true",
                   help="run even though outputs/decision.json already exists; the overwrite is recorded")
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

    out_dir = Path(args.out_dir)
    decision_path = out_dir / "decision.json"
    previous_sha256 = None
    if not args.dry_run and decision_path.exists():
        if not args.allow_overwrite:
            print(
                f"REFUSED: {decision_path} already exists. The pre-registered run happens exactly once; "
                "pass --allow-overwrite to run again (the overwrite is recorded in decision.json).",
                file=sys.stderr,
            )
            return 2
        previous_sha256 = dataset.file_sha256(decision_path)

    if not args.dry_run:
        try:
            report.verify_preregistration_ancestry(lock["commit"], STUDY_ROOT)
        except report.ProvenanceError as exc:
            print(f"REFUSED: {exc} (PREREGISTRATION.md Section 17).", file=sys.stderr)
            return 3

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
    input_hashes = {INPUT_NAMES[k]: dataset.file_sha256(p) for k, p in paths.items()}
    git_sha = report.current_git_sha(STUDY_ROOT)
    git_dirty = report.git_dirty(STUDY_ROOT)
    git_dirty_study_dir = report.git_dirty(STUDY_ROOT, study_dir_only=True)

    def write_outputs(result, warnings=()):
        report.write_tables(result, out_dir / "tables")
        report.write_decision_json(
            result, out_dir / "decision.json", lock=lock, input_hashes=input_hashes,
            git_sha=git_sha, params=effective, warnings=warnings,
            git_dirty=git_dirty, git_dirty_study_dir=git_dirty_study_dir,
            overwrote_previous=previous_sha256 is not None, previous_decision_sha256=previous_sha256,
        )

    # The primary result and the per-season table reach the disk before any
    # rerun starts, so a late failure cannot lose them or invite a second run.
    result = report.run_backtest(target_rows, fwi_rows, mb_rows, effective, checkpoint=write_outputs)

    warnings = report.degeneracy_warnings(result, effective)
    for message in warnings:
        print(f"WARNING: {message}", file=sys.stderr)
    write_outputs(result, warnings)
    o = result.primary.outcome
    print(f"Primary decision: shipped wM = {o.shipped_wm} ({o.claim})")
    print(f"Wrote {out_dir / 'tables'} and {out_dir / 'decision.json'}")

    # Only after every output is on disk: a failed analysis or an interval-less
    # season must not look like a clean run to a calling script.
    problems = [f"{res.name} (failed)" for res in report._all_pooled(result) if res.status == "failed"]
    flagged = {}
    for row in result.per_season:
        if row.get("ci_status") in ("single-class", "bootstrap-failed"):
            flagged.setdefault(row["season"], row["ci_status"])
    problems += [f"season {season} ({status})" for season, status in flagged.items()]
    if problems:
        print(f"INCOMPLETE: {len(problems)} analysis problem(s): {'; '.join(problems)}. Exit code 4.")
        return 4
    return 0


if __name__ == "__main__":
    sys.exit(main())
