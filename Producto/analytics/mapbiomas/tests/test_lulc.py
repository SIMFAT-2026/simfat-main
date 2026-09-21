"""lulc.py: MapBiomas Chile Col 2 COVERAGE sheet reader and comuna join.

Fixtures build tiny synthetic xlsx workbooks with openpyxl (see
``make_coverage_xlsx`` in conftest.py); no binary xlsx is committed.
"""
import pytest

from mb_pipeline import legend, lulc


# --- normalize_name -----------------------------------------------------------


def test_normalize_name_strips_accents_and_uppercases():
    assert lulc.normalize_name("Ñuble") == "NUBLE"
    assert lulc.normalize_name("Bío-Bío") == "BIOBIO"


def test_normalize_name_matches_gadm_camelcase_against_spaced_name():
    """GADM-style 'SanPedrodelaPaz' must normalize the same as the xlsx's
    spaced, accented 'San Pedro de la Paz'."""
    assert lulc.normalize_name("SanPedrodelaPaz") == lulc.normalize_name("San Pedro de la Paz")
    assert lulc.normalize_name("SanPedrodelaPaz") == "SANPEDRODELAPAZ"


def test_normalize_name_collapses_punctuation_and_whitespace():
    assert lulc.normalize_name("Curacautín") == "CURACAUTIN"
    assert lulc.normalize_name("  Los Álamos  ") == "LOSALAMOS"


# --- build_comuna_index --------------------------------------------------------


def _feature(comuna_id, nombre):
    return {"type": "Feature", "properties": {"comunaId": comuna_id, "nombre": nombre}}


def test_build_comuna_index_maps_region_and_normalized_name_to_comuna_id():
    region_features = {
        "Biobío": [_feature("CHL.6.1.1_1", "Arauco"), _feature("CHL.6.1.2_1", "Cañete")],
        "Ñuble": [_feature("CHL.16.1.1_1", "Chillán")],
    }

    index = lulc.build_comuna_index(region_features)

    assert index[("Biobío", "ARAUCO")] == "CHL.6.1.1_1"
    assert index[("Biobío", "CANETE")] == "CHL.6.1.2_1"
    assert index[("Ñuble", "CHILLAN")] == "CHL.16.1.1_1"


def test_build_comuna_index_does_not_cross_match_same_name_across_regions():
    """Two different regions may share a comuna name; the join must stay
    region-scoped and never conflate the two comunaIds."""
    region_features = {
        "RegionA": [_feature("A-1", "San Francisco")],
        "RegionB": [_feature("B-1", "San Francisco")],
    }

    index = lulc.build_comuna_index(region_features)

    assert index[("RegionA", "SANFRANCISCO")] == "A-1"
    assert index[("RegionB", "SANFRANCISCO")] == "B-1"
    assert index[("RegionA", "SANFRANCISCO")] != index[("RegionB", "SANFRANCISCO")]


def test_build_comuna_index_rejects_ambiguous_duplicate_within_region():
    region_features = {
        "Biobío": [_feature("A-1", "Nueva Imperial"), _feature("A-2", "Nueva  Imperial")],
    }

    with pytest.raises(lulc.LulcError, match="NUEVAIMPERIAL"):
        lulc.build_comuna_index(region_features)


# --- read_coverage_sheet -------------------------------------------------------


def test_read_coverage_sheet_parses_region_comuna_class_and_years(make_coverage_xlsx):
    path = make_coverage_xlsx(
        rows=[
            {
                "territory_level_2": "Biobío",
                "territory_level_4": "Arauco",
                "class": 59,
                "years": {1999: 100.5, 2024: 200.25},
            },
            {
                "territory_level_2": "Ñuble",
                "territory_level_4": "Chillán",
                "class": 12,
                "years": {1999: 5.0, 2024: 7.5},
            },
        ],
        year_columns=("y1999", "y2024"),
    )

    rows = lulc.read_coverage_sheet(path)

    assert len(rows) == 2
    first = next(r for r in rows if r["comuna"] == "Arauco")
    assert first["region"] == "Biobío"
    assert first["class"] == 59
    assert first["years"] == {1999: 100.5, 2024: 200.25}
    second = next(r for r in rows if r["comuna"] == "Chillán")
    assert second["years"] == {1999: 5.0, 2024: 7.5}


def test_read_coverage_sheet_handles_missing_year_value_as_zero(make_coverage_xlsx):
    """Triangulation: a blank cell (no observation for that class-year) must
    read as 0 hectares, not None, so aggregation never crashes on it."""
    path = make_coverage_xlsx(
        rows=[
            {
                "territory_level_2": "Biobío",
                "territory_level_4": "Arauco",
                "class": 67,
                "years": {1999: 0, 2024: None},
            },
        ],
        year_columns=("y1999", "y2024"),
    )

    rows = lulc.read_coverage_sheet(path)

    assert rows[0]["years"] == {1999: 0.0, 2024: 0.0}
