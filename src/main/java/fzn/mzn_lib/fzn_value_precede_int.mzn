%-----------------------------------------------------------------------------%
% value_precede: if t occurs in x, s occurs before its first occurrence.
% Posted as a precedence automaton (Regular).
%-----------------------------------------------------------------------------%

predicate minicpbp_value_precede(array[int] of int: values, array[int] of var int: x);

predicate fzn_value_precede_int(int: s, int: t, array[int] of var int: x) =
  if length(x) = 0 \/ s = t then true
  else minicpbp_value_precede([s, t], x)
  endif;
