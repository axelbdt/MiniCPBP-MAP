% Search annotations: the annotated variables are branched on first
% (seq_search of int_search and bool_search), then all others.
array[1..4] of var 1..4: x;
array[1..3] of var bool: b;
var 1..10: y;
constraint forall(i in 1..3)(x[i] != x[i+1]);
constraint b[1] <-> x[1] > x[4];
constraint b[2] \/ b[3];
constraint y = x[2] + x[3];
solve :: seq_search([
    int_search([x[4], x[1]], input_order, indomain_max),
    bool_search(b, input_order, indomain_min)])
  maximize y + 2 * bool2int(b[1]);
