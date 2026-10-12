"""scripts/run_backtest.py: the gated entry point.

The pre-registered run on the real inputs happens exactly once, after review.
These tests therefore NEVER pass ``--confirm-preregistered-run`` against the
real ``data/features`` files, and never read them: the refusal test patches
every loader to blow up if touched.

SYNTHETIC ONLY: no real study data is read here.
"""
import dataclasses
import hashlib
import json
import shutil
import subprocess

import pytest
from synthetic import make_inputs, write_csv

import run_backtest
from study import config, dataset, report

SMALL = dataclasses.replace(config.PARAMETERS, n_boot=30, n_comunas=12)


def fake_git(calls=None, *, dirty="", study_dirty=None, ancestor_rc=0, sha="a" * 40):
    """Stand-in for ``report._run_git``: no real git repository is consulted."""

    def run(args, cwd):
        args = list(args)
        if calls is not None:
            calls.append(args)
        if args[:2] == ["merge-base", "--is-ancestor"]:
            return subprocess.CompletedProcess(args, ancestor_rc, "", "")
        if args[0] == "status":
            out = (study_dirty if study_dirty is not None else dirty) if "--" in args else dirty
            return subprocess.CompletedProcess(args, 0, out, "")
        if args[0] == "rev-parse":
            return subprocess.CompletedProcess(args, 0, sha + "\n", "")
        raise AssertionError(f"unexpected git call {args}")

    return run


@pytest.fixture(autouse=True)
def _clean_git(monkeypatch):
    """Every test sees a clean tree whose pre-registration commit is an ancestor
    of HEAD, unless it installs its own fake."""
    monkeypatch.setattr(report, "_run_git", fake_git())


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

    captured = capsys.readouterr()
    assert code == 4
    assert "WARNING: sens_n1: FAILED" in captured.err
    assert "sens_n1" in captured.out.strip().splitlines()[-1]  # the final summary line names it
    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["complete"] is True
    assert next(e for e in doc["sensitivities"] if e["analysis"] == "sens_n1")["status"] == "failed"
    assert (out_dir / "tables" / "pooled_grid.csv").is_file()  # outputs are on disk before exit 4


def test_a_single_class_season_makes_the_run_exit_4_after_writing_everything(synthetic_files, tmp_path, capsys):
    target = dataset.read_csv_rows(synthetic_files["target"])
    for t in target:
        if t["season"] == "2017" and t["window"] == "primary" and t["filter"] == "type0":
            t["count"], t["y_n1"], t["y_n3"], t["y_n5"] = "9", "1", "1", "1"
    write_csv(synthetic_files["target"], target)
    out_dir = tmp_path / "out"

    code = run_backtest.main(_args(synthetic_files, "--out-dir", str(out_dir)), params=SMALL)

    captured = capsys.readouterr()
    assert code == 4
    assert "season 2017" in captured.out.strip().splitlines()[-1]
    assert "single-class" in captured.err
    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["complete"] is True
    assert any("season 2017" in w and "single-class" in w for w in doc["warnings"])


def test_a_clean_run_exits_0_and_prints_no_failure_summary(synthetic_files, tmp_path, capsys):
    code = run_backtest.main(_args(synthetic_files, "--out-dir", str(tmp_path / "out")), params=SMALL)

    assert code == 0
    assert "INCOMPLETE" not in capsys.readouterr().out


# ---------------------------------------------------------------- run-once guard


def _write_previous(out_dir, text='{"previous": true}\n'):
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "decision.json").write_bytes(text.encode("utf-8"))  # bytes: no newline translation
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def test_refuses_to_start_when_a_decision_already_exists(monkeypatch, synthetic_files, tmp_path, capsys):
    monkeypatch.setattr(run_backtest, "REAL_INPUTS", dict(synthetic_files))
    out_dir = tmp_path / "out"
    _write_previous(out_dir)
    _forbid_everything(monkeypatch)

    code = run_backtest.main(_args(synthetic_files, "--confirm-preregistered-run", "--out-dir", str(out_dir)),
                             params=SMALL)

    assert code == 2
    assert "--allow-overwrite" in capsys.readouterr().err
    assert (out_dir / "decision.json").read_bytes() == b'{"previous": true}\n'
    assert not (out_dir / "tables").exists()


def test_allow_overwrite_records_the_overwrite_and_the_previous_hash(monkeypatch, synthetic_files, tmp_path):
    monkeypatch.setattr(run_backtest, "REAL_INPUTS", dict(synthetic_files))
    out_dir = tmp_path / "out"
    previous = _write_previous(out_dir)

    code = run_backtest.main(_args(synthetic_files, "--confirm-preregistered-run", "--allow-overwrite",
                                   "--out-dir", str(out_dir)), params=SMALL)

    assert code == 0
    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["overwrote_previous"] is True
    assert doc["previous_decision_sha256"] == previous
    assert doc["complete"] is True  # the overwrite note survives the final write


def test_a_first_run_records_no_overwrite(synthetic_files, tmp_path):
    out_dir = tmp_path / "out"

    assert run_backtest.main(_args(synthetic_files, "--out-dir", str(out_dir)), params=SMALL) == 0

    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["overwrote_previous"] is False and doc["previous_decision_sha256"] is None


# ---------------------------------------------------------------- provenance (Section 17)


def test_decision_json_records_whether_the_working_tree_was_dirty(monkeypatch, synthetic_files, tmp_path):
    monkeypatch.setattr(report, "_run_git", fake_git(dirty=" M somewhere/else.md\n", study_dirty=""))
    out_dir = tmp_path / "out"

    assert run_backtest.main(_args(synthetic_files, "--out-dir", str(out_dir)), params=SMALL) == 0

    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["git_dirty"] is True
    assert doc["git_dirty_study_dir"] is False


def test_decision_json_records_a_clean_tree(synthetic_files, tmp_path):
    out_dir = tmp_path / "out"

    run_backtest.main(_args(synthetic_files, "--out-dir", str(out_dir)), params=SMALL)

    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["git_dirty"] is False and doc["git_dirty_study_dir"] is False


def test_refuses_when_the_preregistration_commit_is_not_an_ancestor_of_head(monkeypatch, synthetic_files,
                                                                           tmp_path, capsys):
    calls = []
    monkeypatch.setattr(report, "_run_git", fake_git(calls, ancestor_rc=1))
    _forbid_everything(monkeypatch)
    out_dir = tmp_path / "out"

    code = run_backtest.main(_args(synthetic_files, "--out-dir", str(out_dir)), params=SMALL)

    assert code == 3
    assert "ancestor" in capsys.readouterr().err
    assert ["merge-base", "--is-ancestor", config.load_lock()["commit"], "HEAD"] in calls
    assert not out_dir.exists()


@pytest.mark.parametrize("rc", [128, 2])
def test_an_unverifiable_ancestry_also_refuses(monkeypatch, synthetic_files, tmp_path, rc):
    monkeypatch.setattr(report, "_run_git", fake_git(ancestor_rc=rc))
    _forbid_everything(monkeypatch)

    assert run_backtest.main(_args(synthetic_files, "--out-dir", str(tmp_path / "out")), params=SMALL) == 3


def test_a_missing_git_binary_refuses_instead_of_skipping_the_check(monkeypatch, synthetic_files, tmp_path):
    def no_git(args, cwd):
        raise FileNotFoundError("git")

    monkeypatch.setattr(report, "_run_git", no_git)
    _forbid_everything(monkeypatch)

    assert run_backtest.main(_args(synthetic_files, "--out-dir", str(tmp_path / "out")), params=SMALL) == 3


def _confirmed(files, out_dir):
    return _args(files, "--confirm-preregistered-run", "--out-dir", str(out_dir))


def test_a_real_run_refuses_a_dirty_study_directory(monkeypatch, synthetic_files, tmp_path, capsys):
    monkeypatch.setattr(run_backtest, "REAL_INPUTS", dict(synthetic_files))
    monkeypatch.setattr(report, "_run_git", fake_git(dirty=" M Producto/analytics/study/x.py\n",
                                                     study_dirty=" M x.py\n"))
    _forbid_everything(monkeypatch)
    out_dir = tmp_path / "out"

    code = run_backtest.main(_confirmed(synthetic_files, out_dir), params=SMALL)

    err = capsys.readouterr().err
    assert code == 3
    assert "commit or stash" in err.lower() and "Producto/analytics/study/" in err
    assert not out_dir.exists()


def test_a_real_run_refuses_when_git_cannot_say_whether_the_study_directory_is_dirty(
        monkeypatch, synthetic_files, tmp_path):
    monkeypatch.setattr(run_backtest, "REAL_INPUTS", dict(synthetic_files))
    base = fake_git()

    def flaky(args, cwd):
        if args[0] == "status":
            return subprocess.CompletedProcess(list(args), 128, "", "fatal")
        return base(args, cwd)

    monkeypatch.setattr(report, "_run_git", flaky)
    _forbid_everything(monkeypatch)

    assert run_backtest.main(_confirmed(synthetic_files, tmp_path / "out"), params=SMALL) == 3


def test_changes_outside_the_study_directory_do_not_block_a_real_run(monkeypatch, synthetic_files, tmp_path):
    monkeypatch.setattr(run_backtest, "REAL_INPUTS", dict(synthetic_files))
    monkeypatch.setattr(report, "_run_git", fake_git(dirty=" M Documentacion/notes.md\n", study_dirty=""))
    out_dir = tmp_path / "out"

    code = run_backtest.main(_confirmed(synthetic_files, out_dir), params=SMALL)

    assert code == 0
    doc = json.loads((out_dir / "decision.json").read_text(encoding="utf-8"))
    assert doc["git_dirty"] is True and doc["git_dirty_study_dir"] is False


def test_a_synthetic_run_ignores_a_dirty_study_directory(monkeypatch, synthetic_files, tmp_path):
    monkeypatch.setattr(report, "_run_git", fake_git(dirty=" M x.py\n", study_dirty=" M x.py\n"))

    assert run_backtest.main(_args(synthetic_files, "--out-dir", str(tmp_path / "out")), params=SMALL) == 0


def test_the_dry_run_does_not_need_git(monkeypatch, synthetic_files, tmp_path):
    monkeypatch.setattr(report, "_run_git", lambda *a, **k: pytest.fail("git consulted in a dry run"))

    assert run_backtest.main(_args(synthetic_files, "--dry-run")) == 0


def test_the_default_output_directory_is_the_study_outputs_folder():
    assert run_backtest.DEFAULT_OUT_DIR.name == "outputs"
    assert run_backtest.DEFAULT_OUT_DIR.parent == run_backtest.STUDY_ROOT
