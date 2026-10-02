"""Pre-registration integrity gate (PREREGISTRATION.md Section 17).

The pre-registration is the CONTRACT: ``decision_rule.py`` and every feature
builder must read fixed parameters (margin, season set, seed, grid) only
after confirming the live ``PREREGISTRATION.md`` on disk still matches the
sha256 recorded in ``PREREGISTRATION.lock.json`` at commit time. A mismatch
means the file was edited after pre-registration -- the study must abort
loudly rather than silently run against a changed contract.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

PREREGISTRATION_FILENAME = "PREREGISTRATION.md"
LOCK_FILENAME = "PREREGISTRATION.lock.json"


class PreregistrationIntegrityError(RuntimeError):
    """Raised when PREREGISTRATION.md's live content does not match the lock file."""


def _normalize_to_lf(raw_bytes: bytes) -> bytes:
    """Normalizes CRLF to LF.

    PREREGISTRATION.lock.json records the sha256 of the file as git stores
    it (LF line endings, pinned by .gitattributes' ``text eol=lf``). A
    working copy checked out without that normalization in effect (or edited
    on Windows) can have CRLF bytes on disk; normalizing here keeps the
    comparison platform-independent instead of making every contributor's
    git config part of the contract.
    """
    return raw_bytes.replace(b"\r\n", b"\n")


def study_root(start: Path | None = None) -> Path:
    """Finds the ``Producto/analytics/study`` directory by searching upward
    from ``start`` (default: this file's own directory) for
    ``PREREGISTRATION.lock.json``."""
    here = Path(start) if start is not None else Path(__file__).resolve().parent
    for candidate in (here, *here.parents):
        if (candidate / LOCK_FILENAME).exists():
            return candidate
    raise FileNotFoundError(
        f"Could not find {LOCK_FILENAME} searching upward from {here}"
    )


def load_lock(root: Path | None = None) -> dict:
    root = root if root is not None else study_root()
    lock_path = Path(root) / LOCK_FILENAME
    return json.loads(lock_path.read_text(encoding="utf-8"))


def verify_preregistration(root: Path | None = None) -> dict:
    """Recomputes PREREGISTRATION.md's sha256 (LF-normalized) and compares it
    against the recorded value in PREREGISTRATION.lock.json.

    Returns the lock dict on success. Raises ``PreregistrationIntegrityError``
    on mismatch, and ``FileNotFoundError`` if either file is missing.
    """
    root = Path(root) if root is not None else study_root()
    lock = load_lock(root)
    md_path = root / PREREGISTRATION_FILENAME
    raw = md_path.read_bytes()
    normalized = _normalize_to_lf(raw)
    actual_sha256 = hashlib.sha256(normalized).hexdigest()
    expected_sha256 = lock["sha256"]
    if actual_sha256 != expected_sha256:
        raise PreregistrationIntegrityError(
            f"{PREREGISTRATION_FILENAME} sha256 mismatch: expected "
            f"{expected_sha256}, got {actual_sha256}. The pre-registration may "
            f"have been modified after commit {lock.get('commit', '?')}. "
            "Aborting per Section 17 of the pre-registration -- any change to "
            "this file must be recorded as a numbered, dated deviation in its "
            "own change log, never silently reconciled here."
        )
    return lock
