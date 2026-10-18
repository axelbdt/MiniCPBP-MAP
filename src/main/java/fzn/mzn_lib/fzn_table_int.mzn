%-----------------------------------------------------------------------------%
% A table constraint table(x, t) represents the constraint x in t where we
% consider each row in t to be a tuple and t as a set of tuples.
% FlatZinc builtins take one-dimensional arrays only, so the table is passed
% row-major and reshaped by the solver.
%-----------------------------------------------------------------------------%

predicate minicpbp_table_int(array[int] of var int: x, array[int] of int: t);

predicate fzn_table_int(array[int] of var int: x, array[int, int] of int: t) =
  minicpbp_table_int(x, array1d(t));
