package minicpbp.examples.cops;

import minicpbp.cp.BranchingScheme;
import minicpbp.cp.Factory;
import minicpbp.engine.constraints.*;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Objective;
import minicpbp.search.Search;
import minicpbp.search.SearchStatistics;
import minicpbp.util.Procedure;
import minicpbp.util.io.TeeOutputStream;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

public class LatinSquare {
    public enum Branching {
        MAX_MARGINAL,
        MAX_MARGINAL_REGRET,
        MAX_MARGINAL_STRENGTH,
        FIRST_FAIL,
        LEXICO,
        MAX_VALUE,
        DOM_WDEG,
        DOM_WDEG_MAX_MARGINAL;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum ObjectivePatternType {
        DIAGONAL,
        PSEUDODIAGONAL;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public record ObjectivePattern(ObjectivePatternType type, int nbVars, int rowLength, int overlap) {
        public ObjectivePattern(int n) {
            this(ObjectivePatternType.DIAGONAL, n, 1, 0);
        }

        public ObjectivePattern {
            if (nbVars % rowLength != 0) {
                throw new IllegalArgumentException("nbVars must be a multiple of rowLength");
            }
            if (overlap < 0 || overlap > rowLength) {
                throw new IllegalArgumentException("overlap must be between 0 and rowLength");
            }
        }

        public String toString() {
            return switch (type) {
                case DIAGONAL -> "diagonal";
                case PSEUDODIAGONAL -> String.format("pseudodiagonal_%d_%d_%d", nbVars, rowLength, overlap);
            };
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

    public enum SearchType {
        DFS,
        LDS;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public record ExperimentSpec(BPAlgorithm bp, Branching branching, boolean oracle) {
        public String toString() {
            String oracleStr = oracle ? "-oracle" : "";
            return String.format("%s-%s%s", bp, branching, oracleStr);
        }

    }

    private Solver cp;
    private IntVar[][] x;
    private IntVar[] xFlat;
    private IntVar[] objectiveVars;
    private Search search;
    private Objective obj;

    public LatinSquare(int n, int nbHoles, int nbFile, SearchType searchType, BPAlgorithm bp, Branching branching, ObjectivePattern objective, boolean oracle, int truncateRate) {
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
        objectiveVars = new IntVar[objective.nbVars];
        System.out.println("objective: " + objective);
        System.out.println("objective.nbVars: " + objective.nbVars);
        assert objective.nbVars <= n * n;
        switch (objective.type) {
            case DIAGONAL:
                for (int i = 0; i < n; i++) {
                    objectiveVars[i] = x[i][i];
                }
                break;
            case PSEUDODIAGONAL:
                int rowLength = objective.rowLength;
                if (rowLength > n) {
                    throw new IllegalArgumentException("rowLength must be less than n");
                }
                int nbVars = objective.nbVars;
                int overlap = objective.overlap;
                int nbRows = nbVars / rowLength;
                if (nbRows > n) {
                    throw new IllegalArgumentException("nbRows must be less than n");
                }
                if (n < rowLength + (nbRows - 1) * (rowLength - overlap)) {
                    throw new IllegalArgumentException("pattern won't fit into square");
                }
                int col = 0;
                for (int i = 0; i < nbRows; i++) {
                    for (int j = 0; j < rowLength; j++) {
                        objectiveVars[rowLength * i + j] = x[i][col];
                        col++;
                    }
                    col -= overlap;
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
            case MAX_MARGINAL_STRENGTH:
                branchingProcedure = maxMarginalStrength(xFlat);
                break;
            case DOM_WDEG:
                branchingProcedure = domWdeg(xFlat);
                break;
            case DOM_WDEG_MAX_MARGINAL:
                branchingProcedure = domWdegMaxMarginal(xFlat);
                break;
            default:
                throw new IllegalArgumentException("Unknown branching scheme: " + branching);
        }

        switch (searchType) {
            case DFS:
                search = makeDfs(cp, branchingProcedure);
                break;
            case LDS:
                search = makeLds(cp, branchingProcedure);
                break;
            default:
                throw new IllegalArgumentException("Unknown search type: " + searchType);
        }
        search.onSolution(() -> {
            // sum of objectiveVars
            int sum = Arrays.stream(objectiveVars).mapToInt(IntVar::min).sum();
            System.out.println("NEW SOLUTION FOUND");
            System.out.println("solution score : " + sum);
        });

        obj = cp.maximize(z);
    }


    public SearchStatistics optimize() {
        return search.optimize(obj, stat -> stat.isCompleted() || stat.timeElapsed() > 3600000);
    }

    public SearchStatistics solve() {
        return search.solve(stat -> stat.isCompleted());
    }

    public void onSolution(Procedure listener) {
        search.onSolution(listener);
    }

    public void printSolution() {
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                assert x[i][j].isBound();
                System.out.println(i + " " + j + " " + x[i][j].min());
            }
        }

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

    public static HashMap<String, String> parseArgs(String[] args) {
        HashMap<String, String> arguments = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=", 2);
                if (parts.length == 2) {
                    arguments.put(parts[0], parts[1]);
                }
            }
        }
        return arguments;
    }

    public static int[] parseIntDefaultToArray(String arg, int[] array) {
        if (arg == null) {
            return array;
        }
        return new int[]{Integer.parseInt(arg)};
    }

    public static void main(String[] args) {
        ExperimentSpec[] searchSpecArray = {
                new ExperimentSpec(BPAlgorithm.NO_BP, Branching.MAX_VALUE, false),
                // new ExperimentSpec(searchType, BPAlgorithm.NO_BP, Branching.FIRST_FAIL, false),
                // new ExperimentSpec(searchType, BPAlgorithm.NO_BP, Branching.DOM_WDEG, false),
                new ExperimentSpec(BPAlgorithm.SUM_PRODUCT, Branching.MAX_MARGINAL_REGRET, false),
                new ExperimentSpec(BPAlgorithm.SUM_PRODUCT, Branching.MAX_MARGINAL_REGRET, true),
                new ExperimentSpec(BPAlgorithm.MAX_PRODUCT, Branching.MAX_MARGINAL_REGRET, true),
                new ExperimentSpec(BPAlgorithm.SUM_PRODUCT, Branching.MAX_MARGINAL_STRENGTH, false),
                new ExperimentSpec(BPAlgorithm.SUM_PRODUCT, Branching.MAX_MARGINAL_STRENGTH, true),
                // new ExperimentSpec(searchType, BPAlgorithm.MAX_PRODUCT, Branching.MAX_MARGINAL_STRENGTH, true, truncateRate),
                new ExperimentSpec(BPAlgorithm.MAX_PRODUCT, Branching.DOM_WDEG_MAX_MARGINAL, true),
        };
        var objectivePatternArray = new ObjectivePattern[]{
                new ObjectivePattern(ObjectivePatternType.PSEUDODIAGONAL, 30, 15, 10),
                new ObjectivePattern(ObjectivePatternType.PSEUDODIAGONAL, 30, 15, 15),
                new ObjectivePattern(ObjectivePatternType.PSEUDODIAGONAL, 90, 15, 10),
                new ObjectivePattern(ObjectivePatternType.PSEUDODIAGONAL, 90, 15, 15)
        };
        var nbHolesArray = new int[]{500, 600, 700};
        var searchTypeArray = new SearchType[]{SearchType.DFS, SearchType.LDS};
        var nbFileArray = IntStream.rangeClosed(1, 10).toArray();
        var truncateRateArray = new int[]{0};

        HashMap<String, String> arguments = parseArgs(args);
        String mode = arguments.getOrDefault("mode", "OPTIMIZE").toUpperCase();
        int n = Integer.parseInt(arguments.getOrDefault("n", "30"));
        String nbHolesArg = arguments.get("nbHoles");
        String nbFileArg = arguments.get("nbFile");
        String objectiveString = arguments.get("objective");
        String searchTypeArg = arguments.get("searchType");
        String modelNumberArg = arguments.get("modelNumber");
        String truncateRateArg = arguments.get("truncateRate");

        nbHolesArray = parseIntDefaultToArray(nbHolesArg, nbHolesArray);
        searchTypeArray = searchTypeArg == null ? new SearchType[]{SearchType.DFS, SearchType.LDS} : searchTypeArray;
        nbFileArray = parseIntDefaultToArray(nbFileArg, nbFileArray);
        truncateRateArray = truncateRateArg == null ? new int[]{0} : truncateRateArray;
        if (modelNumberArg != null) {
            searchSpecArray = new ExperimentSpec[]{searchSpecArray[Integer.parseInt(modelNumberArg)]};
        }


        if (objectiveString != null) {
            ObjectivePattern singleObjective;
            String[] parts = objectiveString.toUpperCase().split("_");
            if (parts[0].equals("DIAGONAL")) {
                singleObjective = new ObjectivePattern(n);
            } else if (parts[0].equals("PSEUDODIAGONAL") && parts.length == 4) {
                try {
                    int numVars = Integer.parseInt(parts[1]);
                    int numPerRow = Integer.parseInt(parts[2]);
                    int overlap = Integer.parseInt(parts[3]);
                    singleObjective = new ObjectivePattern(ObjectivePatternType.PSEUDODIAGONAL, numVars, numPerRow, overlap);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid pseudodiagonal parameters: " + objectiveString);
                }
            } else {
                throw new IllegalArgumentException("Invalid objective pattern: " + objectiveString);
            }
            objectivePatternArray = new ObjectivePattern[]{singleObjective};
        }

        PrintStream originalOut = System.out;

        for (var searchType : searchTypeArray) {
            for (var truncateRate : truncateRateArray) {
                for (var spec : searchSpecArray) {
                    for (var nbHoles : nbHolesArray) {
                        for (var objective : objectivePatternArray) {
                            for (var nbFile : nbFileArray) {
                                // var spec = searchSpecs[modelNumber];

                                // Create the model
                                LatinSquare ls = new LatinSquare(n, nbHoles, nbFile, searchType, spec.bp, spec.branching, objective, spec.oracle, truncateRate);

                                // Run the model
                                SearchStatistics stats;
                                if (mode.equals("SOLVE")) {
                                    try {
                                        // System.out to both the console and the log file
                                        FileOutputStream fos = new FileOutputStream(
                                                String.format("solutions/latin-square/latin-square%d-holes%d-%d.sol", n, nbHoles, nbFile));
                                        PrintStream out = new PrintStream(new TeeOutputStream(fos, originalOut), true);
                                        System.setOut(out);

                                        System.out.println("INFO");
                                        System.out.println("n : " + n);
                                        System.out.println("nbHoles : " + nbHoles);
                                        System.out.println("nbFile : " + nbFile);


                                        ls.onSolution(ls::printSolution);
                                        System.out.println("START SEARCH");
                                        stats = ls.solve();
                                    } catch (FileNotFoundException e) {
                                        throw new RuntimeException("Error while creating file", e);
                                    }
                                } else {
                                    try {
                                        String truncateRateStr = truncateRate == 0 ? "" : "-truncate" + truncateRate;
                                        String filename = String.format("logs/latin-square/latin-square%d-holes%d-%d-%s-%s-%s%s.out", n, nbHoles, nbFile, objective, searchType, spec, truncateRateStr);
                                        // System.out to both the console and the log file
                                        FileOutputStream fos = new FileOutputStream(filename);
                                        PrintStream out = new PrintStream(new TeeOutputStream(fos, originalOut), true);
                                        System.setOut(out);

                                        System.out.println("INFO");
                                        System.out.println("n : " + n);
                                        System.out.println("nbHoles : " + nbHoles);
                                        System.out.println("nbFile : " + nbFile);


                                        System.out.println("objective : " + objective);
                                        System.out.println("search: " + searchType);
                                        System.out.println("bp : " + spec.bp);
                                        System.out.println("branchingScheme : " + spec.branching);
                                        System.out.println("oracle : " + spec.oracle);
                                        System.out.println("truncateRate : " + truncateRate);

                                        System.out.println("START SEARCH");
                                        stats = ls.optimize();
                                    } catch (FileNotFoundException e) {
                                        throw new RuntimeException("Error while creating file", e);
                                    }
                                }
                                System.out.println("END OF SEARCH");
                                System.out.println(stats);
                            }
                        }
                    }
                }
            }
        }
    }
}

