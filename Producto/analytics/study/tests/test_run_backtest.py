"""scripts/run_backtest.py: the gated entry point.

The pre-registered run on the real inputs happens exactly once, after review.
These tests therefore NEVER pass ``--confirm-preregistered-run`` against the
real ``data/features`` files, and never read them: the refusal test patches
every loader to blow up if touched.

SYNTHETIC ONLY: no real study data is read here.
"""
import dataclasses
import json
import shutil

import pytest
from synthetic import make_inputs, write_csv

import run_backtest
from study import config, dataset, report

SMALL = dataclasses.replace(config.PARAMETERS, n_boot=30, n_comunas=12)


@pytest.fixture
def synthetic_files(tmp_path):
    target, fwi, mb = make_inputs("mb_strong", n_comunas=12)
    d = tmp_path / "inputs"
    return {
        "target": write_csv(d / "firms_target.csv", target),
        "fwi": write_csv(d / "season_fwi.csv", fwi),
        "mapbiomas": write_csv(d / "season_mapbiomas.csv", mb),
    }


def _args(files, *extra):
    return ["--target", str(files["target"]), "--fwi", str(files["fwi"]),
            "--mapbiomas", str(files["mapbiomas"]), *extra]


def _forbid_everything(monkeypatch):
    def boom(*a, **k):
        raise AssertionError("a loader or an analysis ran against the real inputs")

    monkeypatch.setattr(dataset, "read_csv_rows", boom)
    monkeypatch.setattr(report, "run_backtest", boom)
    monkeypatch.setattr(report, "write_tables", boom)


# ---------------------------------------------------------------- the gate


def test_default_inputs_are_the_real_feature_files():
    assert run_backtest.REAL_INPUTS["target"].name == "firms_target.csv"
    assert run_backtest.REAL_INPUTS["fwi"].name == "season_fwi.csv"
    assert run_backtest.REAL_INPUTS["mapbiomas"].name == "season_mapbiomas.csv"
    assert run_backtest.REAL_INPUTS["target"].parent.name == "features"


def test_refuses_to_run_on_the_real_inputs_without_the_flag(monkeypatch, tmp_path, capsys):
    _forbid_everything(monkeypatch)

    code = run_backtest.main(["--out-dir", str(tmp_path / "out")])

    assert code == 2
    assert "--confirm-preregistered-run" in capsys.readouterr().err
    assert not (tmp_path / "out").exists()


def test_refuses_when_only_one_of_the_inputs_is_real(monkeypatch, synthetic_files, tmp_path):
    _forbid_everything(monkeypatch)
    files = dict(synthetic_files, target=run_backtest.REAL_INPUTS["target"])

    assert run_backtest.main(_args(files, "--out-dir", str(tmp_path / "out"))) == 2


def test_a_byte_identical_copy_of_a_real_file_is_still_real(monkeypatch, synthetic_files, tmp_path):
    # Stand-ins for the real files, so no real data is touched.
    stand_ins = {k: v for k, v in synthetic_files.items()}
    monkeypatch.setattr(run_backtest, "REAL_INPUTS", stand_ins)
    copy = tmp_path / "renamed_copy.csv"
    shutil.copyfile(synthetic_files["target"], copy)
    files = dict(synthetic_files, target=copy)
    _forbid_everything(monkeypatch)

    assert run_backtest.main(_args(files, "--out-dir", str(tmp_path / "out"))) == 2


def test_dry_run_is_not_gated_but_computes_no_metric(monkeypatch, synthetic_files, tmp_path, capsys):
    monkeypatch.setattr(run_backtest, "REAL_INPUTS", dict(synthetic_files))
    monkeypatch.setattr(report, "run_backtest", lambda *a, **k: pytest.fail("metrics computed in dry run"))

    code = run_backtest.main(_args(synthetic_files, "--dry-run", "--out-dir", str(tmp_path / "out")))

    out = capsys.readouterr().out
    assert code == 0
    assert "comunas: 12" in out and "seasons: 9" in out
    assert "missing" in out
    assert "ap" not in out.lower().split() and "auc" not in out.lower()
    assert not (tmp_path / "out").exists()


def test_dry_run_fails_on_missing_join_keys(synthetic_files, tmp_path, capsys):
    rows = dataset.read_csv_rows(synthetic_files["fwi"])
    rows = [r for r in rows if not (r["comunaId"] == "C000" and r["season"] == "2017")]
    write_csv(synthetic_files["fwi"], rows)

    code = run_backtest.main(_args(synthetic_files, "--dry-run"))

    assert code == 1
    assert "missing" in capsys.readouterr().out


def test_flag_allows_a_run_on_the_stand_in_real_inputs(monkeypatch, synthetic_files, tmp_path):
    monkeypatch.setattr(run_backtest, "REAL_INPUTS", dict(synthetic_files))

    code = run_backtest.main(_args(synthetic_files, "--confirm-preregistered-run",
                                   "--out-dir", str(tmp_path / "out")), params=SMALL)

    assert code == 0
    assert (tmp_path / "out" / "decision.json").is_file()


# ---------------------------------------------------------------- the run itself


def test_every_run_verifies_the_preregistration_first(monkeypatch, synthetic_files, tmp_path):
    calls = []

    def fail(root=None):
        calls.append("verify")
        raise config.PreregistrationIntegrityError("sha256 mismatch")

    monkeypatch.setattr(config, "verify_preregistration", fail)
    monkeypatch.setattr(dataset, "read_csv_rows", lambda *a, **k: calls.append("read"))

    with pytest.raises(config.PreregistrationIntegrityError):
        run_backtest.main(_args(synthetic_files))
    assert calls == ["verify"]


def test_synthetic_run_writes_tables_and_a_complete_decision_json(synthetic_files, tmp_path, capsys):
    out_dir = tmp_path / "out"

    code = run_backtest.main(_args(synthetic_files, "--out-dir", str(out_dir)), params=SMALL)

    assert code == 0
    for name in ("pooled_grid.csv", "per_season.csv", "decisions.csv"):
        assert (out_dir / "tables" / name).is_file()
    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["preregistration"]["sha256"] == config.load_lock()["sha256"]
    assert set(doc["inputs_sha256"]) == {"firms_target.csv", "season_fwi.csv", "season_mapbiomas.csv"}
    for name, key in (("firms_target.csv", "target"), ("season_fwi.csv", "fwi"),
                      ("season_mapbiomas.csv", "mapbiomas")):
        assert doc["inputs_sha256"][name] == dataset.file_sha256(synthetic_files[key])
    assert isinstance(doc["git_sha"], str) and doc["git_sha"]
    assert doc["primary"]["shipped_wm"] == 0.5
    assert "shipped wM = 0.5" in capsys.readouterr().out


def test_degeneracy_warnings_are_printed_loudly_and_recorded(monkeypatch, synthetic_files, tmp_path, capsys):
    monkeypatch.setattr(report, "degeneracy_warnings", lambda result, params: ["primary: n_redrawn=99 exceeds 1% of B=30"])
    out_dir = tmp_path / "out"

    code = run_backtest.main(_args(synthetic_files, "--out-dir", str(out_dir)), params=SMALL)

    assert code == 0
    assert "WARNING: primary: n_redrawn=99 exceeds 1% of B=30" in capsys.readouterr().err
    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["warnings"] == ["primary: n_redrawn=99 exceeds 1% of B=30"]


def test_an_interrupted_run_has_already_written_the_primary_result_and_the_per_season_table(
        monkeypatch, synthetic_files, tmp_path):
    real = report.pooled_analysis

    def flaky(ds, name, kind, params, **kw):
        if name == "loso_drop_2017":
            raise KeyboardInterrupt()
        return real(ds, name, kind, params, **kw)

    monkeypatch.setattr(report, "pooled_analysis", flaky)
    out_dir = tmp_path / "out"

    with pytest.raises(KeyboardInterrupt):
        run_backtest.main(_args(synthetic_files, "--out-dir", str(out_dir)), params=SMALL)

    assert (out_dir / "tables" / "per_season.csv").is_file()
    assert (out_dir / "tables" / "pooled_grid.csv").is_file()
    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["complete"] is False and doc["primary"]["status"] == "ok"
    assert doc["leave_one_season_out"] == []


def test_a_failed_secondary_analysis_is_warned_about_but_the_run_completes(monkeypatch, synthetic_files,
                                                                          tmp_path, capsys):
    real = report.pooled_analysis

    def flaky(ds, name, kind, params, **kw):
        if name == "sens_n1":
            raise RuntimeError("boom")
        return real(ds, name, kind, params, **kw)

    monkeypatch.setattr(report, "pooled_analysis", flaky)
    out_dir = tmp_path / "out"

    code = run_backtest.main(_args(synthetic_files, "--out-dir", str(out_dir)), params=SMALL)

    assert code == 0
    assert "WARNING: sens_n1: FAILED" in capsys.readouterr().err
    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["complete"] is True
    assert next(e for e in doc["sensitivities"] if e["analysis"] == "sens_n1")["status"] == "failed"


def test_the_default_output_directory_is_the_study_outputs_folder():
    assert run_backtest.DEFAULT_OUT_DIR.name == "outputs"
    assert run_backtest.DEFAULT_OUT_DIR.parent == run_backtest.STUDY_ROOT
