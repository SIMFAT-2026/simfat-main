"""metrics.py: numpy-only AUC-PR (average precision), ROC AUC, Spearman, Brier.

Every expected value below is computed by hand in the comment next to it, not
by running the code under test.

SYNTHETIC ONLY: no real study data is read here.
"""
import math

import numpy as np
import pytest

from study import metrics


# ---------------------------------------------------------------- average precision


def test_average_precision_hand_computed_untied_case():
    # Ranked: 1 (P=1/1, R=.5), 0, 1 (P=2/3, R=1), 0
    # AP = .5 * 1 + .5 * 2/3 = 5/6
    y = [1, 0, 1, 0]
    s = [0.9, 0.8, 0.7, 0.1]
    assert metrics.average_precision(y, s) == pytest.approx(5 / 6)


def test_average_precision_perfect_separation_is_one():
    y = [0, 0, 1, 1, 0, 1]
    s = [0.1, 0.2, 0.9, 0.8, 0.3, 0.7]
    assert metrics.average_precision(y, s) == pytest.approx(1.0)


def test_average_precision_constant_predictor_equals_prevalence():
    # One tie group holding everything: P = prevalence, R jumps 0 -> 1.
    y = [1, 0, 0, 0]
    s = [0.4, 0.4, 0.4, 0.4]
    assert metrics.average_precision(y, s) == pytest.approx(0.25)
    y2 = [1, 1, 1, 0, 0]
    assert metrics.average_precision(y2, [7.0] * 5) == pytest.approx(0.6)


def test_average_precision_groups_tied_scores_instead_of_using_input_order():
    # Group .9: TP=1 FP=0 -> P=1,   R=1/3 -> 1/3 * 1
    # Group .5: TP=2 FP=2 -> P=.5,  R=2/3 -> 1/3 * .5
    # Group .1: TP=3 FP=2 -> P=.6,  R=1   -> 1/3 * .6
    # AP = (1 + .5 + .6) / 3 = 0.7
    s = [0.9, 0.5, 0.5, 0.5, 0.1]
    y = [1, 1, 0, 0, 1]
    assert metrics.average_precision(y, s) == pytest.approx(0.7)


def test_average_precision_is_invariant_to_row_order_when_scores_tie():
    s = np.array([0.9, 0.5, 0.5, 0.5, 0.1])
    y = np.array([1, 1, 0, 0, 1])
    rng = np.random.default_rng(1)
    for _ in range(20):
        p = rng.permutation(len(y))
        assert metrics.average_precision(y[p], s[p]) == pytest.approx(0.7)


def test_average_precision_is_invariant_to_monotone_rescaling_of_the_score():
    rng = np.random.default_rng(7)
    y = rng.integers(0, 2, 60)
    s = rng.random(60)
    assert metrics.average_precision(y, s) == pytest.approx(
        metrics.average_precision(y, 3.0 * s + 5.0)
    )


def test_average_precision_requires_at_least_one_positive():
    with pytest.raises(metrics.DegenerateLabelsError):
        metrics.average_precision([0, 0, 0], [0.1, 0.2, 0.3])


def test_average_precision_rejects_non_binary_labels_and_shape_mismatch():
    with pytest.raises(ValueError):
        metrics.average_precision([0, 2], [0.1, 0.2])
    with pytest.raises(ValueError):
        metrics.average_precision([0, 1, 1], [0.1, 0.2])


# ---------------------------------------------------------------- ROC AUC


def test_roc_auc_hand_computed_untied_case():
    # pos .9 beats neg .8 and .1; pos .7 loses to .8, beats .1 -> 3/4
    assert metrics.roc_auc([1, 0, 1, 0], [0.9, 0.8, 0.7, 0.1]) == pytest.approx(0.75)


def test_roc_auc_counts_ties_as_half():
    assert metrics.roc_auc([1, 0], [0.5, 0.5]) == pytest.approx(0.5)
    # pos (.5, .5) vs neg (.5, .1): each pos scores .5 + 1 = 1.5 -> 3 / 4
    assert metrics.roc_auc([1, 1, 0, 0], [0.5, 0.5, 0.5, 0.1]) == pytest.approx(0.75)


def test_roc_auc_perfect_and_inverted():
    assert metrics.roc_auc([0, 0, 1, 1], [0.1, 0.2, 0.8, 0.9]) == pytest.approx(1.0)
    assert metrics.roc_auc([0, 0, 1, 1], [0.9, 0.8, 0.2, 0.1]) == pytest.approx(0.0)


def test_roc_auc_matches_brute_force_pair_count_on_random_tied_data():
    rng = np.random.default_rng(3)
    y = rng.integers(0, 2, 80)
    s = rng.integers(0, 6, 80).astype(float)  # many ties
    pos, neg = s[y == 1], s[y == 0]
    wins = sum((p > n) + 0.5 * (p == n) for p in pos for n in neg)
    assert metrics.roc_auc(y, s) == pytest.approx(wins / (len(pos) * len(neg)))


def test_roc_auc_needs_both_classes():
    with pytest.raises(metrics.DegenerateLabelsError):
        metrics.roc_auc([1, 1], [0.1, 0.2])
    with pytest.raises(metrics.DegenerateLabelsError):
        metrics.roc_auc([0, 0], [0.1, 0.2])


# ---------------------------------------------------------------- Spearman


def test_average_ranks_gives_tied_values_the_mean_rank():
    assert list(metrics.average_ranks([10, 20, 20, 30])) == [1.0, 2.5, 2.5, 4.0]
    assert list(metrics.average_ranks([5, 5, 5])) == [2.0, 2.0, 2.0]
    assert list(metrics.average_ranks([3, 1, 2])) == [3.0, 1.0, 2.0]


def test_spearman_perfect_monotone_and_reversed():
    assert metrics.spearman([1, 2, 3, 4], [10, 20, 30, 40]) == pytest.approx(1.0)
    assert metrics.spearman([1, 2, 3, 4], [4, 3, 2, 1]) == pytest.approx(-1.0)


def test_spearman_with_ties_uses_average_ranks():
    # ranks x = [1, 2.5, 2.5, 4], ranks y = [1, 2, 3, 4]
    # cov = 4.5, var x = 4.5, var y = 5 -> r = 4.5 / sqrt(22.5) = sqrt(0.9)
    assert metrics.spearman([1, 2, 2, 3], [1, 2, 3, 4]) == pytest.approx(math.sqrt(0.9))


def test_spearman_is_nan_when_one_side_is_constant():
    assert math.isnan(metrics.spearman([1, 1, 1], [1, 2, 3]))


# ---------------------------------------------------------------- Brier


def test_brier_hand_computed():
    # (.1^2 + .2^2 + .4^2 + .1^2) / 4 = .22 / 4 = .055
    assert metrics.brier([1, 0, 1, 0], [0.9, 0.2, 0.6, 0.1]) == pytest.approx(0.055)


def test_brier_perfect_and_worst():
    assert metrics.brier([1, 0], [1.0, 0.0]) == pytest.approx(0.0)
    assert metrics.brier([1, 0], [0.0, 1.0]) == pytest.approx(1.0)
