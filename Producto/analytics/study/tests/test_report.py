"""report.py: pooled / per-season / leave-one-season-out / sensitivity analyses
and their tables, on synthetic data with a known right answer.

SYNTHETIC ONLY: no real study data is read here.
"""
import csv
import dataclasses
import json
import math

import numpy as np
import pytest
from synthetic import SEASONS, make_inputs

from study import config, dataset, report

PARAMS = dataclasses.replace(config.PARAMETERS, n_boot=200)


def _manual_dataset(sdyn, smap, y, count=None, comuna=None, season=None):
    n = len(y)
    return dataset.Dataset(
        comuna=np.array(comuna if comuna is not None else [f"C{i}" for i in range(n)], dtype=object),
        season=np.array(season if season is not None else [2017] * n),
        count=np.array(count if count is not None else y),
        y=np.array(y), sdyn=np.array(sdyn, dtype=float), smap=np.array(smap, dtype=float),
    )


def _row(result, wm):
    return next(r for r in result.rows if r["wm"] == wm)


@pytest.fixture(scope="module")
def strong():
    target, fwi, mb = make_inputs("mb_strong")
    return (target, fwi, mb), report.run_backtest(target, fwi, mb, PARAMS)


# ---------------------------------------------------------------- known-answer pooled analyses


def test_perfectly_separating_predictor_gives_auc_pr_one():
    y = [0, 1] * 20
    sdyn = [0.5 if v else 0.1 for v in y]
    ds = _manual_dataset(sdyn, [0.5] * 40, y)
    res = report.pooled_analysis(ds, "t", "primary", PARAMS, with_secondary=True)
    assert _row(res, 0.0)["ap"] == pytest.approx(1.0)
    assert _row(res, 0.0)["auc"] == pytest.approx(1.0)


def test_constant_predictor_gives_auc_pr_equal_to_the_prevalence():
    y = ([1] * 3 + [0] * 2) * 8
    ds = _manual_dataset([0.3] * 40, [0.3] * 40, y)
    res = report.pooled_analysis(ds, "t", "primary", PARAMS, with_secondary=False)
    assert res.prevalence == pytest.approx(0.6)
    assert _row(res, 0.0)["ap"] == pytest.approx(0.6)
    assert _row(res, 0.5)["ap"] == pytest.approx(0.6)


def test_strong_mapbiomas_makes_the_rule_ship_the_largest_weight_and_claim_improvement(strong):
    _, out = strong
    assert out.primary.outcome.shipped_wm == 0.50
    assert out.primary.outcome.claim == "improves"
    assert _row(out.primary, 0.50)["diff_lo"] > 0


def test_harmful_mapbiomas_makes_the_rule_ship_the_baseline():
    target, fwi, mb = make_inputs("mb_harmful")
    out = report.run_backtest(target, fwi, mb, PARAMS)
    assert out.primary.outcome.shipped_wm == 0.0
    assert out.primary.outcome.claim == "baseline"
    assert out.primary.outcome.qualifying == ()
    assert all(_row(out.primary, w)["diff"] < -0.02 for w in PARAMS.grid)


# ---------------------------------------------------------------- pooled table contents


def test_pooled_rows_cover_baseline_and_grid_with_cis_and_secondary_metrics(strong):
    _, out = strong
    weights = [r["wm"] for r in out.primary.rows]
    assert weights == [0.0, 0.10, 0.25, 0.35, 0.50]
    for r in out.primary.rows:
        assert r["n"] == 30 * 9
        for m in ("ap", "auc", "spearman", "brier"):
            assert not math.isnan(r[m])
            assert r[f"{m}_lo"] <= r[f"{m}_hi"]
    base = _row(out.primary, 0.0)
    assert base["diff"] == 0.0 and base["diff_lo"] == 0.0 and base["diff_hi"] == 0.0


def test_loso_and_sensitivities_skip_secondary_metrics(strong):
    _, out = strong
    assert math.isnan(out.loso[0].rows[0]["auc"])
    assert math.isnan(out.sensitivities[0].rows[0]["brier"])


def test_spearman_is_computed_against_log1p_count():
    # count is a monotone function of the score -> Spearman 1 for the baseline
    ds = _manual_dataset(
        sdyn=[0.01 * i for i in range(30)], smap=[0.0] * 30,
        y=[0] * 10 + [1] * 20, count=list(range(30)),
    )
    res = report.pooled_analysis(ds, "t", "primary", PARAMS, with_secondary=True)
    assert _row(res, 0.0)["spearman"] == pytest.approx(1.0)


def test_prevalence_warning_below_five_percent():
    y = [1] + [0] * 39  # 2.5%
    ds = _manual_dataset([0.1 * (i % 5) for i in range(40)], [0.2] * 40, y)
    res = report.pooled_analysis(ds, "t", "primary", PARAMS, with_secondary=False)
    assert res.prevalence == pytest.approx(0.025)
    assert res.prevalence_warning is True


def test_prevalence_exactly_five_percent_is_not_flagged():
    y = [1] * 2 + [0] * 38
    ds = _manual_dataset([0.1 * (i % 5) for i in range(40)], [0.2] * 40, y)
    res = report.pooled_analysis(ds, "t", "primary", PARAMS, with_secondary=False)
    assert res.prevalence == pytest.approx(0.05)
    assert res.prevalence_warning is False  # the contract flags prevalence < 5%


# ---------------------------------------------------------------- degenerate resamples (N14)


def _few_negatives_dataset():
    # 40 comunas, 2 negatives: a resample misses both with probability
    # (38/40)^40 ~ 0.13, so it holds no negatives (ROC AUC undefined) but always
    # holds positives (AP defined).
    y = [0, 0] + [1] * 38
    rng = np.random.default_rng(1)
    return _manual_dataset(rng.random(40) * 0.5, rng.random(40), y)


def test_a_resample_without_negatives_is_not_redrawn_and_only_drops_out_of_the_roc_auc_ci():
    res = report.pooled_analysis(_few_negatives_dataset(), "t", "primary", PARAMS, with_secondary=True)
    assert res.n_redrawn == 0  # positives are always present: nothing to redraw
    assert res.n_auc_undefined > 0
    assert res.n_auc_undefined < PARAMS.n_boot
    row = _row(res, 0.0)
    assert not math.isnan(row["auc_lo"]) and not math.isnan(row["auc_hi"])
    assert not math.isnan(row["ap_lo"]) and not math.isnan(row["brier_lo"])


def test_a_resample_without_positives_is_redrawn_and_counted():
    y = [1] + [0] * 39
    rng = np.random.default_rng(2)
    ds = _manual_dataset(rng.random(40) * 0.5, rng.random(40), y)
    res = report.pooled_analysis(ds, "t", "primary", PARAMS, with_secondary=True)
    assert res.n_redrawn > 0
    assert res.n_auc_undefined == 0  # the lone positive is present in every kept resample, negatives too


def test_degeneracy_warning_fires_above_one_percent_of_b_and_names_the_analysis():
    res = report.pooled_analysis(_few_negatives_dataset(), "t", "primary", PARAMS, with_secondary=True)
    out = report.BacktestResult(primary=res, per_season=[], loso=[], sensitivities=[])
    msgs = report.degeneracy_warnings(out, PARAMS)
    assert any("t" in m and "n_auc_undefined" in m for m in msgs)


def test_degeneracy_warning_is_silent_at_or_below_one_percent():
    res = report.pooled_analysis(_few_negatives_dataset(), "t", "primary", PARAMS, with_secondary=True)
    quiet = dataclasses.replace(res, n_redrawn=PARAMS.n_boot // 100, n_auc_undefined=PARAMS.n_boot // 100)
    out = report.BacktestResult(primary=quiet, per_season=[], loso=[], sensitivities=[])
    assert report.degeneracy_warnings(out, PARAMS) == []


# ---------------------------------------------------------------- per-season, LOSO, sensitivities


def test_per_season_table_has_one_row_per_season_and_weight(strong):
    _, out = strong
    assert len(out.per_season) == 9 * 5
    for season in SEASONS:
        rows = [r for r in out.per_season if r["season"] == season]
        assert [r["wm"] for r in rows] == [0.0, 0.10, 0.25, 0.35, 0.50]
        assert all(r["n"] == 30 for r in rows)
        assert all(r["ap_lo"] <= r["ap_hi"] for r in rows)
        assert all(r["ci_status"] == "ok" for r in rows)
        assert rows[0]["prevalence"] == rows[1]["prevalence"]


def test_per_season_ci_status_flags_a_single_class_season_instead_of_crashing():
    target, fwi, mb = make_inputs("mb_strong")
    for t in target:
        if t["season"] == "2017" and t["window"] == "primary" and t["filter"] == "type0":
            t["count"], t["y_n1"], t["y_n3"], t["y_n5"] = "9", "1", "1", "1"
    out = report.run_backtest(target, fwi, mb, PARAMS)
    rows = [r for r in out.per_season if r["season"] == 2017]
    assert all(r["ci_status"] == "single-class" for r in rows)
    assert all(math.isnan(r["ap_lo"]) for r in rows)


def test_per_season_all_positive_season_is_single_class_too():
    target, fwi, mb = make_inputs("mb_strong")
    for t in target:
        if t["season"] == "2018" and t["window"] == "primary" and t["filter"] == "type0":
            t["count"], t["y_n1"], t["y_n3"], t["y_n5"] = "9", "1", "1", "1"
    out = report.run_backtest(target, fwi, mb, PARAMS)
    rows = [r for r in out.per_season if r["season"] == 2018]
    assert all(r["ci_status"] == "single-class" for r in rows)


def test_per_season_redraw_budget_exhaustion_is_never_labelled_single_class(monkeypatch):
    target, fwi, mb = make_inputs("mb_strong")
    real = report.bootstrap.cluster_bootstrap

    def flaky(y, clusters, evaluate, **kw):
        if len(y) == 30 and report_state["season_calls"] == 0:
            report_state["season_calls"] += 1
            raise report.bootstrap.BootstrapError("gave up after 99 draws: too many degenerate resamples")
        return real(y, clusters, evaluate, **kw)

    report_state = {"season_calls": 0}
    ds = dataset.build_dataset(target, fwi, mb, n_threshold=3, window="primary",
                               type_filter="type0", variant="pct")
    monkeypatch.setattr(report.bootstrap, "cluster_bootstrap", flaky)
    rows = report.per_season_table(ds, PARAMS)
    first = [r for r in rows if r["season"] == 2017]
    assert all(r["ci_status"] == "bootstrap-failed" for r in first)
    assert all("too many degenerate resamples" in r["ci_error"] for r in first)
    assert all(math.isnan(r["ap_lo"]) for r in first)
    others = [r for r in rows if r["season"] != 2017]
    assert all(r["ci_status"] == "ok" and r["ci_error"] == "" for r in others)


def test_leave_one_season_out_runs_nine_reruns_and_never_changes_the_primary(strong):
    (target, fwi, mb), out = strong
    assert [r.name for r in out.loso] == [f"loso_drop_{s}" for s in SEASONS]
    assert all(r.kind == "loso" and r.n == 30 * 8 for r in out.loso)
    ds = dataset.build_dataset(target, fwi, mb, n_threshold=3, window="primary",
                               type_filter="type0", variant="pct")
    solo = report.pooled_analysis(ds, "primary", "primary", PARAMS, with_secondary=True)
    assert solo.outcome == out.primary.outcome
    assert solo.rows == out.primary.rows


def test_sensitivities_are_the_pre_declared_five_and_use_their_own_target(strong):
    _, out = strong
    names = [r.name for r in out.sensitivities]
    assert names == ["sens_n1", "sens_n5", "sens_window_nov_apr", "sens_no_type_filter", "sens_log_burned"]
    by = {r.name: r for r in out.sensitivities}
    assert by["sens_n1"].prevalence >= out.primary.prevalence >= by["sens_n5"].prevalence
    assert by["sens_n1"].prevalence > by["sens_n5"].prevalence
    assert all(r.kind == "sensitivity" for r in out.sensitivities)
    # reported next to the primary, never substituted for it
    assert out.primary.name == "primary"


def test_sensitivity_window_uses_the_sensitivity_target_and_fwi_windows_together():
    target, fwi, mb = make_inputs("mb_strong")
    for r in fwi:
        if r["window"] == "sensitivity":
            r["sdyn_mean"] = "0.0"  # break only the sensitivity-window predictor
    out = report.run_backtest(target, fwi, mb, PARAMS)
    sens = {r.name: r for r in out.sensitivities}["sens_window_nov_apr"]
    # S_dynamic == 0 everywhere: the baseline is a constant predictor -> AP equals prevalence
    assert _row(sens, 0.0)["ap"] == pytest.approx(sens.prevalence)
    # while the primary (standard window) is untouched
    assert _row(out.primary, 0.0)["ap"] != pytest.approx(out.primary.prevalence)


def test_log_variant_uses_the_log_burned_norm_column(strong):
    _, out = strong
    sens = {r.name: r for r in out.sensitivities}["sens_log_burned"]
    # s_mapbiomas_log = 0.9 * s_mapbiomas in the fixture, so blended rankings differ
    assert any(_row(sens, w)["ap"] != _row(out.primary, w)["ap"] for w in PARAMS.grid)


# ---------------------------------------------------------------- reproducibility and outputs


def test_the_whole_backtest_is_reproducible_for_a_fixed_seed():
    target, fwi, mb = make_inputs("mb_strong", seed=3)
    a = report.run_backtest(target, fwi, mb, PARAMS)
    b = report.run_backtest(target, fwi, mb, PARAMS)
    assert a.primary.rows == b.primary.rows
    assert a.per_season == b.per_season
    assert [r.rows for r in a.loso] == [r.rows for r in b.loso]


def test_write_tables_writes_the_three_csvs(strong, tmp_path):
    _, out = strong
    report.write_tables(out, tmp_path / "tables")
    for name in ("pooled_grid.csv", "per_season.csv", "decisions.csv"):
        assert (tmp_path / "tables" / name).is_file()
    with open(tmp_path / "tables" / "pooled_grid.csv", encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))
    analyses = {r["analysis"] for r in rows}
    assert "primary" in analyses and "loso_drop_2017" in analyses and "sens_n1" in analyses
    assert len(rows) == (1 + 9 + 5) * 5
    with open(tmp_path / "tables" / "decisions.csv", encoding="utf-8", newline="") as f:
        decisions = list(csv.DictReader(f))
    assert len(decisions) == 1 + 9 + 5
    assert decisions[0]["analysis"] == "primary" and decisions[0]["shipped_wm"] == "0.5"


def test_decision_json_records_hash_inputs_git_sha_and_all_decisions(strong, tmp_path):
    _, out = strong
    lock = {"sha256": "abc", "commit": "def", "version": "1.0"}
    path = report.write_decision_json(
        out, tmp_path / "decision.json", lock=lock,
        input_hashes={"firms_target.csv": "h1", "season_fwi.csv": "h2", "season_mapbiomas.csv": "h3"},
        git_sha="deadbeef", params=PARAMS,
    )
    doc = json.loads(path.read_text(encoding="utf-8"))
    assert doc["preregistration"]["sha256"] == "abc"
    assert doc["preregistration"]["commit"] == "def"
    assert doc["inputs_sha256"]["season_fwi.csv"] == "h2"
    assert doc["git_sha"] == "deadbeef"
    assert doc["primary"]["shipped_wm"] == 0.5 and doc["primary"]["claim"] == "improves"
    assert len(doc["leave_one_season_out"]) == 9
    assert len(doc["sensitivities"]) == 5
    assert doc["parameters"]["seed"] == 20261027 and doc["parameters"]["delta"] == 0.02
    assert "never substituted" in doc["note"]


def test_git_sha_helper_returns_a_string():
    assert isinstance(report.current_git_sha(), str)
