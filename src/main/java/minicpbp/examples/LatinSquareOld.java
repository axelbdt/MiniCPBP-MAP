/*
' * mini-cp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License  v3
 * as published by the Free Software Foundation.
 *
 * mini-cp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY.
 * See the GNU Lesser General Public License  for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with mini-cp. If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 *
 * Copyright (c)  2017. by Laurent Michel, Pierre Schaus, Pascal Van Hentenryck
 *
 * mini-cpbp, replacing classic propagation by belief propagation 
 * Copyright (c)  2019. by Gilles Pesant
 */

package minicpbp.examples;

import minicpbp.engine.constraints.AllDifferentDC;
import minicpbp.engine.constraints.AllDifferentDCMAP;
import minicpbp.engine.constraints.ObjectiveSum;
import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Search;
import minicpbp.search.SearchStatistics;
import minicpbp.util.io.TeeOutputStream;

import java.io.*;
import java.util.Arrays;
import java.util.Scanner;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

/**
 * The Magic Square Completion problem.
 * <a href="http://csplib.org/Problems/prob019/">CSPLib</a>.
 */
public class LatinSquareOld {

    static final String MAX_MARGINAL = "maxMarginal";
    static final String MAX_MARGINAL_REGRET_RANDOM_TIE_BREAK = "maxMarginalRegretRandomTieBreak";
    static final String FIRST_FAIL_RANDOM_VAL = "firstFailRandomVal";

    static final String MAX_PRODUCT_ORACLE = "max-product-oracle";
    static final String MAX_PRODUCT_INIT = "max-product-init";
    static final String SUM_PRODUCT_ORACLE = "sum-product-oracle";
    static final String SUM_PRODUCT_INIT = "sum-product-init";
    static final String SUM_PRODUCT_NO_INIT = "sum-product-no-init";
    static final String MAX_PRODUCT_INIT_EXP = "max-product-init-exp";
    static final String SUM_PRODUCT_INIT_EXP = "sum-product-init-exp";
    static final String MAX_PRODUCT_ORACLE_EXP = "max-product-oracle-exp";
    static final String SUM_PRODUCT_ORACLE_EXP = "sum-product-oracle-exp";
    static final String MAX_PRODUCT_OBJECTIVE = "max-product-objective";

    static final String DIAGONAL = "diagonal";
    static final String CROSS = "cross";

    public static int[] filledList(int n) {
        int first = (int) n * n * 8 / 20;
        int second = (int) n * n * 9 / 20;
        int[] result = {first, second};
        return result;
    }

    public static void main(String[] args) {
        new LatinSquareOld();
    }

    public LatinSquareOld() {
        int n = 8;
        int[] nbFilledArray = filledList(n);
        int maxNbFile = 100;

        for (int nbFilled : nbFilledArray) {
            for (int nbFile = 1; nbFile <= maxNbFile; nbFile++) {
                runInstance(n, nbFilled, nbFile);
            }
        }
    }

    public void runInstance(int n, int nbFilled, int nbFile) {
        ExperimentFilename fn = new ExperimentFilename("latinSquare");
        // String[] models = {SUM_PRODUCT_NO_INIT, MAX_PRODUCT_ORACLE, MAX_PRODUCT_INIT, SUM_PRODUCT_ORACLE, SUM_PRODUCT_INIT};
        // String[] branchingSchemes = {MAX_MARGINAL, MAX_MARGINAL_REGRET_RANDOM_TIE_BREAK, FIRST_FAIL_RANDOM_VAL};
        String[] branchingSchemes = {MAX_MARGINAL, MAX_MARGINAL_REGRET_RANDOM_TIE_BREAK};
        String[] models = {MAX_PRODUCT_OBJECTIVE};
        String[] objectives = {CROSS};
        // int n = 5; // Integer.parseInt(args[0]);
        // int nbFilled = 8; // Integer.parseInt(args[1]);
        // int nbFile = 1; // Integer.parseInt(args[2]);

        PrintStream originalOut = System.out;

        for (String model : models) {
            for (String branchingScheme : branchingSchemes) {
                for (String objective : objectives) {
                    // Set up a new output stream for this model
                    try {
                        FileOutputStream fos = new FileOutputStream(
                                fn.outputFilepath(n, nbFilled, nbFile, model, branchingScheme, objective), false);
                        PrintStream out = new PrintStream(new TeeOutputStream(fos, originalOut), true);

                        // Redirect System.out to our new PrintStream
                        System.setOut(out);

                        // Run the model
                        run(n, nbFilled, nbFile, model, branchingScheme, objective);

                        // Close the new PrintStream
                        out.close();
                    } catch (FileNotFoundException e) {
                        originalOut.println("Could not open out file for model: " + model);
                    } finally {
                        // Restore the original System.out
                        System.setOut(originalOut);
                    }
                }
            }
        }
    }

    public void run(int n, int nbFilled, int nbFile, String model, String branchingScheme, String objective) {
        ExperimentFilename fn = new ExperimentFilename("latinSquare");
        Solver cp = makeSolver();

        IntVar[][] x = makeModel(n, nbFilled, nbFile, model, objective, cp);

        System.out.println(x[0][0].toString());
        IntVar[] xFlat = flatten(x);

        // System.out.println("branching scheme: " + branchingScheme);
        System.out.println("model: " + model);

        Search dfs = makeSearch(branchingScheme, cp, xFlat);

        cp.setTraceBPFlag(true);
        cp.setTraceSearchFlag(true);

        // solve
        SearchStatistics stats = null;
        try (FileWriter fw = new FileWriter(fn.foundSolutionFilepath(n, nbFilled, nbFile, model, branchingScheme, objective))) {
            dfs.onSolution(() -> {
                writeSolution(fw, n, x);
            });
            stats = dfs.solve(stat -> stat.numberOfSolutions() >= 1); // first solution
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Errow while writting file");
        }
//		}
        System.out.println(stats);

    }

    // --- Search ---
    public static Search makeSearch(String branchingScheme, Solver cp, IntVar[] xFlat) {
        Search search;
        switch (branchingScheme) {
            case MAX_MARGINAL:
                search = makeDfs(cp, maxMarginal(xFlat));
                break;
            case MAX_MARGINAL_REGRET_RANDOM_TIE_BREAK:
                search = makeDfs(cp, maxMarginalRegretRandomTieBreak(xFlat));
                break;

            case FIRST_FAIL_RANDOM_VAL:
                search = makeDfs(cp, firstFailRandomVal(xFlat));
                break;
            default:
                search = makeDfs(cp, firstFailRandomVal(xFlat));
                break;
        }

        return search;
    }

    // --- Filenames ---

    public class ExperimentFilename {
        private String modelName;

        public ExperimentFilename(String modelName) {
            this.modelName = modelName;
        }

        public static String capitalizeFirstLetter(String input) {
            if (input == null || input.isEmpty()) {
                return input;
            }
            return input.substring(0, 1).toUpperCase() + input.substring(1);
        }

        public String baseFilename(int n, int nbFilled, int nbFile) {
            return modelName + n + "-filled" + nbFilled + "-" + nbFile;
        }

        public String runFilename(int n, int nbFilled, int nbFile, String model, String branchingScheme, String objective) {
            return String.format("%s-%s-%s-%s.out", baseFilename(n, nbFilled, nbFile), model, branchingScheme, objective);
        }

        public String instanceFilename(int n, int nbFilled, int nbFile) {
            return baseFilename(n, nbFilled, nbFile) + ".dat";
        }

        public String instanceFilepath(int n, int nbFilled, int nbFile) {
            return "./src/main/java/minicpbp/examples/data/" + capitalizeFirstLetter(modelName) + "/" + instanceFilename(n, nbFilled, nbFile);
        }

        public String solutionFilename(int n, int nbFilled, int nbFile) {
            return "solutions-" + baseFilename(n, nbFilled, nbFile) + ".sol";
        }

        public String solutionFilepath(int n, int nbFilled, int nbFile) {
            return "./solutions/" + capitalizeFirstLetter(modelName) + "/" + solutionFilename(n, nbFilled, nbFile);
        }

        public String outputFilename(int n, int nbFilled, int nbFile, String model, String branchingScheme, String objective) {
            return String.format("out-%s", runFilename(n, nbFilled, nbFile, model, branchingScheme, objective));
        }

        public String outputFilepath(int n, int nbFilled, int nbFile, String model, String branchingScheme, String objective) {
            return "./logs/" + capitalizeFirstLetter(modelName) + "/traces/" + outputFilename(n, nbFilled, nbFile, model, branchingScheme, objective);
        }

        public String foundSolutionFilename(int n, int nbFilled, int nbFile, String model, String branchingScheme, String objective) {
            return String.format("out-solution-%s", runFilename(n, nbFilled, nbFile, model, branchingScheme, objective));
        }

        public String foundSolutionFilepath(int n, int nbFilled, int nbFile, String model, String branchingScheme, String objective) {
            return "./logs/" + capitalizeFirstLetter(modelName) + "/solutions/" + foundSolutionFilename(n, nbFilled, nbFile, model, branchingScheme, objective);
        }

    }

    // --- Write solution ---

    public void writeSolution(FileWriter fw, int n, IntVar[][] x) {
        try {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    assert x[i][j].isBound();
                    fw.write(i + " " + j + " " + x[i][j].min() + "\n");
                }
            }
            fw.write("=======\n");
            fw.flush();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // --- create Latin Square model ---

    public IntVar[][] makeModel(int n, int nbFilled, int nbFile, String model, String objective, Solver cp) {
        IntVar[][] x;

        switch (model) {
            case MAX_PRODUCT_INIT:
                x = makeLatinSquareInit(cp, n, nbFilled, nbFile, true, objective);
                break;
            case MAX_PRODUCT_INIT_EXP:
                x = makeLatinSquareInitExp(cp, n, nbFilled, nbFile, true, objective);
                break;
            case MAX_PRODUCT_ORACLE:
                x = makeLatinSquareOracle(cp, n, nbFilled, nbFile, true, objective);
                break;
            case MAX_PRODUCT_ORACLE_EXP:
                x = makeLatinSquareOracleExp(cp, n, nbFilled, nbFile, true, objective);
                break;
            case SUM_PRODUCT_NO_INIT:
                x = makeLatinSquare(cp, n, nbFilled, nbFile, false);
                break;
            case SUM_PRODUCT_INIT:
                x = makeLatinSquareInit(cp, n, nbFilled, nbFile, false, objective);
                break;
            case SUM_PRODUCT_INIT_EXP:
                x = makeLatinSquareInitExp(cp, n, nbFilled, nbFile, false, objective);
                break;
            case SUM_PRODUCT_ORACLE:
                x = makeLatinSquareOracle(cp, n, nbFilled, nbFile, false, objective);
                break;
            case SUM_PRODUCT_ORACLE_EXP:
                x = makeLatinSquareOracleExp(cp, n, nbFilled, nbFile, false, objective);
                break;
            case MAX_PRODUCT_OBJECTIVE:
                x = makeLatinSquareObjective(cp, n, nbFilled, nbFile, objective);
                break;
            default:
                throw new IllegalArgumentException("Unexpected value: " + model);
        }
        return x;
    }

    public void partialAssignments(IntVar[][] vars, int n, int nbFilled, int nbFile) {
        var fn = new ExperimentFilename("latinSquare");
        try {
            Scanner scanner = new Scanner(new FileReader(fn.instanceFilepath(n, nbFilled, nbFile)));

            scanner.nextInt();
            scanner.nextInt();

            while (scanner.hasNextInt()) {
                int row = scanner.nextInt(); // remove -1
                int column = scanner.nextInt(); // remove -1
                int value = scanner.nextInt();
                vars[row][column].assign(value);
            }
            scanner.close();
        } catch (IOException e) {
            System.err.println("Error : " + e.getMessage());
            System.exit(2);
        }
    }

    public static IntVar[] flatten(IntVar[][] x) {
        IntVar[] xFlat = Arrays.stream(x).flatMap(Arrays::stream).toArray(IntVar[]::new);
        return xFlat;
    }

    /*
     * Make latin square and set marginals on the diagonal to proportional values
     */
    public IntVar[][] makeLatinSquareInit(Solver cp, int n, int nbFilled, int nbFile, boolean maxProduct, String objective) {
        IntVar[][] x = makeLatinSquare(cp, n, nbFilled, nbFile, maxProduct);

        // proportional init on diagonal
        for (var v : varPattern(x, n, objective)) {
            setProportionalMarginals(v);
        }
        return x;
    }

    public IntVar[][] makeLatinSquareInitExp(Solver cp, int n, int nbFilled, int nbFile, boolean maxProduct, String objective) {
        IntVar[][] x = makeLatinSquare(cp, n, nbFilled, nbFile, maxProduct);

        // exponential init on diagonal
        for (var v : varPattern(x, n, objective)) {
            setExponentialMarginals(v);
        }
        return x;
    }

    /*
     * Make latin square with proportional oracle on the diagonal
     */
    public IntVar[][] makeLatinSquareOracle(Solver cp, int n, int nbFilled, int nbFile, boolean maxProduct, String objective) {
        IntVar[][] x = makeLatinSquare(cp, n, nbFilled, nbFile, maxProduct);

        // proportional oracle on diagonal
        for (var v : varPattern(x, n, objective)) {
            cp.post(proportionalOracle(v));
        }
        return x;
    }

    public IntVar[][] makeLatinSquareOracleExp(Solver cp, int n, int nbFilled, int nbFile, boolean maxProduct, String objective) {
        IntVar[][] x = makeLatinSquare(cp, n, nbFilled, nbFile, maxProduct);
        // exponential oracle on diagonal
        for (var v : varPattern(x, n, objective)) {
            cp.post(exponentialOracle(v));
        }
        return x;
    }

    public IntVar[][] makeLatinSquareObjective(Solver cp, int n, int nbFilled, int nbFile, String objective) {
        IntVar[][] x = makeLatinSquare(cp, n, nbFilled, nbFile, true);
        cp.post(new ObjectiveSum(varPattern(x, n, objective)));
        return x;
    }


    public IntVar[][] makeLatinSquare(Solver cp, int n, int nbFilled, int nbFile, boolean maxProduct) {
        IntVar[][] x = new IntVar[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                x[i][j] = makeIntVar(cp, 0, n - 1);
                x[i][j].setName("x" + "[" + i + "," + j + "]");
            }
        }

        partialAssignments(x, n, nbFilled, nbFile);

        // allDifferent on lines
        for (int i = 0; i < n; i++) {
            Constraint c = maxProduct ? new AllDifferentDCMAP(x[i]) : new AllDifferentDC(x[i]);
            cp.post(c);
        }

        // allDifferent on columns
        for (int j = 0; j < x.length; j++) {
            IntVar[] column = new IntVar[n];
            for (int i = 0; i < x.length; i++)
                column[i] = x[i][j];
            Constraint c = maxProduct ? new AllDifferentDCMAP(column) : new AllDifferentDC(column);
            cp.post(c);
        }

        return x;
    }

    /* return an oracle where each marginal is proportional to value */
    public static Constraint proportionalOracle(IntVar var) {
        int[] values = new int[var.size()];
        var.fillArray(values);

        double[] marginals = new double[var.size()];
        for (int i = 0; i < marginals.length; i++) {
            marginals[i] = (double) values[i] + 1;
        }

        return oracle(var, values, marginals);
    }

    public static Constraint exponentialOracle(IntVar var) {
        int[] values = new int[var.size()];
        var.fillArray(values);

        double[] marginals = new double[var.size()];
        for (int i = 0; i < marginals.length; i++) {
            marginals[i] = (double) Math.pow(2, values[i]);
        }

        return oracle(var, values, marginals);
    }

    public static void setProportionalMarginals(IntVar var) {
        int[] values = new int[var.size()];
        var.fillArray(values);

        for (int i = 0; i < values.length; i++) {
            int v = values[i];
            var.setMarginalWithDefault(v, (double) v + 1);
        }
    }

    public static void setExponentialMarginals(IntVar var) {
        int[] values = new int[var.size()];
        var.fillArray(values);

        for (int i = 0; i < values.length; i++) {
            int v = values[i];
            var.setMarginalWithDefault(v, (double) Math.pow(2, v));
        }
    }

    public IntVar[] varPattern(IntVar[][] vars, int n, String pattern) {
        var result = new IntVar[n];
        switch (pattern) {
            case "diagonal":
                for (int i = 0; i < n; i++) {
                    result[i] = vars[i][i];
                }
                break;
            case "cross":
                if (!(n % 4 == 0 || n % 4 == 1)) {
                    throw new IllegalArgumentException("n must 4k or 4k+1");
                }
                for (int i = 0; i < (n / 4); i++) {
                    result[4 * i] = vars[n / 2 + i - 1][n / 2 - i];
                    result[4 * i + 1] = vars[n / 2 - i][n / 2 + i - 1];
                    result[4 * i + 2] = vars[n / 2 - i][n / 2 + i - 1];
                    result[4 * i + 3] = vars[n / 2 + i - 1][n / 2 - i];
                }
                if (n % 4 == 1) {
                    result[n - 1] = vars[n / 2][n / 2];
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown pattern: " + pattern);
        }
        return result;
    }
}
