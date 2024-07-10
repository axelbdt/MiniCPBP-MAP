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

import static minicpbp.cp.BranchingScheme.firstFailRandomVal;
import static minicpbp.cp.BranchingScheme.maxMarginal;
import static minicpbp.cp.BranchingScheme.maxMarginalRegretRandomTieBreak;
import static minicpbp.cp.Factory.makeDfs;
import static minicpbp.cp.Factory.makeIntVar;
import static minicpbp.cp.Factory.makeSolver;
import static minicpbp.cp.Factory.oracle;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import minicpbp.engine.constraints.AllDifferentDC;
import minicpbp.engine.constraints.AllDifferentDCMAP;
import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Search;
import minicpbp.search.SearchStatistics;
import minicpbp.util.io.TeeOutputStream;

/**
 * The Magic Square Completion problem.
 * <a href="http://csplib.org/Problems/prob019/">CSPLib</a>.
 */
public class LatinSquare {

	static final String MAX_MARGINAL = "maxMarginal";
	static final String MAX_MARGINAL_REGRET_RANDOM_TIE_BREAK = "maxMarginalRegretRandomTieBreak";
	static final String FIRST_FAIL_RANDOM_VAL = "firstFailRandomVal";

	static final String MAX_PRODUCT_ORACLE = "max-product-oracle";
	static final String MAX_PRODUCT_INIT = "max-product-init";
	static final String SUM_PRODUCT_ORACLE = "sum-product-oracle";
	static final String SUM_PRODUCT_INIT = "sum-product-init";
	static final String SUM_PRODUCT_NO_INIT = "sum-product-no-init";

	public static void main(String[] args) {
		// String[] models = { MAX_PRODUCT_ORACLE, MAX_PRODUCT_INIT, SUM_PRODUCT_ORACLE,
		// SUM_PRODUCT_INIT,
		// SUM_PRODUCT_NO_INIT };

		String[] models = { MAX_PRODUCT_INIT };
		int n = 5; // Integer.parseInt(args[0]);
		int nbFilled = 8; // Integer.parseInt(args[1]);
		int nbFile = 1; // Integer.parseInt(args[2]);

		PrintStream originalOut = System.out;

		for (String model : models) {
			// Set up a new output stream for this model
			try {
				FileOutputStream fos = new FileOutputStream(outputFilepath(n, nbFilled, nbFile, model), false);
				PrintStream out = new PrintStream(new TeeOutputStream(fos, originalOut), true);

				// Redirect System.out to our new PrintStream
				System.setOut(out);

				// Run the model
				run(n, nbFilled, nbFile, model);

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

	public static void run(int n, int nbFilled, int nbFile, String model) {
		Solver cp = makeSolver();

		IntVar[][] x = makeModel(n, nbFilled, nbFile, model, cp);

		System.out.println(x[0][0].toString());
		IntVar[] xFlat = flatten(x);

		// System.out.println("branching scheme: " + branchingScheme);
		System.out.println("model: " + model);

		String branchingScheme = MAX_MARGINAL;
		Search dfs = makeSearch(branchingScheme, cp, xFlat);

		cp.setTraceBPFlag(true);
		cp.setTraceSearchFlag(true);

		var solutions = new ArrayList<int[][]>();
		dfs.onSolution(() -> {
			int[][] solution = new int[n][n];
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					assert x[i][j].isBound();
					solution[i][j] = x[i][j].min();
				}
			}
			solutions.add(solution);
		});

		// solve
		SearchStatistics stats = dfs.solve(stat -> stat.isCompleted()); // exhaustive search

		// write all solutions to single file
		try (FileWriter fw = new FileWriter(solutionFilepath(n, nbFilled, nbFile))) {
			System.out.println("Writing file");
			for (int[][] solution : solutions) {
				for (int i = 0; i < solution.length; i++) {
					for (int j = 0; j < solution[i].length; j++) {
						fw.write(i + " " + j + " " + solution[i][j] + "\n");
					}
				}
				fw.write("=======\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Errow while writting file");
		}
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

	public static String baseFilename(int n, int nbFilled, int nbFile) {
		return "latinSquare" + n + "-filled" + nbFilled + "-" + nbFile;
	}

	public static String instanceFilename(int n, int nbFilled, int nbFile) {
		return baseFilename(n, nbFilled, nbFile) + ".dat";
	}

	public static String instanceFilepath(int n, int nbFilled, int nbFile) {
		return "./src/main/java/minicpbp/examples/data/LatinSquare/" + instanceFilename(n, nbFilled, nbFile);
	}

	public static String solutionFilename(int n, int nbFilled, int nbFile) {
		return "solutions-" + baseFilename(n, nbFilled, nbFile) + ".sol";
	}

	public static String solutionFilepath(int n, int nbFilled, int nbFile) {
		return "./solutions/LatinSquare/" + solutionFilename(n, nbFilled, nbFile);
	}

	public static String outputFilename(int n, int nbFilled, int nbFile) {
		return outputFilename(n, nbFilled, nbFile, "");
	}

	public static String outputFilename(int n, int nbFilled, int nbFile, String runName) {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String runNamePart = (runName != null && !runName.isEmpty()) ? "-" + runName : "";
		return String.format("out-%s%s-%s.out", baseFilename(n, nbFilled, nbFile), runNamePart, timestamp);
	}

	public static String outputFilepath(int n, int nbFilled, int nbFile, String runName) {
		return "./logs/LatinSquare/" + outputFilename(n, nbFilled, nbFile, runName);
	}

	// --- create Latin Square model ---

	public static IntVar[][] makeModel(int n, int nbFilled, int nbFile, String model, Solver cp) {
		IntVar[][] x;

		switch (model) {
		case MAX_PRODUCT_INIT:
			x = makeLatinSquareInit(cp, n, nbFilled, nbFile, true);
			break;
		case MAX_PRODUCT_ORACLE:
			x = makeLatinSquareOracle(cp, n, nbFilled, nbFile, true);
			break;
		case SUM_PRODUCT_NO_INIT:
			x = makeLatinSquare(cp, n, nbFilled, nbFile, false);
			break;
		case SUM_PRODUCT_INIT:
			x = makeLatinSquareInit(cp, n, nbFilled, nbFile, false);
			break;
		case SUM_PRODUCT_ORACLE:
			x = makeLatinSquareOracle(cp, n, nbFilled, nbFile, false);
			break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + model);
		}
		return x;
	}

	public static void partialAssignments(IntVar[][] vars, int n, int nbFilled, int nbFile) {
		try {
			Scanner scanner = new Scanner(new FileReader(instanceFilepath(n, nbFilled, nbFile)));

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
	public static IntVar[][] makeLatinSquareInit(Solver cp, int n, int nbFilled, int nbFile, boolean maxProduct) {
		IntVar[][] x = makeLatinSquare(cp, n, nbFilled, nbFile, maxProduct);

		// proportional oracle on diagonal
		for (int i = 0; i < n; i++) {
			setProportionalMarginals(x[i][i]);
		}
		return x;
	}

	/*
	 * Make latin square with proportional oracle on the diagonal
	 */
	public static IntVar[][] makeLatinSquareOracle(Solver cp, int n, int nbFilled, int nbFile, boolean maxProduct) {
		IntVar[][] x = makeLatinSquare(cp, n, nbFilled, nbFile, maxProduct);

		// proportional oracle on diagonal
		for (int i = 0; i < n; i++) {
			cp.post(proportionalOracle(x[i][i]));
		}
		return x;
	}

	public static IntVar[][] makeLatinSquare(Solver cp, int n, int nbFilled, int nbFile, boolean maxProduct) {
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

	public static void setProportionalMarginals(IntVar var) {
		int[] values = new int[var.size()];
		var.fillArray(values);

		for (int i = 0; i < values.length; i++) {
			var.setMarginal(values[i], (double) values[i] + 1);
		}
	}

}
