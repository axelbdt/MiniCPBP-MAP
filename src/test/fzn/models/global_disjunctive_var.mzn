% disjunctive_strict with a zero duration and disjunctive with variable
% durations: both use the pairwise decomposition.
include "disjunctive.mzn";
include "disjunctive_strict.mzn";
array[1..3] of var 0..6: s;
array[1..3] of var 0..6: t;
array[1..3] of var 0..2: d;
constraint disjunctive_strict(s, [2, 0, 3]);
constraint disjunctive(t, d);
constraint sum(d) = 5;
solve minimize max([s[1] + 2, s[2], s[3] + 3]) + max(i in 1..3)(t[i] + d[i]);
