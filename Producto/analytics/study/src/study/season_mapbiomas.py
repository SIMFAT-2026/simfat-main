"""Leakage-safe, per-season recomputation of the MapBiomas Fuego/land-cover
features and a Python parity port of ``S_mapbiomas``
(PREREGISTRATION.md Section 7 leakage rules, Section 8.2 formula).

For each test season ``s`` (2017..2025) and each of the 86 monitored
comunas:

- Fuego (fire) inputs use ONLY years ``2013..(s-2)`` -- never the full
  2013-2025 production seed aggregates, which are anchored at 2025 and
  would leak season ``s``'s own December into its own fire history.
  Recomputed from the committed, sha256-verified annual rasters with the
  SAME zonal method (``mb_pipeline.zonal``), polygons, and gates
  (``mb_pipeline.fire_stats``) as the production seed pipeline.
- Land-cover shares use the committed seed's ``sharesByYear`` for year
  ``s-1`` (no leakage: an annual classification, not a forward-looking
  aggregate).
- ``fuel``/``history``/``S_mapbiomas`` mirror
  ``MapbiomasSusceptibilityServiceImpl`` (Java) EXACTLY: same weights
  (0.65/0.35 fuel/history, 0.60/0.40 burned/recurrence, FREQUENCY_CAP=5.0),
  same class-27-excluded fuel sum, same clamp01 everywhere. Recency is
  always 1.0 here (``R_MAX=0``, disabled by production default, per
  PREREGISTRATION.md Section 8.2) -- never computed from
  ``yearsSinceLastFire``, which would be a dead branch.

BLINDING RULE (hard, non-negotiable): this module builds PREDICTOR INPUTS
ONLY. It never loads FIRMS data and never computes any quantity relating a
predictor (fuel, history, S_mapbiomas, burnedNorm, ...) to any
fire-activity target -- not here, not in its tests, not in
build_season_mapbiomas.py's printed output.

IMPLEMENTATION NOTES (not fixed by the pre-registration, documented here
per the launch prompt):

- ``frequencyMean`` (feeding ``recurrenceNorm``) is production's
  pixel-count-weighted mean of a ``frequency_burned_v1`` raster
  (``mb_pipeline.fire_stats.frequency_stats``). No per-season frequency
  raster is cached (only the full 2013-2025 one, which the launch prompt
  explicitly forbids using here), so this module reproduces the SAME
  definition from the per-year ``annual_burned_v1`` rasters instead, via
  an exact identity: for a pixel-level "number of years burned" frequency
  raster, the pixel-count-weighted mean over a fixed area equals
  ``sum(burned_pixel_count_y for y in years) / total_pixel_count`` --
  because summing per-pixel burn-year-counts over all pixels is the same
  as summing per-year burned-pixel-counts over all years, regardless of
  which pixels overlap across years. See ``frequency_mean_from_zonal_results``.
- ``yearLastFire``/``yearsSinceLastFire`` are derived PURELY from the
  annual-burned evidence restricted to ``<= s-2`` (the latest year in that
  range with ``burnedHa > 0``), never from any ``year_last_fire_v1``
  raster: the only cached year-last-fire rasters cover windows ending in
  2017 or 2025, both of which would leak a season's own future years for
  most ``s``. Since ``R_MAX=0`` (recency disabled), these two fields do
  not affect ``history``/``S_mapbiomas`` at all; they are computed and
  reported here only because the launch prompt asked them to be
  documented, never persisted to the output CSV (whose header is fixed by
  the launch prompt without these two columns).
- The s=2019 independent cross-check (union area vs. the area with
  frequency > 0 in the cached ``..._frequency_burned_2013_2017.tif``) is
  SKIPPED: that file has no entry in
  ``Producto/analytics/mapbiomas/data/manifest.json`` (it predates the
  manifest's current 17-entry set, from earlier exploratory work), so its
  sha256 cannot be verified before use -- the launch prompt explicitly
  sanctions skipping with a note in that case rather than using an
  unverified raster.
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence

from mb_pipeline import build_stats as mb_build_stats
from mb_pipeline import fire_stats as mb_fire_stats
from mb_pipeline import zonal as mb_zonal
from mb_pipeline.zonal import ZonalResult

STUDY_ROOT = Path(__file__).resolve().parents[2]  # .../Producto/analytics/study
STUDY_PRODUCTO_ROOT = STUDY_ROOT.parents[1]  # .../Producto

SEASONS: tuple[int, ...] = tuple(range(2017, 2026))

# design D3 / MapbiomasSusceptibilityServiceImpl.java parity constants.
FUEL_WEIGHT = 0.65
HISTORY_WEIGHT = 0.35
BURNED_WEIGHT = 0.60
RECURRENCE_WEIGHT = 0.40
FREQUENCY_CAP = 5.0
SHARE_SUM_TOLERANCE = 0.01

ANNUAL_RASTER_FIRST_YEAR = 2013
ANNUAL_RASTER_LAST_YEAR = 2025


class ShareSumMismatchError(ValueError):
    """Raised when a comuna-year's land-cover shares do not sum to ~1.0
    (fractions), mirroring MapbiomasSusceptibilityServiceImpl's
    SHARE_SUM_TOLERANCE guard."""


class UnknownFuelClassError(ValueError):
    """Raised when a land-cover class has no fuel weight and is not in the
    excluded-classes list -- never silently treated as weight 0."""


class RasterIntegrityError(ValueError):
    """Raised when a cached Fuego raster is missing from the manifest or its
    sha256 does not match the recorded value -- abort, never use an
    unverified raster."""


def _clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


# ---------------------------------------------------------------------------
# Leakage rule: Fuego years <= s-2 (PREREGISTRATION.md Section 7, rule 1)
# ---------------------------------------------------------------------------


def fire_years_for_season(season: int) -> list[int]:
    """Fuego years ``2013..(season-2)`` inclusive. Season 2017 -> [2013,
    2014, 2015] (3 years, the thinnest history, disclosed consequence of
    rule 1 in PREREGISTRATION.md Section 7)."""
    return list(range(ANNUAL_RASTER_FIRST_YEAR, season - 2 + 1))


# ---------------------------------------------------------------------------
# Land-cover shares for year s-1
# ---------------------------------------------------------------------------


def shares_for_year(shares_by_year: Mapping, year: int) -> dict[str, float]:
    """Converts one year's slice of a seed's ``landCover.sharesByYear``
    (``{firstYear, lastYear, unit: "bp", classes: {code: [bp, ...]}}``) to
    ``{classCode: fraction}``, dropping classes with 0 bp that year (the
    same sparse convention ``mb_pipeline.build_stats.to_basis_points``
    uses for the reference-year ``sharesByClass``)."""
    first_year, last_year = shares_by_year["firstYear"], shares_by_year["lastYear"]
    if not (first_year <= year <= last_year):
        raise ValueError(f"year {year} outside sharesByYear range [{first_year}, {last_year}]")
    idx = year - first_year
    shares: dict[str, float] = {}
    for code, bp_series in shares_by_year["classes"].items():
        bp = bp_series[idx]
        if bp:
            shares[code] = bp / 10_000.0
    return shares


def load_land_cover_shares_by_year(seed_path: Path) -> dict[str, dict]:
    """``{comunaId: sharesByYear}`` read from the committed merged seed."""
    result: dict[str, dict] = {}
    with open(seed_path, encoding="utf-8") as f:
        for line in f:
            doc = json.loads(line)
            land_cover = doc.get("landCover")
            if land_cover is not None:
                result[doc["comunaId"]] = land_cover["sharesByYear"]
    return result


# ---------------------------------------------------------------------------
# Fuel index (MapbiomasSusceptibilityServiceImpl.computeFuelIndex parity)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class FuelWeights:
    class_weights: Mapping[str, float]
    excluded_classes: frozenset

    def is_excluded(self, code: str) -> bool:
        return code in self.excluded_classes

    def weight_for(self, code: str) -> float:
        if code not in self.class_weights:
            raise UnknownFuelClassError(
                f"No fuel weight for class {code!r} and it is not in excludedClasses"
            )
        return self.class_weights[code]


def load_fuel_weights(path: Path) -> FuelWeights:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return FuelWeights(
        class_weights={str(k): float(v) for k, v in data["classWeights"].items()},
        excluded_classes=frozenset(str(c) for c in data["excludedClasses"]),
    )


def compute_fuel_index(shares_by_class: Mapping[str, float], weights: FuelWeights) -> float:
    """``fuel = sum(share_c * weight_c)``, skipping excluded classes
    (class 27) entirely -- exact parity with
    ``MapbiomasSusceptibilityServiceImpl.computeFuelIndex``."""
    total = sum(shares_by_class.values())
    if abs(total - 1.0) > SHARE_SUM_TOLERANCE:
        raise ShareSumMismatchError(
            f"land-cover shares must sum to ~1.0 (fractions), got {total}"
        )
    fuel = 0.0
    for code, share in shares_by_class.items():
        if weights.is_excluded(code):
            continue
        fuel += share * weights.weight_for(code)
    return _clamp01(fuel)


# ---------------------------------------------------------------------------
# History / S_mapbiomas (MapbiomasSusceptibilityServiceImpl parity, recency=1.0)
# ---------------------------------------------------------------------------


def compute_recurrence_norm(frequency_mean: float | None) -> float:
    if frequency_mean is None:
        return 0.0
    return _clamp01(min(frequency_mean, FREQUENCY_CAP) / FREQUENCY_CAP)


def compute_history_index(burned_norm: float, frequency_mean: float | None) -> float:
    """``history = clamp(0.60*burnedNorm + 0.40*recurrenceNorm) * recency``
    with ``recency`` fixed at 1.0 (``R_MAX=0``, disabled by production
    default, PREREGISTRATION.md Section 8.2) -- never a function of
    ``yearsSinceLastFire``."""
    recurrence_norm = compute_recurrence_norm(frequency_mean)
    raw = BURNED_WEIGHT * _clamp01(burned_norm) + RECURRENCE_WEIGHT * recurrence_norm
    return _clamp01(raw)


def compute_s_mapbiomas(fuel: float, history: float) -> float:
    return _clamp01(FUEL_WEIGHT * fuel + HISTORY_WEIGHT * history)


# ---------------------------------------------------------------------------
# Fuego raster cache verification (abort on sha256 mismatch)
# ---------------------------------------------------------------------------


def _annual_raster_filename(year: int) -> str:
    return f"mapbiomas_fire_chile_col1_annual_burned_{year}.tif"


def load_verified_annual_rasters(cache_dir: Path, manifest_path: Path) -> dict[int, Path]:
    """Verifies every annual ``2013..2025`` Fuego raster's sha256 against
    ``manifest.json`` before returning its path. Aborts (``RasterIntegrityError``)
    on a missing manifest entry, a missing cached file, or a sha256
    mismatch -- never uses an unverified raster."""
    manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
    by_filename = {entry["fileName"]: entry for entry in manifest}

    paths: dict[int, Path] = {}
    for year in range(ANNUAL_RASTER_FIRST_YEAR, ANNUAL_RASTER_LAST_YEAR + 1):
        filename = _annual_raster_filename(year)
        entry = by_filename.get(filename)
        if entry is None:
            raise RasterIntegrityError(f"No manifest entry for {filename!r} -- refusing to use it unverified")
        path = Path(cache_dir) / filename
        if not path.exists():
            raise RasterIntegrityError(f"{path} not found -- run the mapbiomas Fuego download first")
        actual_sha256 = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual_sha256 != entry["sha256"]:
            raise RasterIntegrityError(
                f"{filename}: sha256 mismatch (expected {entry['sha256']}, got {actual_sha256})"
            )
        paths[year] = path
    return paths


# ---------------------------------------------------------------------------
# Frequency mean from per-year zonal results (see module docstring)
# ---------------------------------------------------------------------------


def frequency_mean_from_zonal_results(zonal_results: Sequence[ZonalResult]) -> float | None:
    """Pixel-count-weighted mean "years burned" over a fixed comuna mask,
    derived from a sequence of per-year ``ZonalResult``s (one per Fuego
    year restricted to ``<= s-2``) via the identity documented in the
    module docstring: ``sum(burned_pixel_count_y) / total_pixel_count``.
    Returns ``None`` if the mask has no pixels at all (``pixel_count == 0``
    for every year, e.g. a comuna entirely outside the raster's extent)."""
    total_pixels = max((zr.pixel_count for zr in zonal_results), default=0)
    if total_pixels == 0:
        return None
    burned_pixel_sum = sum(zr.value_pixels.get(mb_fire_stats.BURNED_VALUE, 0) for zr in zonal_results)
    return burned_pixel_sum / total_pixels


# ---------------------------------------------------------------------------
# Per-season, per-comuna Fuego feature computation (leakage-safe)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class FireFeatures:
    burned_union: float
    frequency_mean: float | None
    year_last_fire: int | None
    years_since_last_fire: int | None


def compute_fire_features(
    comuna_id: str,
    geometry: dict,
    annual_raster_paths: Mapping[int, Path],
    season: int,
) -> FireFeatures:
    """Recomputes one comuna-season's Fuego features from the annual
    rasters restricted to ``fire_years_for_season(season)``, with the SAME
    zonal method, polygon, and hard gates (``validate_fire_gates``) as the
    production seed pipeline -- never the full-history seed aggregates."""
    years = fire_years_for_season(season)
    per_year: dict[int, dict] = {}
    zonal_per_year: dict[int, ZonalResult] = {}
    for year in years:
        zr = mb_zonal.zonal_stats(annual_raster_paths[year], geometry)
        zonal_per_year[year] = zr
        per_year[year] = mb_fire_stats.annual_burned_fraction(zr)

    union_zr = mb_zonal.union_zonal_stats([annual_raster_paths[y] for y in years], geometry)
    union = mb_fire_stats.union_burned_fraction(union_zr)
    mb_fire_stats.validate_fire_gates(
        per_year, union_fraction=union["burnedFraction"], comuna_id=comuna_id
    )

    frequency_mean = frequency_mean_from_zonal_results(list(zonal_per_year.values()))

    as_of_year = season - 2
    burned_years = [year for year, stats in per_year.items() if stats["burnedHa"] > 0]
    year_last_fire = max(burned_years) if burned_years else None
    years_since_last_fire = (as_of_year - year_last_fire) if year_last_fire is not None else None

    return FireFeatures(
        burned_union=union["burnedFraction"],
        frequency_mean=frequency_mean,
        year_last_fire=year_last_fire,
        years_since_last_fire=years_since_last_fire,
    )


# ---------------------------------------------------------------------------
# Cross-comuna normalization (recomputed PER SEASON, Q27)
# ---------------------------------------------------------------------------


def compute_cross_comuna_normalizations(
    burned_union_by_comuna: Mapping[str, float]
) -> tuple[dict[str, float | None], dict[str, float | None]]:
    """``(burnedFractionPct, burnedFractionLog)`` recomputed over the 86
    comunas for ONE season, via the SAME production functions
    (``mb_pipeline.build_stats``)."""
    pct = mb_build_stats.percentile_rank_burned_fraction(burned_union_by_comuna)
    log = mb_build_stats.log1p_p95_clip_burned_fraction(burned_union_by_comuna)
    return pct, log


# ---------------------------------------------------------------------------
# Orchestration: one row per comuna-season
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class SeasonMapbiomasRow:
    comuna_id: str
    season: int
    fuel: float
    burned_union: float
    burned_pct: float
    burned_log: float
    frequency_mean: float | None
    recurrence_norm: float
    history: float
    s_mapbiomas: float
    history_log: float
    s_mapbiomas_log: float


def build_season_mapbiomas_features(
    comuna_geometries: Mapping[str, dict],
    land_cover_shares_by_year: Mapping[str, dict],
    annual_raster_paths: Mapping[int, Path],
    fuel_weights: FuelWeights,
    *,
    seasons: Sequence[int] = SEASONS,
) -> list[SeasonMapbiomasRow]:
    rows: list[SeasonMapbiomasRow] = []
    for season in seasons:
        fire_by_comuna: dict[str, FireFeatures] = {
            comuna_id: compute_fire_features(comuna_id, geometry, annual_raster_paths, season)
            for comuna_id, geometry in comuna_geometries.items()
        }
        burned_union_by_comuna = {cid: f.burned_union for cid, f in fire_by_comuna.items()}
        pct_by_comuna, log_by_comuna = compute_cross_comuna_normalizations(burned_union_by_comuna)

        for comuna_id in comuna_geometries:
            fire = fire_by_comuna[comuna_id]
            shares = shares_for_year(land_cover_shares_by_year[comuna_id], season - 1)
            fuel = compute_fuel_index(shares, fuel_weights)

            burned_pct = pct_by_comuna[comuna_id]
            burned_log = log_by_comuna[comuna_id]
            recurrence_norm = compute_recurrence_norm(fire.frequency_mean)
            history = compute_history_index(burned_pct, fire.frequency_mean)
            history_log = compute_history_index(burned_log, fire.frequency_mean)
            s_mapbiomas = compute_s_mapbiomas(fuel, history)
            s_mapbiomas_log = compute_s_mapbiomas(fuel, history_log)

            rows.append(
                SeasonMapbiomasRow(
                    comuna_id=comuna_id,
                    season=season,
                    fuel=fuel,
                    burned_union=fire.burned_union,
                    burned_pct=burned_pct,
                    burned_log=burned_log,
                    frequency_mean=fire.frequency_mean,
                    recurrence_norm=recurrence_norm,
                    history=history,
                    s_mapbiomas=s_mapbiomas,
                    history_log=history_log,
                    s_mapbiomas_log=s_mapbiomas_log,
                )
            )
    return rows
