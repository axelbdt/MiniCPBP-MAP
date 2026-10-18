% disjunctive with a zero-duration task (unconstrained), and
% disjunctive_strict with positive durations (same propagator).
% expect-native: minicpbp_disjunctive
include "disjunctive.mzn";
include "disjunctive_strict.mzn";
array[1..4] of var 0..10: s;
array[1..3] of var 0..10: t;
constraint disjunctive(s, [3, 2, 0, 4]);
constraint disjunctive_strict(t, [2, 2, 3]);
constraint s[3] = 4;
constraint t[1] >= s[2];
solve minimize max([s[1] + 3, s[2] + 2, s[4] + 4]) + max([t[1] + 2, t[2] + 2, t[3] + 3]);
