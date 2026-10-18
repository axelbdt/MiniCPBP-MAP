%-----------------------------------------------------------------------------%
% count_eq: c is the number of occurrences of y in x. With y fixed this is an
% among constraint over a single value; otherwise the sum decomposition.
%-----------------------------------------------------------------------------%

predicate minicpbp_count_eq(array[int] of var int: x, int: y, var int: c);

predicate fzn_count_eq(array[int] of var int: x, var int: y, var int: c) =
  if length(x) = 0 then c = 0
  elseif is_fixed(y) then minicpbp_count_eq(x, fix(y), c)
  else c = sum (i in index_set(x)) (bool2int(x[i] = y))
  endif;
