%-----------------------------------------------------------------------------%
% count_geq: c >= the number of occurrences of y in x, through count_eq.
%-----------------------------------------------------------------------------%

include "fzn_count_eq.mzn";

predicate fzn_count_geq(array[int] of var int: x, var int: y, var int: c) =
  let { var 0..length(x): n } in fzn_count_eq(x, y, n) /\ n <= c;
