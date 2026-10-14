% Boolean table and lex; lex over arrays of different lengths uses the
% standard decomposition.
% expect-native: minicpbp_table_bool
% expect-native: minicpbp_lex_less_bool
% expect-native: minicpbp_lex_lesseq_bool
% expect-native: minicpbp_lex_less_int
include "globals.mzn";
array[1..4] of var bool: p;
array[1..4] of var bool: q;
array[1..3] of var 0..3: u;
array[1..2] of var 0..3: v;
constraint table([p[1], p[2], q[1]], [| true, false, true | false, true, true | false, false, false |]);
constraint lex_less(p, q);
constraint lex_lesseq(q, [true, true, false, p[3]]);
constraint lex_less(u, v);
constraint lex_less([u[1], u[2]], [v[2], u[3]]);
solve maximize sum(i in 1..4)(bool2int(p[i]) * i) + sum(u) + sum(v);
