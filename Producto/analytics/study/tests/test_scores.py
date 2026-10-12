"""scores.py: the blended season score and the proof that the clamp never binds.

SYNTHETIC ONLY: no real study data is read here.
"""
import numpy as np
import pytest

from study import config, scores
from study.season_fwi import W_FWI_STD

WEIGHTS = config.PARAMETERS.all_weights


def _clamp01(x):
    return np.minimum(1.0, np.maximum(0.0, x))


def test_blend_at_zero_weight_is_the_dynamic_score():
    sdyn = np.array([0.0, 0.2, 0.52])
    smap = np.array([1.0, 0.5, 0.0])
    assert np.allclose(scores.blend(sdyn, smap, 0.0), sdyn)


def test_blend_hand_computed():
    # (1 - .25) * .4 + .25 * .8 = .3 + .2 = .5
    assert scores.blend(np.array([0.4]), np.array([0.8]), 0.25)[0] == pytest.approx(0.5)


def test_mean_of_daily_clamped_blend_equals_blend_of_the_mean():
    # PROOF BY CONSTRUCTION, for the contract's bounds (S_dynamic in
    # [0, 0.52], S_mapbiomas in [0, 1]): clamp((1-w) s + w m) never binds, so
    # mean_day(clamp(...)) == (1-w) mean_day(s) + w m exactly.
    rng = np.random.default_rng(11)
    for wm in WEIGHTS:
        for _ in range(50):
            daily = rng.uniform(0.0, W_FWI_STD, size=121)
            smap = rng.uniform(0.0, 1.0)
            via_daily = np.mean(_clamp01((1 - wm) * daily + wm * smap))
            via_mean = scores.blend(np.array([daily.mean()]), np.array([smap]), wm)[0]
            assert via_daily == pytest.approx(via_mean, abs=1e-12)


def test_the_largest_possible_blend_never_exceeds_one():
    # sup of (1-w)*0.52 + w*1 over w in [0, 1] is 1 (at w = 1): the clamp's
    # upper arm cannot bind for any w <= 1, and the lower arm cannot bind
    # because every term is non-negative.
    for wm in np.linspace(0.0, 1.0, 101):
        assert (1 - wm) * W_FWI_STD + wm * 1.0 <= 1.0 + 1e-15


def test_identity_would_fail_if_the_clamp_could_bind_and_the_runtime_check_rejects_it():
    # Outside the contract's bounds the daily clamp binds and the identity
    # breaks, which is exactly why the bounds are asserted at runtime.
    daily = np.array([2.0, 0.0])  # not a legal S_dynamic
    smap = 1.0
    via_daily = np.mean(_clamp01(0.5 * daily + 0.5 * smap))
    assert via_daily != pytest.approx(0.5 * daily.mean() + 0.5 * smap)
    with pytest.raises(scores.ScoreBoundsError):
        scores.blend(np.array([daily.mean()]), np.array([smap]), 0.5)


@pytest.mark.parametrize(
    "sdyn, smap",
    [
        (np.array([-0.01]), np.array([0.5])),
        (np.array([0.53]), np.array([0.5])),
        (np.array([0.3]), np.array([-0.01])),
        (np.array([0.3]), np.array([1.01])),
        (np.array([np.nan]), np.array([0.5])),
    ],
)
def test_blend_rejects_inputs_outside_the_contract_bounds(sdyn, smap):
    with pytest.raises(scores.ScoreBoundsError):
        scores.blend(sdyn, smap, 0.25)


def test_blend_rejects_weights_outside_zero_one():
    with pytest.raises(ValueError):
        scores.blend(np.array([0.3]), np.array([0.5]), 1.5)


def test_blend_accepts_the_exact_upper_bounds():
    out = scores.blend(np.array([W_FWI_STD]), np.array([1.0]), 0.5)
    assert out[0] == pytest.approx(0.76)


def test_scores_by_wm_returns_one_array_per_weight_with_baseline_first():
    sdyn = np.array([0.1, 0.4])
    smap = np.array([0.9, 0.2])
    out = scores.scores_by_wm(sdyn, smap, WEIGHTS)
    assert list(out) == list(WEIGHTS)
    assert np.allclose(out[0.0], sdyn)
    assert np.allclose(out[0.5], 0.5 * sdyn + 0.5 * smap)
