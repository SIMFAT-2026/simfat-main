"""bootstrap.py: comuna-clustered, paired, seeded percentile bootstrap.

SYNTHETIC ONLY: no real study data is read here.
"""
import numpy as np
import pytest

from study import bootstrap, metrics


def _toy(n_clusters=20, per_cluster=3, seed=0):
    rng = np.random.default_rng(seed)
    clusters = np.repeat(np.arange(n_clusters), per_cluster).astype(str)
    y = (rng.random(n_clusters * per_cluster) < 0.5).astype(int)
    y[0], y[1] = 1, 0  # both classes always present
    score = rng.random(n_clusters * per_cluster)
    return y, clusters, score


def test_same_seed_gives_identical_resamples_and_values():
    y, clusters, score = _toy()
    ev = lambda idx: {"ap": metrics.average_precision(y[idx], score[idx])}
    a = bootstrap.cluster_bootstrap(y, clusters, ev, B=200, seed=123)
    b = bootstrap.cluster_bootstrap(y, clusters, ev, B=200, seed=123)
    assert np.array_equal(a.values["ap"], b.values["ap"])
    assert a.n_redrawn == b.n_redrawn
    ca = bootstrap.percentile_ci(a.values["ap"])
    cb = bootstrap.percentile_ci(b.values["ap"])
    assert ca == cb


def test_different_seed_gives_different_resamples():
    y, clusters, score = _toy()
    ev = lambda idx: {"ap": metrics.average_precision(y[idx], score[idx])}
    a = bootstrap.cluster_bootstrap(y, clusters, ev, B=200, seed=1)
    b = bootstrap.cluster_bootstrap(y, clusters, ev, B=200, seed=2)
    assert not np.array_equal(a.values["ap"], b.values["ap"])


def test_returns_exactly_b_resamples():
    y, clusters, score = _toy()
    ev = lambda idx: {"ap": metrics.average_precision(y[idx], score[idx])}
    res = bootstrap.cluster_bootstrap(y, clusters, ev, B=77, seed=5)
    assert len(res.values["ap"]) == 77


def test_resamples_are_made_of_whole_clusters_drawn_with_replacement():
    y, clusters, _ = _toy(n_clusters=10, per_cluster=4)
    seen = []

    def ev(idx):
        seen.append(idx.copy())
        return {"n": float(len(idx))}

    bootstrap.cluster_bootstrap(y, clusters, ev, B=100, seed=9)
    repeated_any = False
    for idx in seen:
        assert len(idx) == len(y)  # 10 clusters x 4 rows, same cluster count as the data
        per_row = np.bincount(idx, minlength=len(y))
        for c in np.unique(clusters):
            rows = np.flatnonzero(clusters == c)
            # Every row of a comuna is drawn the same number of times: seasons travel together.
            assert len(set(per_row[rows])) == 1
            repeated_any |= per_row[rows][0] > 1
    assert repeated_any  # sampling is with replacement


def test_resample_count_of_clusters_equals_number_of_clusters():
    y, clusters, _ = _toy(n_clusters=7, per_cluster=2)
    sizes = []
    bootstrap.cluster_bootstrap(y, clusters, lambda idx: sizes.append(len(idx)) or {"n": 0.0}, B=30, seed=3)
    assert set(sizes) == {14}


def test_unequal_cluster_sizes_are_supported():
    clusters = np.array(["a", "a", "a", "b", "c", "c"])
    y = np.array([1, 0, 1, 0, 1, 0])
    lengths = []
    bootstrap.cluster_bootstrap(y, clusters, lambda idx: lengths.append(len(idx)) or {"n": 0.0}, B=50, seed=4)
    assert min(lengths) >= 3 and max(lengths) <= 9


def test_paired_arms_see_identical_indices_so_a_clone_has_zero_difference_everywhere():
    y, clusters, score = _toy()
    arms = {0.0: score, 0.5: score.copy()}

    def ev(idx):
        return {f"ap_{w}": metrics.average_precision(y[idx], s[idx]) for w, s in arms.items()}

    res = bootstrap.cluster_bootstrap(y, clusters, ev, B=150, seed=8)
    diff = res.values["ap_0.5"] - res.values["ap_0.0"]
    assert np.all(diff == 0.0)
    assert bootstrap.percentile_ci(diff)[:2] == (0.0, 0.0)


def test_paired_difference_uses_the_same_rows_for_both_arms():
    # candidate = monotone transform of baseline -> identical ranking on any common rows
    y, clusters, score = _toy(seed=2)

    def ev(idx):
        return {"base": metrics.average_precision(y[idx], score[idx]),
                "cand": metrics.average_precision(y[idx], 3 * score[idx] + 1)}

    res = bootstrap.cluster_bootstrap(y, clusters, ev, B=120, seed=6)
    assert np.allclose(res.values["base"], res.values["cand"])


def test_degenerate_resamples_are_redrawn_and_counted_without_changing_b():
    # 4 clusters, only the first holds a positive: a resample misses it with
    # probability (3/4)^4 = 0.316, so a plain bootstrap would be degenerate.
    clusters = np.repeat(np.arange(4), 2).astype(str)
    y = np.array([1, 0, 0, 0, 0, 0, 0, 0])
    kept = []

    def ev(idx):
        kept.append(y[idx].copy())
        return {"n_pos": float(y[idx].sum())}

    res = bootstrap.cluster_bootstrap(y, clusters, ev, B=500, seed=10)
    assert len(res.values["n_pos"]) == 500
    assert all(v.sum() > 0 for v in kept)  # no positive-free resample ever reaches evaluate
    # Expected redraws ~ B * p / (1 - p) = 500 * .316 / .684 ~ 231
    assert 120 < res.n_redrawn < 380
    again = bootstrap.cluster_bootstrap(y, clusters, ev, B=500, seed=10)
    assert again.n_redrawn == res.n_redrawn  # the rule is deterministic given the seed


def test_resamples_without_negatives_are_kept_not_redrawn():
    # Only the first cluster holds a negative. A resample that misses it has no
    # negatives (ROC AUC undefined) but AUC-PR is perfectly defined, so redrawing
    # it would silently condition the interval on the presence of a negative.
    clusters = np.repeat(np.arange(4), 2).astype(str)
    y = np.array([0, 0, 1, 1, 1, 1, 1, 1])
    n_neg = []

    def ev(idx):
        n_neg.append(int((y[idx] == 0).sum()))
        return {"n_neg": float(n_neg[-1])}

    res = bootstrap.cluster_bootstrap(y, clusters, ev, B=400, seed=11)
    assert res.n_redrawn == 0
    assert len(n_neg) == 400
    assert 0 in n_neg  # some kept resamples hold no negatives at all


def test_data_without_positives_cannot_be_bootstrapped():
    clusters = np.array(["a", "b"])
    with pytest.raises(bootstrap.BootstrapError):
        bootstrap.cluster_bootstrap(np.array([0, 0]), clusters, lambda idx: {"x": 0.0}, B=10, seed=1)


def test_data_without_negatives_can_still_be_bootstrapped_for_auc_pr():
    clusters = np.array(["a", "b"])
    res = bootstrap.cluster_bootstrap(np.array([1, 1]), clusters, lambda idx: {"x": 1.0}, B=10, seed=1)
    assert len(res.values["x"]) == 10 and res.n_redrawn == 0


def test_redraw_budget_is_enforced():
    clusters = np.repeat(np.arange(4), 2).astype(str)
    y = np.array([1, 0, 0, 0, 0, 0, 0, 0])
    with pytest.raises(bootstrap.BootstrapError, match="without positives"):
        bootstrap.cluster_bootstrap(y, clusters, lambda idx: {"x": 0.0}, B=300, seed=10, max_attempts=300)


def test_percentile_ci_uses_the_2_5_and_97_5_percentiles():
    values = np.arange(0, 1001, dtype=float)  # linear interpolation: exact 25 and 975
    lo, hi, n_valid = bootstrap.percentile_ci(values, level=0.95)
    assert (lo, hi, n_valid) == (25.0, 975.0, 1001)


def test_percentile_ci_ignores_nan_and_reports_how_many_were_valid():
    values = np.array([np.nan, 1.0, 2.0, 3.0, np.nan])
    lo, hi, n_valid = bootstrap.percentile_ci(values)
    assert n_valid == 3 and 1.0 <= lo <= hi <= 3.0


def test_percentile_ci_of_all_nan_is_nan():
    lo, hi, n_valid = bootstrap.percentile_ci(np.array([np.nan, np.nan]))
    assert np.isnan(lo) and np.isnan(hi) and n_valid == 0
