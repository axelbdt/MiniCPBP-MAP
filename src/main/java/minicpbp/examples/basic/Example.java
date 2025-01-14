package minicpbp.examples.basic;

import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Objective;
import minicpbp.search.Search;
import minicpbp.util.Procedure;

import java.util.function.Supplier;

import static minicpbp.cp.BranchingScheme.minMarginal;
import static minicpbp.cp.Factory.*;

public class Example {
    public static void main(String[] args) {
        Solver cp = makeSolver();
        cp.setBPAlgorithm(Solver.BPAlgorithm.MAX_PRODUCT);

        IntVar a = makeIntVar(cp, 1, 4);
        IntVar b = makeIntVar(cp, 1, 4);
        IntVar c = makeIntVar(cp, 1, 4);
        IntVar d = makeIntVar(cp, 1, 4);
        IntVar[] allVars = new IntVar[]{a, b, c, d};

        a.setName("a");
        b.setName("b");
        c.setName("c");
        d.setName("d");

        IntVar objective_var = a;
        cp.setOracleOnObjective(false);
        Objective objective = cp.maximize(objective_var);
        System.out.println("objective: " + objective_var.getName());

        // Set constraints
        Constraint allDiffC = allDifferent(new IntVar[]{a, b, c});
        Constraint sumC = sum(allVars, 7);
        Constraint lessOrEqualC = lessOrEqual(c, d);

        allDiffC.setName("allDiff");
        sumC.setName("sum");
        lessOrEqualC.setName("lessOrEqual");

        cp.post(allDiffC);
        cp.post(sumC);
        cp.post(lessOrEqualC);

        // configure search
        cp.setSwitchToSumProductAfterSolution(true);
        cp.setTraceBPFlag(true);
        cp.setTraceSearchFlag(true);
        Supplier<Procedure[]> branchingProcedure = minMarginal(allVars);
        Search search = makeDfs(cp, branchingProcedure);
        search.onSolution(() -> {
            System.out.println("SOLUTION FOUND: " + a.min() + " " + b.min() + " " + c.min() + " " + d.min());
        });

        search.optimize(objective, stat -> stat.isCompleted());
    }
}
