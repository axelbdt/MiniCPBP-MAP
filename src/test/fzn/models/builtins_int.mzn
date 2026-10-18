% Integer built-ins: arithmetic, element, min/max, reified linear constraints.
% expect-native: bool_lt_reif
% expect-native: int_lin_ne_reif
array[1..5] of var 0..6: x;
array[1..4] of var bool: b;
var -5..5: y; var 1..3: z; var 0..30: w;
constraint b[1] <-> (b[2] == b[3]);
constraint b[2] <-> (b[4] -> b[3]);
constraint b[3] <-> (b[1] < b[4]);
constraint b[4] <-> (x[1] in {1,3,5});
constraint sum(i in 1..4)(bool2int(b[i])) = 2;
constraint max(x) = 5 /\ min(x) = 1;
constraint y = x[2] - x[3];
constraint abs(y) = 2;
constraint w = x[4] * x[5];
constraint x[4] div z = 1 /\ x[5] mod z = 0;
constraint x[1] != x[2] \/ (x[3] + 2*x[4] != 7);
constraint (b[1] xor b[2]) \/ (x[1] > x[2]);
constraint (x[1] < x[2]) == (x[2] < x[3]);
constraint x[z] >= 2;
constraint [b[1],b[2],b[3]][z];
solve satisfy;
