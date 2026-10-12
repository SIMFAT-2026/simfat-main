"""The pre-registered decision rule (PREREGISTRATION.md Sections 10-12).

Ship the LARGEST ``wM`` in the fixed sequence 0.50 -> 0.35 -> 0.25 -> 0.10
whose paired-bootstrap CI lower bound of ``AUC-PR(wM) - AUC-PR(0)`` is
``>= -delta``; if none qualifies, ship ``wM = 0``. The claim rule only governs
wording: a shipped ``wM > 0`` is "improves" iff the whole CI lies above 0,
otherwise "non-inferior".

Grid, delta, and sequence come from ``study.config`` (never hard-coded here),
and the pre-registration hash is verified on every call.
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping

from study import config


@dataclass(frozen=True)
class Decision:
    shipped_wm: float
    qualifying: tuple[float, ...]  # weights that passed, in sequence order
    delta: float
    sequence: tuple[float, ...]


@dataclass(frozen=True)
class RuleOutcome:
    shipped_wm: float
    claim: str
    qualifying: tuple[float, ...]
    lowers: Mapping[float, float]
    delta: float


def decide(lowers: Mapping[float, float], *, root: Path | None = None) -> Decision:
    """``lowers`` maps each grid weight to the CI lower bound of the paired
    AUC-PR difference against ``wM = 0``."""
    params, _lock = config.load_parameters(root)
    missing = [w for w in params.decision_sequence if w not in lowers]
    if missing:
        raise ValueError(f"missing CI lower bounds for wM in {missing}")
    for w in params.decision_sequence:
        if math.isnan(lowers[w]):
            raise ValueError(f"CI lower bound for wM={w} is NaN; the rule cannot be applied")
    qualifying = tuple(w for w in params.decision_sequence if lowers[w] >= -params.delta)
    shipped = qualifying[0] if qualifying else params.baseline_wm
    return Decision(shipped, qualifying, params.delta, params.decision_sequence)


def claim(shipped_wm: float, ci: tuple[float, float] | None) -> str:
    """Section 12 wording rule for the shipped weight."""
    if shipped_wm == config.PARAMETERS.baseline_wm:
        return "baseline"
    if ci is None:
        raise ValueError("a CI is required to label a shipped wM > 0")
    return "improves" if ci[0] > 0 else "non-inferior"


def apply_rule(cis: Mapping[float, tuple[float, float]], *, root: Path | None = None) -> RuleOutcome:
    """Decision plus claim from the per-weight paired-difference CIs."""
    lowers = {w: ci[0] for w, ci in cis.items()}
    d = decide(lowers, root=root)
    label = claim(d.shipped_wm, cis.get(d.shipped_wm))
    return RuleOutcome(d.shipped_wm, label, d.qualifying, lowers, d.delta)
