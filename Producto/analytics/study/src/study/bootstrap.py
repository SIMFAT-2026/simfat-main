"""Cluster bootstrap by comuna (PREREGISTRATION.md Section 10).

Resamples the comunas with replacement; every sampled comuna brings ALL its
seasons (they are repeated measures of one place, not independent rows). The
number of comunas drawn equals the number of comunas in the data. For every
resample the caller's ``evaluate(idx)`` receives ONE index array and must
score every arm (baseline and each candidate ``wM``) on exactly those rows,
which is what makes differences paired.

Implementation choices left open by the contract (see DEVIATIONS.md):
- RNG: ``numpy.random.default_rng(seed)`` (PCG64); clusters are indexed in
  sorted order so a seed reproduces the same resamples on any machine.
- Degenerate resamples (no positives or no negatives among the sampled rows;
  AUC-PR is undefined without positives and ROC AUC without both classes) are
  REDRAWN, not kept and not skipped, so exactly ``B`` usable resamples are
  returned. The number of discarded draws is reported as ``n_redrawn``.
- Confidence intervals use the percentile method (``numpy.percentile``,
  linear interpolation).
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Mapping

import numpy as np


class BootstrapError(RuntimeError):
    """Raised when the bootstrap cannot produce B non-degenerate resamples."""


@dataclass(frozen=True)
class BootstrapResult:
    values: Mapping[str, np.ndarray]  # name -> array of length B
    n_redrawn: int  # degenerate draws discarded to reach B


def cluster_bootstrap(
    y,
    clusters,
    evaluate: Callable[[np.ndarray], Mapping[str, float]],
    *,
    B: int,
    seed: int,
    max_attempts: int | None = None,
) -> BootstrapResult:
    y = np.asarray(y)
    clusters = np.asarray(clusters)
    if y.shape != clusters.shape or y.ndim != 1:
        raise ValueError("y and clusters must be 1-D with equal length")
    if y.sum() == 0 or y.sum() == len(y):
        raise BootstrapError("the data holds a single class; no resample can be non-degenerate")
    max_attempts = max_attempts if max_attempts is not None else 100 * B

    unique_clusters, inverse = np.unique(clusters, return_inverse=True)
    rows_of = [np.flatnonzero(inverse == k) for k in range(len(unique_clusters))]
    n_clusters = len(unique_clusters)

    rng = np.random.default_rng(seed)
    collected: dict[str, list[float]] = {}
    n_redrawn = 0
    attempts = 0
    n_done = 0
    while n_done < B:
        if attempts >= max_attempts:
            raise BootstrapError(
                f"gave up after {attempts} draws: too many degenerate resamples "
                f"({n_redrawn} discarded, {n_done} of {B} kept)"
            )
        attempts += 1
        picks = rng.integers(0, n_clusters, size=n_clusters)
        idx = np.concatenate([rows_of[k] for k in picks])
        n_pos = int(y[idx].sum())
        if n_pos == 0 or n_pos == len(idx):
            n_redrawn += 1
            continue
        for name, value in evaluate(idx).items():
            collected.setdefault(name, []).append(float(value))
        n_done += 1

    return BootstrapResult(
        values={k: np.asarray(v, dtype=float) for k, v in collected.items()},
        n_redrawn=n_redrawn,
    )


def percentile_ci(values, level: float = 0.95) -> tuple[float, float, int]:
    """Percentile CI ``(lower, upper, n_valid)``; NaN resamples are ignored
    and counted out of ``n_valid``."""
    values = np.asarray(values, dtype=float)
    valid = values[~np.isnan(values)]
    if len(valid) == 0:
        return float("nan"), float("nan"), 0
    tail = (100.0 - 100.0 * level) / 2.0  # 2.5 for a 95% interval, exactly
    lo, hi = np.percentile(valid, [tail, 100.0 - tail])
    return float(lo), float(hi), int(len(valid))
