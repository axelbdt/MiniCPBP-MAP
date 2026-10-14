%-----------------------------------------------------------------------------%
% bin_packing_capa: bins are the index set of 'capacity'. FlatZinc arrays are
% 1-based, so the first bin index is passed explicitly.
%-----------------------------------------------------------------------------%

predicate minicpbp_bin_packing_capa(array[int] of int: capacity,
                                    array[int] of var int: bin,
                                    array[int] of int: weight, int: first_bin);

predicate fzn_bin_packing_capa(array[int] of int: capacity,
                               array[int] of var int: bin,
                               array[int] of int: weight) =
  if length(bin) = 0 then true
  else minicpbp_bin_packing_capa(capacity, bin, weight, min(index_set(capacity)))
  endif;
