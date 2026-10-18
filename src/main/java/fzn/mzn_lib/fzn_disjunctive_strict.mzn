%-----------------------------------------------------------------------------%
% disjunctive_strict: no two tasks overlap, zero-duration tasks included.
% With fixed positive durations this equals disjunctive and MiniCPBP's
% propagator is posted; otherwise the pairwise decomposition.
%-----------------------------------------------------------------------------%

include "fzn_disjunctive.mzn";

predicate fzn_disjunctive_strict(array[int] of var int: s, array[int] of var int: d) =
  if is_fixed(d) /\ forall (i in index_set(d)) (fix(d[i]) > 0) then
    fzn_disjunctive(s, d)
  else
    forall (i in index_set(d)) (d[i] >= 0) /\
    forall (i, j in index_set(d) where i < j) (
      s[i] + d[i] <= s[j] \/ s[j] + d[j] <= s[i]
    )
  endif;
