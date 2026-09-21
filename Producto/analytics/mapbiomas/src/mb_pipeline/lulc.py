"""MapBiomas Chile Col 2 land-cover COVERAGE reader and comuna join.

Reads the ``COVERAGE`` sheet of the per-comuna statistics workbook, matches
its territory names against the SIMFAT comuna GeoJSON seeds (name-based --
the workbook has no CUT/INE code column), and returns per (comunaId, year)
hectares by class code for every year 1999-2024.
"""
from __future__ import annotations

import json
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Collection, Mapping, Sequence

import openpyxl

from . import legend

YEAR_COLUMNS: tuple[str, ...] = tuple(f"y{year}" for year in range(1999, 2025))
_YEAR_COLUMN_RE = re.compile(r"^y(\d{4})$")

# Repo root, three levels above Producto/analytics/mapbiomas.
_REPO_ROOT = Path(__file__).resolve().parents[5]
_GEOJSON_DIR = (
    _REPO_ROOT
    / "Producto/backend/simfat-backend/src/main/resources/static/geojson"
)


@dataclass(frozen=True)
class RegionSource:
    """Pairs the xlsx's exact ``territory_level_2`` name with its comuna GeoJSON.

    Region names are matched by exact string, not by normalize_name: the
    xlsx and the GADM-derived GeoJSON spell region names too differently
    ("La Araucanía" vs "Araucanía", "Biobío" vs "Bío-Bío") for a generic
    fold to be safe, so the pairing is explicit and scope stays to the 3
    regions this pipeline targets.
    """

    xlsx_region: str
    geojson_path: Path


DEFAULT_REGIONS: tuple[RegionSource, ...] = (
    RegionSource("Biobío", _GEOJSON_DIR / "comunas-biobio.geojson"),
    RegionSource("Ñuble", _GEOJSON_DIR / "comunas-nuble.geojson"),
    RegionSource("La Araucanía", _GEOJSON_DIR / "comunas-araucania.geojson"),
)

_NON_LETTER = re.compile(r"[^A-Z]")


class LulcError(ValueError):
    """Raised when the COVERAGE join or aggregation invariants are violated."""


def normalize_name(name: str) -> str:
    """Fold a territory name to bare uppercase letters for name matching.

    NFKD-decomposes accents away, then strips everything that is not an
    ASCII letter (spaces, hyphens, apostrophes). This makes GADM-style
    camelCase ("SanPedrodelaPaz") and the xlsx's spaced, accented form
    ("San Pedro de la Paz") normalize identically.
    """
    decomposed = unicodedata.normalize("NFKD", name)
    ascii_only = decomposed.encode("ascii", "ignore").decode("ascii")
    return _NON_LETTER.sub("", ascii_only.upper())


def build_comuna_index(
    region_features: Mapping[str, Sequence[dict]],
) -> dict[tuple[str, str], str]:
    """Map (xlsx region name, normalized comuna name) -> comunaId.

    The index is scoped by region on purpose: two different regions may
    share a comuna name, and this must never conflate their comunaIds.
    Raises ``LulcError`` if two comunas in the SAME region normalize to the
    same name (ambiguous match).
    """
    index: dict[tuple[str, str], str] = {}
    for region, features in region_features.items():
        for feature in features:
            props = feature["properties"]
            key = (region, normalize_name(props["nombre"]))
            if key in index and index[key] != props["comunaId"]:
                raise LulcError(
                    f"Ambiguous comuna name {key[1]!r} in region {region!r}: "
                    f"matches both {index[key]} and {props['comunaId']}"
                )
            index[key] = props["comunaId"]
    return index


def read_coverage_sheet(xlsx_path: Path) -> list[dict]:
    """Parse the ``COVERAGE`` sheet into one dict per (territory, class) row.

    Each dict has ``region`` (territory_level_2), ``comuna``
    (territory_level_4), ``class`` (legend code, int) and ``years``
    (``{year: hectares}`` for every ``yYYYY`` column found in the header).
    Blank/missing year cells read as ``0.0``.
    """
    wb = openpyxl.load_workbook(xlsx_path, read_only=True, data_only=True)
    ws = wb["COVERAGE"]
    rows_iter = ws.iter_rows(values_only=True)
    header = next(rows_iter)
    col = {name: i for i, name in enumerate(header)}
    year_cols = [
        (i, int(match.group(1)))
        for i, name in enumerate(header)
        if isinstance(name, str) and (match := _YEAR_COLUMN_RE.match(name))
    ]
    result = []
    for row in rows_iter:
        if row[col["territory_level_4"]] is None:
            continue
        result.append(
            {
                "region": row[col["territory_level_2"]],
                "comuna": row[col["territory_level_4"]],
                "class": int(row[col["class"]]),
                "years": {year: float(row[i] or 0.0) for i, year in year_cols},
            }
        )
    return result


def join_coverage(
    rows: Sequence[dict],
    comuna_index: Mapping[tuple[str, str], str],
    expected_comuna_ids: Collection[str],
) -> dict[str, dict[int, dict[int, float]]]:
    """Join COVERAGE rows to comunaId via ``comuna_index`` and aggregate.

    Returns ``{comunaId: {year: {classCode: hectares}}}``. Fails loudly
    (``LulcError``) if a row's (region, comuna) does not match the index, or
    if the matched comunaIds are not exactly ``expected_comuna_ids``.

    For every (comunaId, year), the set of class codes present is checked
    with ``legend.assert_disjoint`` before being returned: if a class code
    and one of its legend ancestors (e.g. 3 "Forest" and 59 "Primary
    forest") were both present for the same comuna+year, summing them would
    double count area. This never happens in the real xlsx for our 3
    regions (see test_real_evidence_our_regions_never_mix_class_3_with_children)
    but does happen nationally for class 3, so the check stays in place
    rather than being narrowed to "our AOI never needs it".
    """
    matched: dict[str, dict[int, dict[int, float]]] = {}
    for row in rows:
        key = (row["region"], normalize_name(row["comuna"]))
        comuna_id = comuna_index.get(key)
        if comuna_id is None:
            raise LulcError(
                f"COVERAGE row for region={row['region']!r} comuna={row['comuna']!r} "
                f"(normalized {key[1]!r}) does not match any comuna in the index"
            )
        by_year = matched.setdefault(comuna_id, {})
        for year, hectares in row["years"].items():
            by_year.setdefault(year, {})[row["class"]] = hectares

    expected = set(expected_comuna_ids)
    got = set(matched)
    if got != expected:
        missing = expected - got
        extra = got - expected
        raise LulcError(
            f"COVERAGE join matched {len(got)}/{len(expected)} expected comunas "
            f"(missing={sorted(missing)}, extra={sorted(extra)})"
        )

    for comuna_id, by_year in matched.items():
        for year, by_class in by_year.items():
            legend.assert_disjoint(by_class.keys())

    return matched


def load_geojson_features(path: Path) -> list[dict]:
    """Read a GeoJSON FeatureCollection and return its ``features`` list."""
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return data["features"]


def load_region_features(regions: Sequence[RegionSource]) -> dict[str, list[dict]]:
    """Load every region's GeoJSON, keyed by its xlsx region name."""
    return {region.xlsx_region: load_geojson_features(region.geojson_path) for region in regions}


def build_name_mapping(
    rows: Sequence[dict], comuna_index: Mapping[tuple[str, str], str]
) -> dict[str, dict[str, str]]:
    """comunaId -> the exact xlsx region/comuna name strings that matched it.

    Committed as a small JSON fixture so the name join is auditable without
    re-downloading the xlsx.
    """
    mapping: dict[str, dict[str, str]] = {}
    for row in rows:
        key = (row["region"], normalize_name(row["comuna"]))
        comuna_id = comuna_index.get(key)
        if comuna_id is not None:
            mapping.setdefault(comuna_id, {"xlsxRegion": row["region"], "xlsxName": row["comuna"]})
    return mapping


def write_name_mapping(path: Path, mapping: Mapping[str, dict]) -> None:
    """Write the comunaId -> xlsx name mapping as sorted, stable JSON."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    ordered = {comuna_id: mapping[comuna_id] for comuna_id in sorted(mapping)}
    path.write_text(json.dumps(ordered, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
