%-----------------------------------------------------------------------------%
% cumulative: at any time the resource use of the running tasks is at most b.
% MiniCPBP's propagator needs fixed durations, demands and capacity; tasks
% with zero duration or demand use no resource and are dropped. Otherwise the
% task-based decomposition is posted: the load at every start time is <= b.
%-----------------------------------------------------------------------------%

predicate minicpbp_cumulative(array[int] of var int: s, array[int] of int: d,
                              array[int] of int: r, int: b);

predicate fzn_cumulative(array[int] of var int: s, array[int] of var int: d,
                         array[int] of var int: r, var int: b) =
  if is_fixed(d) /\ is_fixed(r) /\ is_fixed(b) then
    let {
      set of int: T = {i | i in index_set(s) where fix(d[i]) > 0 /\ fix(r[i]) > 0};
    } in
    if card(T) = 0 then length(s) = 0 \/ fix(b) >= 0
    else minicpbp_cumulative([s[i] | i in T], [fix(d[i]) | i in T],
                             [fix(r[i]) | i in T], fix(b))
    endif
  else
    forall (j in index_set(s)) (
      b >= sum (i in index_set(s)) (bool2int(s[i] <= s[j] /\ s[j] < s[i] + d[i]) * r[i])
    ) /\ (length(s) = 0 \/ b >= 0)
  endif;
