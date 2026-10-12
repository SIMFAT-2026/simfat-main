"""H1 step 1: connected-component labelling of burned pixels (8-connectivity),
numpy only. Known answers on hand-built shapes, plus a cross-check against an
independent breadth-first search on random arrays."""
from collections import deque

import numpy as np
import pytest

from study import h1


def _label(array):
    array = np.asarray(array, dtype=bool)
    rows, cols = np.nonzero(array)
    labels = h1.connected_components(rows, cols, array.shape[1])
    return rows, cols, labels


def _groups(array):
    """Set of frozensets of (row, col) pixels, one per component."""
    rows, cols, labels = _label(array)
    out = {}
    for r, c, lab in zip(rows, cols, labels):
        out.setdefault(int(lab), set()).add((int(r), int(c)))
    return {frozenset(g) for g in out.values()}


def test_empty_input_gives_empty_labels():
    labels = h1.connected_components(np.array([], dtype=int), np.array([], dtype=int), 5)
    assert labels.shape == (0,)


def test_single_pixel_is_one_component():
    assert _groups([[0, 0], [0, 1]]) == {frozenset({(1, 1)})}


def test_l_shaped_scar_is_one_component():
    a = np.zeros((6, 6), dtype=bool)
    a[0:5, 1] = True  # vertical bar
    a[4, 1:5] = True  # foot of the L
    groups = _groups(a)
    assert len(groups) == 1
    assert len(next(iter(groups))) == int(a.sum())


def test_diagonal_neighbours_are_connected_under_8_connectivity():
    a = np.zeros((4, 4), dtype=bool)
    a[0, 0] = a[1, 1] = a[2, 2] = a[3, 3] = True
    assert len(_groups(a)) == 1


def test_anti_diagonal_neighbours_are_connected():
    a = np.zeros((4, 4), dtype=bool)
    a[0, 3] = a[1, 2] = a[2, 1] = a[3, 0] = True
    assert len(_groups(a)) == 1


def test_one_pixel_gap_separates_two_scars():
    a = np.zeros((3, 7), dtype=bool)
    a[1, 0:3] = True
    a[1, 4:7] = True  # column 3 is empty
    assert _groups(a) == {
        frozenset({(1, 0), (1, 1), (1, 2)}),
        frozenset({(1, 4), (1, 5), (1, 6)}),
    }


def test_two_touching_blocks_are_one_scar_even_across_what_would_be_a_comuna_border():
    # The blocks share only a corner (diagonal contact): still one scar.
    a = np.zeros((6, 6), dtype=bool)
    a[0:3, 0:3] = True
    a[3:6, 3:6] = True
    assert len(_groups(a)) == 1


def test_labels_are_compact_and_start_at_zero():
    a = np.zeros((3, 9), dtype=bool)
    a[1, 0] = a[1, 4] = a[1, 8] = True
    _, _, labels = _label(a)
    assert sorted(set(labels.tolist())) == [0, 1, 2]


def test_no_wrap_around_between_row_end_and_next_row_start():
    # (0, 4) is the last column and (1, 0) the first column of the next row:
    # adjacent in a flattened index but 4 columns apart on the grid.
    a = np.zeros((2, 5), dtype=bool)
    a[0, 4] = True
    a[1, 0] = True
    assert len(_groups(a)) == 2


def test_spiral_forces_many_hooking_rounds():
    # A long serpentine path: a naive single-pass labelling would split it.
    a = np.zeros((9, 9), dtype=bool)
    a[0, :] = True
    a[:, 8] = True
    a[8, :] = True
    a[2:9, 0] = True
    a[2, 0:7] = True
    a[2:7, 6] = True
    a[6, 2:7] = True
    assert len(_groups(a)) == 1


def _bfs_groups(array):
    array = np.asarray(array, dtype=bool)
    seen = np.zeros_like(array)
    groups = set()
    h, w = array.shape
    for r0 in range(h):
        for c0 in range(w):
            if array[r0, c0] and not seen[r0, c0]:
                comp, queue = set(), deque([(r0, c0)])
                seen[r0, c0] = True
                while queue:
                    r, c = queue.popleft()
                    comp.add((r, c))
                    for dr in (-1, 0, 1):
                        for dc in (-1, 0, 1):
                            rr, cc = r + dr, c + dc
                            if 0 <= rr < h and 0 <= cc < w and array[rr, cc] and not seen[rr, cc]:
                                seen[rr, cc] = True
                                queue.append((rr, cc))
                groups.add(frozenset(comp))
    return groups


@pytest.mark.parametrize("seed", range(8))
@pytest.mark.parametrize("density", [0.15, 0.4, 0.6])
def test_matches_independent_bfs_on_random_arrays(seed, density):
    rng = np.random.default_rng(seed)
    a = rng.random((30, 40)) < density
    assert _groups(a) == _bfs_groups(a)
