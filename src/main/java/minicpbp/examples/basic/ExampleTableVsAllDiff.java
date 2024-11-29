package minicpbp.examples.basic;

import minicpbp.engine.constraints.MaximizeOracle;
import minicpbp.engine.constraints.TableCTMAP;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Search;
import minicpbp.util.Procedure;

import java.util.function.Supplier;

import static minicpbp.cp.BranchingScheme.maxMarginalRegret;
import static minicpbp.cp.Factory.*;

public class ExampleTableVsAllDiff {
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

        System.out.println("Max product");
        boolean oracle = true;
        System.out.println("oracle: " + oracle);
        IntVar objective = a;
        System.out.println("objective: " + objective.getName());

        // cp.post(new AllDifferentDCMAP(new IntVar[]{a, b, c}));
        cp.post(new TableCTMAP(new IntVar[]{a, b, c}, tableAllDiff));

        cp.post(new MaximizeOracle(objective));

        cp.setTraceBPFlag(true);
        cp.setTraceSearchFlag(true);
        Supplier<Procedure[]> branchingProcedure = maxMarginalRegret(allVars);
        Search search = makeDfs(cp, branchingProcedure);
        search.onSolution(() -> {
            System.out.println("solution: " + a.min() + " " + b.min() + " " + c.min());
        });

        search.solve(stat -> stat.isCompleted());
    }
}
