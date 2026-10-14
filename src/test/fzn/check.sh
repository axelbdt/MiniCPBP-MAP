#!/usr/bin/env bash
# End-to-end check of the FlatZinc front end.
#
# Each model in models/ (or each model given as argument) is compiled with
# MiniCPBP's mzn_lib, the .fzn file (with MiniZinc's comment header) is solved
# by MiniCPBP, its output goes through MiniZinc's solns2out, and the solution is
# verified by a reference solver: the solution is passed back as data, so the
# reference solver must find the model satisfiable with those values fixed.
# Optimization models proven optimal by MiniCPBP must match the reference
# optimum.
#
# Directives, as comment lines in a model:
#   % expect-native: NAME   the FlatZinc must post constraint NAME (not a decomposition)
#   % expect: UNSAT         both solvers must report unsatisfiability
#
# Models must have no output item: MiniZinc then prints every decision
# variable, which is what the reference solver needs to verify the solution.
#
# Environment:
#   MINIZINC     minizinc executable (default: minizinc)
#   MINICPBP_CP  classpath for the solver (default: target/minicpbp-1.0.jar)
#   REF_SOLVER   reference solver id (default: gecode)
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MCP="$(cd "$HERE/../../.." && pwd)"
MINIZINC="${MINIZINC:-minizinc}"
MINICPBP_CP="${MINICPBP_CP:-$MCP/target/minicpbp-1.0.jar}"
REF_SOLVER="${REF_SOLVER:-gecode}"

command -v "$MINIZINC" >/dev/null || { echo "minizinc not found (set MINIZINC)"; exit 2; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/fzn-minicpbp" <<WRAP
#!/usr/bin/env bash
exec java -cp "$MINICPBP_CP" launch.SolveXCSPFZN --input "\${!#}" --branching min-entropy --search-type dfs --timeout 60
WRAP
chmod +x "$TMP/fzn-minicpbp"
cat > "$TMP/minicpbp.msc" <<MSC
{"id": "org.minicpbp.check", "name": "MiniCPBP (check)", "version": "1.0",
 "mznlib": "$MCP/src/main/java/fzn/mzn_lib", "executable": "$TMP/fzn-minicpbp",
 "tags": ["cp", "int"], "stdFlags": [], "supportsMzn": false, "supportsFzn": true,
 "needsSolns2Out": true}
MSC
SOLVER="$TMP/minicpbp.msc"

if [ $# -gt 0 ]; then models=("$@"); else models=("$HERE"/models/*.mzn); fi

fail=0
for m in "${models[@]}"; do
  name="$(basename "$m" .mzn)"
  problems=()

  if ! "$MINIZINC" --solver "$SOLVER" -c "$m" --fzn "$TMP/$name.fzn" --ozn "$TMP/$name.ozn" \
       --output-mode dzn --output-objective >"$TMP/$name.compile" 2>&1; then
    problems+=("compilation failed: $(grep -m1 -i error "$TMP/$name.compile")")
  else
    for native in $(sed -n 's/^% expect-native: *//p' "$m"); do
      grep -q "^constraint $native(" "$TMP/$name.fzn" || problems+=("$native not posted natively")
    done
  fi

  if [ ${#problems[@]} -eq 0 ]; then
    out="$TMP/$name.out"
    "$TMP/fzn-minicpbp" "$TMP/$name.fzn" >"$TMP/$name.raw" 2>"$TMP/$name.err"
    if [ $? -ne 0 ] || grep -q 'Exception' "$TMP/$name.err" \
       || ! "$MINIZINC" --ozn-file "$TMP/$name.ozn" <"$TMP/$name.raw" >"$out" 2>>"$TMP/$name.err"; then
      problems+=("solver failed: $(grep -v '_JAVA_OPTIONS' "$TMP/$name.err" | head -3 | tr '\n' ' ')")
    elif grep -q '^% expect: UNSAT' "$m"; then
      grep -q '=====UNSATISFIABLE=====' "$out" || problems+=("expected UNSAT")
    elif ! grep -q '^----------' "$out"; then
      problems+=("no solution: $(tr '\n' ' ' <"$out" | cut -c1-200)")
    else
      # last solution block, without the objective line
      awk '/^----------/{last=buf; buf=""; next} /^=====/{next} {buf=buf $0 "\n"} END{printf "%s", last}' "$out" \
        | grep -v '^_objective' > "$TMP/$name.dzn"
      if "$MINIZINC" --solver "$REF_SOLVER" "$m" "$TMP/$name.dzn" 2>&1 | grep -q 'UNSATISFIABLE\|rror'; then
        problems+=("solution rejected by $REF_SOLVER: $(tr '\n' ' ' <"$TMP/$name.dzn")")
      fi
      if grep -q '^==========' "$out" && grep -q '^_objective' "$out"; then
        mine="$(grep '^_objective' "$out" | tail -1)"
        ref="$("$MINIZINC" --solver "$REF_SOLVER" "$m" --output-mode dzn --output-objective | grep '^_objective' | tail -1)"
        [ "$mine" = "$ref" ] || problems+=("optimum $mine, $REF_SOLVER says $ref")
      fi
    fi
  fi

  if [ ${#problems[@]} -eq 0 ]; then
    echo "ok   $name"
  else
    fail=1
    for p in "${problems[@]}"; do echo "FAIL $name: $p"; done
  fi
done
exit $fail
