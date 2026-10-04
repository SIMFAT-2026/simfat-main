"""Backtest analyses and report tables (PREREGISTRATION.md Sections 9, 10, 12, 13).

Pure orchestration over ``scores``, ``metrics``, ``bootstrap`` and
``decision_rule``; every number comes from those modules, nothing is
re-derived here.

- ``pooled_analysis``: one pooled comuna-clustered paired bootstrap for the
  baseline and every grid weight, then the Section 10 rule and Section 12
  claim.
- ``run_backtest``: the primary pooled result, the per-season table, the nine
  leave-one-season-out reruns, and the five pre-declared sensitivities. The
  last two run the SAME rule and are reported next to the primary; they never
  replace it and never change it.

Secondary metrics (ROC AUC, Spearman against ``log1p(count)``, Brier) are
bootstrapped, with percentile CIs from the same paired resamples, in EVERY
analysis and in the per-season table (Sections 9 and 12, Table 2b). They have no
decision power: the rule reads the AUC-PR difference only.
"""
from __future__ import annotations

import csv
import json
import math
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Mapping, Sequence

import numpy as np

from study import bootstrap, config, dataset, decision_rule, metrics, scores

NAN = float("nan")
SECONDARY = ("auc", "spearman", "brier")


@dataclass(frozen=True)
class PooledResult:
    name: str
    kind: str  # primary | loso | sensitivity
    n: int
    n_pos: int
    prevalence: float
    prevalence_warning: bool
    rows: list  # one dict per weight, baseline first
    outcome: decision_rule.RuleOutcome | None  # None only for a failed analysis
    n_redrawn: int  # resamples without positives, redrawn (AUC-PR undefined)
    n_auc_undefined: int = 0  # kept resamples without negatives: out of the ROC AUC CI only
    status: str = "ok"  # ok | failed
    error: str = ""


def failed_result(name: str, kind: str, exc: BaseException) -> PooledResult:
    """A leave-one-season-out or sensitivity analysis that raised: reported in
    every output with its error, never dropped."""
    return PooledResult(
        name=name, kind=kind, n=0, n_pos=0, prevalence=NAN, prevalence_warning=False,
        rows=[], outcome=None, n_redrawn=0, n_auc_undefined=0,
        status="failed", error=f"{type(exc).__name__}: {exc}",
    )


@dataclass(frozen=True)
class BacktestResult:
    primary: PooledResult
    per_season: list  # one dict per (season, weight)
    loso: list  # PooledResult per dropped season
    sensitivities: list  # PooledResult per sensitivity
    complete: bool = True  # False for the checkpoint written before the reruns


def _fmt(w: float) -> str:
    return repr(float(w))


def _auc_or_nan(y, score) -> float:
    """ROC AUC, or NaN for a resample without both classes (N14): the resample
    stays in the AP and Brier intervals and drops out of the ROC AUC one."""
    try:
        return metrics.roc_auc(y, score)
    except metrics.DegenerateLabelsError:
        return NAN


def _count_nan(values) -> int:
    return int(np.isnan(np.asarray(values, dtype=float)).sum())


def pooled_analysis(
    ds: dataset.Dataset,
    name: str,
    kind: str,
    params: config.StudyParameters,
    *,
    with_secondary: bool,
) -> PooledResult:
    weights = params.all_weights
    by_wm = scores.scores_by_wm(ds.sdyn, ds.smap, weights)
    log_count = np.log1p(ds.count.astype(float))
    base_w = params.baseline_wm

    def evaluate(idx: np.ndarray) -> dict:
        yy = ds.y[idx]
        out = {}
        for w in weights:
            s = by_wm[w][idx]
            out[f"ap|{_fmt(w)}"] = metrics.average_precision(yy, s)
            if with_secondary:
                out[f"auc|{_fmt(w)}"] = _auc_or_nan(yy, s)
                out[f"spearman|{_fmt(w)}"] = metrics.spearman(s, log_count[idx])
                out[f"brier|{_fmt(w)}"] = metrics.brier(yy, s)
        return out

    boot = bootstrap.cluster_bootstrap(ds.y, ds.comuna, evaluate, B=params.n_boot, seed=params.seed)

    point_ap = {w: metrics.average_precision(ds.y, by_wm[w]) for w in weights}
    diff_cis = {}
    for w in weights:
        if w != base_w:
            diff_values = boot.values[f"ap|{_fmt(w)}"] - boot.values[f"ap|{_fmt(base_w)}"]
            d_lo, d_hi, _ = bootstrap.percentile_ci(diff_values, params.ci_level)
            diff_cis[w] = (d_lo, d_hi)
    outcome = decision_rule.apply_rule(diff_cis)

    rows = []
    for w in weights:
        row = {"wm": w, "n": len(ds), "n_pos": int(ds.y.sum()), "prevalence": float(ds.y.mean())}
        ap_lo, ap_hi, _ = bootstrap.percentile_ci(boot.values[f"ap|{_fmt(w)}"], params.ci_level)
        diff_values = boot.values[f"ap|{_fmt(w)}"] - boot.values[f"ap|{_fmt(base_w)}"]
        d_lo, d_hi, _ = bootstrap.percentile_ci(diff_values, params.ci_level)
        row.update(ap=point_ap[w], ap_lo=ap_lo, ap_hi=ap_hi,
                   diff=point_ap[w] - point_ap[base_w], diff_lo=d_lo, diff_hi=d_hi)
        # Straight from the rule: no second implementation of the comparison here.
        row["qualifies"] = None if w == base_w else bool(w in outcome.qualifying)
        for m, fn in (("auc", _auc_or_nan), ("spearman", None), ("brier", metrics.brier)):
            if with_secondary:
                if m == "spearman":
                    point = metrics.spearman(by_wm[w], log_count)
                else:
                    point = fn(ds.y, by_wm[w])
                lo, hi, _ = bootstrap.percentile_ci(boot.values[f"{m}|{_fmt(w)}"], params.ci_level)
            else:
                point = lo = hi = NAN
            row.update({m: point, f"{m}_lo": lo, f"{m}_hi": hi})
        rows.append(row)

    prevalence = float(ds.y.mean())
    return PooledResult(
        name=name, kind=kind, n=len(ds), n_pos=int(ds.y.sum()), prevalence=prevalence,
        prevalence_warning=bool(prevalence < params.prevalence_warning),
        rows=rows, outcome=outcome, n_redrawn=boot.n_redrawn,
        n_auc_undefined=_count_nan(boot.values[f"auc|{_fmt(base_w)}"]) if with_secondary else 0,
    )


def per_season_table(ds: dataset.Dataset, params: config.StudyParameters) -> list:
    """Every Section 9 metric (AUC-PR, ROC AUC, Spearman, Brier), prevalence, n
    and the paired difference, each with a per-season comuna-bootstrap CI, for
    every weight. Each season holds one row per comuna, so the cluster bootstrap
    is a plain bootstrap over the comunas."""
    weights = params.all_weights
    base_w = params.baseline_wm
    out = []
    for season in sorted(set(int(s) for s in ds.season)):
        sub = ds.subset(ds.season == season)
        by_wm = scores.scores_by_wm(sub.sdyn, sub.smap, weights)
        log_count = np.log1p(sub.count.astype(float))
        prevalence = float(sub.y.mean())
        n_pos = int(sub.y.sum())
        single_class = n_pos in (0, len(sub))

        def evaluate(idx, sub=sub, by_wm=by_wm, log_count=log_count):
            yy = sub.y[idx]
            vals = {}
            for w in weights:
                s_w = by_wm[w][idx]
                vals[f"ap|{_fmt(w)}"] = metrics.average_precision(yy, s_w)
                vals[f"auc|{_fmt(w)}"] = _auc_or_nan(yy, s_w)
                vals[f"spearman|{_fmt(w)}"] = metrics.spearman(s_w, log_count[idx])
                vals[f"brier|{_fmt(w)}"] = metrics.brier(yy, s_w)
            return vals

        boot, status, error, n_redrawn, n_auc_undefined = None, "ok", "", 0, 0
        if single_class:
            status = "single-class"
        else:
            try:
                boot = bootstrap.cluster_bootstrap(sub.y, sub.comuna, evaluate,
                                                   B=params.n_boot, seed=params.seed)
                n_redrawn = boot.n_redrawn
                n_auc_undefined = _count_nan(boot.values[f"auc|{_fmt(base_w)}"])
            except bootstrap.BootstrapError as exc:
                status, error = "bootstrap-failed", str(exc)

        point_ap = {w: (metrics.average_precision(sub.y, by_wm[w]) if n_pos > 0 else NAN) for w in weights}
        for w in weights:
            row = {"season": season, "wm": w, "n": len(sub), "n_pos": n_pos,
                   "prevalence": prevalence,
                   "prevalence_warning": bool(prevalence < params.prevalence_warning),
                   "ap": point_ap[w], "diff": point_ap[w] - point_ap[base_w],
                   "ci_status": status, "ci_error": error, "n_redrawn": n_redrawn,
                   "n_auc_undefined": n_auc_undefined}
            points = {
                "auc": NAN if single_class else metrics.roc_auc(sub.y, by_wm[w]),
                "spearman": metrics.spearman(by_wm[w], log_count),
                "brier": metrics.brier(sub.y, by_wm[w]),
            }
            for m in ("ap", "auc", "spearman", "brier"):
                if m != "ap":
                    row[m] = points[m]
                if boot is None:
                    row[f"{m}_lo"] = row[f"{m}_hi"] = NAN
                else:
                    row[f"{m}_lo"], row[f"{m}_hi"], _ = bootstrap.percentile_ci(
                        boot.values[f"{m}|{_fmt(w)}"], params.ci_level)
            if boot is None:
                row["diff_lo"] = row["diff_hi"] = NAN
            else:
                d = boot.values[f"ap|{_fmt(w)}"] - boot.values[f"ap|{_fmt(base_w)}"]
                row["diff_lo"], row["diff_hi"], _ = bootstrap.percentile_ci(d, params.ci_level)
            out.append(row)
    return out


def run_backtest(
    target_rows: Sequence[Mapping],
    fwi_rows: Sequence[Mapping],
    mapbiomas_rows: Sequence[Mapping],
    params: config.StudyParameters | None = None,
    *,
    checkpoint: Callable[[BacktestResult], None] | None = None,
) -> BacktestResult:
    """Every analysis of Sections 10-13. Verifies the pre-registration first.

    The primary pooled result and the per-season table are computed first and
    handed to ``checkpoint`` (the entry point writes them to disk) before any
    rerun starts. A failure in the primary analysis or in the per-season panel
    check aborts loudly. Each leave-one-season-out rerun and each sensitivity
    then runs inside its own guard: a failure is recorded in the result with its
    error (``status="failed"``) and the remaining analyses continue.

    ``params`` exists so tests can shrink ``n_boot``; the entry point never
    passes it.
    """
    _, _lock = config.load_parameters()
    params = params if params is not None else config.PARAMETERS

    def build(n_threshold, window, type_filter, variant):
        return dataset.build_dataset(
            target_rows, fwi_rows, mapbiomas_rows, n_threshold=n_threshold, window=window,
            type_filter=type_filter, variant=variant, seasons=params.seasons,
        )

    def check(ds, label, seasons=None, reference=None):
        dataset.assert_balanced_panel(ds, n_comunas=params.n_comunas,
                                      seasons=seasons if seasons is not None else params.seasons, label=label)
        if reference is not None:
            dataset.assert_same_panel(ds, reference, label=label)
        return ds

    primary_ds = check(build(params.n_firms_primary, "primary", "type0", "pct"), "primary")
    primary = pooled_analysis(primary_ds, "primary", "primary", params, with_secondary=True)
    for season in params.seasons:
        check(primary_ds.subset(primary_ds.season == season), f"primary season {season}", seasons=[season])
    per_season = per_season_table(primary_ds, params)
    if checkpoint is not None:
        checkpoint(BacktestResult(primary, per_season, [], [], complete=False))

    def guarded(name, kind, make_dataset):
        try:
            return pooled_analysis(make_dataset(), name, kind, params, with_secondary=True)
        except Exception as exc:  # reported in every output, never hidden
            return failed_result(name, kind, exc)

    loso = [
        guarded(
            f"loso_drop_{season}", "loso",
            lambda season=season: check(
                primary_ds.subset(primary_ds.season != season), f"loso_drop_{season}",
                seasons=[s for s in params.seasons if s != season]),
        )
        for season in params.seasons
    ]

    sens_specs = [(f"sens_n{n}", dict(n_threshold=n, window="primary", type_filter="type0", variant="pct"))
                  for n in params.n_firms_sensitivities]
    sens_specs += [
        ("sens_window_nov_apr", dict(n_threshold=params.n_firms_primary, window="sensitivity",
                                     type_filter="type0", variant="pct")),
        ("sens_no_type_filter", dict(n_threshold=params.n_firms_primary, window="primary",
                                     type_filter="notype", variant="pct")),
        ("sens_log_burned", dict(n_threshold=params.n_firms_primary, window="primary",
                                 type_filter="type0", variant="log")),
    ]
    sensitivities = [
        guarded(name, "sensitivity",
                lambda name=name, spec=spec: check(build(**spec), name, reference=primary_ds))
        for name, spec in sens_specs
    ]
    return BacktestResult(primary, per_season, loso, sensitivities)


# ---------------------------------------------------------------- outputs

POOLED_COLUMNS = (
    ["analysis", "kind", "wm", "n", "n_pos", "prevalence", "prevalence_warning"]
    + ["ap", "ap_lo", "ap_hi", "diff", "diff_lo", "diff_hi", "qualifies"]
    + [f"{m}{suffix}" for m in SECONDARY for suffix in ("", "_lo", "_hi")]
    + ["n_redrawn", "n_auc_undefined", "status", "error"]
)
PER_SEASON_COLUMNS = [
    "season", "wm", "n", "n_pos", "prevalence", "prevalence_warning",
    "ap", "ap_lo", "ap_hi", "diff", "diff_lo", "diff_hi",
    *[f"{m}{suffix}" for m in SECONDARY for suffix in ("", "_lo", "_hi")],
    "ci_status", "ci_error", "n_redrawn", "n_auc_undefined",
]


def _cell(v):
    if v is None:
        return ""
    return v


def degeneracy_warnings(result: BacktestResult, params: config.StudyParameters) -> list:
    """Human-readable warnings for every analysis whose ``n_redrawn`` or
    ``n_auc_undefined`` exceeds 1% of B (N14)."""
    limit = 0.01 * params.n_boot
    out = []
    for res in _all_pooled(result):
        if res.status == "failed":
            out.append(f"{res.name}: FAILED ({res.error})")
            continue
        for field in ("n_redrawn", "n_auc_undefined"):
            value = getattr(res, field)
            if value > limit:
                out.append(f"{res.name}: {field}={value} exceeds 1% of B={params.n_boot}")
    seen = set()
    for row in result.per_season:
        key = row["season"]
        if key in seen:
            continue
        seen.add(key)
        for field in ("n_redrawn", "n_auc_undefined"):
            value = row.get(field, 0)
            if value > limit:
                out.append(f"season {key}: {field}={value} exceeds 1% of B={params.n_boot}")
    return out


def _all_pooled(result: BacktestResult) -> list:
    return [result.primary, *result.loso, *result.sensitivities]


def write_tables(result: BacktestResult, out_dir: Path) -> None:
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    with open(out_dir / "pooled_grid.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(POOLED_COLUMNS)
        for res in _all_pooled(result):
            if res.status == "failed":
                merged = {"analysis": res.name, "kind": res.kind, "status": res.status, "error": res.error}
                w.writerow([_cell(merged.get(c)) for c in POOLED_COLUMNS])
                continue
            for row in res.rows:
                merged = {**row, "analysis": res.name, "kind": res.kind, "n_redrawn": res.n_redrawn,
                          "n_auc_undefined": res.n_auc_undefined, "status": res.status, "error": res.error,
                          "prevalence_warning": res.prevalence_warning}
                w.writerow([_cell(merged[c]) for c in POOLED_COLUMNS])

    with open(out_dir / "per_season.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(PER_SEASON_COLUMNS)
        for row in result.per_season:
            w.writerow([_cell(row[c]) for c in PER_SEASON_COLUMNS])

    grid = sorted(result.primary.outcome.lowers)
    with open(out_dir / "decisions.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["analysis", "kind", "shipped_wm", "claim", "qualifying", "n", "prevalence",
                    "prevalence_warning", "n_redrawn", "n_auc_undefined", "status", "error"]
                   + [f"diff_lo_{_fmt(g)}" for g in grid])
        for res in _all_pooled(result):
            o = res.outcome
            if res.status == "failed":
                w.writerow([res.name, res.kind, "", "", "", "", "", "", "", "", res.status, res.error]
                           + [""] * len(grid))
                continue
            w.writerow([res.name, res.kind, o.shipped_wm, o.claim,
                        " ".join(_fmt(q) for q in o.qualifying), res.n, res.prevalence,
                        res.prevalence_warning, res.n_redrawn, res.n_auc_undefined, res.status, res.error]
                       + [o.lowers[g] for g in grid])


def current_git_sha(cwd: Path | None = None) -> str:
    try:
        out = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=str(cwd or config.study_root()),
            capture_output=True, text=True, check=True, timeout=30,
        )
        return out.stdout.strip() or "unknown"
    except (OSError, subprocess.SubprocessError):
        return "unknown"


def _decision_entry(res: PooledResult, primary: PooledResult) -> dict:
    if res.status == "failed":
        return {"analysis": res.name, "status": "failed", "error": res.error}
    o = res.outcome
    return {
        "analysis": res.name,
        "status": res.status,
        "shipped_wm": o.shipped_wm,
        "claim": o.claim,
        "qualifying": list(o.qualifying),
        "diff_ci_lower_by_wm": {_fmt(k): v for k, v in sorted(o.lowers.items())},
        "n": res.n,
        "prevalence": res.prevalence,
        "prevalence_warning": res.prevalence_warning,
        "n_redrawn": res.n_redrawn,
        "n_auc_undefined": res.n_auc_undefined,
        "agrees_with_primary": o.shipped_wm == primary.outcome.shipped_wm,
    }


def write_decision_json(
    result: BacktestResult,
    path: Path,
    *,
    lock: Mapping,
    input_hashes: Mapping[str, str],
    git_sha: str,
    params: config.StudyParameters,
    warnings: Sequence[str] = (),
) -> Path:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    doc = {
        "preregistration": {"sha256": lock["sha256"], "commit": lock["commit"],
                            "version": lock.get("version")},
        "inputs_sha256": dict(input_hashes),
        "git_sha": git_sha,
        "complete": bool(result.complete),
        "warnings": list(warnings),
        "parameters": {
            "grid": list(params.grid), "decision_sequence": list(params.decision_sequence),
            "delta": params.delta, "n_boot": params.n_boot, "seed": params.seed,
            "ci_level": params.ci_level, "n_firms_primary": params.n_firms_primary,
            "seasons": list(params.seasons),
        },
        "primary": _decision_entry(result.primary, result.primary),
        "leave_one_season_out": [_decision_entry(r, result.primary) for r in result.loso],
        "sensitivities": [_decision_entry(r, result.primary) for r in result.sensitivities],
        "note": ("Leave-one-season-out and sensitivity decisions are reported next to the "
                 "primary decision and are never substituted for it (Sections 10, 12, 13)."),
    }
    path.write_text(json.dumps(doc, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return path
