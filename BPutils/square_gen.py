from copy import deepcopy
from random import shuffle, choice


# --- Latin square generation and helpers ---
def rls(n):
    if n <= 0:
        return []
    else:
        symbols = list(range(n))
        square = _rls(symbols)
        return _shuffle_transpose_shuffle(square)


def _shuffle_transpose_shuffle(matrix):
    square = deepcopy(matrix)
    shuffle(square)
    trans = list(zip(*square))
    shuffle(trans)
    return trans


def _rls(symbols):
    n = len(symbols)
    if n == 1:
        return [symbols]
    else:
        sym = choice(symbols)
        symbols.remove(sym)
        square = _rls(symbols)
        square.append(square[0].copy())
        for i in range(n):
            square[i].insert(i, sym)
        return square


def _check(square):
    transpose = list(zip(*square))
    assert _check_rows(square) and _check_rows(transpose), "Not a Latin square"


def _check_rows(square):
    if not square:
        return True
    set_row0 = set(square[0])
    return all(len(row) == len(set(row)) and set(row) == set_row0 for row in square)
