% Variable durations and capacity: the decomposition fallback is used.
include "cumulative.mzn";
array[1..4] of var 0..8: s;
array[1..4] of var 1..3: d;
var 2..3: cap;
constraint cumulative(s, d, [2, 1, 2, 1], cap);
constraint sum(d) >= 9;
solve minimize max(i in 1..4)(s[i] + d[i]) + cap;
