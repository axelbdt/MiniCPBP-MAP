package minicpbp.examples;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Search;

import static minicpbp.cp.BranchingScheme.firstFail;
import static minicpbp.cp.Factory.*;

public class Example {
    public static void main(String[] args) {
        Solver cp = makeSolver();
        IntVar a = makeIntVar(cp, 1, 4);
        IntVar b = makeIntVar(cp, 1, 4);
        IntVar c = makeIntVar(cp, 1, 4);
        IntVar d = makeIntVar(cp, 1, 4);

        a.setName("a");
        b.setName("b");
        c.setName("c");
        d.setName("d");

        cp.post(allDifferent(new IntVar[]{a, b, c}));
        cp.post(sum(new IntVar[]{a, b, c, d}, 7));
        cp.post(lessOrEqual(c, d));

        // cp.post(new MaximizeOracle(a));

        System.out.println("Model ok");

        Search search = makeDfs(cp, firstFail(a, b, c, d));
        search.onSolution(() -> {
            System.out.println("solution: " + a.min() + " " + b.min() + " " + c.min() + " " + d.min());
        });

        cp.setTraceBPFlag(true);
        cp.fixPoint();
        cp.vanillaBP(10);
        // cp.beliefPropa();
        // search.solve(stat -> stat.isCompleted());

        System.out.println("=== Assigning c=1 ===");
        c.assign(1);

        a.resetMarginals();
        b.resetMarginals();
        c.resetMarginals();
        d.resetMarginals();

        cp.fixPoint();
        cp.vanillaBP(10);
    }
}
