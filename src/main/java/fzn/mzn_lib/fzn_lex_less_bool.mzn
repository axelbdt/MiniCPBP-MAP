%-----------------------------------------------------------------------------%
% lex_less: x is lexicographically less than y. MiniCPBP's
% constraint needs two non-empty arrays of the same length; other cases use
% the standard decomposition.
%-----------------------------------------------------------------------------%

predicate minicpbp_lex_less_bool(array[int] of var bool: x, array[int] of var bool: y);

predicate fzn_lex_less_bool(array[int] of var bool: x, array[int] of var bool: y) =
  if length(x) = length(y) /\ length(x) > 0 then minicpbp_lex_less_bool(x, y)
  else lex_less_std_decomposition(x, y)
  endif;
