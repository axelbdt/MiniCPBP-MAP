% Boolean built-ins and reifications emitted by MiniZinc's standard library.
% expect-native: bool_eq_reif
% expect-native: set_in_reif
array[1..3] of var bool: b;
array[1..3] of var 1..5: x;
var 1..5: y;
constraint b[1] = (y in {1,3});
constraint b[2] <-> (b[1] /\ b[3]);
constraint b[3] <-> (b[1] \/ (y > 2));
constraint b[1] -> x[1] < x[2];
constraint (2*x[1] + x[2] < y) \/ b[2];
constraint bool2int(b[1]) + bool2int(b[2]) + 2*bool2int(b[3]) = 2;
constraint x[3] = pow(y, 2) div 5;
constraint b[1] = (x[3] != y);
constraint (b[1] < b[2]) \/ (b[2] <= b[3]);
constraint (b[1] == b[2]) \/ x[1] = 3;
constraint not (b[1] xor b[3]) \/ x[2] = 2;
solve satisfy;
