% regular: no three consecutive 1s, never 3 right after 2, end in state 1 or 2.
% x's domain includes values outside the alphabet 1..3.
% expect-native: minicpbp_regular
include "regular.mzn";
array[1..8] of var 0..4: x;
constraint regular(x, 4, 3,
  [| 2, 1, 1
   | 3, 4, 1
   | 0, 4, 1
   | 2, 4, 0 |], 1, {1, 2});
solve maximize sum(i in 1..8)(x[i] * i);
