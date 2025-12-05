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
import java.util.Scanner;
import java.util.function.Supplier;

import static minicpbp.cp.BranchingScheme.firstFail;
import static minicpbp.cp.Factory.*;

public class LatinSquareSolution {

    public Solver cp;
    public IntVar[][] x;
    public IntVar[] xFlat;
    public IntVar[] objectiveVars;
    public Search search;
    public Objective obj;
    public String fileName;
    public int n;
    public int nbHoles;

    public LatinSquareSolution(String fileName) {
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

        Supplier<Procedure[]> branchingProcedure = firstFail(xFlat);

        search = makeDfs(cp, branchingProcedure);

        search.onSolution(this::printSolution);
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
        printSolution();
    }

    public void printSolution() {
        // print all variables
        for (int i = 0; i < xFlat.length; i++) {
            System.out.println(xFlat[i].getName() + " : " + xFlat[i]);
        }
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

        LatinSquareSolution ls = new LatinSquareSolution(inputFile);

        ls.x[0][0].assign(4);
        ls.x[1][1].assign(5);

        ls.cp.setMode(Solver.PropaMode.SP);
        ls.search.solve(stat -> stat.numberOfSolutions() > 0);


    }
}
