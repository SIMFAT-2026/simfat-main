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


# --- join_coverage --------------------------------------------------------------


def _row(region, comuna, klass, years):
    return {"region": region, "comuna": comuna, "class": klass, "years": years}


def test_join_coverage_aggregates_hectares_by_comuna_year_class():
    rows = [
        _row("Biobío", "Arauco", 59, {1999: 10.0, 2024: 20.0}),
        _row("Biobío", "Arauco", 60, {1999: 5.0, 2024: 6.0}),
        _row("Ñuble", "Chillán", 12, {1999: 1.0, 2024: 2.0}),
    ]
    index = {
        ("Biobío", "ARAUCO"): "CHL.6.1.1_1",
        ("Ñuble", "CHILLAN"): "CHL.16.1.1_1",
    }

    joined = lulc.join_coverage(rows, index, expected_comuna_ids={"CHL.6.1.1_1", "CHL.16.1.1_1"})

    assert joined["CHL.6.1.1_1"][1999] == {59: 10.0, 60: 5.0}
    assert joined["CHL.6.1.1_1"][2024] == {59: 20.0, 60: 6.0}
    assert joined["CHL.16.1.1_1"][1999] == {12: 1.0}


def test_join_coverage_fails_loudly_when_not_all_expected_comunas_matched():
    rows = [_row("Biobío", "Arauco", 59, {1999: 10.0})]
    index = {
        ("Biobío", "ARAUCO"): "CHL.6.1.1_1",
        ("Biobío", "CANETE"): "CHL.6.1.2_1",
    }

    with pytest.raises(lulc.LulcError, match=r"1/2"):
        lulc.join_coverage(rows, index, expected_comuna_ids={"CHL.6.1.1_1", "CHL.6.1.2_1"})


def test_join_coverage_fails_loudly_on_unmatched_row_name():
    rows = [_row("Biobío", "Not A Real Comuna", 59, {1999: 10.0})]
    index = {("Biobío", "ARAUCO"): "CHL.6.1.1_1"}

    with pytest.raises(lulc.LulcError, match="NOTAREALCOMUNA"):
        lulc.join_coverage(rows, index, expected_comuna_ids={"CHL.6.1.1_1"})


def test_join_coverage_rejects_ancestor_and_descendant_class_in_same_comuna_year():
    """The BLOCKING QUESTION from S1a1, resolved empirically in S1a2: in the
    real xlsx, class 3 (Forest, the legend PARENT of 59/60/67) never appears
    for the same comuna+year as its children in our 3 regions (Biobío, Ñuble,
    La Araucanía) -- see test_real_evidence_our_regions_never_mix_class_3_with_children
    below for the captured real rows. Nationally it CAN happen (verified by
    downloading the real workbook: 19 comunas outside our 3 regions report
    both). join_coverage must not guess how to reconcile 3 with 59/60/67; it
    must fail loudly via legend.assert_disjoint, exactly like a bare
    aggregation would."""
    rows = [
        _row("RegionX", "Comuna Ambigua", 3, {2020: 50.0}),
        _row("RegionX", "Comuna Ambigua", 59, {2020: 30.0}),
    ]
    index = {("RegionX", "COMUNAAMBIGUA"): "X-1"}

    with pytest.raises(legend.LegendError, match="Class 3 is an ancestor of class 59"):
        lulc.join_coverage(rows, index, expected_comuna_ids={"X-1"})


def test_real_evidence_our_regions_never_mix_class_3_with_children():
    """Captured from the real MapBiomas Chile Col 2 statistics workbook
    (statistics_lulc_chile_col2_political_level_234.xlsx, sha256
    c4d5d6b3c8b227e2c280e06d4647730f9e72febd9e7faa390fb4f01b72e66cb9,
    downloaded 2026-09-21): for every one of the 86 comunas in Biobío, Ñuble
    and La Araucanía, class 3 does not appear at all in the COVERAGE sheet --
    only its leaf children (59/60/67) do. These are real (region, comuna,
    class, year, hectares) rows, not synthesized ones. join_coverage must
    accept them without raising (no ancestor/descendant overlap for our AOI)."""
    rows = [
        _row("La Araucanía", "Nueva Imperial", 59, {1999: 354.2566349304234, 2024: 554.2874318542513}),
        _row("La Araucanía", "Nueva Imperial", 60, {1999: 8733.431332537735, 2024: 11360.33585045184}),
        _row("La Araucanía", "Nueva Imperial", 67, {1999: 0.0, 2024: 0.0}),
        _row("La Araucanía", "Cunco", 59, {1999: 29660.44730461376, 2024: 47858.88486692165}),
        _row("La Araucanía", "Cunco", 60, {1999: 65686.81443390931, 2024: 54707.61978837784}),
        _row("La Araucanía", "Cunco", 67, {1999: 715.8391669433736, 2024: 886.8477642944653}),
    ]
    index = {
        ("La Araucanía", "NUEVAIMPERIAL"): "CHL.9.5.1_1",
        ("La Araucanía", "CUNCO"): "CHL.9.1.1_1",
    }

    joined = lulc.join_coverage(
        rows, index, expected_comuna_ids={"CHL.9.5.1_1", "CHL.9.1.1_1"}
    )

    assert 3 not in joined["CHL.9.5.1_1"][2024]
    assert joined["CHL.9.5.1_1"][2024] == {59: pytest.approx(554.2874318542513), 60: pytest.approx(11360.33585045184), 67: 0.0}
    assert joined["CHL.9.1.1_1"][1999][67] == pytest.approx(715.8391669433736)
