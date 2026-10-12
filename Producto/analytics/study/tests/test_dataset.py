"""dataset.py: strict join of target + predictors into comuna-season rows.

SYNTHETIC ONLY: no real study data is read here.
"""
import numpy as np
import pytest

from study import dataset

COMUNAS = ["A", "B", "C"]
SEASONS = [2017, 2018]


def _target(counts=None, comunas=COMUNAS, seasons=SEASONS):
    counts = counts or {}
    rows = []
    for c in comunas:
        for s in seasons:
            for window in ("primary", "sensitivity"):
                for flt in ("type0", "notype"):
                    n = counts.get((c, s, window, flt), 4)
                    rows.append(
                        {"comunaId": c, "season": str(s), "window": window, "filter": flt,
                         "count": str(n), "y_n1": str(int(n >= 1)),
                         "y_n3": str(int(n >= 3)), "y_n5": str(int(n >= 5))}
                    )
    return rows


def _fwi(comunas=COMUNAS, seasons=SEASONS):
    rows = []
    for i, c in enumerate(comunas):
        for s in seasons:
            for window, base in (("standard", 0.10), ("sensitivity", 0.20)):
                rows.append({"comunaId": c, "season": str(s), "window": window,
                             "sdyn_mean": str(base + 0.01 * i), "fwi_mean": "5.0", "n_days": "121"})
    return rows


def _mb(comunas=COMUNAS, seasons=SEASONS):
    rows = []
    for i, c in enumerate(comunas):
        for s in seasons:
            rows.append({"comunaId": c, "season": str(s),
                         "s_mapbiomas": str(0.5 + 0.1 * i), "s_mapbiomas_log": str(0.3 + 0.1 * i)})
    return rows


def test_build_dataset_joins_one_row_per_comuna_season_sorted():
    ds = dataset.build_dataset(_target(), _fwi(), _mb(), n_threshold=3, window="primary",
                               type_filter="type0", variant="pct", seasons=SEASONS)
    assert len(ds) == 6
    assert list(ds.comuna) == ["A", "A", "B", "B", "C", "C"]
    assert list(ds.season) == [2017, 2018] * 3
    assert ds.sdyn[0] == pytest.approx(0.10) and ds.sdyn[2] == pytest.approx(0.11)
    assert ds.smap[0] == pytest.approx(0.5) and ds.smap[4] == pytest.approx(0.7)


def test_primary_window_uses_the_standard_fwi_window_and_sensitivity_uses_its_own():
    p = dataset.build_dataset(_target(), _fwi(), _mb(), n_threshold=3, window="primary",
                              type_filter="type0", variant="pct", seasons=SEASONS)
    s = dataset.build_dataset(_target(), _fwi(), _mb(), n_threshold=3, window="sensitivity",
                              type_filter="type0", variant="pct", seasons=SEASONS)
    assert p.sdyn[0] == pytest.approx(0.10)
    assert s.sdyn[0] == pytest.approx(0.20)


def test_variant_log_uses_the_log_burned_norm_column():
    ds = dataset.build_dataset(_target(), _fwi(), _mb(), n_threshold=3, window="primary",
                               type_filter="type0", variant="log", seasons=SEASONS)
    assert ds.smap[0] == pytest.approx(0.3)


def test_label_follows_the_n_threshold_and_count_is_kept():
    counts = {("A", 2017, "primary", "type0"): 1, ("B", 2017, "primary", "type0"): 3,
              ("C", 2017, "primary", "type0"): 5}
    t = _target(counts)
    for n, expected in ((1, [1, 1, 1]), (3, [0, 1, 1]), (5, [0, 0, 1])):
        ds = dataset.build_dataset(t, _fwi(), _mb(), n_threshold=n, window="primary",
                                   type_filter="type0", variant="pct", seasons=[2017])
        assert list(ds.y) == expected
        assert list(ds.count) == [1, 3, 5]


def test_type_filter_selects_the_matching_target_rows():
    counts = {("A", 2017, "primary", "notype"): 9, ("A", 2017, "primary", "type0"): 2}
    t = _target(counts)
    a = dataset.build_dataset(t, _fwi(), _mb(), n_threshold=3, window="primary",
                              type_filter="notype", variant="pct", seasons=[2017])
    b = dataset.build_dataset(t, _fwi(), _mb(), n_threshold=3, window="primary",
                              type_filter="type0", variant="pct", seasons=[2017])
    assert a.count[0] == 9 and b.count[0] == 2


def test_missing_predictor_row_is_an_error_not_a_silent_drop():
    fwi = [r for r in _fwi() if not (r["comunaId"] == "B" and r["season"] == "2018"
                                     and r["window"] == "standard")]
    with pytest.raises(dataset.JoinError, match="B"):
        dataset.build_dataset(_target(), fwi, _mb(), n_threshold=3, window="primary",
                              type_filter="type0", variant="pct", seasons=SEASONS)


def test_missing_mapbiomas_row_is_an_error():
    mb = [r for r in _mb() if not (r["comunaId"] == "C" and r["season"] == "2017")]
    with pytest.raises(dataset.JoinError, match="C"):
        dataset.build_dataset(_target(), _fwi(), mb, n_threshold=3, window="primary",
                              type_filter="type0", variant="pct", seasons=SEASONS)


def test_duplicate_keys_are_an_error():
    with pytest.raises(dataset.JoinError, match="duplicate"):
        dataset.build_dataset(_target(), _fwi() + _fwi()[:1], _mb(), n_threshold=3,
                              window="primary", type_filter="type0", variant="pct", seasons=SEASONS)


def test_inconsistent_y_column_is_an_error():
    t = _target()
    t[0]["y_n3"] = "0"  # count is 4, so y_n3 must be 1
    with pytest.raises(dataset.JoinError, match="y_n3"):
        dataset.build_dataset(t, _fwi(), _mb(), n_threshold=3, window="primary",
                              type_filter="type0", variant="pct", seasons=SEASONS)


def test_unknown_options_are_rejected():
    for kwargs in ({"window": "weekly"}, {"type_filter": "x"}, {"variant": "z"}, {"n_threshold": 2}):
        base = dict(n_threshold=3, window="primary", type_filter="type0", variant="pct", seasons=SEASONS)
        base.update(kwargs)
        with pytest.raises(ValueError):
            dataset.build_dataset(_target(), _fwi(), _mb(), **base)


def test_subset_keeps_arrays_aligned():
    ds = dataset.build_dataset(_target(), _fwi(), _mb(), n_threshold=3, window="primary",
                               type_filter="type0", variant="pct", seasons=SEASONS)
    sub = ds.subset(ds.season == 2018)
    assert len(sub) == 3 and set(sub.season) == {2018}
    assert list(sub.comuna) == ["A", "B", "C"]


def test_validate_keys_reports_counts_and_missing_keys_without_any_values():
    fwi = [r for r in _fwi() if not (r["comunaId"] == "B" and r["window"] == "standard")]
    rep = dataset.validate_keys(_target(), fwi, _mb(), seasons=SEASONS)
    assert rep.n_target_keys == 6 * 2 * 2  # comuna-seasons x windows x filters
    assert rep.n_comunas == 3 and rep.n_seasons == 2
    assert rep.missing_fwi == 4  # B x 2 seasons x 2 filters, primary window
    assert rep.missing_mapbiomas == 0
    assert not rep.ok
    assert not any(isinstance(v, float) for v in vars(rep).values())


def test_validate_keys_ok_on_complete_inputs():
    rep = dataset.validate_keys(_target(), _fwi(), _mb(), seasons=SEASONS)
    assert rep.ok


def test_file_sha256_matches_hashlib(tmp_path):
    import hashlib
    p = tmp_path / "x.csv"
    p.write_bytes(b"abc\n")
    assert dataset.file_sha256(p) == hashlib.sha256(b"abc\n").hexdigest()


def test_read_csv_rows_returns_dicts(tmp_path):
    p = tmp_path / "x.csv"
    p.write_text("a,b\n1,2\n3,4\n", encoding="utf-8")
    assert dataset.read_csv_rows(p) == [{"a": "1", "b": "2"}, {"a": "3", "b": "4"}]


# ---------------------------------------------------------------- balanced panel (completeness)


def _ds(comunas=COMUNAS, seasons=SEASONS, **kw):
    return dataset.build_dataset(_target(comunas=comunas, seasons=seasons), _fwi(comunas=comunas, seasons=seasons),
                                 _mb(comunas=comunas, seasons=seasons), n_threshold=3, window="primary",
                                 type_filter="type0", variant="pct", seasons=seasons, **kw)


def test_a_complete_panel_passes_the_balanced_panel_check():
    dataset.assert_balanced_panel(_ds(), n_comunas=3, seasons=SEASONS, label="t")


def test_a_target_row_missing_from_the_join_is_an_error_not_a_silent_drop():
    target = [r for r in _target() if not (r["comunaId"] == "B" and r["season"] == "2018")]
    ds = dataset.build_dataset(target, _fwi(), _mb(), n_threshold=3, window="primary",
                               type_filter="type0", variant="pct", seasons=SEASONS)
    assert len(ds) == 5  # build_dataset alone keeps what it finds ...
    with pytest.raises(dataset.PanelError, match="6"):  # ... the panel check refuses it
        dataset.assert_balanced_panel(ds, n_comunas=3, seasons=SEASONS, label="t")


def test_a_missing_comuna_is_an_error():
    ds = _ds(comunas=["A", "B"])
    with pytest.raises(dataset.PanelError, match="comunas"):
        dataset.assert_balanced_panel(ds, n_comunas=3, seasons=SEASONS, label="t")


def test_an_unexpected_season_set_is_an_error():
    ds = _ds(seasons=[2017])
    with pytest.raises(dataset.PanelError, match="seasons"):
        dataset.assert_balanced_panel(ds, n_comunas=3, seasons=SEASONS, label="t")


def test_duplicate_comuna_season_keys_are_an_error():
    ds = _ds()
    dup = dataset.Dataset(
        comuna=np.concatenate([ds.comuna, ds.comuna[:1]]), season=np.concatenate([ds.season, ds.season[:1]]),
        count=np.concatenate([ds.count, ds.count[:1]]), y=np.concatenate([ds.y, ds.y[:1]]),
        sdyn=np.concatenate([ds.sdyn, ds.sdyn[:1]]), smap=np.concatenate([ds.smap, ds.smap[:1]]),
    )
    with pytest.raises(dataset.PanelError, match="duplicate"):
        dataset.assert_balanced_panel(dup, n_comunas=3, seasons=SEASONS, label="t")


def test_every_comuna_must_hold_every_season_even_when_the_total_matches():
    ds = _ds()
    # right total (6) and no duplicates, but comuna C holds 2019 instead of 2018
    skewed = dataset.Dataset(ds.comuna, np.array([2017, 2018, 2017, 2018, 2017, 2019]), ds.count, ds.y,
                             ds.sdyn, ds.smap)
    with pytest.raises(dataset.PanelError):
        dataset.assert_balanced_panel(skewed, n_comunas=3, seasons=SEASONS, label="t")


def test_same_panel_across_windows_and_filters():
    a = _ds()
    dataset.assert_same_panel(a, a.subset(np.ones(len(a), dtype=bool)), label="t")
    with pytest.raises(dataset.PanelError, match="different comuna-season"):
        dataset.assert_same_panel(a, a.subset(a.comuna != "B"), label="t")
