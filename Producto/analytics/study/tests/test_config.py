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
