import pytest

from mb_pipeline import legend

XLSX_CODES = [3, 9, 11, 12, 15, 18, 23, 24, 25, 27, 29, 33, 34, 59, 60, 61, 63, 66, 67]


def test_class_3_is_parent_of_59_60_67():
    assert legend.children(3) == (59, 60, 67)


def test_ancestors_walk_the_whole_chain():
    assert legend.ancestors(59) == (3, 1)
    assert legend.ancestors(12) == (10,)
    assert legend.ancestors(1) == ()


def test_only_leaf_classes_aggregate():
    assert legend.is_leaf(59)
    assert legend.is_leaf(12)
    assert not legend.is_leaf(3)
    assert not legend.is_leaf(1)


def test_the_19_xlsx_codes_are_all_known():
    assert len(set(XLSX_CODES)) == 19
    legend.require_known(XLSX_CODES)  # must not raise


def test_19_xlsx_codes_are_disjoint_once_class_3_is_removed():
    legend.assert_disjoint([c for c in XLSX_CODES if c != 3])  # must not raise


def test_19_xlsx_codes_as_listed_violate_the_tree_only_through_class_3():
    # Whether class 3 in the real xlsx means "forest not sub-classified" (then
    # it is disjoint from 59/60/67 in each comuna-year) is UNVERIFIED and is
    # settled against the real xlsx in S1a2/S1b. The strict tree check must
    # flag exactly this pair set until then.
    with pytest.raises(legend.LegendError, match="Class 3 is an ancestor of class 59"):
        legend.assert_disjoint(XLSX_CODES)
    assert legend.overlapping_pairs(XLSX_CODES) == [(3, 59), (3, 60), (3, 67)]


def test_code_3_together_with_59_fails_loudly():
    with pytest.raises(legend.LegendError, match=r"3.*59"):
        legend.assert_disjoint([3, 59, 12])


def test_parent_1_together_with_leaf_fails_loudly():
    with pytest.raises(legend.LegendError, match="Class 1 is an ancestor of class 59"):
        legend.assert_disjoint([1, 59])
    assert legend.overlapping_pairs([1, 12]) == []


def test_disjointness_is_pairwise_over_any_subset():
    # 3 alone (forest not sub-classified) is valid; so is {59, 60, 67} without 3.
    legend.assert_disjoint([3, 12, 9])
    legend.assert_disjoint([59, 60, 67, 12])


def test_unknown_code_fails_loudly():
    with pytest.raises(legend.LegendError, match="999"):
        legend.require_known([3, 999])


def test_class_27_is_unobserved_and_others_are_observed():
    assert legend.UNOBSERVED_CODE == 27
    assert not legend.is_observed(27)
    assert legend.is_observed(59)


def test_every_code_has_a_label():
    for code in XLSX_CODES:
        assert legend.label(code)
    assert legend.label(59) == "Primary forest"
