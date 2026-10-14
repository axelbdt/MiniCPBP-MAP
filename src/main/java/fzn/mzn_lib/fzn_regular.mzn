%-----------------------------------------------------------------------------%
% regular: x is accepted by the DFA with states 1..Q, alphabet 1..S,
% transition table d (0 = failure state), start state q0 and final states F.
% The table is passed row-major: FlatZinc builtins take 1D arrays only.
%-----------------------------------------------------------------------------%

predicate minicpbp_regular(array[int] of var int: x, int: Q, int: S,
                           array[int] of int: d, int: q0, set of int: F);

predicate fzn_regular(array[int] of var int: x, int: Q, int: S,
                      array[int,int] of int: d, int: q0, set of int: F) =
  if length(x) = 0 then q0 in F
  else minicpbp_regular(x, Q, S, array1d(d), q0, F)
  endif;
