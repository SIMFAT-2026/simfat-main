"""S1b4 gate: every land-cover class in the committed seed has a fuel weight.

design D3: ``MapbiomasSusceptibilityServiceImpl.computeFuelIndex`` THROWS on
any class code present in ``sharesByClass`` that has no entry in
``fuel-weights-v1.json`` and is not explicitly excluded (class 27,
"not observed"). If real data ever introduces a class code the weight table
does not know about, the production risk-recompute run would crash. This
test reads the SAME committed ``fuel-weights-v1.json`` the Java service
reads (one source of truth, per D3) and the SAME committed seed the Java
loader reads, so a future collection refresh that introduces a new class
code fails here, in CI, instead of at runtime in production.
"""
from __future__ import annotations

import json
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parents[4]
# The pipeline's own output (Python side) -- byte-identical to the copy the Java
# MapbiomasSeedLoader reads from simfat-backend/src/main/resources/seed/mapbiomas/
# (see generate_landcover_seed.py), so this gate stays self-contained in the
# Python pipeline's own commit/test run and does not depend on the backend
# resource copy existing.
_SEED_PATH = (
    _REPO_ROOT
    / "Producto/analytics/mapbiomas/data"
    / "comuna-mapbiomas-stats.lulc-col2+fuego-col1-2017-partial.jsonl"
)
# fuel-weights-v1.json is the ONE source of truth (design D3) the Java service reads;
# referenced here by relative path, same pattern S2c's Python port is designed to use.
_WEIGHTS_PATH = (
    _REPO_ROOT
    / "Producto/backend/simfat-backend/src/main/resources/mapbiomas/fuel-weights-v1.json"
)


def _load_seed_docs() -> list[dict]:
    with open(_SEED_PATH, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def _load_weights() -> tuple[set[str], set[str]]:
    data = json.loads(_WEIGHTS_PATH.read_text(encoding="utf-8"))
    return set(data["classWeights"]), set(data["excludedClasses"])


def test_every_land_cover_class_in_the_seed_has_a_fuel_weight_or_is_excluded():
    weighted_codes, excluded_codes = _load_weights()
    docs = _load_seed_docs()
    assert len(docs) == 86, "seed must have exactly 86 comuna documents"

    missing: set[tuple[str, str]] = set()
    for doc in docs:
        land_cover = doc.get("landCover")
        if not land_cover:
            continue
        for code in land_cover["sharesByClass"]:
            if code not in weighted_codes and code not in excluded_codes:
                missing.add((doc["comunaId"], code))

    assert not missing, (
        f"Class codes present in the real seed with NO fuel weight and NOT "
        f"excluded (would crash MapbiomasSusceptibilityServiceImpl.computeFuelIndex "
        f"in production): {sorted(missing)}"
    )


def test_every_land_cover_class_in_shares_by_year_has_a_fuel_weight_or_is_excluded():
    """Same gate, but over EVERY year's class set (sharesByYear), not just the
    reference year -- a class that only appears in an early/off-reference
    year must still have a weight before it could ever be used at wM>0."""
    weighted_codes, excluded_codes = _load_weights()
    docs = _load_seed_docs()

    missing: set[tuple[str, str]] = set()
    for doc in docs:
        land_cover = doc.get("landCover")
        if not land_cover:
            continue
        for code in land_cover["sharesByYear"]["classes"]:
            if code not in weighted_codes and code not in excluded_codes:
                missing.add((doc["comunaId"], code))

    assert not missing, f"sharesByYear class codes with no fuel weight: {sorted(missing)}"
