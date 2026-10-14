% Table constraint (2D table passed row-major) under optimization.
% expect-native: minicpbp_table_int
include "table.mzn";
include "alldifferent.mzn";
array[1..4] of var 1..6: x;
constraint table([x[1], x[2]], [|1,2|2,3|3,4|4,5|5,6|]);
constraint table([x[3], x[4]], [|6,1|5,2|4,3|]);
constraint alldifferent(x);
solve maximize x[1] + x[3];
