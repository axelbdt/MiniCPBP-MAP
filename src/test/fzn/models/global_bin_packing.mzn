% The three bin_packing forms. Capacities are indexed from 0, loads from 2,
% to exercise the explicit first-bin offset.
% expect-native: minicpbp_bin_packing
% expect-native: minicpbp_bin_packing_capa
% expect-native: minicpbp_bin_packing_load
include "bin_packing.mzn";
include "bin_packing_capa.mzn";
include "bin_packing_load.mzn";
array[1..6] of int: w = [4, 3, 3, 2, 2, 1];
array[1..6] of var 1..3: b1;
array[1..6] of var 0..3: b2;
array[1..6] of var 0..9: b3;
array[2..4] of var 0..15: load;
constraint bin_packing(6, b1, w);
constraint bin_packing_capa(array1d(0..2, [7, 5, 3]), b2, w);
constraint bin_packing_load(load, b3, w);
constraint load[2] = 2 * load[4];
solve minimize sum(b2) + load[3];
