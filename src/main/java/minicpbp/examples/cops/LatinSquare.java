package minicpbp.examples.cops;

import minicpbp.engine.constraints.AllDifferentDC;
import minicpbp.engine.constraints.SumDC;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Objective;
import minicpbp.search.Search;
import minicpbp.search.SearchStatistics;
import minicpbp.util.Procedure;

import java.io.FileReader;
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
    public IntVar[][] x;
    public IntVar[] xFlat;
    public IntVar[] objectiveVars;
    public Search search;
    public Objective obj;
    public String fileName;
    public int n;
    public int nbHoles;

    public LatinSquare(String fileName, SearchType searchType, BPAlgorithm bp, Branching branching,
                       float oracle, int maxIter, float entropyBranchingThreshold,
                       boolean propagationShortcut, boolean resetMarginalsBeforeBP,
                       boolean skipUniformMaxProduct, boolean fasterAllDiff) {
        this.fileName = fileName;

        // Extract instance information from file
        this.n = getInstanceSize(fileName);

        // Create solver and square variables
        cp = makeSolver();

        // Set optimization parameters
        cp.setPropagationShortcut(propagationShortcut);
        cp.setResetMarginalsBeforeBP(resetMarginalsBeforeBP);
        cp.setSkipUniformMaxProd(skipUniformMaxProduct);
        cp.setFasterAllDiffMaxProd(fasterAllDiff);

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

        // create objective - square of side length ceil(sqrt(n))
        int sideLength = (int) Math.ceil(Math.sqrt(n));
        int numObjectiveVars = sideLength * sideLength;
        objectiveVars = new IntVar[numObjectiveVars];

        // Fill objective vars in row-major order
        int idx = 0;
        for (int i = 0; i < sideLength && idx < numObjectiveVars; i++) {
            for (int j = 0; j < sideLength && idx < numObjectiveVars; j++) {
                objectiveVars[idx++] = x[i][j];
            }
        }

        int maxObj = numObjectiveVars * (n - 1); // var from 0 to n-1
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

        // Load instance values
        loadInstanceValues();

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
            int sum = z.min();
            System.out.println("NEW SOLUTION FOUND");
            System.out.println("solution score : " + sum);
        });
    }

    private static int getInstanceSize(String fileName) {
        try {
            Scanner scanner = new Scanner(new FileReader(fileName));
            scanner.nextLine(); // skip the first line
            int lineCount = 0;
            while (scanner.hasNextLine()) {
                scanner.nextLine();
                lineCount++;
            }
            scanner.close();
            return lineCount;
        } catch (Exception e) {
            System.err.println("Error parsing instance file: " + e.getMessage());
            System.exit(2);
            return 0;
        }
    }

    private void loadInstanceValues() {
        try {
            Scanner scanner = new Scanner(new FileReader(fileName));
            scanner.nextLine(); // skip the first line
            this.nbHoles = 0;
            for (int i = 0; i < n; i++) {
                String[] line = scanner.nextLine().trim().split("\\s+");
                for (int j = 0; j < n; j++) {
                    int value = Integer.parseInt(line[j]);
                    if (value != -1) {
                        x[i][j].assign(value);
                    } else {
                        nbHoles++;
                    }
                }
            }
            scanner.close();
        } catch (Exception e) {
            System.err.println("Error loading instance values: " + e.getMessage());
            System.exit(2);
        }
    }

    public void propagate() {
        cp.setTraceSearchFlag(true);
        cp.setTraceBPFlag(true);
        search.solve(stat -> stat.numberOfNodes() > 1);
    }

    public SearchStatistics optimize() {
        int timeLimit = 1 * 3600000; // 1 hour
        return search.optimize(obj, stat -> stat.isCompleted() || stat.timeElapsed() > timeLimit);
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
        String inputFile = arguments.get("input");
        String searchTypeArg = arguments.get("search-type");
        String oracleArg = arguments.get("oracle-on-objective");
        String maxIterArg = arguments.get("max-iter");
        String bpArg = arguments.get("bp-algorithm");
        String branchingArg = arguments.get("branching");
        String entropyBranchingThresholdArg = arguments.get("entropy-branching-threshold");
        String propagationShortcutArg = arguments.get("propagation-shortcut");
        String resetMarginalsBeforeBPArg = arguments.get("reset-marginals-before-bp");
        String skipUniformMaxProductArg = arguments.get("skip-uniform-max-prod");
        String fasterAllDiffArg = arguments.get("faster-all-diff-max-prod");

        if (inputFile == null) {
            System.err.println("Error: input file must be specified with --input=filename");
            System.exit(1);
        }

        SearchType searchType = SearchType.valueOf(searchTypeArg.toUpperCase());
        float oracle = Float.parseFloat(oracleArg);
        int maxIter = Integer.parseInt(maxIterArg);
        BPAlgorithm bp = BPAlgorithm.valueOf(bpArg.toUpperCase());
        Branching branching = Branching.valueOf(branchingArg.toUpperCase());
        float entropyBranchingThreshold = Float.parseFloat(entropyBranchingThresholdArg);
        boolean propagationShortcut = Boolean.valueOf(propagationShortcutArg);
        boolean resetMarginalsBeforeBP = Boolean.valueOf(resetMarginalsBeforeBPArg);
        boolean skipUniformMaxProduct = Boolean.valueOf(skipUniformMaxProductArg);
        boolean fasterAllDiff = Boolean.valueOf(fasterAllDiffArg);

        LatinSquare ls = new LatinSquare(
                inputFile,
                searchType,
                bp,
                branching,
                oracle,
                maxIter,
                entropyBranchingThreshold,
                propagationShortcut,
                resetMarginalsBeforeBP,
                skipUniformMaxProduct,
                fasterAllDiff
        );

        System.out.println("INFO");
        System.out.println("input file: " + inputFile);
        System.out.println("n : " + ls.n);
        System.out.println("nbHoles : " + ls.nbHoles);
        System.out.println("search type: " + searchType);
        System.out.println("BP algorithm: " + bp);
        System.out.println("branching strategy: " + branching);
        System.out.println("entropy branching threshold: " + entropyBranchingThreshold);
        System.out.println("propagation shortcut: " + propagationShortcut);
        System.out.println("reset marginals before BP: " + resetMarginalsBeforeBP);
        System.out.println("skip uniform max product: " + skipUniformMaxProduct);
        System.out.println("faster all diff: " + fasterAllDiff);
        System.out.println("oracle on objective: " + oracle);
        System.out.println("max iterations: " + maxIter);

        System.out.println("START SEARCH");
        SearchStatistics stats = ls.optimize();
        System.out.println("END OF SEARCH");
        System.out.println(stats);
        System.out.println("damping factor: " + ls.cp.dampingFactor());
    }
}
