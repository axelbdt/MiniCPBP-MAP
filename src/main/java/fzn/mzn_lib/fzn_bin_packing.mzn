%-----------------------------------------------------------------------------%
% bin_packing: the load of every bin used by 'bin' is at most 'capacity'.
%-----------------------------------------------------------------------------%

predicate minicpbp_bin_packing(int: capacity, array[int] of var int: bin,
                               array[int] of int: weight);

predicate fzn_bin_packing(int: capacity, array[int] of var int: bin,
                          array[int] of int: weight) =
  if length(bin) = 0 then true
  else minicpbp_bin_packing(capacity, bin, weight)
  endif;
