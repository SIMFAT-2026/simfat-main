"""Evaluation metrics (PREREGISTRATION.md Section 9), numpy only.

- ``average_precision``: AUC-PR, computed as average precision (the
  non-interpolated step-function definition, the same as scikit-learn's
  ``average_precision_score``), with tied scores grouped so the result does
  not depend on row order.
- ``roc_auc``: Mann-Whitney U statistic, ties counted as 0.5.
- ``spearman``: Pearson correlation of average ranks (ties share the mean
  rank).
- ``brier``: mean squared error of the raw score read as a probability. The
  score is NOT calibrated, so this is a secondary diagnostic only.
"""
from __future__ import annotations

import numpy as np


class DegenerateLabelsError(ValueError):
    """Raised when a metric is undefined for the label vector it received
    (no positives for AUC-PR, a single class for ROC AUC)."""


def _as_binary_and_scores(y, score) -> tuple[np.ndarray, np.ndarray]:
    y = np.asarray(y)
    score = np.asarray(score, dtype=float)
    if y.ndim != 1 or score.ndim != 1 or y.shape != score.shape:
        raise ValueError(f"y and score must be 1-D with equal length, got {y.shape} and {score.shape}")
    if not np.all((y == 0) | (y == 1)):
        raise ValueError("y must contain only 0 and 1")
    return y.astype(np.int64), score


def average_ranks(x) -> np.ndarray:
    """1-based ranks, tied values receive the mean of the ranks they span."""
    x = np.asarray(x, dtype=float)
    order = np.argsort(x, kind="mergesort")
    sorted_x = x[order]
    n = len(x)
    ranks_sorted = np.empty(n, dtype=float)
    # Boundaries of runs of equal values in sorted order.
    boundaries = np.flatnonzero(np.diff(sorted_x) != 0) + 1
    starts = np.concatenate(([0], boundaries))
    ends = np.concatenate((boundaries, [n]))
    for start, end in zip(starts, ends):
        ranks_sorted[start:end] = (start + 1 + end) / 2.0
    ranks = np.empty(n, dtype=float)
    ranks[order] = ranks_sorted
    return ranks


def average_precision(y, score) -> float:
    """Non-interpolated average precision: sum over distinct score
    thresholds of ``(R_k - R_{k-1}) * P_k``. Tied scores are one threshold."""
    y, score = _as_binary_and_scores(y, score)
    n_pos = int(y.sum())
    if n_pos == 0:
        raise DegenerateLabelsError("average precision is undefined without positives")
    order = np.argsort(-score, kind="mergesort")
    s_sorted = score[order]
    y_sorted = y[order]
    # Index of the last element of every run of equal scores.
    last_of_group = np.concatenate((np.flatnonzero(np.diff(s_sorted) != 0), [len(s_sorted) - 1]))
    tp = np.cumsum(y_sorted)[last_of_group]
    seen = last_of_group + 1
    precision = tp / seen
    recall = tp / n_pos
    recall_prev = np.concatenate(([0.0], recall[:-1]))
    return float(np.sum((recall - recall_prev) * precision))


def roc_auc(y, score) -> float:
    """ROC AUC as the Mann-Whitney U statistic over positive/negative pairs,
    ties counted as 0.5."""
    y, score = _as_binary_and_scores(y, score)
    n_pos = int(y.sum())
    n_neg = len(y) - n_pos
    if n_pos == 0 or n_neg == 0:
        raise DegenerateLabelsError("ROC AUC needs at least one positive and one negative")
    ranks = average_ranks(score)
    u = ranks[y == 1].sum() - n_pos * (n_pos + 1) / 2.0
    return float(u / (n_pos * n_neg))


def spearman(x, y) -> float:
    """Spearman rank correlation; NaN when either side is constant."""
    x = np.asarray(x, dtype=float)
    y = np.asarray(y, dtype=float)
    if x.shape != y.shape or x.ndim != 1:
        raise ValueError("x and y must be 1-D with equal length")
    rx = average_ranks(x) - (len(x) + 1) / 2.0
    ry = average_ranks(y) - (len(y) + 1) / 2.0
    denom = np.sqrt(np.sum(rx * rx) * np.sum(ry * ry))
    if denom == 0.0:
        return float("nan")
    return float(np.sum(rx * ry) / denom)


def brier(y, score) -> float:
    """Mean squared difference between the raw score and the 0/1 label."""
    y, score = _as_binary_and_scores(y, score)
    return float(np.mean((score - y) ** 2))
