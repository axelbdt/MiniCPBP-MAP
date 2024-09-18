package minicpbp.examples.cops;

import minicpbp.cp.BranchingScheme;
import minicpbp.cp.Factory;
import minicpbp.engine.constraints.*;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.Objective;
import minicpbp.search.SearchStatistics;
import minicpbp.util.Procedure;
import minicpbp.util.io.TeeOutputStream;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.function.Supplier;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

public class LatinSquare {
    public enum Branching {
        MAX_MARGINAL,
        MAX_MARGINAL_REGRET,
        FIRST_FAIL,
        LEXICO,
        MAX_VALUE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum ObjectivePattern {
        DIAGONAL,
        CROSS;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum BPAlgorithm {
        NO_BP,
        SUM_PRODUCT,
        MAX_PRODUCT;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public record ExperimentSpec(BPAlgorithm bp, Branching branching, boolean oracle,
                                 int truncateRate) {
        public String toString() {
            String oracleStr = oracle ? "-oracle" : "";
            String truncateRateStr = truncateRate == 0 ? "" : "-truncate" + truncateRate;
            return String.format("%s-%s%s", bp, branching, oracleStr + truncateRateStr);
        }

    }

    private Solver cp;
    private IntVar[][] x;
    private IntVar[] xFlat;
    private IntVar[] objectiveVars;
    private DFSearch dfs;
    private Objective obj;

    public LatinSquare(int n, int nbHoles, int nbFile, BPAlgorithm bp, Branching branching, ObjectivePattern objective, boolean oracle, int truncateRate) {
        // Create solver and square variables
        cp = makeSolver();
        if (BPAlgorithm.NO_BP == bp) {
            cp.setMode(Solver.PropaMode.SP);
        }
        x = new IntVar[n][n];
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
                case NO_BP:
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
        objectiveVars = new IntVar[n];
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

        int maxObj = n * (n - 1);
        int minObj = Math.max(n, truncateRate * maxObj / 100);
        IntVar z = makeIntVar(cp, minObj, maxObj);
        switch (bp) {
            case NO_BP:
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
        xFlat = new IntVar[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                xFlat[i * n + j] = x[i][j];
            }
        }
        Supplier<Procedure[]> branchingProcedure;
        switch (branching) {
            case FIRST_FAIL:
                branchingProcedure = firstFail(xFlat);
                break;
            case LEXICO:
                branchingProcedure = lexico(xFlat);
                break;
            case MAX_VALUE:
                branchingProcedure = maxValue();
                break;
            case MAX_MARGINAL:
                branchingProcedure = maxMarginal(xFlat);
                break;
            case MAX_MARGINAL_REGRET:
                branchingProcedure = maxMarginalRegret(xFlat);
                break;
            default:
                throw new IllegalArgumentException("Unknown branching scheme: " + branching);
        }
        dfs = makeDfs(cp, branchingProcedure);
        dfs.onSolution(() -> {
            var myDFS = dfs;
            // sum of objectiveVars
            int sum = Arrays.stream(objectiveVars).mapToInt(IntVar::min).sum();
            System.out.println("NEW SOLUTION FOUND");
            System.out.println("solution score : " + sum);
        });

        obj = cp.maximize(z);
    }


    public SearchStatistics optimize() {
        return dfs.optimize(obj, stat -> stat.isCompleted());
    }

    /**
     * Max value selection.
     * It selects the largest value in all the variables.
     * Then it creates two branches:
     * the left branch assigning the variable to its maximum value;
     * the right branch removing this maximum value from the domain.
     *
     * @return a lexicographic branching strategy
     * @see Factory#makeDfs(Solver, Supplier)
     */
    public Supplier<Procedure[]> maxValue() {
        boolean tracing = x[0][0].getSolver().tracingSearch();
        return () -> {
            IntVar xs = BranchingScheme.selectMin(objectiveVars,
                    xi -> xi.size() > 1,
                    xi -> xi.max());
            if (xs == null) {
                IntVar xs2 = BranchingScheme.selectMin(xFlat,
                        xi -> xi.size() > 1,
                        xi -> 1); // any constant value
                if (xs2 == null)
                    return EMPTY;
                else {
                    int v = xs2.min();
                    return branch(
                            () -> {
                                if (tracing)
                                    System.out.println("### branching on " + xs.getName() + "=" + v);
                                branchEqual(xs2, v);
                            },
                            () -> {
                                if (tracing)
                                    System.out.println("### branching on " + xs.getName() + "!=" + v);
                                branchNotEqual(xs2, v);
                            });
                }
            } else {
                int v = xs.max();
                return branch(
                        () -> {
                            if (tracing)
                                System.out.println("### branching on " + xs.getName() + "=" + v);
                            branchEqual(xs, v);
                        },
                        () -> {
                            if (tracing)
                                System.out.println("### branching on " + xs.getName() + "!=" + v);
                            branchNotEqual(xs, v);
                        });
            }
        };
    }

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        int nbHoles = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        int nbFile = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        int modelNumber = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int truncateRate = args.length > 4 ? Integer.parseInt(args[4]) : 0;

        // file nb is cli argument
        ObjectivePattern objective = ObjectivePattern.DIAGONAL;

        ExperimentSpec[] searchSpecs = {
                new ExperimentSpec(BPAlgorithm.NO_BP, Branching.MAX_VALUE, false, truncateRate),
                new ExperimentSpec(BPAlgorithm.NO_BP, Branching.FIRST_FAIL, false, truncateRate),
                new ExperimentSpec(BPAlgorithm.SUM_PRODUCT, Branching.MAX_MARGINAL_REGRET, false, truncateRate),
                new ExperimentSpec(BPAlgorithm.SUM_PRODUCT, Branching.MAX_MARGINAL_REGRET, true, truncateRate),
                new ExperimentSpec(BPAlgorithm.MAX_PRODUCT, Branching.MAX_MARGINAL_REGRET, true, truncateRate),
        };
        var spec = searchSpecs[modelNumber];

        // Run the model
        try {
            FileOutputStream fos = new FileOutputStream(
                    String.format("logs/latin-square/latin-square%d-holes%d-%d-%s.out", n, nbHoles, nbFile, spec));
            PrintStream out = new PrintStream(new TeeOutputStream(fos, System.out), true);

            // Redirect System.out to our new PrintStream
            System.setOut(out);


            LatinSquare ls = new LatinSquare(n, nbHoles, nbFile, spec.bp, spec.branching, objective, spec.oracle, spec.truncateRate);

            System.out.println("INFO");
            System.out.println("n : " + n);
            System.out.println("nbHoles : " + nbHoles);
            System.out.println("nbFile : " + nbFile);
            System.out.println("bp : " + spec.bp);
            System.out.println("branchingScheme : " + spec.branching);
            System.out.println("oracle : " + spec.oracle);
            System.out.println("objective : " + objective);
            System.out.println("truncateRate : " + spec.truncateRate);

            System.out.println("START SEARCH");
            SearchStatistics stats = ls.optimize();
            System.out.println("END OF SEARCH");

            System.out.println(stats);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error while creating file", e);
        }
    }
}
