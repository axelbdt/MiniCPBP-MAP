% Native array_int_maximum/minimum (redefinitions-2.0.mzn), nvalue and
% bool_clause_reif.
% expect-native: array_int_maximum
% expect-native: array_int_minimum
% expect-native: minicpbp_nvalue
% expect-native: bool_clause_reif
include "nvalue.mzn";
array[1..5] of var 1..6: x;
array[1..3] of var bool: b;
var bool: r;
constraint max(x) - min(x) = 3;
constraint nvalue(3, x);
constraint r <-> (b[1] \/ not b[2] \/ b[3] \/ x[1] > 4);
constraint r -> x[2] = 6;
constraint not r -> x[3] = 1;
solve minimize sum(x) + 3 * bool2int(r) - 2 * bool2int(b[2]);
