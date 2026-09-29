"""Merge REAL MapBiomas land cover (Col 2, 1999-2024) into the committed seed.

S1b4 (2026-09-28): the previously committed seed
(``fuego-col1@2017-partial``) has ``landCover=null`` for all 86 comunas --
S_mapbiomas' fuel term (65% of the blend, design D3) and the LANDCOVER map
layer have never had real data. This script:

1. Downloads (or reuses an already-downloaded, sha256-verified copy of) the
   real ``statistics_lulc_chile_col2_political_level_234.xlsx`` via
   ``mb_pipeline.download`` and records/refreshes ``data/manifest.json``.
2. Reads its ``COVERAGE`` sheet and joins it to the 86 target comunas via
   ``mb_pipeline.lulc`` (region-scoped, normalized name join).
3. Runs the D2 hard gates (legend disjointness -- already enforced inside
   ``lulc.join_coverage`` per comuna-year; per-comuna-year total-drift gate,
   ``build_stats.validate_comuna_year_shares``) and ABORTS on any failure,
   naming the offending ``(comunaId, year)`` -- never silently renormalizes.
4. Builds ``landCover`` (``sharesByClass``, ``sharesByClassMean5y``,
   ``sharesByYear`` bp series 1999-2024) per comuna via
   ``build_stats.land_cover_section``.
5. Reads the EXISTING fire-only seed (``data/fire_stats_2017_partial.jsonl``,
   the source of the currently committed Java seed) and merges each
   comuna's REAL ``fire`` section VERBATIM (byte-identical values) with the
   new ``landCover`` section into one document under a NEW ``dataVersion``.
6. Writes the merged seed as ``data/comuna_mapbiomas_stats.<dataVersion>.jsonl``
   (one doc per line, minified) -- the Java resource copy is a separate,
   explicit step (see the S1b4 apply-progress), not performed by this
   script, mirroring how S1b2/S1b3 already worked (Python emits under
   ``data/``; a human/CI step copies the reviewed file into
   ``simfat-backend/src/main/resources/seed/mapbiomas/``).

Usage (from ``Producto/analytics/mapbiomas``):

    PYTHONPATH=src python scripts/generate_landcover_seed.py
"""
from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from mb_pipeline import area, build_stats, download, lulc

_ROOT = Path(__file__).resolve().parents[1]
_DATA_DIR = _ROOT / "data"
_RAW_DIR = _DATA_DIR / "raw"
_MANIFEST_PATH = _DATA_DIR / "manifest.json"
_FIRE_SEED_PATH = _DATA_DIR / "fire_stats_2017_partial.jsonl"

_LULC_URL = (
    "https://chile.mapbiomas.org/wp-content/uploads/sites/7/2026/08/"
    "statistics_lulc_chile_col2_political_level_234.xlsx"
)
_LULC_FILENAME = "statistics_lulc_chile_col2_political_level_234.xlsx"
_LULC_DATASET = "mapbiomas_lulc_col2_coverage"

DATA_VERSION = "lulc-col2+fuego-col1@2017-partial"
FIRST_YEAR = 1999
LAST_YEAR = 2024
REFERENCE_YEAR = 2024
MEAN_YEARS = range(2020, 2025)


def _ensure_xlsx() -> Path:
    """Reuse an already-downloaded, sha256-verified xlsx, or download it fresh."""
    dest = _RAW_DIR / _LULC_FILENAME
    if dest.exists():
        return dest
    result = download.download_file(_LULC_URL, dest, timeout=120.0, retries=3)
    _record_manifest(result)
    return dest


def _record_manifest(result: download.DownloadResult) -> None:
    entry = download.manifest_entry(_LULC_DATASET, _LULC_URL, result)
    download.write_manifest(_MANIFEST_PATH, entry)


def _load_fire_only_docs() -> dict[str, dict]:
    docs: dict[str, dict] = {}
    with open(_FIRE_SEED_PATH, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            doc = json.loads(line)
            docs[doc["comunaId"]] = doc
    return docs


def generate(data_dir: Path | None = None) -> Path:
    data_dir = data_dir or _DATA_DIR
    xlsx_path = _ensure_xlsx()

    region_features = lulc.load_region_features(lulc.DEFAULT_REGIONS)
    comuna_index = lulc.build_comuna_index(region_features)
    expected_ids = sorted(
        {f["properties"]["comunaId"] for feats in region_features.values() for f in feats}
    )

    rows = lulc.read_coverage_sheet(xlsx_path)
    target_regions = {region.xlsx_region for region in lulc.DEFAULT_REGIONS}
    our_rows = [r for r in rows if r["region"] in target_regions]

    joined = lulc.join_coverage(our_rows, comuna_index, expected_comuna_ids=expected_ids)
    if set(joined) != set(expected_ids):
        raise SystemExit(
            f"LULC join did not match all 86 comunas: got {len(joined)}/{len(expected_ids)}"
        )

    # Auditable name-mapping fixture (design D2): comunaId -> the exact xlsx
    # region/comuna name strings that matched it.
    name_mapping = lulc.build_name_mapping(our_rows, comuna_index)
    lulc.write_name_mapping(_ROOT / "fixtures" / "comuna_name_mapping.json", name_mapping)

    # D2 hard gate: per-comuna-year classified-total drift, aborts loudly
    # (never renormalizes) naming (comunaId, year) on failure. This alone
    # cannot catch a double count stable across every year -- see the
    # independent area gate right below.
    for comuna_id, by_year in joined.items():
        build_stats.validate_comuna_year_shares(
            by_year, comuna_id=comuna_id, reference_year=REFERENCE_YEAR
        )

    # D2 independent hard gate (S1b4 review): the LULC classified total
    # (reference year, all classes including 27) must be close to that SAME
    # comuna's own GeoJSON polygon area -- a source with no dependency on
    # the xlsx, so it catches a double count that is stable across years
    # (see validate_comuna_polygon_area_ratio's docstring for the full
    # rationale and the real ratio distribution).
    comuna_geometry = {
        feature["properties"]["comunaId"]: feature["geometry"]
        for feats in region_features.values()
        for feature in feats
    }
    for comuna_id, by_year in joined.items():
        lulc_total_ha = sum(by_year[REFERENCE_YEAR].values())
        polygon_ha = area.polygon_area_ha(comuna_geometry[comuna_id])
        build_stats.validate_comuna_polygon_area_ratio(
            lulc_total_ha, polygon_ha, comuna_id=comuna_id
        )

    fire_only_docs = _load_fire_only_docs()
    missing_fire = set(expected_ids) - set(fire_only_docs)
    if missing_fire:
        raise SystemExit(f"Fire-only seed is missing comunas: {sorted(missing_fire)}")

    computed_at = datetime.now(timezone.utc).isoformat()
    download_date = datetime.now(timezone.utc).strftime("%Y-%m-%d")

    docs = []
    for comuna_id in expected_ids:
        land_cover = build_stats.land_cover_section(
            joined[comuna_id],
            reference_year=REFERENCE_YEAR,
            mean_years=MEAN_YEARS,
            first_year=FIRST_YEAR,
            last_year=LAST_YEAR,
        )
        fire_only_doc = fire_only_docs[comuna_id]
        fire_section = fire_only_doc["fire"]  # byte-identical, never recomputed
        old_provenance = fire_only_doc.get("provenance", {})
        fire_sources = old_provenance.get("sources", [])
        fire_scope = old_provenance.get("scope", "")
        fire_download_date = old_provenance.get("downloadDate", "")

        provenance = {
            "sources": [
                f"{_LULC_FILENAME} (MapBiomas Chile Colección 2, COVERAGE sheet, "
                f"sha256 verified in data/manifest.json)",
                *fire_sources,
            ],
            "scope": (
                f"S1b4: land cover Col 2 {FIRST_YEAR}-{LAST_YEAR} (real, full, all 86 "
                f"comunas). Fire: {fire_scope} (downloaded {fire_download_date})."
            ),
            "downloadDate": download_date,
        }

        doc = build_stats.build_comuna_document(
            comuna_id,
            land_cover=land_cover,
            fire=fire_section,
            provenance=provenance,
            computed_at=computed_at,
            partial=True,  # fire is still 2017-only; landCover is now full 1999-2024
        )
        doc["fireReason"] = (
            "Fuego Colección 1: only fire year 2017 processed (partial); frequency "
            "window 2013-2017. See provenance.scope for detail."
        )
        docs.append(doc)

    seed_filename = f"comuna-mapbiomas-stats.{DATA_VERSION.replace('@', '-')}.jsonl"
    seed_path = data_dir / seed_filename
    build_stats.write_seed_jsonl(seed_path, docs)
    return seed_path


if __name__ == "__main__":
    path = generate()
    print(f"Wrote {path}")
