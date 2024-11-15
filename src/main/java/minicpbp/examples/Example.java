package minicpbp.examples;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Search;
import minicpbp.util.Procedure;

import java.util.function.Supplier;

import static minicpbp.cp.BranchingScheme.maxMarginalRegret;
import static minicpbp.cp.Factory.*;

public class Example {
    public static void main(String[] args) {
        Solver cp = makeSolver();
        IntVar a = makeIntVar(cp, 1, 4);
        IntVar b = makeIntVar(cp, 1, 4);
        IntVar c = makeIntVar(cp, 1, 4);
        IntVar d = makeIntVar(cp, 1, 4);
        IntVar[] allVars = new IntVar[]{a, b, c, d};

        a.setName("a");
        b.setName("b");
        c.setName("c");
        d.setName("d");

        cp.post(allDifferent(new IntVar[]{a, b, c}));
        cp.post(sum(allVars, 7));
        cp.post(lessOrEqual(c, d));

        // cp.post(new MaximizeOracle(a));

        cp.setTraceBPFlag(true);
        cp.setTraceSearchFlag(true);
        Supplier<Procedure[]> branchingProcedure = maxMarginalRegret(allVars);
        Search search = makeDfs(cp, branchingProcedure);
        search.onSolution(() -> {
            System.out.println("solution: " + a.min() + " " + b.min() + " " + c.min() + " " + d.min());
        });

        // cp.fixPoint();
        // cp.vanillaBP(10);
        // cp.beliefPropa();
        search.solve(stat -> stat.isCompleted());
    }
}
