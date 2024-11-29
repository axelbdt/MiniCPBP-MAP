package minicpbp.examples.basic;

import minicpbp.engine.constraints.*;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Search;
import minicpbp.util.Procedure;

import java.util.Arrays;
import java.util.function.Supplier;

import static minicpbp.cp.BranchingScheme.maxMarginalRegret;
import static minicpbp.cp.Factory.*;

public class ExampleMAPSum {
    static int[][] tableAllDiff0 = new int[][]{
            {2, 3, 1}, {3, 2, 1}
    };

    static int[][] tableSum0 = new int[][]{
            {2, 3, 1, 1}, {3, 2, 1, 1}
    };

    static int[][] tableLessOrEqual0 = new int[][]{
            {1, 1}
    };

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

    static int[][] tableSum = new int[][]{
            {1, 1, 1, 4},
            {1, 1, 2, 3},
            {1, 1, 3, 2},
            {1, 1, 4, 1},
            {1, 2, 1, 3},
            {1, 2, 3, 1},
            {1, 3, 1, 2},
            {1, 3, 2, 1},
            {1, 4, 1, 1},
            {2, 1, 1, 3},
            {2, 1, 3, 1},
            {2, 3, 1, 1},
            {3, 1, 1, 2},
            {3, 1, 2, 1},
            {3, 2, 1, 1},
            {4, 1, 1, 1}
    };

    static int[][] tableLessOrEqual = new int[][]{
            {1, 1},
            {1, 2},
            {1, 3},
            {1, 4},
            {2, 2},
            {2, 3},
            {2, 4},
            {3, 3},
            {3, 4},
            {4, 4}
    };


    enum AsTable {
        NONE,
        SUM,
        LESS_OR_EQUAL,
        ALL_DIFF,
        ALL
    }

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

        System.out.println("Max product");
        System.out.println("oracle: " + true);
        IntVar[] objective = new IntVar[]{a};
        System.out.println("objective: " + Arrays.stream(objective).map(IntVar::getName).collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString());
        AsTable asTable = AsTable.NONE;

        switch (asTable) {
            case ALL:
                cp.post(new TableCTMAP(allVars, new int[][]{{2, 3, 1, 1}, {3, 2, 1, 1}}));
                break;
            default:
                if (asTable == AsTable.ALL_DIFF) {
                    cp.post(new TableCTMAP(new IntVar[]{a, b, c}, tableAllDiff));
                } else {
                    cp.post(new AllDifferentDCMAP(new IntVar[]{a, b, c}));
                }

                if (asTable == AsTable.SUM) {
                    cp.post(new TableCTMAP(allVars, tableSum));
                } else {
                    cp.post(new SumDCMAP(allVars, 7));
                }
                if (asTable == AsTable.LESS_OR_EQUAL) {
                    cp.post(new TableCTMAP(new IntVar[]{c, d}, tableLessOrEqual));
                } else {
                    cp.post(new LessOrEqualMAP(c, d));
                }
                break;
        }

        IntVar z = makeIntVar(cp, 0, 16);
        z.setName("z");
        cp.post(new SumDCMAP(objective, z));

        MaximizeOracle maximizeOracle = new MaximizeOracle(z);
        maximizeOracle.setWeight(1);
        cp.post(maximizeOracle);


        cp.setTraceBPFlag(true);
        cp.setTraceSearchFlag(true);
        Supplier<Procedure[]> branchingProcedure = maxMarginalRegret(allVars);
        Search search = makeDfs(cp, branchingProcedure);
        search.onSolution(() -> {
            System.out.println("solution: " + a.min() + " " + b.min() + " " + c.min() + " " + d.min());
        });

        search.solve(stat -> stat.isCompleted());
    }
}
