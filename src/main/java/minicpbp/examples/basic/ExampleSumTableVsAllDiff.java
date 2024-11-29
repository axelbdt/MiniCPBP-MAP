package minicpbp.examples.basic;

import minicpbp.engine.constraints.AllDifferentDCMAP;
import minicpbp.engine.constraints.MaximizeOracle;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Search;
import minicpbp.util.Procedure;

import java.util.Arrays;
import java.util.function.Supplier;

import static minicpbp.cp.BranchingScheme.maxMarginalRegret;
import static minicpbp.cp.Factory.*;

public class ExampleSumTableVsAllDiff {
    static int[][] tableAllDiff = new int[][]{
            {1, 2, 3},
            {1, 2, 4},
            {1, 3, 2},
            {1, 3, 4},
            {1, 4, 2},
            {1, 4, 3},
            {2, 1, 3},
            {2, 1, 4},
            {2, 3, 1},
            {2, 3, 4},
            {2, 4, 1},
            {2, 4, 3},
            {3, 1, 2},
            {3, 1, 4},
            {3, 2, 1},
            {3, 2, 4},
            {3, 4, 1},
            {3, 4, 2},
            {4, 1, 2},
            {4, 1, 3},
            {4, 2, 1},
            {4, 2, 3},
            {4, 3, 1},
            {4, 3, 2}
    };

    public static void main(String[] args) {
        Solver cp = makeSolver();
        IntVar a = makeIntVar(cp, 1, 4);
        IntVar b = makeIntVar(cp, 1, 4);
        IntVar c = makeIntVar(cp, 1, 4);
        IntVar[] allVars = new IntVar[]{a, b, c};

        a.setName("a");
        b.setName("b");
        c.setName("c");

        System.out.println("Sum product");
        boolean oracle = true;
        // oracle = false;
        System.out.println("oracle: " + oracle);
        IntVar[] objective = new IntVar[]{a};
        System.out.println("objective: " + Arrays.stream(objective).map(IntVar::getName).collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString());

        cp.post(new AllDifferentDCMAP(new IntVar[]{a, b, c}));
        // cp.post(new TableCTMAP(new IntVar[]{a, b, c}, tableAllDiff));

        IntVar z = makeIntVar(cp, 0, 16);
        z.setName("z");
        cp.post(sum(objective, z));
        if (oracle) {
            cp.post(new MaximizeOracle(z));
        }

        cp.setTraceBPFlag(true);
        cp.setTraceSearchFlag(true);
        Supplier<Procedure[]> branchingProcedure = maxMarginalRegret(allVars);
        Search search = makeDfs(cp, branchingProcedure);
        search.onSolution(() -> {
            System.out.println("solution: " + a.min() + " " + b.min() + " " + c.min());
        });

        // cp.fixPoint();
        // cp.vanillaBP(10);
        // cp.beliefPropa();
        search.solve(stat -> stat.isCompleted());
    }
}
