%-----------------------------------------------------------------------------%
% lex_lesseq: x is lexicographically less than or equal to y. MiniCPBP's
% constraint needs two non-empty arrays of the same length; other cases use
% the standard decomposition.
%-----------------------------------------------------------------------------%

predicate minicpbp_lex_lesseq_int(array[int] of var int: x, array[int] of var int: y);

predicate fzn_lex_lesseq_int(array[int] of var int: x, array[int] of var int: y) =
  if length(x) = length(y) /\ length(x) > 0 then minicpbp_lex_lesseq_int(x, y)
  else lex_lesseq_std_decomposition(x, y)
  endif;
