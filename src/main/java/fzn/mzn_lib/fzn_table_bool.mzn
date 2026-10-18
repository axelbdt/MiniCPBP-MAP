%-----------------------------------------------------------------------------%
% table over Boolean variables; the table is passed row-major, as for
% fzn_table_int.
%-----------------------------------------------------------------------------%

predicate minicpbp_table_bool(array[int] of var bool: x, array[int] of bool: t);

predicate fzn_table_bool(array[int] of var bool: x, array[int, int] of bool: t) =
  minicpbp_table_bool(x, array1d(t));
