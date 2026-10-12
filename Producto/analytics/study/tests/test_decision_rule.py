"""decision_rule.py: the pre-registered rule of Section 10 and the claim
rule of Section 12, on hand-built confidence intervals.

SYNTHETIC ONLY: no real study data is read here.
"""
import hashlib

import pytest

from study import config, decision_rule

SEQ = (0.50, 0.35, 0.25, 0.10)


def _ci(lo, hi=None):
    return (lo, lo + 0.05 if hi is None else hi)


def _lowers(l50, l35, l25, l10):
    return {0.50: l50, 0.35: l35, 0.25: l25, 0.10: l10}


def test_all_pass_ships_the_largest_weight():
    assert decision_rule.decide(_lowers(0.01, 0.0, -0.01, -0.019)).shipped_wm == 0.50


def test_lower_bound_exactly_minus_delta_passes():
    assert decision_rule.decide(_lowers(-0.02, -0.5, -0.5, -0.5)).shipped_wm == 0.50


def test_lower_bound_just_below_minus_delta_fails():
    assert decision_rule.decide(_lowers(-0.0200001, -0.5, -0.5, -0.5)).shipped_wm == 0.0


def test_all_fail_ships_the_baseline():
    out = decision_rule.decide(_lowers(-0.03, -0.04, -0.05, -0.06))
    assert out.shipped_wm == 0.0
    assert out.qualifying == ()


def test_only_some_pass_ships_the_largest_of_those_in_sequence_order():
    # 0.50 and 0.35 fail, 0.25 and 0.10 pass -> 0.25
    out = decision_rule.decide(_lowers(-0.03, -0.021, -0.01, 0.0))
    assert out.shipped_wm == 0.25
    assert out.qualifying == (0.25, 0.10)


def test_a_smaller_weight_passing_does_not_pull_a_failing_larger_one_in():
    # not monotone: 0.35 fails although 0.25 passes; 0.50 passes -> still 0.50
    assert decision_rule.decide(_lowers(0.0, -0.1, 0.0, 0.0)).shipped_wm == 0.50
    # 0.50 fails, 0.35 fails, 0.25 passes
    assert decision_rule.decide(_lowers(-0.1, -0.1, 0.0, -0.1)).shipped_wm == 0.25


def test_only_the_smallest_weight_passes():
    assert decision_rule.decide(_lowers(-0.1, -0.1, -0.1, -0.02)).shipped_wm == 0.10


def test_missing_or_nan_bounds_are_errors_not_silent_failures():
    with pytest.raises(ValueError):
        decision_rule.decide({0.50: 0.0, 0.35: 0.0, 0.25: 0.0})
    with pytest.raises(ValueError):
        decision_rule.decide(_lowers(float("nan"), 0.0, 0.0, 0.0))


def test_decide_reads_delta_and_sequence_from_the_config_module():
    out = decision_rule.decide(_lowers(0.0, 0.0, 0.0, 0.0))
    assert out.delta == config.PARAMETERS.delta
    assert out.sequence == config.PARAMETERS.decision_sequence == SEQ


def test_decide_aborts_when_the_preregistration_hash_does_not_match(tmp_path):
    (tmp_path / "PREREGISTRATION.md").write_bytes(b"edited\n")
    (tmp_path / "PREREGISTRATION.lock.json").write_text(
        '{"sha256": "%s", "commit": "x"}' % hashlib.sha256(b"original\n").hexdigest(),
        encoding="utf-8",
    )
    with pytest.raises(config.PreregistrationIntegrityError):
        decision_rule.decide(_lowers(0.0, 0.0, 0.0, 0.0), root=tmp_path)


# ---------------------------------------------------------------- claim rule (Section 12)


def test_claim_improves_only_if_the_whole_ci_is_above_zero():
    assert decision_rule.claim(0.50, (0.001, 0.05)) == "improves"


def test_claim_is_non_inferior_when_the_ci_touches_or_crosses_zero():
    assert decision_rule.claim(0.35, (0.0, 0.05)) == "non-inferior"
    assert decision_rule.claim(0.35, (-0.015, 0.05)) == "non-inferior"
    assert decision_rule.claim(0.10, (-0.02, -0.001)) == "non-inferior"


def test_claim_for_the_baseline_is_no_blend_shipped():
    assert decision_rule.claim(0.0, None) == "baseline"


def test_apply_rule_combines_decision_and_claim():
    cis = {0.50: _ci(-0.03), 0.35: _ci(0.004, 0.06), 0.25: _ci(0.0), 0.10: _ci(0.0)}
    out = decision_rule.apply_rule(cis)
    assert out.shipped_wm == 0.35
    assert out.claim == "improves"
    assert out.lowers[0.35] == 0.004


def test_apply_rule_on_failure_ships_baseline_with_baseline_claim():
    cis = {w: _ci(-0.1) for w in SEQ}
    out = decision_rule.apply_rule(cis)
    assert (out.shipped_wm, out.claim) == (0.0, "baseline")
