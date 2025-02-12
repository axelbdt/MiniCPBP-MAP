package minicpbp.examples.basic;

import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Objective;
import minicpbp.search.Search;
import minicpbp.util.Procedure;

import java.util.function.Supplier;

import static minicpbp.cp.BranchingScheme.maxMarginalRegret;
import static minicpbp.cp.Factory.*;

public class Example13 {
    public static void main(String[] args) {
        Solver cp = makeSolver();
        cp.setBPAlgorithm(Solver.BPAlgorithm.MAX_PRODUCT);

        IntVar a = makeIntVar(cp, 1, 4);
        IntVar b = makeIntVar(cp, 1, 4);
        IntVar c = makeIntVar(cp, 1, 4);
        IntVar d = makeIntVar(cp, 1, 4);

        // IntVar a = makeIntVar(cp, 2, 3);
        // IntVar b = makeIntVar(cp, 2, 3);
        // IntVar c = makeIntVar(cp, 1, 1);
        // IntVar d = makeIntVar(cp, 1, 1);
        IntVar[] allVars = new IntVar[]{a, b, c, d};

        a.setName("a");
        b.setName("b");
        c.setName("c");
        d.setName("d");

        IntVar objective_var = a;
        String max_min = "minimize";
        // max_min = "maximize";
        Objective objective;
        if (max_min.equals("minimize")) {
            objective = cp.minimize(objective_var);
        } else if (max_min.equals("maximize")) {
            objective = cp.maximize(objective_var);
        } else {
            throw new IllegalArgumentException("Invalid max_min: " + max_min);
        }
        cp.setOracleOnObjective(true);
        System.out.println(max_min);
        System.out.println("objective: " + objective_var.getName());
        System.out.println("oracle: " + objective.getSolver().getOracleOnObjective());

        // Set constraints
        Constraint allDiffC = allDifferent(new IntVar[]{a, b, c});
        Constraint sumC = sum(allVars, 13);
        Constraint lessOrEqualC = lessOrEqual(d, c);

        allDiffC.setName("allDiff");
        sumC.setName("sum");
        lessOrEqualC.setName("lessOrEqual");

        cp.post(allDiffC);
        cp.post(sumC);
        cp.post(lessOrEqualC);

        // configure search
        cp.setSwitchToSumProductAfterSolution(false);
        cp.setTraceBPFlag(true);
        cp.setTraceSearchFlag(true);
        Supplier<Procedure[]> branchingProcedure = maxMarginalRegret(allVars);
        Search search = makeDfs(cp, branchingProcedure);
        search.onSolution(() -> {
            System.out.println("SOLUTION FOUND: " + a.min() + " " + b.min() + " " + c.min() + " " + d.min());
        });

        var stats = search.optimize(objective, stat -> stat.isCompleted());

        System.out.println(stats);
    }
}
