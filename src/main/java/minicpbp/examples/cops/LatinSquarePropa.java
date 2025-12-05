package minicpbp.examples.cops;

import minicpbp.engine.constraints.AllDifferentDC;
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

public class LatinSquarePropa {

    public Solver cp;
    public IntVar[][] x;
    public IntVar[] xFlat;
    public IntVar[] objectiveVars;
    public Search search;
    public Objective obj;
    public String fileName;
    public int n;
    public int nbHoles;

    public LatinSquarePropa(String fileName) {
        this.fileName = fileName;

        // Extract instance information from file
        this.n = getInstanceSize(fileName);

        // Create solver and square variables
        cp = makeSolver();


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

        // Load instance values
        loadInstanceValues();

        // add search
        xFlat = new IntVar[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                xFlat[i * n + j] = x[i][j];
            }
        }
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

        if (inputFile == null) {
            System.err.println("Error: input file must be specified with --input=filename");
            System.exit(1);
        }

        LatinSquarePropa ls = new LatinSquarePropa(inputFile);

        ls.cp.setMode(Solver.PropaMode.SP);
        ls.cp.fixPoint();

        // print all variables
        for (int i = 0; i < ls.xFlat.length; i++) {
            System.out.println(ls.xFlat[i].getName() + " : " + ls.xFlat[i]);
        }

    }
}
