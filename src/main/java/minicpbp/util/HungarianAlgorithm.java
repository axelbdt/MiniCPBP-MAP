/*
 * Created on Apr 25, 2005
 * Updated on May 2, 2013
 *
 * Konstantinos A. Nedas
 * Department of Spatial Information Science & Engineering
 * University of Maine, Orono, ME 04469-5711, USA
 * kostas@spatial.maine.edu
 * http://www.spatial.maine.edu/~kostas
 *
 * This Java class implements the Hungarian algorithm
 * [a.k.a Munkres' algorithm,
 * a.k.a. Kuhn algorithm,
 * a.k.a. Assignment problem,
 * a.k.a. Marriage problem,
 * a.k.a. Maximum Weighted Maximum Cardinality Bipartite Matching].
 *
 * It takes two arguments:
 * a. A 2D array with all values >= 0.
 * b. A dimension wich will limit the algo to a square subarray
 *
 * [This version contains only scarce comments.
 * If you want to understand the inner workings of the algorithm,
 * get the tutorial version of the algorithm
 * from the same website you got this one
 * (www.spatial.maine.edu/~kostas).]
 *
 * Any comments, corrections, or additions would be much appreciated.
 * Credit due to professor Bob Pilgrim for providing an online copy of the
 * pseudocode for this algorithm
 * (http://216.249.163.93/bob.pilgrim/445/munkres.html)
 *
 * Feel free to redistribute this source code, as long as this header--with
 * the exception of sections in brackets--remains as part of the file.
 *
 * Note: Some sections in brackets have been modified as not to provide
 * misinformation about the current functionality of this code.
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
    int[] assignments;
    int dim;

    public HungarianAlgorithm(int n) {
        this.n = n;
        dim = n;
        costs = new double[n][n];
        mask = new Mask[n][n]; // The mask array.
        rowCover = new boolean[n]; // The row covering vector.
        colCover = new boolean[n]; // The column covering vector.
        // TODO : remove zero_RC ?
        zero_RC = new int[2]; // Position of last zero from Step 4.
        path = new int[n * n + 2][2];
        assignments = new int[n];
    }

    public void resetMask() {
        for (var row : mask) {
            Arrays.fill(row, Mask.NONE);
        }
    }

    public void resetPath() {
        for (var row : path) {
            Arrays.fill(row, 0);
        }
    }

    public double[][] getCosts() {
        return costs;
    }

    public int[] getAssignments() {
        return assignments;
    }


    // **********************************//
    // METHODS OF THE HUNGARIAN ALGORITHM//
    // **********************************//

    // Core of the algorithm; takes required inputs and returns the assignments
    public void hgAlgorithmAssignments(double[][] array, int dim) {
        this.dim = dim;
        costs = array;

        resetMask();
        Arrays.fill(rowCover, false);
        Arrays.fill(colCover, false);
        zero_RC[0] = 0;
        zero_RC[1] = 0;
        resetPath();

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

        Arrays.fill(assignments, -1);
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                if (mask[i][j] == Mask.STAR) {
                    assignments[i] = j;
                    break;
                }
            }
        }
    }

    public int hg_step1() {
        // What STEP 1 does:
        // For each row of the cost matrix, find the smallest element
        // and subtract it from from every other element in its row.

        double minval;

        for (int i = 0; i < dim; i++) {
            minval = costs[i][0];
            // 1st inner loop finds min val in row.
            for (int j = 0; j < dim; j++) {
                if (minval > costs[i][j]) {
                    minval = costs[i][j];
                }
            }
            // 2nd inner loop subtracts it.
            for (int j = 0; j < dim; j++) {
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
                if (costs[i][j] == 0 && !colCover[j] && !rowCover[i]) {
                    mask[i][j] = Mask.STAR;
                    colCover[j] = true;
                    rowCover[i] = true;
                }
            }
        }

        clearCovers(); // Reset cover vectors.

        return 3; // next step is 3
    }

    public int hg_step3() {
        // What STEP 3 does:
        // Cover columns of starred zeros.
        // Check if all columns are covered.
        int step = -1;

        // Cover columns of starred zeros.
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                if (mask[i][j] == Mask.STAR) {
                    colCover[j] = true;
                }
            }
        }

        // Check if all columns are covered.
        int count = 0;
        for (int j = 0; j < dim; j++) {
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
        // Find an uncovered zero in cost and prime it (if none go to step 6).
        // Check for star in same row:
        // if yes, cover the row and uncover the star's column.
        // Repeat until no uncovered zeros are left and go to step 6.
        // If not, save location of primed zero and go to step 5.
        int step = -1;

        boolean done = false;
        while (done == false) {
            // find Uncovered Zeroes
            int row = -1; // Just a check value. Not a real index.
            int col = 0;

            int i = 0;
            boolean findDone = false;
            while (findDone == false) {
                int j = 0;
                while (j < dim) {
                    if (costs[i][j] == 0 && !rowCover[i] && !colCover[j]) {
                        row = i;
                        col = j;
                        findDone = true;
                    }
                    j = j + 1;
                } // end inner while
                i = i + 1;
                if (i >= dim) {
                    findDone = true;
                }
            } // end outer while
            if (row == -1) {
                done = true;
                step = 6;
            } else {
                mask[row][col] = Mask.PRIME; // Prime the found uncovered zero.

                boolean starInRow = false;
                for (int j = 0; j < dim; j++) {
                    // If there is a star in the same row...
                    if (mask[row][j] == Mask.STAR) {
                        starInRow = true;
                        col = j; // remember its column.
                    }
                }

                if (starInRow == true) {
                    rowCover[row] = true; // Cover the star's row.
                    colCover[col] = false; // Uncover its column.
                } else {
                    zero_RC[0] = row; // Save row of primed zero.
                    zero_RC[1] = col; // Save column of primed zero.
                    done = true;
                    step = 5;
                }
            }
        }

        return step;
    }


    public int hg_step5() {
        // What STEP 5 does:
        // Construct series of alternating primes and stars.
        // Start with prime from step 4.
        // Take star in the same column.
        // Next take prime in the same row as the star.
        // Finish at a prime with no star in its column.
        // Unstar all stars and star the primes of the series.
        // Erase any other primes. Reset covers. Go to step 3.

        int count = 0; // Counts rows of the path matrix.
        path[count][0] = zero_RC[0]; // Row of last prime.
        path[count][1] = zero_RC[1]; // Column of last prime.

        boolean done = false;
        while (done == false) {
            int r = findStarInCol(path[count][1]);
            if (r >= 0) {
                count = count + 1;
                path[count][0] = r; // Row of starred zero.
                path[count][1] = path[count - 1][1]; // Col of starred zero.
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

        convertPath(count);
        clearCovers();
        erasePrimes();

        return 3; // next step is 3

    }

    public int findStarInCol(int col) { // Aux 1 for hg_step5.
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

    public void convertPath(int count) { // Aux 3 for hg_step5.
        for (int i = 0; i <= count; i++) {
            if (mask[path[i][0]][path[i][1]] == Mask.STAR) {
                mask[path[i][0]][path[i][1]] = Mask.NONE;
            } else {
                mask[path[i][0]][path[i][1]] = Mask.STAR;
            }
        }
    }

    public void erasePrimes() { // Aux 4 for hg_step5.
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                if (mask[i][j] == Mask.PRIME) {
                    mask[i][j] = Mask.NONE;
                }
            }
        }
    }

    public void clearCovers() { // Aux 5 for hg_step5 (and not only).
        Arrays.fill(rowCover, false);
        Arrays.fill(colCover, false);
    }

    public int hg_step6() {
        // What STEP 6 does:
        // Find smallest uncovered value in cost:
        // a. Add it to every element of covered rows
        // b. Subtract it from every elem of uncovered col. Go to step 4.
        int step;

        double minval = findSmallest();

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

    public double findSmallest() { // Aux 1 for hg_step6.
        double minval = Double.POSITIVE_INFINITY;
        // Now find the smallest uncovered value.
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                if (!rowCover[i] && !colCover[j] && minval > costs[i][j]) {
                    minval = costs[i][j];
                }
            }
        }
        return minval;
    }

    public void set(double[][] arr, int i, int j, double v) {
        arr[i][j] = v;
    }
}
