% Small RCPSP. Task 5 has zero duration and task 6 zero demand: both are
% dropped from the propagator.
% expect-native: minicpbp_cumulative
include "cumulative.mzn";
array[1..6] of int: d = [3, 2, 4, 2, 0, 3];
array[1..6] of int: r = [2, 3, 1, 2, 5, 0];
array[1..6] of var 0..12: s;
constraint cumulative(s, d, r, 4);
constraint s[1] + d[1] <= s[3];
constraint s[2] + d[2] <= s[4];
constraint s[5] >= s[3];
solve minimize max(i in 1..6)(s[i] + d[i]);
