% count family with fixed values (among) and with a variable value (decomposition).
% expect-native: minicpbp_count_eq
include "globals.mzn";
array[1..6] of var 1..4: x;
var 0..6: c; var 1..4: y;
constraint count(x, 3) = c;
constraint count_geq(x, 2, 2);
constraint count_gt(x, 1, 1);
constraint count_leq(x, 4, 1);
constraint count_lt(x, 4, 2);
constraint count_neq(x, 2, 1);
constraint count(x, y) = 1;
solve maximize c * 10 + y;
