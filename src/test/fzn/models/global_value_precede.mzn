% value_precede and value_precede_chain as precedence automata; z includes
% negative values (Factory.precedence shifts them).
% expect-native: minicpbp_value_precede
include "globals.mzn";
array[1..5] of var 1..4: x;
array[1..5] of var -2..2: z;
constraint value_precede(3, 2, x);
constraint value_precede_chain([1, 2, 3], x);
constraint value_precede_chain([-1, 0, 2], z);
constraint z[1] != -1 /\ z[4] = 2;
solve maximize sum(i in 1..5)(x[i] * (6 - i)) + sum(z);
