"""config.py: pre-registration integrity gate.

Every study entry point must call ``verify_preregistration`` and abort on a
sha256 mismatch (PREREGISTRATION.md Section 17). Fixtures here are
self-contained (not the repo's real PREREGISTRATION.md) so the test does not
depend on that file's content ever staying byte-identical.
"""
import hashlib

import pytest

from study import config


def _write_pair(tmp_path, md_bytes, recorded_sha256, *, commit="deadbeef"):
    (tmp_path / "PREREGISTRATION.md").write_bytes(md_bytes)
    (tmp_path / "PREREGISTRATION.lock.json").write_text(
        f'{{"sha256": "{recorded_sha256}", "commit": "{commit}"}}', encoding="utf-8"
    )


def test_verify_preregistration_passes_when_hash_matches(tmp_path):
    content = b"# Pre-Registration\n\nSome body text.\n"
    sha = hashlib.sha256(content).hexdigest()
    _write_pair(tmp_path, content, sha)

    lock = config.verify_preregistration(tmp_path)

    assert lock["sha256"] == sha
    assert lock["commit"] == "deadbeef"


def test_verify_preregistration_normalizes_crlf_to_lf_before_hashing(tmp_path):
    # The working-tree copy may have CRLF line endings (Windows checkout
    # without eol=lf enforcement in the local git config), but the hash was
    # recorded against the git-stored LF content. The CRLF-affected bytes on
    # disk must still match once normalized.
    lf_content = b"line one\nline two\n"
    crlf_content = lf_content.replace(b"\n", b"\r\n")
    sha_of_lf = hashlib.sha256(lf_content).hexdigest()
    _write_pair(tmp_path, crlf_content, sha_of_lf)

    lock = config.verify_preregistration(tmp_path)

    assert lock["sha256"] == sha_of_lf


def test_verify_preregistration_raises_on_mismatch(tmp_path):
    content = b"# Pre-Registration\n\nOriginal body.\n"
    wrong_sha = hashlib.sha256(b"something else entirely\n").hexdigest()
    _write_pair(tmp_path, content, wrong_sha)

    with pytest.raises(config.PreregistrationIntegrityError, match="sha256 mismatch"):
        config.verify_preregistration(tmp_path)


def test_verify_preregistration_raises_when_lock_file_missing(tmp_path):
    (tmp_path / "PREREGISTRATION.md").write_bytes(b"body\n")

    with pytest.raises(FileNotFoundError):
        config.verify_preregistration(tmp_path)


def test_study_root_finds_directory_holding_the_lock_file(tmp_path):
    _write_pair(tmp_path, b"body\n", hashlib.sha256(b"body\n").hexdigest())
    nested = tmp_path / "src" / "study"
    nested.mkdir(parents=True)

    found = config.study_root(nested)

    assert found == tmp_path


# ---------------------------------------------------------------- study parameters
# S4b2: grid, delta, seed and B live in ONE place (config.StudyParameters) and
# are cross-checked here against the text of the real contract, so a typo in
# the constants cannot silently diverge from PREREGISTRATION.md.


def _real_contract_text():
    return (config.study_root() / "PREREGISTRATION.md").read_text(encoding="utf-8")


def test_load_parameters_verifies_the_preregistration_first(tmp_path):
    _write_pair(tmp_path, b"body\n", hashlib.sha256(b"something else\n").hexdigest())

    with pytest.raises(config.PreregistrationIntegrityError):
        config.load_parameters(tmp_path)


def test_load_parameters_returns_params_and_the_lock_for_the_real_contract():
    params, lock = config.load_parameters()

    assert isinstance(params, config.StudyParameters)
    assert lock["sha256"] == "541b92660db09d53d3f82217a6b23f4981ccdfb0b168903cd4df4a37cc2a4b4b"


def test_parameters_match_the_numbers_written_in_the_contract():
    p = config.PARAMETERS
    text = _real_contract_text()

    assert p.grid == (0.10, 0.25, 0.35, 0.50)
    assert "`wM` in `{0.10, 0.25, 0.35, 0.50}`" in text
    assert p.decision_sequence == (0.50, 0.35, 0.25, 0.10)
    assert "`0.50 → 0.35 → 0.25 → 0.10`" in text
    assert p.baseline_wm == 0.0
    assert p.delta == 0.02
    assert "`delta = 0.02`" in text
    assert p.n_boot == 2000 and "`B = 2000`" in text
    assert p.seed == 20261027 and "seed `20261027`" in text
    assert p.n_firms_primary == 3 and "`N_FIRMS = 3`" in text
    assert p.n_firms_sensitivities == (1, 5)
    assert p.prevalence_warning == 0.05 and "prevalence `< 5%`" in text
    assert p.seasons == tuple(range(2017, 2026))
    assert p.ci_level == 0.95


def test_decision_sequence_is_the_grid_in_descending_order():
    p = config.PARAMETERS
    assert p.decision_sequence == tuple(sorted(p.grid, reverse=True))
    assert p.all_weights == (0.0,) + tuple(sorted(p.grid))


def test_decision_sequence_that_disagrees_with_the_grid_is_rejected_at_construction():
    with pytest.raises(ValueError, match="decision_sequence"):
        config.StudyParameters(decision_sequence=(0.50, 0.25, 0.35, 0.10))
    with pytest.raises(ValueError, match="decision_sequence"):
        config.StudyParameters(grid=(0.10, 0.20), decision_sequence=(0.50, 0.35, 0.25, 0.10))
    config.StudyParameters(grid=(0.10, 0.20), decision_sequence=(0.20, 0.10))  # consistent: accepted


def test_expected_comunas_match_the_contract():
    assert config.PARAMETERS.n_comunas == 86
    assert "86 monitored comunas" in _real_contract_text()
