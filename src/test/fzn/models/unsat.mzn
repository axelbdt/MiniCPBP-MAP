% expect: UNSAT
include "alldifferent.mzn";
array[1..4] of var 1..3: x;
constraint alldifferent(x);
solve satisfy;
