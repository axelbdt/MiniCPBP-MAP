%-----------------------------------------------------------------------------%
% value_precede_chain: each value of 'order' precedes the next one in x.
% Posted as one precedence automaton (Regular) when the values are distinct,
% otherwise as a chain of pairwise precedences.
%-----------------------------------------------------------------------------%

include "fzn_value_precede_int.mzn";

predicate fzn_value_precede_chain_int(array[int] of int: order, array[int] of var int: x) =
  if length(x) = 0 \/ length(order) <= 1 then true
  elseif card(array2set(order)) = length(order) then minicpbp_value_precede(order, x)
  else forall (i in min(index_set(order))..max(index_set(order)) - 1) (
         fzn_value_precede_int(order[i], order[i + 1], x))
  endif;
