"""Strict join of the three study input tables into comuna-season rows.

The three inputs (``firms_target.csv``, ``season_fwi.csv``,
``season_mapbiomas.csv``) were built as separate files precisely so no
predictor-target relation existed before the pre-registered run. This module
is the ONE place they meet. ``validate_keys`` checks key coverage only (counts
and missing keys, never a value) and is what the entry point's dry run uses.
"""
from __future__ import annotations

import csv
import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping, Sequence

import numpy as np

from study.season_fwi import SEASONS

# Target window name -> season_fwi window name (identical date boundaries).
WINDOW_TO_FWI = {"primary": "standard", "sensitivity": "sensitivity"}
TYPE_FILTERS = ("type0", "notype")
N_THRESHOLDS = (1, 3, 5)
VARIANT_COLUMN = {"pct": "s_mapbiomas", "log": "s_mapbiomas_log"}


class JoinError(ValueError):
    """Raised when the inputs cannot be joined one-to-one."""


def read_csv_rows(path: Path) -> list[dict]:
    with open(path, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def file_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


@dataclass(frozen=True)
class Dataset:
    """Aligned arrays, one entry per comuna-season, sorted by (comuna, season)."""

    comuna: np.ndarray
    season: np.ndarray
    count: np.ndarray
    y: np.ndarray
    sdyn: np.ndarray
    smap: np.ndarray

    def __len__(self) -> int:
        return len(self.comuna)

    def subset(self, mask) -> "Dataset":
        mask = np.asarray(mask)
        return Dataset(self.comuna[mask], self.season[mask], self.count[mask],
                       self.y[mask], self.sdyn[mask], self.smap[mask])


def _index_unique(rows: Iterable[Mapping], key_fields: Sequence[str], label: str) -> dict:
    index: dict = {}
    for r in rows:
        key = tuple(r[k] for k in key_fields)
        if key in index:
            raise JoinError(f"duplicate key in {label}: {key}")
        index[key] = r
    return index


def build_dataset(
    target_rows: Sequence[Mapping],
    fwi_rows: Sequence[Mapping],
    mapbiomas_rows: Sequence[Mapping],
    *,
    n_threshold: int,
    window: str,
    type_filter: str,
    variant: str,
    seasons: Sequence[int] = SEASONS,
) -> Dataset:
    if window not in WINDOW_TO_FWI:
        raise ValueError(f"window must be one of {sorted(WINDOW_TO_FWI)}, got {window!r}")
    if type_filter not in TYPE_FILTERS:
        raise ValueError(f"type_filter must be one of {TYPE_FILTERS}, got {type_filter!r}")
    if variant not in VARIANT_COLUMN:
        raise ValueError(f"variant must be one of {sorted(VARIANT_COLUMN)}, got {variant!r}")
    if n_threshold not in N_THRESHOLDS:
        raise ValueError(f"n_threshold must be one of {N_THRESHOLDS}, got {n_threshold}")

    wanted_seasons = {str(s) for s in seasons}
    fwi_window = WINDOW_TO_FWI[window]
    fwi_index = _index_unique(fwi_rows, ("comunaId", "season", "window"), "season_fwi")
    mb_index = _index_unique(mapbiomas_rows, ("comunaId", "season"), "season_mapbiomas")
    target_index = _index_unique(target_rows, ("comunaId", "season", "window", "filter"), "firms_target")

    smap_col = VARIANT_COLUMN[variant]
    y_col = f"y_n{n_threshold}"
    records = []
    for (comuna, season, w, flt), t in target_index.items():
        if w != window or flt != type_filter or season not in wanted_seasons:
            continue
        f = fwi_index.get((comuna, season, fwi_window))
        m = mb_index.get((comuna, season))
        if f is None:
            raise JoinError(f"missing season_fwi row for comuna {comuna}, season {season}, window {fwi_window}")
        if m is None:
            raise JoinError(f"missing season_mapbiomas row for comuna {comuna}, season {season}")
        count = int(t["count"])
        y = int(count >= n_threshold)
        if y_col in t and int(t[y_col]) != y:
            raise JoinError(
                f"{y_col}={t[y_col]} disagrees with count={count} for comuna {comuna}, season {season}"
            )
        records.append((comuna, int(season), count, y, float(f["sdyn_mean"]), float(m[smap_col])))

    records.sort(key=lambda r: (r[0], r[1]))
    return Dataset(
        comuna=np.array([r[0] for r in records], dtype=object),
        season=np.array([r[1] for r in records], dtype=np.int64),
        count=np.array([r[2] for r in records], dtype=np.int64),
        y=np.array([r[3] for r in records], dtype=np.int64),
        sdyn=np.array([r[4] for r in records], dtype=float),
        smap=np.array([r[5] for r in records], dtype=float),
    )


@dataclass(frozen=True)
class KeyReport:
    """Key coverage only: counts, never a value derived from the target."""

    n_target_keys: int
    n_comunas: int
    n_seasons: int
    missing_fwi: int
    missing_mapbiomas: int

    @property
    def ok(self) -> bool:
        return self.missing_fwi == 0 and self.missing_mapbiomas == 0


def validate_keys(
    target_rows: Sequence[Mapping],
    fwi_rows: Sequence[Mapping],
    mapbiomas_rows: Sequence[Mapping],
    *,
    seasons: Sequence[int] = SEASONS,
) -> KeyReport:
    wanted = {str(s) for s in seasons}
    fwi_keys = {(r["comunaId"], r["season"], r["window"]) for r in fwi_rows}
    mb_keys = {(r["comunaId"], r["season"]) for r in mapbiomas_rows}
    target_keys = {
        (r["comunaId"], r["season"], r["window"], r["filter"])
        for r in target_rows
        if r["season"] in wanted
    }
    missing_fwi = sum(
        1 for (c, s, w, _f) in target_keys if (c, s, WINDOW_TO_FWI.get(w, w)) not in fwi_keys
    )
    missing_mb = sum(1 for (c, s, _w, _f) in target_keys if (c, s) not in mb_keys)
    return KeyReport(
        n_target_keys=len(target_keys),
        n_comunas=len({k[0] for k in target_keys}),
        n_seasons=len({k[1] for k in target_keys}),
        missing_fwi=missing_fwi,
        missing_mapbiomas=missing_mb,
    )
