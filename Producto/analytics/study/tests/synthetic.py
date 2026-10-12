"""Synthetic study inputs with a known right answer. NEVER real data.

``make_inputs`` returns the three input tables as lists of string-valued dicts,
shaped exactly like the CSVs the real builders write, so every join and report
code path can be exercised without touching ``data/features``.
"""
from __future__ import annotations

import csv

import numpy as np

SEASONS = list(range(2017, 2026))


def make_inputs(scenario: str, *, n_comunas: int = 30, seed: int = 0):
    """scenario:
    - ``mb_strong``: S_dynamic is pure noise; S_mapbiomas tracks the latent
      risk that drives the target -> blending must help.
    - ``mb_harmful``: S_dynamic tracks the latent risk but has a tiny range;
      S_mapbiomas is pure noise -> any blend weight must hurt.
    """
    rng = np.random.default_rng(seed)
    comunas = [f"C{i:03d}" for i in range(n_comunas)]
    latent_c = rng.random(n_comunas)
    target, fwi, mb = [], [], []
    for ci, c in enumerate(comunas):
        for s in SEASONS:
            latent = float(np.clip(latent_c[ci] + rng.normal(0, 0.08), 0, 1))
            count = int(round(6 * latent))
            noise_dyn = float(rng.uniform(0, 0.52))
            noise_map = float(rng.random())
            if scenario == "mb_strong":
                sdyn = noise_dyn
                smap = float(np.clip(0.05 + 0.9 * latent_c[ci] + rng.normal(0, 0.03), 0, 1))
            elif scenario == "mb_harmful":
                sdyn = 0.26 + 0.052 * (latent - 0.5)
                smap = noise_map
            else:
                raise ValueError(scenario)
            for window in ("primary", "sensitivity"):
                for flt in ("type0", "notype"):
                    n = count + (1 if window == "sensitivity" else 0) + (1 if flt == "notype" else 0)
                    target.append({
                        "comunaId": c, "season": str(s), "window": window, "filter": flt,
                        "count": str(n), "y_n1": str(int(n >= 1)),
                        "y_n3": str(int(n >= 3)), "y_n5": str(int(n >= 5)),
                    })
            for window in ("standard", "sensitivity"):
                fwi.append({"comunaId": c, "season": str(s), "window": window,
                            "sdyn_mean": repr(sdyn), "fwi_mean": "5.0", "n_days": "121"})
            mb.append({"comunaId": c, "season": str(s), "s_mapbiomas": repr(smap),
                       "s_mapbiomas_log": repr(float(np.clip(smap * 0.9, 0, 1)))})
    return target, fwi, mb


def write_csv(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    return path
