"""Comuna-season prediction (PREREGISTRATION.md Sections 8.1 and 8.2).

The pre-registered prediction is the SEASON MEAN of the daily blended score
``clamp((1 - wM) * S_dynamic(day) + wM * S_mapbiomas)``. ``S_mapbiomas`` is a
per-comuna-season constant and ``S_dynamic(day) = 0.52 * fwiNorm`` lies in
``[0, 0.52]``, so the blend is a convex combination of values in ``[0, 1]``
(its maximum is ``(1 - wM) * 0.52 + wM <= 1``) and the clamp can never bind.
Then the mean of the daily blend equals ``(1 - wM) * mean(S_dynamic) + wM *
S_mapbiomas`` exactly, and the season mean ``sdyn_mean`` already stored in
``season_fwi.csv`` is all that is needed.

The identity only holds inside those bounds, so they are asserted at runtime
instead of being trusted.
"""
from __future__ import annotations

from typing import Iterable, Mapping

import numpy as np

from study.season_fwi import W_FWI_STD

# Float slack for values that were produced by summing/averaging in floating point.
_EPS = 1e-12


class ScoreBoundsError(ValueError):
    """Raised when an input leaves the range that makes the clamp-free
    identity valid (S_dynamic in [0, 0.52], S_mapbiomas in [0, 1])."""


def _check_bounds(sdyn: np.ndarray, smap: np.ndarray) -> None:
    if not (np.all(np.isfinite(sdyn)) and np.all(np.isfinite(smap))):
        raise ScoreBoundsError("S_dynamic and S_mapbiomas must be finite")
    if sdyn.min() < -_EPS or sdyn.max() > W_FWI_STD + _EPS:
        raise ScoreBoundsError(
            f"S_dynamic must lie in [0, {W_FWI_STD}], got [{sdyn.min()}, {sdyn.max()}]"
        )
    if smap.min() < -_EPS or smap.max() > 1.0 + _EPS:
        raise ScoreBoundsError(f"S_mapbiomas must lie in [0, 1], got [{smap.min()}, {smap.max()}]")


def blend(sdyn_mean, s_mapbiomas, wm: float) -> np.ndarray:
    """Season-level prediction for blend weight ``wm``."""
    if not 0.0 <= wm <= 1.0:
        raise ValueError(f"wM must be in [0, 1], got {wm}")
    sdyn = np.asarray(sdyn_mean, dtype=float)
    smap = np.asarray(s_mapbiomas, dtype=float)
    _check_bounds(sdyn, smap)
    out = (1.0 - wm) * sdyn + wm * smap
    # Cannot fire for in-bounds inputs; guards the identity, not the data.
    if out.min() < -_EPS or out.max() > 1.0 + _EPS:
        raise ScoreBoundsError("blended score left [0, 1]: the clamp would bind")
    return out


def scores_by_wm(sdyn_mean, s_mapbiomas, weights: Iterable[float]) -> Mapping[float, np.ndarray]:
    """One prediction array per weight, in the order given."""
    return {float(w): blend(sdyn_mean, s_mapbiomas, float(w)) for w in weights}
