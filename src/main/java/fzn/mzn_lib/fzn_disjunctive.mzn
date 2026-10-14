%-----------------------------------------------------------------------------%
% disjunctive: tasks with positive duration do not overlap; zero-duration
% tasks are unconstrained. MiniCPBP's propagator needs fixed durations;
% otherwise the pairwise decomposition is posted.
%-----------------------------------------------------------------------------%

predicate minicpbp_disjunctive(array[int] of var int: s, array[int] of int: d);

predicate fzn_disjunctive(array[int] of var int: s, array[int] of var int: d) =
  if is_fixed(d) then
    let {
      set of int: T = {i | i in index_set(s) where fix(d[i]) > 0};
    } in
    forall (i in index_set(d)) (fix(d[i]) >= 0) /\
    if card(T) <= 1 then true
    else minicpbp_disjunctive([s[i] | i in T], [fix(d[i]) | i in T])
    endif
  else
    forall (i in index_set(d)) (d[i] >= 0) /\
    forall (i, j in index_set(d) where i < j) (
      d[i] = 0 \/ d[j] = 0 \/ s[i] + d[i] <= s[j] \/ s[j] + d[j] <= s[i]
    )
  endif;
