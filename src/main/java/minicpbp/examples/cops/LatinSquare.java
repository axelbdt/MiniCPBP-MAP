package minicpbp.examples.cops;

import minicpbp.cp.Factory;
import minicpbp.engine.constraints.AllDifferentDC;
import minicpbp.engine.constraints.SumDC;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Objective;
import minicpbp.search.Search;
import minicpbp.search.SearchStatistics;
import minicpbp.util.Procedure;

import java.io.FileReader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.function.Supplier;

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
        DOM_WDEG_BEST_VALUE,
        DOM_WDEG_MAX_MARGINAL,
        MIN_ENTROPY,
        MIN_NORMALIZED_ENTROPY;

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


    public Solver cp;
    private IntVar[][] x;
    private IntVar[] xFlat;
    private IntVar[] objectiveVars;
    private Search search;
    private Objective obj;

    public LatinSquare(int n, int nbHoles, int nbFile, SearchType searchType, BPAlgorithm bp, Branching branching, ObjectivePattern objective, float oracle, int maxIter, float entropyBranchingThreshold, boolean propagationShortcut) {
        // Create solver and square variables
        cp = makeSolver();

        cp.setPropagationShortcut(propagationShortcut);

        switch (bp) {
            case NO_BP:
                cp.setMode(Solver.PropaMode.SP);
                break;
            case SUM_PRODUCT:
                cp.setMode(Solver.PropaMode.SBP);
                cp.setBPAlgorithm(Solver.BPAlgorithm.SUM_PRODUCT);
                break;
            case MAX_PRODUCT:
                cp.setMode(Solver.PropaMode.SBP);
                cp.setBPAlgorithm(Solver.BPAlgorithm.MAX_PRODUCT);
                break;
            default:
                throw new IllegalArgumentException("Unknown BP algorithm: " + bp);
        }
        cp.setMaxIter(maxIter);

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

            var rowConstraint = new AllDifferentDC(row);
            var colConstraint = new AllDifferentDC(col);

            cp.post(rowConstraint);
            cp.post(colConstraint);
        }

        // create objective and bind it to the variables
        objectiveVars = new IntVar[objective.nbVars];
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

        int maxObj = objective.nbVars * (n - 1); // var from 0 to n-1
        int minObj = n;
        IntVar z = makeIntVar(cp, minObj, maxObj);
        z.setName("score");
        cp.post(new SumDC(objectiveVars, z));

        // add preference for larger values
        if (oracle > 0) {
            cp.setOracleOnObjective(true);
            cp.setOracleWeight(oracle);
        }

        obj = cp.maximize(z);

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
                branchingProcedure = maxMarginalOrDomWDeg(xFlat, entropyBranchingThreshold);
                break;
            case MAX_MARGINAL_REGRET:
                branchingProcedure = maxMarginalRegretOrDomWDeg(xFlat, entropyBranchingThreshold);
                break;
            case MAX_MARGINAL_STRENGTH:
                branchingProcedure = maxMarginalStrengthOrDomWDeg(xFlat, entropyBranchingThreshold);
                break;
            case DOM_WDEG_BEST_VALUE:
                branchingProcedure = domWdegBestValue();
                break;
            case DOM_WDEG_MAX_MARGINAL:
                branchingProcedure = domWdegMaxMarginal(xFlat);
                break;
            case MIN_ENTROPY:
                branchingProcedure = minEntropyOrDomWDeg(xFlat, entropyBranchingThreshold);
                break;
            case MIN_NORMALIZED_ENTROPY:
                branchingProcedure = minNormalizedEntropyOrDomWDeg(xFlat, entropyBranchingThreshold);
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
            int sum = z.min();
            System.out.println("NEW SOLUTION FOUND");
            System.out.println("solution score : " + sum);
        });
    }

    public void propagate() {
        cp.setTraceSearchFlag(true);
        cp.setTraceBPFlag(true);
        search.solve(stat -> stat.numberOfNodes() > 1);
    }

    public SearchStatistics optimize() {
        int timeLimit = 1 * 3600000; // 1 hour
        // int timeLimit = 4 * 3600000; // 4 hours
        // int timeLimit = 10 * 60000; // 10 minutes
        return search.optimize(obj, stat -> stat.isCompleted() || stat.timeElapsed() > timeLimit);
    }

    public SearchStatistics solve() {
        onSolution(this::printSolution);
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

    public void printAlgorithmComparisonReport() {
        cp.printAlgorithmComparisonReport();
    }

    /**
     * Max value selection.
     * It selects the largest value in all the objective variables.
     * If all objective variables, we do the rest in lexicographic order.
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
            IntVar xs = selectMin(objectiveVars,
                    xi -> xi.size() > 1,
                    xi -> xi.max());
            if (xs == null) {
                IntVar xs2 = selectMin(xFlat,
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

    /**
     * DomWDeg variable selection and best value selection
     * It selects the largest value in all the variables.
     * Then it creates two branches:
     * the left branch assigning the variable to its maximum value;
     * the right branch removing this maximum value from the domain.
     *
     * @return a lexicographic branching strategy
     * @see Factory#makeDfs(Solver, Supplier)
     */
    public Supplier<Procedure[]> domWdegBestValue() {
        boolean tracing = cp.tracingSearch();
        for (IntVar a : xFlat)
            a.setForBranching(true);

        HashSet<String> objectiveVarSet = new HashSet<>();
        for (var a : objectiveVars)
            objectiveVarSet.add(a.getName());

        return () -> {
            IntVar xs = selectMin(xFlat,
                    xi -> xi.size() > 1,
                    xi -> ((double) xi.size()) / ((double) xi.wDeg()));
            if (xs == null)
                return EMPTY;
            else {
                int v = objectiveVarSet.contains(xs.getName()) ? xs.max() : xs.min();
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

    public static void main(String[] args) {
        HashMap<String, String> arguments = parseArgs(args);
        String mode = arguments.getOrDefault("mode", "OPTIMIZE").toUpperCase();
        int n = Integer.parseInt(arguments.getOrDefault("n", "20"));
        String nbHolesArg = arguments.get("nbHoles");
        String nbFileArg = arguments.get("nbFile");
        String objectiveString = arguments.get("objective");
        String searchTypeArg = arguments.get("searchType");
        String oracleArg = arguments.get("oracle");
        String maxIterArg = arguments.get("maxIter");
        String bpArg = arguments.get("bp");
        String branchingArg = arguments.get("branching");
        String entropyBranchingThresholdArg = arguments.get("entropyBranchingThreshold");
        String propagationShortcutArg = arguments.get("propagationShortcut");
        // TODO reset marginals before BP
        // TODO skip uniform max product
        // TODO faster alldiff max prod

        int nbHoles = Integer.parseInt(nbHolesArg);
        SearchType searchType = SearchType.valueOf(searchTypeArg.toUpperCase());
        int nbFile = Integer.parseInt(nbFileArg);
        float oracle = Float.parseFloat(oracleArg);
        int maxIter = Integer.parseInt(maxIterArg);
        BPAlgorithm bp = BPAlgorithm.valueOf(bpArg.toUpperCase());
        Branching branching = Branching.valueOf(branchingArg.toUpperCase());
        float entropyBranchingThreshold = Float.parseFloat(entropyBranchingThresholdArg);
        boolean propagationShortcut = Boolean.valueOf(propagationShortcutArg);

        ObjectivePattern objective;
        String[] parts = objectiveString.toUpperCase().split("_");
        if (parts[0].equals("DIAGONAL")) {
            objective = new ObjectivePattern(n);
        } else if (parts[0].equals("PSEUDODIAGONAL") && parts.length == 4) {
            try {
                int numVars = Integer.parseInt(parts[1]);
                int numPerRow = Integer.parseInt(parts[2]);
                int overlap = Integer.parseInt(parts[3]);
                objective = new ObjectivePattern(ObjectivePatternType.PSEUDODIAGONAL, numVars, numPerRow, overlap);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid pseudodiagonal parameters: " + objectiveString);
            }
        } else {
            throw new IllegalArgumentException("Invalid objective pattern: " + objectiveString);
        }

        // Create the model
        LatinSquare ls = new LatinSquare(
                n,
                nbHoles,
                nbFile,
                searchType,
                bp,
                branching,
                objective,
                oracle,
                maxIter,
                entropyBranchingThreshold,
                propagationShortcut
        );

        System.out.println("INFO");
        System.out.println("n : " + n);
        System.out.println("nbHoles : " + nbHoles);
        System.out.println("nbFile : " + nbFile);

        String filename = String.format("latin-square-%d-holes%d-%d.pls", n, nbHoles, nbFile);
        String filepath = Paths.get("data", "latin-square", filename).toString();
        System.out.println("input file: " + filepath);
        // Run the model
        if (mode.equals("SOLVE")) {
            // get all solutions
            System.out.println("START SEARCH");
            SearchStatistics stats = ls.solve();
            System.out.println("END OF SEARCH");
            System.out.println(stats);
        } else if (mode.equals("PROPAGATE")) {
            System.out.println("INFO");
            System.out.println("n : " + n);
            System.out.println("nbHoles : " + nbHoles);
            System.out.println("nbFile : " + nbFile);
            System.out.println("bp : " + bp);

            ls.propagate();
        } else {
            System.out.println("objective : " + objective);
            System.out.println("search type: " + searchType);
            System.out.println("BP algorithm: " + bp);
            System.out.println("branching strategy: " + branching);
            System.out.println("entropy branching threshold: " + entropyBranchingThreshold);
            System.out.println("propagation shortcut: " + propagationShortcut);
            System.out.println("oracle on objective: " + oracle);
            System.out.println("max iterations: " + maxIter);


            System.out.println("START SEARCH");
            SearchStatistics stats = ls.optimize();
            System.out.println("END OF SEARCH");
            System.out.println(stats);
            System.out.println("damping factor: " + ls.cp.dampingFactor());

            // ls.printAlgorithmComparisonReport();
        }
    }

}

