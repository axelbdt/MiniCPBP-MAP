%-----------------------------------------------------------------------------%
% bin_packing_load: load[b] is the total weight in bin b; bins are the index
% set of 'load'. FlatZinc arrays are 1-based, so the first bin index is
% passed explicitly.
%-----------------------------------------------------------------------------%

predicate minicpbp_bin_packing_load(array[int] of var int: load,
                                    array[int] of var int: bin,
                                    array[int] of int: weight, int: first_bin);

predicate fzn_bin_packing_load(array[int] of var int: load,
                               array[int] of var int: bin,
                               array[int] of int: weight) =
  if length(bin) = 0 then forall (b in index_set(load)) (load[b] = 0)
  else minicpbp_bin_packing_load(load, bin, weight, min(index_set(load)))
  endif;
