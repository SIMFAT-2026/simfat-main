"""Builds data/features/season_fwi.csv from the committed noon-input table
(PREREGISTRATION.md Section 8.1, design D4).

For every comuna and every pre-registered season (``study.season_fwi.
SEASONS``, 2017..2025), runs the Van Wagner chain from Sep 1 of ``s-1`` and
aggregates both the standard (Dec 1-Mar 31) and sensitivity (Nov 1-Apr 30)
windows, then writes one CSV row per comuna-season-window.

BLINDING RULE: this script builds INPUTS only. It never loads FIRMS data
and never computes or prints any quantity relating a predictor to a
fire-activity target -- only min/max ranges and row counts for the gate
checks described in the launch prompt.

Usage (from Producto/analytics/study):

    python scripts/build_season_fwi.py
"""
from __future__ import annotations

import csv
import gzip
import sys
from datetime import date
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
STUDY_ROOT = SCRIPT_DIR.parent
SRC_DIR = STUDY_ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from study import config, season_fwi  # noqa: E402
from study.season_fwi import SeasonFeatureRow  # noqa: E402

ERA5_DIR = STUDY_ROOT / "data" / "era5"
OUTPUT_PATH = STUDY_ROOT / "data" / "features" / "season_fwi.csv"

FEATURES_HEADER = ["comunaId", "season", "window", "sdyn_mean", "fwi_mean", "n_days"]


def load_noon_inputs(csv_gz_path: Path) -> dict[str, dict[date, tuple[float, float, float, float]]]:
    """Reads the committed ``noon_inputs_*.csv.gz`` into
    ``{comunaId: {date: (tempC, rhPct, windKmh, rainMm)}}``."""
    weather: dict[str, dict[date, tuple[float, float, float, float]]] = {}
    with gzip.open(csv_gz_path, "rt", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            comuna_id = row["comunaId"]
            d = date.fromisoformat(row["date"])
            weather.setdefault(comuna_id, {})[d] = (
                float(row["tempC"]),
                float(row["rhPct"]),
                float(row["windKmh"]),
                float(row["rainMm"]),
            )
    return weather


def write_season_fwi_csv(rows: list[SeasonFeatureRow], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(FEATURES_HEADER)
        for r in rows:
            writer.writerow([r.comuna_id, r.season, r.window, r.sdyn_mean, r.fwi_mean, r.n_days])


def _find_noon_inputs_csv() -> Path:
    candidates = sorted(ERA5_DIR.glob("noon_inputs_*.csv.gz"))
    if not candidates:
        raise FileNotFoundError(
            f"No noon_inputs_*.csv.gz found under {ERA5_DIR} -- run scripts/download_era5.py first"
        )
    return candidates[-1]


def main() -> int:
    lock = config.verify_preregistration(STUDY_ROOT)
    print(f"Pre-registration integrity OK (sha256={lock['sha256'][:12]}..., commit={lock['commit'][:12]}...)")

    noon_inputs_path = _find_noon_inputs_csv()
    print(f"Reading noon inputs from {noon_inputs_path}")
    weather_by_comuna = load_noon_inputs(noon_inputs_path)
    print(f"Loaded weather for {len(weather_by_comuna)} comunas")

    all_rows: list[SeasonFeatureRow] = []
    for comuna_id in sorted(weather_by_comuna):
        weather = weather_by_comuna[comuna_id]
        for season in season_fwi.SEASONS:
            all_rows.extend(season_fwi.build_season_features(comuna_id, weather, season))

    write_season_fwi_csv(all_rows, OUTPUT_PATH)

    sdyn_values = [r.sdyn_mean for r in all_rows]
    fwi_values = [r.fwi_mean for r in all_rows]
    print(f"Wrote {OUTPUT_PATH} ({len(all_rows)} rows)")
    print(f"sdyn_mean range: [{min(sdyn_values):.4f}, {max(sdyn_values):.4f}]")
    print(f"fwi_mean range: [{min(fwi_values):.4f}, {max(fwi_values):.4f}]")
    return 0


if __name__ == "__main__":
    sys.exit(main())
