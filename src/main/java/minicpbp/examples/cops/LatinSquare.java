package minicpbp.examples.cops;

import minicpbp.engine.constraints.*;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.Objective;
import minicpbp.search.SearchStatistics;
import minicpbp.util.Procedure;

import java.io.FileReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.function.Supplier;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

public class LatinSquare {
    private Solver cp;
    private IntVar[][] x;
    private DFSearch dfs;
    private Objective obj;

    public LatinSquare(int n, int nbHoles, int nbFile, Experiment.BPAlgorithm bp, Experiment.BranchingScheme branchingScheme, Experiment.ObjectivePattern objective, boolean oracle) {
        // Create solver and square variables
        Solver cp = makeSolver();
        IntVar[][] x = new IntVar[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                x[i][j] = makeIntVar(cp, 0, n - 1);
                x[i][j].setName("x" + "[" + i + "," + j + "]");
            }
        }

        // add constraints
        for (int i = 0; i < x.length; i++) {
            IntVar[] row = x[i];
            IntVar[] col = new IntVar[n];
            for (int j = 0; j < n; j++) {
                col[j] = x[j][i];
            }

            switch (bp) {
                case SUM_PRODUCT:
                    cp.post(new AllDifferentDC(row));
                    cp.post(new AllDifferentDC(col));
                    break;
                case MAX_PRODUCT:
                    cp.post(new AllDifferentDCMAP(row));
                    cp.post(new AllDifferentDCMAP(col));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown BP algorithm: " + bp);
            }
        }

        // create objective and bind it to the variables
        IntVar[] objectiveVars = new IntVar[n];
        switch (objective) {
            case DIAGONAL:
                for (int i = 0; i < n; i++) {
                    objectiveVars[i] = x[i][i];
                }
                break;
            case CROSS:
                if (!(n % 4 == 0 || n % 4 == 1)) {
                    throw new IllegalArgumentException("n must 4k or 4k+1");
                }
                for (int i = 0; i < (n / 4); i++) {
                    objectiveVars[4 * i] = x[n / 2 + i - 1][n / 2 - i];
                    objectiveVars[4 * i + 1] = x[n / 2 - i][n / 2 + i - 1];
                    objectiveVars[4 * i + 2] = x[n / 2 - i][n / 2 + i - 1];
                    objectiveVars[4 * i + 3] = x[n / 2 + i - 1][n / 2 - i];
                }
                if (n % 4 == 1) {
                    objectiveVars[n - 1] = x[n / 2][n / 2];
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown objective: " + objective);
        }

        IntVar z = makeIntVar(cp, 0, n * (n - 1));
        switch (bp) {
            case SUM_PRODUCT:
                cp.post(new SumDC(objectiveVars, z));
                break;
            case MAX_PRODUCT:
                cp.post(new SumDCMAP(objectiveVars, z));
                break;
        }

        // add preference for larger values
        if (oracle) {
            cp.post(new MaximizeOracle(z));
        }

        try {
            String filename = String.format("latin-square-%d-holes%d-%d.pls", n, nbHoles, nbFile);
            String filepath = Paths.get("data", "latin-square", filename).toString();
            Scanner scanner = new Scanner(new FileReader(filepath));

            scanner.nextLine(); // skip the first line

            for (int i = 0; i < n; i++) {
                String[] line = scanner.nextLine().trim().split("\\s+");
                for (int j = 0; j < n; j++) {
                    int value = Integer.parseInt(line[j]);
                    if (value != -1) {
                        // System.out.println(String.format("x[%d][%d] = %d", i, j, value));
                        x[i][j].assign(value);
                    }
                }
            }
            scanner.close();

        } catch (Exception e) {
            System.err.println("Error : " + e.getMessage());
            System.exit(2);
        }

        // add search
        IntVar[] xFlat = new IntVar[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                xFlat[i * n + j] = x[i][j];
            }
        }
        Supplier<Procedure[]> branchingProcedure;
        switch (branchingScheme) {
            case FIRST_FAIL:
                branchingProcedure = firstFail(xFlat);
                break;
            case MAX_MARGINAL:
                branchingProcedure = maxMarginal(xFlat);
                break;
            case MAX_MARGINAL_REGRET:
                branchingProcedure = maxMarginalRegret(xFlat);
                break;
            default:
                throw new IllegalArgumentException("Unknown branching scheme: " + branchingScheme);
        }
        dfs = makeDfs(cp, branchingProcedure);
        dfs.onSolution(() -> {
            var myDFS = dfs;
            // sum of objectiveVars
            int sum = Arrays.stream(objectiveVars).mapToInt(IntVar::min).sum();
            System.out.println("sum of objectiveVars = " + sum);
        });

        obj = cp.maximize(z);
    }


    public SearchStatistics optimize() {
        return dfs.optimize(obj, stat -> stat.isCompleted());
    }

    public static void main(String[] args) {
        int n = 12;
        int nbHoles = 79;
        int nbFile = 1;
        Experiment.BPAlgorithm bp = Experiment.BPAlgorithm.MAX_PRODUCT;
        Experiment.BranchingScheme branchingScheme = Experiment.BranchingScheme.MAX_MARGINAL_REGRET;
        Experiment.ObjectivePattern objective = Experiment.ObjectivePattern.DIAGONAL;
        boolean oracle = false;

        LatinSquare ls = new LatinSquare(n, nbHoles, nbFile, bp, branchingScheme, objective, oracle);
        SearchStatistics stats = ls.optimize();
        System.out.println(stats);
    }
}
