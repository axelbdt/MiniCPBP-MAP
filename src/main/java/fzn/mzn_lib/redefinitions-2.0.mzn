%-----------------------------------------------------------------------------%
% FlatZinc builtins added in MiniZinc 2.0 that MiniCPBP posts natively.
% This file replaces the standard library's redefinitions-2.0.mzn, whose
% float variants are left out: MiniCPBP has no float variables.
%-----------------------------------------------------------------------------%

predicate bool_clause_reif(array[int] of var bool: as, array[int] of var bool: bs,
                           var bool: b);

predicate array_int_maximum(var int: m, array[int] of var int: x);

predicate array_int_minimum(var int: m, array[int] of var int: x);
