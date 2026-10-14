%-----------------------------------------------------------------------------%
% nvalue: n is the number of distinct values taken by x.
%-----------------------------------------------------------------------------%

predicate minicpbp_nvalue(var int: n, array[int] of var int: x);

predicate fzn_nvalue(var int: n, array[int] of var int: x) =
  if length(x) = 0 then n = 0 else minicpbp_nvalue(n, x) endif;
