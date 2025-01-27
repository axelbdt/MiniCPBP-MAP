/*
 * Created on Apr 25, 2005
 * Updated on May 2, 2013 (support for rectangular matrices)
 *
 * Konstantinos A. Nedas
 * Department of Spatial Information Science & Engineering
 * University of Maine, Orono, ME 04469-5711, USA
 * kostas@spatial.maine.edu
 * http://www.spatial.maine.edu/~kostas
 *
 * This Java class implements the Hungarian algorithm [a.k.a Munkres' algorithm,
 * a.k.a. Kuhn algorithm, a.k.a. Assignment problem, a.k.a. Marriage problem,
 * a.k.a. Maximum Weighted Maximum Cardinality Bipartite Matching].
 *
 * [It can be used as a method call from within any main (or other function).]
 * It takes two arguments:
 * a. A 2D array (could be rectangular or square) with all values >= 0.
 * b. A string ("min" or "max") specifying whether you want the min or max assignment.
 * [It returns an assignment matrix[min(array.length, array[0].length)][2] that contains
 * the row and col of the elements (in the original inputted array) that make up the
 * optimum assignment or the sum of the assignment weights, depending on which method
 * is used: hgAlgorithmAssignments or hgAlgorithm, respectively.]
 *
 * [This version contains only scarce comments. If you want to understand the
 * inner workings of the algorithm, get the tutorial version of the algorithm
 * from the same website you got this one (www.spatial.maine.edu/~kostas).]
 *
 * Any comments, corrections, or additions would be much appreciated.
 * Credit due to professor Bob Pilgrim for providing an online copy of the
 * pseudocode for this algorithm (http://216.249.163.93/bob.pilgrim/445/munkres.html)
 *
 * Feel free to redistribute this source code, as long as this header--with
 * the exception of sections in brackets--remains as part of the file.
 *
 * Note: Some sections in brackets have been modified as not to provide misinformation
 *       about the current functionality of this code.
 *
 * Requirements: JDK 1.5.0_01 or better.
 * [Created in Eclipse 3.1M6 (www.eclipse.org).]
 *
 */

package minicpbp.util;

import java.util.Arrays;

public class HungarianAlgorithm {

    public enum Mask {
        NONE, STAR, PRIME
    }

    int n;
    double[][] costs;
    Mask[][] mask;
    boolean[] rowCover;
    boolean[] colCover;
    int[] zero_RC;
    int[][] path;
    int dim;

    public HungarianAlgorithm(int n) {
        this.n = n;
        dim = n;
        costs = new double[n][n];
        mask = new Mask[n][n]; // The mask array.
        rowCover = new boolean[n]; // The row covering vector.
        colCover = new boolean[n]; // The column covering vector.
        zero_RC = new int[2]; // Position of last zero from Step 4.
        path = new int[n * n + 2][2];
    }

    public void resetMask() {
        for (var row : mask) {
            Arrays.fill(row, Mask.NONE);
        }
    }

    public record HungarianResult(int[][] assignments, double[][] costs, Mask[][] mask, double assignmentSum) {
    }

    // *******************************************//
    // METHODS THAT PERFORM ARRAY-PROCESSING TASKS//
    // *******************************************//

    public double[][] copyOf // Copies all elements of an array to a new array.
    (double[][] original) {
        double[][] copy = new double[original.length][original[0].length];
        for (int i = 0; i < original.length; i++) {
            // Need to do it this way, otherwise it copies only memory location
            System.arraycopy(original[i], 0, copy[i], 0, original[i].length);
        }

        return copy;
    }

    public double[][] copyToSquare(double[][] original) {
        // Creates a copy of an array, made square by padding the right or bottom.
        int rows = original.length;
        int cols = original[0].length; // Assume we're given a rectangular array.
        double[][] result = null;

        if (rows == cols) // The matrix is already square.
        {
            result = copyOf(original);
        } else if (rows > cols) // Pad on some extra columns on the right.
        {
            result = new double[rows][rows];
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < rows; j++) {
                    if (j >= cols) // Use the padValue to fill the right columns.
                    {
                        result[i][j] = Double.MAX_VALUE;
                    } else {
                        result[i][j] = original[i][j];
                    }
                }
            }
        } else { // rows < cols; Pad on some extra rows at the bottom.
            result = new double[cols][cols];
            for (int i = 0; i < cols; i++) {
                for (int j = 0; j < cols; j++) {
                    if (i >= rows) // Use the padValue to fill the bottom rows.
                    {
                        result[i][j] = Double.MAX_VALUE;
                    } else {
                        result[i][j] = original[i][j];
                    }
                }
            }
        }

        return result;
    }

    public void resetToZeroes(int[][] array) {
        for (var row : array) {
            Arrays.fill(row, 0);
        }
    }

    public void resetToZeroes(int[] array) {
        Arrays.fill(array, 0);
    }

    // **********************************//
    // METHODS OF THE HUNGARIAN ALGORITHM//
    // **********************************//

    // Core of the algorithm; takes required inputs and returns the assignments
    public HungarianResult hgAlgorithmAssignments(double[][] array, int dim, boolean copy) {
        // This variable is used to pad a rectangular array (so it will be picked all
        // last [cost] or first [profit])
        // and will not interfere with final assignments. Also, it is used to flip the
        // relationship between weight when "max" defines it as a profit matrix instead of a cost matrix.
        // Double.MAX_VALUE is not ideal, since arithmetic
        // needs to be performed and overflow may occur.
        double maxWeightPlusOne = Double.MAX_VALUE;

        this.dim = dim;

        costs = array;
        if (copy) {
            costs = copyToSquare(array);
        }

        resetMask();
        Arrays.fill(rowCover, false);
        Arrays.fill(colCover, false);
        zero_RC = new int[2]; // Position of last zero from Step 4.
        path = new int[dim * dim + 2][2];
        int step = 1;
        boolean done = false;
        while (done == false) // main execution loop
        {
            switch (step) {
                case 1:
                    step = hg_step1();
                    break;
                case 2:
                    step = hg_step2();
                    break;
                case 3:
                    step = hg_step3();
                    break;
                case 4:
                    step = hg_step4();
                    break;
                case 5:
                    step = hg_step5();
                    break;
                case 6:
                    step = hg_step6();
                    break;
                case 7:
                    done = true;
                    break;
            }
        } // end while

        int[][] assignments = new int[dim][2]; // Create the returned array.
        int assignmentCount = 0; // In a input matrix taller than it is wide, the first
        // assignments column will have to skip some numbers, so
        // the index will not always match the first column ([0])
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                if (i < dim && j < dim && mask[i][j] == Mask.STAR) {
                    assignments[assignmentCount][0] = i;
                    assignments[assignmentCount][1] = j;
                    assignmentCount++;
                }
            }
        }
        double assignmentCost = getAssignmentSum(array, assignments);
        return new HungarianResult(assignments, costs, mask, assignmentCost);
    }

    public double getAssignmentSum(double[][] array, int[][] assignments) {
        // Returns the min/max sum (cost/profit of the assignment) given the
        // original input matrix and an assignment array (from hgAlgorithmAssignments)
        double sum = 0;
        for (int i = 0; i < assignments.length; i++) {
            sum = sum + array[assignments[i][0]][assignments[i][1]];
        }
        return sum;
    }

    public double getAssignmentProduct(double[][] array, int[][] assignments) {
        // Returns the min/max sum (cost/profit of the assignment) given the
        // original input matrix and an assignment array (from hgAlgorithmAssignments)
        double product = 1;
        for (int i = 0; i < assignments.length; i++) {
            product = product * array[assignments[i][0]][assignments[i][1]];
        }
        return product;
    }

    public int hg_step1() {
        // What STEP 1 does:
        // For each row of the cost matrix, find the smallest element
        // and subtract it from from every other element in its row.

        double minval;

        for (int i = 0; i < dim; i++) {
            minval = costs[i][0];
            for (int j = 0; j < dim; j++) // 1st inner loop finds min val in row.
            {
                if (minval > costs[i][j]) {
                    minval = costs[i][j];
                }
            }
            for (int j = 0; j < dim; j++) // 2nd inner loop subtracts it.
            {
                costs[i][j] = costs[i][j] - minval;
            }
        }

        return 2; // next step is 2
    }

    public int hg_step2() {
        // What STEP 2 does:
        // Marks uncovered zeros as starred and covers their row and column.

        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                if ((costs[i][j] == 0) && (!colCover[j]) && (!rowCover[i])) {
                    mask[i][j] = Mask.STAR;
                    colCover[j] = true;
                    rowCover[i] = true;
                }
            }
        }

        clearCovers(rowCover, colCover); // Reset cover vectors.

        return 3; // next step is 3
    }

    public int hg_step3() {
        // What STEP 3 does:
        // Cover columns of starred zeros. Check if all columns are covered.
        int step = -1;

        for (int i = 0; i < dim; i++) // Cover columns of starred zeros.
        {
            for (int j = 0; j < dim; j++) {
                if (mask[i][j] == Mask.STAR) {
                    colCover[j] = true;
                }
            }
        }

        int count = 0;
        for (int j = 0; j < colCover.length; j++) // Check if all columns are covered.
        {
            count += colCover[j] ? 1 : 0;
        }

        if (count >= dim) {
            step = 7;
        } else {
            step = 4;
        }

        return step;
    }

    public int hg_step4() {
        // What STEP 4 does:
        // Find an uncovered zero in cost and prime it (if none go to step 6). Check for
        // star in same row:
        // if yes, cover the row and uncover the star's column. Repeat until no
        // uncovered zeros are left
        // and go to step 6. If not, save location of primed zero and go to step 5.
        int step = -1;

        int[] row_col = new int[2]; // Holds row and col of uncovered zero.
        boolean done = false;
        while (done == false) {
            row_col = findUncoveredZero(row_col, costs, rowCover, colCover);
            if (row_col[0] == -1) {
                done = true;
                step = 6;
            } else {
                mask[row_col[0]][row_col[1]] = Mask.PRIME; // Prime the found uncovered zero.

                boolean starInRow = false;
                for (int j = 0; j < dim; j++) {
                    if (mask[row_col[0]][j] == Mask.STAR) // If there is a star in the same row...
                    {
                        starInRow = true;
                        row_col[1] = j; // remember its column.
                    }
                }

                if (starInRow == true) {
                    rowCover[row_col[0]] = true; // Cover the star's row.
                    colCover[row_col[1]] = false; // Uncover its column.
                } else {
                    zero_RC[0] = row_col[0]; // Save row of primed zero.
                    zero_RC[1] = row_col[1]; // Save column of primed zero.
                    done = true;
                    step = 5;
                }
            }
        }

        return step;
    }

    public int[] findUncoveredZero // Aux 1 for hg_step4.
    (int[] row_col, double[][] cost, boolean[] rowCover, boolean[] colCover) {
        row_col[0] = -1; // Just a check value. Not a real index.
        row_col[1] = 0;

        int i = 0;
        boolean done = false;
        while (done == false) {
            int j = 0;
            while (j < dim) {
                if (cost[i][j] == 0 && !rowCover[i] && !colCover[j]) {
                    row_col[0] = i;
                    row_col[1] = j;
                    done = true;
                }
                j = j + 1;
            } // end inner while
            i = i + 1;
            if (i >= dim) {
                done = true;
            }
        } // end outer while

        return row_col;
    }

    public int hg_step5() {
        // What STEP 5 does:
        // Construct series of alternating primes and stars. Start with prime from step
        // 4.
        // Take star in the same column. Next take prime in the same row as the star.
        // Finish
        // at a prime with no star in its column. Unstar all stars and star the primes
        // of the
        // series. Erasy any other primes. Reset covers. Go to step 3.

        int count = 0; // Counts rows of the path matrix.
        // int[][] path = new int[(mask[0].length + 2)][2]; //Path matrix (stores row
        // and col).
        path[count][0] = zero_RC[0]; // Row of last prime.
        path[count][1] = zero_RC[1]; // Column of last prime.

        boolean done = false;
        while (done == false) {
            int r = findStarInCol(mask, path[count][1]);
            if (r >= 0) {
                count = count + 1;
                path[count][0] = r; // Row of starred zero.
                path[count][1] = path[count - 1][1]; // Column of starred zero.
            } else {
                done = true;
            }

            if (done == false) {
                int c = findPrimeInRow(mask, path[count][0]);
                count = count + 1;
                path[count][0] = path[count - 1][0]; // Row of primed zero.
                path[count][1] = c; // Col of primed zero.
            }
        } // end while

        convertPath(mask, path, count);
        clearCovers(rowCover, colCover);
        erasePrimes(mask);

        return 3; // next step is 3

    }

    public int findStarInCol // Aux 1 for hg_step5.
    (Mask[][] mask, int col) {
        int r = -1; // Again this is a check value.
        for (int i = 0; i < dim; i++) {
            if (mask[i][col] == Mask.STAR) {
                r = i;
            }
        }

        return r;
    }

    public int findPrimeInRow // Aux 2 for hg_step5.
    (Mask[][] mask, int row) {
        int c = -1;
        for (int j = 0; j < dim; j++) {
            if (mask[row][j] == Mask.PRIME) {
                c = j;
            }
        }

        return c;
    }

    public void convertPath // Aux 3 for hg_step5.
    (Mask[][] mask, int[][] path, int count) {
        for (int i = 0; i <= count; i++) {
            if (mask[path[i][0]][path[i][1]] == Mask.STAR) {
                mask[path[i][0]][path[i][1]] = Mask.NONE;
            } else {
                mask[path[i][0]][path[i][1]] = Mask.STAR;
            }
        }
    }

    public void erasePrimes // Aux 4 for hg_step5.
    (Mask[][] mask) {
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                if (mask[i][j] == Mask.PRIME) {
                    mask[i][j] = Mask.NONE;
                }
            }
        }
    }

    public void clearCovers // Aux 5 for hg_step5 (and not only).
    (boolean[] rowCover, boolean[] colCover) {
        for (int i = 0; i < dim; i++) {
            rowCover[i] = false;
        }
        for (int j = 0; j < colCover.length; j++) {
            colCover[j] = false;
        }
    }

    public int hg_step6() {
        // What STEP 6 does:
        // Find smallest uncovered value in cost: a. Add it to every element of covered
        // rows
        // b. Subtract it from every element of uncovered columns. Go to step 4.
        int step;

        double minval = findSmallest(costs, rowCover, colCover);

        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                if (rowCover[i]) {
                    costs[i][j] = costs[i][j] + minval;
                }
                if (!colCover[j]) {
                    costs[i][j] = costs[i][j] - minval;
                }
            }
        }

        return 4; // next step is 4
    }

    public double findSmallest // Aux 1 for hg_step6.
    (double[][] cost, boolean[] rowCover, boolean[] colCover) {
        double minval = Double.POSITIVE_INFINITY; // There cannot be a larger cost than this.
        for (int i = 0; i < dim; i++) // Now find the smallest uncovered value.
        {
            for (int j = 0; j < dim; j++) {
                if (!rowCover[i] && !colCover[j] && (minval > cost[i][j])) {
                    minval = cost[i][j];
                }
            }
        }

        return minval;
    }

    public void set(double[][] arr, int i, int j, double v) {
        arr[i][j] = v;
    }
}
