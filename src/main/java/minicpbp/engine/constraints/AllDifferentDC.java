/*
 * mini-cp is free software: you can redistribute it and/or modify
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
 * Copyright (c)  2018. by Laurent Michel, Pierre Schaus, Pascal Van Hentenryck
 *
 * mini-cpbp, replacing classic propagation by belief propagation
 * Copyright (c)  2019. by Gilles Pesant
 */

package minicpbp.engine.constraints;

import minicpbp.engine.core.AbstractConstraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.state.StateSparseSet;
import minicpbp.util.GraphUtil;
import minicpbp.util.GraphUtil.Graph;
import minicpbp.util.OldHungarianAlgorithm;
import minicpbp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Domain Consistent AllDifferent Constraint
 * <p>
 * Algorithm described in
 * "A filtering algorithm for constraints of difference in CSPs" J-C. Régin, AAAI-94
 */
public class AllDifferentDC extends AbstractConstraint {

    private IntVar[] x;

    private final MaximumMatching maximumMatching;

    private final int nVar;
    private int nVal;

    // residual graph
    private ArrayList<Integer>[] in;
    private ArrayList<Integer>[] out;
    private int nNodes;
    private Graph g = new Graph() {
        @Override
        public int n() {
            return nNodes;
        }

        @Override
        public Iterable<Integer> in(int idx) {
            return in[idx];
        }

        @Override
        public Iterable<Integer> out(int idx) {
            return out[idx];
        }
    };

    private int[] match;
    private boolean[] matched;

    private int minVal;
    private int maxVal;

    private static final int exactPermanentThreshold = 6;
    private double[][] beliefs;
    private StateSparseSet freeVars; // holds an index for vars
    private StateSparseSet freeVals; // holds the actual values of freeVals
    private double[] gamma;
    private int[] c;
    private int[] permutation;
    private int[] varIndices;
    private int[] vals;
    private double[] rowMax;
    private double[] rowMaxSecondBest;

    // fields for JVC algo for maximum weight matching
    private double[] u;
    private double[] v;
    private double[] shortestPathCosts;
    private int[] path;
    private int[] row4col;
    private int[] col4row;
    private boolean[] SR;
    private boolean[] SC;
    private int[] remaining;
    private double[] minWeight;

    private int[] pathBackUp;
    private int[] row4colBackUp;
    private int[] col4rowBackUp;

    // Additional fields for fast max product algorithm
    private int[] optimalCol4Row;
    private int[] optimalRow4Col;
    private double[] optimalU;
    private double[] optimalV;

    // Fields for comparing algorithm results
    private double[][] exactBeliefs;    // Store exact algorithm results
    private double[][] fastBeliefs;     // Store fast algorithm results
    private double maxBeliefDifference; // Track maximum difference found

    public AllDifferentDC(IntVar... x) {
        super(x[0].getSolver(), x);
        setName("AllDifferentDC");
        this.x = x;
        maximumMatching = new MaximumMatching(x);
        match = new int[x.length];
        this.nVar = x.length;

        freeVars = new StateSparseSet(getSolver().getStateManager(), x.length, 0);
        // accumulate values from domains
        SortedSet<Integer> allVals = new TreeSet<Integer>();
        for (IntVar var : x) {
            int s = var.fillArray(domainValues);
            for (int j = 0; j < s; j++) {
                allVals.add(domainValues[j]);
            }
        }
        // remove assigned variables and their values from further consideration
        for (int i = 0; i < x.length; i++) {
            if (x[i].isBound()) {
                freeVars.remove(i);
                int val = x[i].min();
                allVals.remove(val);
                // apply basic fwd checking (because we may not call propagate())
                for (int k = 0; k < i; k++) {
                    x[k].remove(val);
                }
                for (int k = i + 1; k < x.length; k++) {
                    x[k].remove(val);
                }
            }
        }
        if (freeVars.isEmpty()) {
            freeVals = new StateSparseSet(getSolver().getStateManager(), 0, 0); // make it empty as well
            return; // special case of all variables in its scope already being bound
        }
        freeVals = new StateSparseSet(
                getSolver().getStateManager(),
                allVals.last().intValue() - allVals.first().intValue() + 1,
                allVals.first().intValue());
        // remove missing intermediate values from interval domain
        for (int i = allVals.first().intValue() + 1; i < allVals.last().intValue(); i++) {
            if (!allVals.contains(i))
                freeVals.remove(i);
        } // from now on freeVals will be maintained as a superset of the available values,
        // only removing values as they are taken on by a variable

        // allocate enough space for the data structures
        // even though we will need less and less as we go down the search tree
        beliefs = new double[freeVals.size()][freeVals.size()];
        c = new int[freeVals.size()];
        permutation = new int[freeVals.size()];
        varIndices = new int[freeVars.size()];
        vals = new int[freeVals.size()];
        rowMax = new double[freeVals.size()];
        rowMaxSecondBest = new double[freeVals.size()];
        if (freeVals.size() - 1 <= exactPermanentThreshold) {
            setExactWCounting(true);
        } else {
            setExactWCounting(false);
            // it will be exact below the threshold,
            // which may happen lower in the search tree
        }
        precompute_gamma(freeVals.size());

        // allocate for JVC algo for maximum weight matching
        u = new double[freeVars.size()];
        v = new double[freeVals.size()];
        shortestPathCosts = new double[freeVals.size()];
        path = new int[freeVals.size()];
        row4col = new int[freeVals.size()];
        col4row = new int[freeVars.size()];
        SR = new boolean[freeVars.size()];
        SC = new boolean[freeVals.size()];
        remaining = new int[freeVals.size()];
        minWeight = new double[1];
        pathBackUp = new int[freeVals.size()];
        row4colBackUp = new int[freeVals.size()];
        col4rowBackUp = new int[freeVars.size()];

        // Additional allocations for fast max product algorithm
        optimalCol4Row = new int[freeVars.size()];
        optimalRow4Col = new int[freeVals.size()];
        optimalU = new double[freeVars.size()];
        optimalV = new double[freeVals.size()];

        // Allocations for comparing algorithm results
        exactBeliefs = new double[freeVars.size()][freeVals.size()];
        fastBeliefs = new double[freeVars.size()][freeVals.size()];
        maxBeliefDifference = 0.0;
    }

    @Override
    public void post() {
        switch (getSolver().getMode()) {
            case BP:
                break;
            case SP:
            case SBP:
                for (int i = 0; i < nVar; i++) {
                    x[i].propagateOnDomainChange(this);
                }
                updateRange();
                matched = new boolean[nVal];
                nNodes = nVar + nVal + 1;
                in = new ArrayList[nNodes];
                out = new ArrayList[nNodes];
                for (int i = 0; i < nNodes; i++) {
                    in[i] = new ArrayList<>();
                    out[i] = new ArrayList<>();
                }
                propagate();
        }
    }

    private void updateRange() {
        minVal = Integer.MAX_VALUE;
        maxVal = Integer.MIN_VALUE;
        for (int i = 0; i < nVar; i++) {
            minVal = Math.min(minVal, x[i].min());
            maxVal = Math.max(maxVal, x[i].max());
        }
        nVal = maxVal - minVal + 1;
    }


    private void updateGraph() {
        nNodes = nVar + nVal + 1;
        int sink = nNodes - 1;
        for (int i = 0; i < nNodes; i++) {
            in[i].clear();
            out[i].clear();
        }
        Arrays.fill(matched, 0, nVal, false);
        for (int i = 0; i < x.length; i++) {
            in[i].add(match[i] - minVal + x.length);
            out[match[i] - minVal + nVar].add(i);
            matched[match[i] - minVal] = true;
        }
        for (int i = 0; i < nVar; i++) {
            for (int v = x[i].min(); v <= x[i].max(); v++) {
                if (x[i].contains(v) && match[i] != v) {
                    in[v - minVal + nVar].add(i);
                    out[i].add(v - minVal + nVar);
                }
            }
        }
        for (int v = minVal; v <= maxVal; v++) {
            if (!matched[v - minVal]) {
                in[sink].add(v - minVal + nVar);
                out[v - minVal + nVar].add(sink);
            } else {
                in[v - minVal + nVar].add(sink);
                out[sink].add(v - minVal + nVar);
            }
        }
    }


    @Override
    public void propagate() {
        // update the maximum matching
        int size = maximumMatching.compute(match);
        if (size < x.length) {
            throw new InconsistencyException();
        }
        // update the range of values
        updateRange();
        // update the residual graph
        updateGraph();
        // compute SCC's
        int[] scc = GraphUtil.stronglyConnectedComponents(g);
        for (int i = 0; i < nVar; i++) {
            for (int v = minVal; v <= maxVal; v++) {
                if (match[i] != v && scc[i] != scc[v - minVal + nVar]) {
                    x[i].remove(v);
                }
            }
        }
    }

    @Override
    public void updateBeliefSumProduct() {
        // System.out.println("AllDifferentDC - SumProduct");
        int nbVar, nbVal;
        // update freeVars/Vals according to bound variables
        nbVar = freeVars.fillArray(varIndices);
        for (int j = 0; j < nbVar; j++) {
            int i = varIndices[j];
            if (x[i].isBound()) {
                freeVars.remove(i);
                int val = x[i].min();
                freeVals.remove(val);
                // set trivial local belief for bound var...
                setLocalBelief(i, val, beliefRep.one());
                // ...and for other vars on that value
                for (int k = 0; k < j; k++) {
                    int l = varIndices[k];
                    if (x[l].contains(val))
                        setLocalBelief(l, val, beliefRep.zero());
                }
                for (int k = j + 1; k < nbVar; k++) {
                    int l = varIndices[k];
                    if (x[l].contains(val))
                        setLocalBelief(l, val, beliefRep.zero());
                }
            }
        }
        nbVar = freeVars.fillArray(varIndices);
        nbVal = freeVals.fillArray(vals);
        // initialize outside beliefs matrix (MUST BE IN STANDARD [0,1] REPRESENTATION)
        for (int j = 0; j < nbVar; j++) {
            int i = varIndices[j];
            for (int k = 0; k < nbVal; k++) {
                int val = vals[k];
                beliefs[j][k] = x[i].contains(val) ?
                        beliefRep.rep2std(outsideBelief(i, val))
                        : 0;
            }
        }
        // may need to add dummy rows in order to make the beliefs matrix square
        for (int j = 0; j < nbVal - nbVar; j++) {
            for (int k = 0; k < nbVal; k++) {
                // make row sum to 1
                // because we use this property in costBasedPermanent_UB3_faster()
                beliefs[nbVar + j][k] = 1.0 / nbVal; // (STANDARD REPRESENTATION)
            }
        }
        // set local beliefs by computing the permanent of beliefs sub-matrices
        if (nbVal - 1 <= exactPermanentThreshold) {
            // exact permanent
            setExactWCounting(true);
            for (int j = 0; j < nbVar; j++) {
                int i = varIndices[j];
                for (int k = 0; k < nbVal; k++) {
                    int val = vals[k];
                    if (x[i].contains(val)) {
                        // note: will be normalized later in AbstractConstraint.sendMessages()
                        // put beliefs back to their original representation
                        double newBelief = costBasedPermanent_exact(j, k, nbVal);
                        setLocalBelief(i, val, beliefRep.std2rep(newBelief));
                    }
                }
            }
        } else {
            // approximate permanent
            setExactWCounting(false);
            costBasedPermanent_UB3_precomputeRowMax(nbVal);
            for (int j = 0; j < nbVar; j++) {
                int i = varIndices[j];
                for (int k = 0; k < nbVal; k++) {
                    int val = vals[k];
                    if (x[i].contains(val)) {
                        // note: will be normalized later in AbstractConstraint.sendMessages()
                        // put beliefs back to their original representation
                        double newBelief = costBasedPermanent_UB3_faster(
                                j, k, nbVal, nbVal - nbVar);
                        setLocalBelief(i, val, beliefRep.std2rep(newBelief));
                    }
                }
            }
        }
    }


    @Override
    public void updateBeliefMaxProduct() {
        int nbVar, nbVal;
        // update freeVars/Vals according to bound variables
        nbVar = freeVars.fillArray(varIndices);
        for (int j = 0; j < nbVar; j++) {
            int i = varIndices[j];
            if (x[i].isBound()) {
                freeVars.remove(i);
                int val = x[i].min();
                freeVals.remove(val);
                // set trivial local belief for bound var...
                setLocalBelief(i, val, beliefRep.one());
                // ...and for other vars on that value
                for (int k = 0; k < j; k++) {
                    int l = varIndices[k];
                    if (x[l].contains(val))
                        setLocalBelief(l, val, beliefRep.zero());
                }
                for (int k = j + 1; k < nbVar; k++) {
                    int l = varIndices[k];
                    if (x[l].contains(val))
                        setLocalBelief(l, val, beliefRep.zero());
                }
            }
        }
        nbVar = freeVars.fillArray(varIndices);
        nbVal = freeVals.fillArray(vals);

        if (nbVar <= 1) return;

        // Initialize cost matrix for JVC algo and max matching
        for (int i = 0; i < nbVar; i++) {
            int var = varIndices[i];
            for (int j = 0; j < nbVal; j++) {
                int val = vals[j];
                beliefs[i][j] = x[var].contains(val) && !beliefRep.isZero(outsideBelief(var, val)) ?
                        -beliefRep.rep2log(outsideBelief(var, val))
                        : Double.MAX_VALUE;
            }
        }

        // Choose algorithm based on flag
        Solver.FasterAllDiffMaxProd flag = cp.fasterAllDiffMaxProd();

        if (flag == Solver.FasterAllDiffMaxProd.NO) {
            // Use only exact algorithm
            updateBeliefMaxProductExact(nbVar, nbVal, true); // true = call setLocalBelief
        } else if ((flag == Solver.FasterAllDiffMaxProd.YES) ||
                (flag == Solver.FasterAllDiffMaxProd.SQUARE && nbVar == nbVal)) {
            // Use only fast algorithm
            updateBeliefMaxProductFast(nbVar, nbVal, true); // true = call setLocalBelief
        }
        // Note: To enable comparison mode, add FasterAllDiffMaxProd.COMPARE to your enum
        // and uncomment the following:
        else if (flag == Solver.FasterAllDiffMaxProd.COMPARE) {
            compareAlgorithms(nbVar, nbVal);
        }
    }

    private void compareAlgorithms(int nbVar, int nbVal) {
        // Run both algorithms and store results without calling setLocalBelief
        updateBeliefMaxProductExact(nbVar, nbVal, false);
        updateBeliefMaxProductFast(nbVar, nbVal, false);

        // Compare normalized results and track maximum difference
        maxBeliefDifference = 0.0;
        double totalDifference = 0.0;
        int comparisonCount = 0;

        // Temporary arrays for normalized beliefs
        double[] exactNormalized = new double[nbVal];
        double[] fastNormalized = new double[nbVal];

        for (int i = 0; i < nbVar; i++) {
            int var = varIndices[i];

            // Clear normalization arrays
            Arrays.fill(exactNormalized, 0.0);
            Arrays.fill(fastNormalized, 0.0);

            // Calculate sums for normalization (only over valid values for this variable)
            double exactSum = 0.0;
            double fastSum = 0.0;
            int validValueCount = 0;

            for (int j = 0; j < nbVal; j++) {
                int val = vals[j];
                if (x[var].contains(val)) {
                    // Convert from belief representation to standard representation for normalization
                    double exactStd = beliefRep.rep2std(exactBeliefs[i][j]);
                    double fastStd = beliefRep.rep2std(fastBeliefs[i][j]);

                    exactNormalized[j] = exactStd;
                    fastNormalized[j] = fastStd;
                    exactSum += exactStd;
                    fastSum += fastStd;
                    validValueCount++;
                }
            }

            // Normalize beliefs for this variable (handle zero sum cases)
            if (exactSum > 1e-15 && fastSum > 1e-15) {
                for (int j = 0; j < nbVal; j++) {
                    int val = vals[j];
                    if (x[var].contains(val)) {
                        exactNormalized[j] /= exactSum;
                        fastNormalized[j] /= fastSum;

                        // Compare normalized beliefs
                        double difference = Math.abs(exactNormalized[j] - fastNormalized[j]);

                        if (difference > maxBeliefDifference) {
                            maxBeliefDifference = difference;
                        }
                        totalDifference += difference;
                        comparisonCount++;
                    }
                }
            } else {
                // Handle degenerate cases where one or both sums are zero
                for (int j = 0; j < nbVal; j++) {
                    int val = vals[j];
                    if (x[var].contains(val)) {
                        // If both sums are zero, normalized beliefs are equal (0/0 = uniform)
                        // If only one sum is zero, there's a significant difference
                        double difference;
                        if (exactSum <= 1e-15 && fastSum <= 1e-15) {
                            difference = 0.0; // Both algorithms predict zero probability
                        } else if (exactSum <= 1e-15) {
                            difference = 1.0 / validValueCount; // Exact is zero, fast is non-zero
                        } else {
                            difference = 1.0 / validValueCount; // Fast is zero, exact is non-zero
                        }

                        if (difference > maxBeliefDifference) {
                            maxBeliefDifference = difference;
                        }
                        totalDifference += difference;
                        comparisonCount++;
                    }
                }
            }

            // Set local beliefs using original unnormalized exact algorithm results
            for (int j = 0; j < nbVal; j++) {
                int val = vals[j];
                if (x[var].contains(val)) {
                    setLocalBelief(var, val, exactBeliefs[i][j]);
                }
            }
        }

        // Optional: Store statistics or log comparison results
        double avgDifference = comparisonCount > 0 ? totalDifference / comparisonCount : 0.0;
        System.out.println("Max difference: " + maxBeliefDifference +
                ", Avg difference: " + avgDifference +
                ", Comparisons: " + comparisonCount +
                ", Variables: " + nbVar +
                ", Values: " + nbVal);
    }

    /**
     * Get the maximum belief difference found during algorithm comparison
     *
     * @return Maximum absolute difference between exact and fast algorithm results
     */
    public double getMaxBeliefDifference() {
        return maxBeliefDifference;
    }

    /**
     * Reset the maximum belief difference tracker
     */
    public void resetMaxBeliefDifference() {
        maxBeliefDifference = 0.0;
    }

    /**
     * Update global maximum difference if needed (call this after constraint propagation)
     * You could add this to your CP interface to track across all AllDifferent constraints
     */
    public void updateGlobalMaxDifference() {
        // Example: cp.updateGlobalMaxBeliefDifference(maxBeliefDifference);
    }

    private void updateBeliefMaxProductExact(int nbVar, int nbVal, boolean setBeliefs) {
        // Original exact algorithm
        // Initialize belief storage if needed
        if (!setBeliefs) {
            for (int i = 0; i < nbVar; i++) {
                Arrays.fill(exactBeliefs[i], 0, nbVal, beliefRep.zero());
            }
        }

        for (int i = 0; i < nbVar; i++) {
            int var = varIndices[i];
            swapRow(i, nbVar);
            lsap(nbVar - 1, nbVal);
            System.arraycopy(path, 0, pathBackUp, 0, nbVal);
            System.arraycopy(row4col, 0, row4colBackUp, 0, nbVal);
            System.arraycopy(col4row, 0, col4rowBackUp, 0, nbVar);
            for (int j = 0; j < nbVal; j++) {
                int val = vals[j];
                if (x[var].contains(val)) {
                    // augmenting path starting with edge from i to j
                    int sink = augmentingPath(nbVal, nbVar - 1, j);

                    // augment partial solution
                    int l = sink;
                    while (true) {
                        int k = path[l];
                        row4col[l] = k;

                        // swap col4row[k] and l
                        int tmp = col4row[k];
                        col4row[k] = l;
                        l = tmp;

                        if (k == nbVar - 1) {
                            break;
                        }
                    }
                    // compute matching cost for n - 1 vars
                    double matchingCost = assignmentScore(nbVar - 1);
                    if (j < nbVal - 1) {
                        System.arraycopy(pathBackUp, 0, path, 0, nbVal);
                        System.arraycopy(row4colBackUp, 0, row4col, 0, nbVal);
                        System.arraycopy(col4rowBackUp, 0, col4row, 0, nbVar);
                    }

                    double newBelief = matchingCost == Double.MAX_VALUE ? beliefRep.zero()
                            : beliefRep.log2rep(-matchingCost);

                    if (setBeliefs) {
                        setLocalBelief(var, val, newBelief);
                    } else {
                        exactBeliefs[i][j] = newBelief;
                    }

                    // compareHungarianAlgorithms(i, j, nbVar, nbVal, newBelief);
                }
            }
            swapRow(i, nbVar);
        }
    }

    private void updateBeliefMaxProductFast(int nbVar, int nbVal, boolean setBeliefs) {
        // Faster algorithm using forced edge matching
        // Initialize belief storage if needed
        if (!setBeliefs) {
            for (int i = 0; i < nbVar; i++) {
                Arrays.fill(fastBeliefs[i], 0, nbVal, beliefRep.zero());
            }
        }

        // Compute initial optimal matching for all variables
        lsap(nbVar, nbVal);

        // Store optimal solution
        System.arraycopy(col4row, 0, optimalCol4Row, 0, nbVar);
        System.arraycopy(row4col, 0, optimalRow4Col, 0, nbVal);
        System.arraycopy(u, 0, optimalU, 0, nbVar);
        System.arraycopy(v, 0, optimalV, 0, nbVal);

        // For each variable and each possible value
        for (int i = 0; i < nbVar; i++) {
            int var = varIndices[i];
            for (int j = 0; j < nbVal; j++) {
                int val = vals[j];
                if (x[var].contains(val)) {
                    double matchingCost = computeMatchingCostWithForcedEdge(i, j, nbVar, nbVal);
                    double newBelief = matchingCost == Double.MAX_VALUE ? beliefRep.zero()
                            : beliefRep.log2rep(-matchingCost);

                    if (setBeliefs) {
                        setLocalBelief(var, val, newBelief);
                    } else {
                        fastBeliefs[i][j] = newBelief;
                    }
                }
            }
        }
    }

    private double computeMatchingCostWithForcedEdge(int forcedRow, int forcedCol, int nbVar, int nbVal) {
        // Restore optimal matching and dual variables
        System.arraycopy(optimalCol4Row, 0, col4row, 0, nbVar);
        System.arraycopy(optimalRow4Col, 0, row4col, 0, nbVal);
        System.arraycopy(optimalU, 0, u, 0, nbVar);
        System.arraycopy(optimalV, 0, v, 0, nbVal);

        // Check if forced edge is already in optimal matching
        if (col4row[forcedRow] == forcedCol) {
            return assignmentScore(nbVar);
        }

        // Force the edge and identify displaced nodes
        int originalColForRow = col4row[forcedRow];
        int originalRowForCol = row4col[forcedCol];

        // Remove old assignments
        if (originalColForRow != -1) {
            row4col[originalColForRow] = -1;
        }
        if (originalRowForCol != -1) {
            col4row[originalRowForCol] = -1;
        }

        // Force the edge - this is now locked
        col4row[forcedRow] = forcedCol;
        row4col[forcedCol] = forcedRow;

        // Find new assignment for displaced row if any
        if (originalRowForCol != -1 && originalRowForCol != forcedRow) {
            int displacedRow = originalRowForCol;

            // Use augmenting path while avoiding the forced row
            int sink = augmentingPathAvoidingRow(nbVal, displacedRow, -1, forcedRow);

            if (sink == -1) {
                return Double.MAX_VALUE; // No feasible assignment
            }

            // Update dual variables
            u[displacedRow] += minWeight[0];
            for (int i = 0; i < nbVar; i++) {
                if (SR[i] && i != displacedRow && col4row[i] != -1) {
                    u[i] += minWeight[0] - shortestPathCosts[col4row[i]];
                }
            }
            for (int j = 0; j < nbVal; j++) {
                if (SC[j]) {
                    v[j] -= minWeight[0] - shortestPathCosts[j];
                }
            }

            // Apply the augmenting path
            int j = sink;
            while (true) {
                int i = path[j];
                int oldJ = col4row[i];
                row4col[j] = i;
                col4row[i] = j;
                j = oldJ;
                if (i == displacedRow) {
                    break;
                }
            }
        }

        return assignmentScore(nbVar);
    }

    public int augmentingPathAvoidingRow(int nbVal, int currentRow, int assignedVal, int forbiddenRow) {
        // Find shortest augmenting path while avoiding paths through forbiddenRow
        double newMinWeight = 0;

        Arrays.fill(SR, false);
        Arrays.fill(SC, false);
        Arrays.fill(shortestPathCosts, Double.POSITIVE_INFINITY);

        // Reset remaining - set of values not visited yet
        int assignedIndex = -1;
        for (int it = 0; it < nbVal; it++) {
            int remainingVal = nbVal - it - 1;
            remaining[it] = remainingVal;
            if (remainingVal == assignedVal) {
                assignedIndex = it;
            }
        }
        int numRemaining = nbVal;

        int sink = -1;
        if (assignedIndex != -1) {
            SR[currentRow] = true;

            int j = assignedVal;
            path[j] = currentRow;
            shortestPathCosts[j] = 0;
            newMinWeight = 0;

            SC[j] = true;
            numRemaining--;
            remaining[assignedIndex] = remaining[numRemaining];

            if (row4col[j] == -1) {
                return j;
            } else {
                currentRow = row4col[j];
            }
        }

        while (sink == -1) {
            int index = -1;
            double lowest = Double.POSITIVE_INFINITY;
            SR[currentRow] = true;

            for (int it = 0; it < numRemaining; it++) {
                int j = remaining[it];

                double r = newMinWeight + beliefs[currentRow][j] - u[currentRow] - v[j];
                if (r < shortestPathCosts[j]) {
                    path[j] = currentRow;
                    shortestPathCosts[j] = r;
                }

                // Skip columns that are matched to the forbidden row
                boolean skipThisColumn = (forbiddenRow != -1 && row4col[j] == forbiddenRow);

                if (!skipThisColumn && (shortestPathCosts[j] < lowest ||
                        (shortestPathCosts[j] == lowest && row4col[j] == -1))) {
                    lowest = shortestPathCosts[j];
                    index = it;
                }
            }

            newMinWeight = lowest;
            if (newMinWeight == Double.POSITIVE_INFINITY) {
                return -1; // No feasible path found
            }

            int endVal = remaining[index];
            if (row4col[endVal] == -1) {
                sink = endVal;
            } else {
                currentRow = row4col[endVal];
            }

            SC[endVal] = true;
            numRemaining--;
            remaining[index] = remaining[numRemaining];
        }

        minWeight[0] = newMinWeight;
        return sink;
    }

    public int augmentingPath(int nbVal, int currentRow, int assignedVal) {
        // CAUTION: mutates the following
        // path
        // CAUTION: resets the following
        // SR, SC, remaining, minWeight, shortestPathCosts
        double newMinWeight = 0;

        Arrays.fill(SR, false);
        Arrays.fill(SC, false);
        Arrays.fill(shortestPathCosts, Double.POSITIVE_INFINITY);

        // reset remaining
        // remaining is the set of values not visited yet (not in SC)
        int assignedIndex = -1;
        for (int it = 0; it < nbVal; it++) {
            int remainingVal = nbVal - it - 1;
            remaining[it] = remainingVal;
            if (remainingVal == assignedVal) {
                assignedIndex = it;
            }
        }
        int numRemaining = nbVal;

        int sink = -1;
        if (assignedIndex != -1) {
            SR[currentRow] = true;

            // Find the shortest augmenting path from currentRow
            // index is the value assigned to currentRow
            int j = assignedVal; // select a value to visit
            path[j] = currentRow;
            shortestPathCosts[j] = 0;
            newMinWeight = 0;

            SC[j] = true; // mark end value as visited
            numRemaining--;
            remaining[assignedIndex] = remaining[numRemaining];

            // check if we arrived to an unassigned value
            if (row4col[j] == -1) {
                return j;
            } else {
                currentRow = row4col[j];
            }

        }


        while (sink == -1) {
            int index = -1;
            double lowest = Double.POSITIVE_INFINITY;
            SR[currentRow] = true;

            // Find the shortest augmenting path from currentRow
            // index is the value assigned to currentRow
            for (int it = 0; it < numRemaining; it++) {
                int j = remaining[it]; // select a value to visit

                // r is the cost of the path if we assign value j to the current row
                double r = newMinWeight + beliefs[currentRow][j] - u[currentRow] - v[j];
                if (r < shortestPathCosts[j]) {
                    // if this is the shortest path so far,
                    // we do assign value j to current row
                    path[j] = currentRow;
                    shortestPathCosts[j] = r;
                }

                if (shortestPathCosts[j] < lowest ||
                        (shortestPathCosts[j] == lowest && row4col[j] == -1)) {
                    lowest = shortestPathCosts[j];
                    index = it;
                }
            }

            newMinWeight = lowest;

            // check if we arrived to an unassigned value
            int endVal = remaining[index];
            if (row4col[endVal] == -1) {
                sink = endVal;
            } else {
                currentRow = row4col[endVal];
            }

            SC[endVal] = true; // mark end value as visited
            numRemaining--;
            remaining[index] = remaining[numRemaining];
        }

        minWeight[0] = newMinWeight;
        return sink;
    }

    public double assignmentScore(int nbVar) {
        double weight = 0;
        for (int i = 0; i < nbVar; i++) {
            weight += beliefs[i][col4row[i]];
        }
        return weight;
    }

    public int[] lsap(int nbVar, int nbVal) {
        // returns the assignment for minimal weight matching
        Arrays.fill(u, 0);
        Arrays.fill(v, 0);
        Arrays.fill(path, -1);
        Arrays.fill(col4row, -1);
        Arrays.fill(row4col, -1);

        for (int currentRow = 0; currentRow < nbVar; currentRow++) {
            minWeight[0] = 0;
            int sink = augmentingPath(nbVal, currentRow, -1);

            // update dual variables
            u[currentRow] += minWeight[0];
            for (int i = 0; i < nbVar; i++) {
                if (SR[i] && i != currentRow) {
                    u[i] += minWeight[0] - shortestPathCosts[col4row[i]];
                }
            }
            for (int j = 0; j < nbVal; j++) {
                if (SC[j]) {
                    v[j] -= minWeight[0] - shortestPathCosts[j];
                }
            }

            // augment previous solution
            int j = sink;
            while (true) {
                int i = path[j];
                row4col[j] = i;

                // swap rows
                int tmp = col4row[i];
                col4row[i] = j;
                j = tmp;

                if (i == currentRow) {
                    break;
                }
            }
        }

        return col4row;
    }

    public double[][] createCostMatrix(int var, int val, int nbVar, int nbVal) {
        double[][] costs = new double[nbVar - 1][nbVal - 1];
        for (int j = 0; j < nbVar; j++) {
            if (j != var) {
                int jj = j > var ? j - 1 : j; // adjust index relative to var
                int i = varIndices[j];
                for (int k = 0; k < nbVal; k++) {
                    if (k != val) {
                        int kk = k > val ? k - 1 : k; // adjust index relative to val
                        int v = vals[k];
                        if (x[i].contains(v)) {
                            double b = outsideBelief(i, v);

                            costs[jj][kk] = !beliefRep.isZero(b) ?
                                    -beliefRep.rep2log(b)
                                    : Double.MAX_VALUE;
                        } else {
                            costs[jj][kk] = Double.MAX_VALUE;
                        }
                    }
                }
            }
        }
        return costs;
    }

    public double compareHungarianAlgorithms
            (int i, int j, int nbVar, int nbVal, double newBelief) {
        // Compare with old hungarian algorithm
        double[][] oldCosts = createCostMatrix(i, j, nbVar, nbVal);
        double oldMatchingCost = OldHungarianAlgorithm.hgAlgorithm(oldCosts, "min");
        double oldNewBelief = oldMatchingCost == Double.MAX_VALUE ? beliefRep.zero()
                : beliefRep.log2rep(-oldMatchingCost);
        double beliefDiff = Math.abs(newBelief - oldNewBelief);
        cp.setMaxBeliefDiff(beliefDiff, nbVar, nbVal);
        return beliefDiff;
    }


    @Override
    public double weightedCounting() {
        double weightedCount = 1.0;
        // contribution of bound variables to the weighted count
        for (int i = 0; i < nVar; i++) {
            if (x[i].isBound()) {
                weightedCount *= beliefRep.rep2std(outsideBelief(i, x[i].min()));
            }
        }
        int nbVar, nbVal;
        // update freeVars/Vals according to bound variables
        nbVar = freeVars.fillArray(varIndices);
        for (int j = 0; j < nbVar; j++) {
            int i = varIndices[j];
            if (x[i].isBound()) {
                freeVars.remove(i);
                freeVals.remove(x[i].min());
            }
        }
        nbVar = freeVars.fillArray(varIndices);
        nbVal = freeVals.fillArray(vals);
        // initialize outside beliefs matrix (MUST BE IN STANDARD [0,1] REPRESENTATION)
        for (int j = 0; j < nbVar; j++) {
            int i = varIndices[j];
            for (int k = 0; k < nbVal; k++) {
                int val = vals[k];
                beliefs[j][k] = (x[i].contains(val) ? beliefRep.rep2std(outsideBelief(i, val)) : 0);
            }
        }
        // may need to add dummy rows in order to make the beliefs matrix square
        for (int j = 0; j < nbVal - nbVar; j++) {
            for (int k = 0; k < nbVal; k++) {
                beliefs[nbVar + j][k] = 1.0; // (STANDARD REPRESENTATION)
            }
        }
        if (nbVal <= exactPermanentThreshold) {
            // exact permanent
            setExactWCounting(true);
            weightedCount *= permanent(beliefs, nbVal);
            // that value should actually be divided by (# dummy rows)!
            for (int i = 2; i <= nbVal - nbVar; i++)
                weightedCount /= (double) i;
        } else {
            // approximate permanent
            setExactWCounting(false);
            weightedCount *= costBasedPermanent_UB3(-1, -1, nbVal, nbVal - nbVar);
        }
        System.out.println("weighted count for " + this.getName()
                + " constraint: " + beliefRep.std2rep(weightedCount));
        return beliefRep.std2rep(weightedCount); // put beliefs back to their original representation
    }

    // precompute gamma function up to n+1, to account for small floating-point errors
    private void precompute_gamma(int n) {
        int gamma_threshold = 100; // value of n beyond which we approximate n!
        double factorial = 1.0;
        gamma = new double[n + 2];
        gamma[0] = 1.0;
        for (int i = 1; (i <= n + 1) && (i <= gamma_threshold); i++) {
            factorial *= (double) i;
            gamma[i] = Math.pow(factorial, 1.0 / ((double) i));
        }
        for (int i = gamma_threshold + 1; i <= n + 1; i++) {
            // from n>gamma_threshold,
            // Stirling's formula is a decent approximation of factorial
            // which will avoid intermediate overflow
            gamma[i] = (double) i / Math.E * Math.pow(2 * Math.PI * i, 1.0 / ((double) 2 * i));
        }
    }

    private double costBasedPermanent_UB3(int var, int val, int dim, int nbDummyRows) {
        // permanent upper bound U^3 for nonnegative matrices (from Soules 2003)
        // for matrix m without row of var and column of val
        double U3 = 1.0;
        double rowSum, rowMax, tmp;
        int tmpFloor, tmpCeil;
        int dummyRowCount = nbDummyRows;

        for (int i = 0; i < dim; i++) {
            if (i != var) { // exclude row of var whose belief we are computing
                rowSum = rowMax = 0;
                for (int j = 0; j < dim; j++) {
                    tmp = beliefs[i][j];
                    if (j != val) { // exclude column of val whose belief we are computing
                        rowSum += tmp;
                        if (tmp > rowMax)
                            rowMax = tmp;
                    }
                }
                if (rowMax == 0)
                    return 0;
                tmp = rowSum / rowMax;
                tmpFloor = (int) Math.floor(tmp);
                tmpCeil = (int) Math.ceil(tmp);
                U3 *= rowMax * (gamma[tmpFloor] + (tmp - tmpFloor) * (gamma[tmpCeil] - gamma[tmpFloor]));
                if (dummyRowCount > 1) {
                    // that upper bound should be divided by (# dummy rows)!
                    U3 /= (double) dummyRowCount;
                    dummyRowCount--;
                }
            }
        }
        return U3;
    }

    private void costBasedPermanent_UB3_precomputeRowMax(int dim) {
        double tmp;
        for (int i = 0; i < dim; i++) {
            rowMax[i] = rowMaxSecondBest[i] = 0;
            for (int j = 0; j < dim; j++) {
                tmp = beliefs[i][j];
                if (tmp > rowMax[i]) {
                    rowMaxSecondBest[i] = rowMax[i];
                    rowMax[i] = tmp;
                } else if (tmp > rowMaxSecondBest[i]) {
                    rowMaxSecondBest[i] = tmp;
                }
            }
        }
    }

    private void costBasedPermanent_UB3_precomputeRowMax_sparseMatrix(int dim) {
        double tmp;
        for (int i = 0; i < dim; i++) {
            rowMax[i] = rowMaxSecondBest[i] = 0;
            int var = varIndices[i];
            int s = x[var].fillArray(domainValues);
            for (int k = 0; k < s; k++) {
                tmp = beliefRep.rep2std(outsideBelief(var, domainValues[k]));
                if (tmp > rowMax[i]) {
                    rowMaxSecondBest[i] = rowMax[i];
                    rowMax[i] = tmp;
                } else if (tmp > rowMaxSecondBest[i]) {
                    rowMaxSecondBest[i] = tmp;
                }
            }
        }
    }

    private double costBasedPermanent_UB3_faster(int var, int val, int dim, int nbDummyRows) {
        // permanent upper bound U^3 for nonnegative matrices (from Soules 2003)
        // for matrix m without row of var and column of val
        // assumes that each row of m sums to one
        double U3 = 1.0;
        double rSum, rMax, tmp;
        int tmpFloor, tmpCeil;
        int dummyRowCount = nbDummyRows;

        for (int i = 0; i < dim; i++) {
            if (i != var) { // exclude row of var whose belief we are computing
                rSum = 1.0 - beliefs[i][val]; // each row of m (beliefs) sums to one
                rMax = (rowMax[i] == beliefs[i][val] ? rowMaxSecondBest[i] : rowMax[i]);
                if (rMax == 0)
                    return 0;
                tmp = rSum / rMax;
                tmpFloor = (int) Math.floor(tmp);
                tmpCeil = (int) Math.ceil(tmp);
                U3 *= rMax * (gamma[tmpFloor] + (tmp - tmpFloor) * (gamma[tmpCeil] - gamma[tmpFloor]));
                if (dummyRowCount > 1) {
                    // that upper bound should be divided by (# dummy rows)!
                    U3 /= dummyRowCount;
                    dummyRowCount--;
                }
            }
        }
        return U3;
    }

    private double costBasedPermanent_UB3_faster_sparseMatrix(int var, int val, int dim) {
        // permanent upper bound U^3 for nonnegative matrices (from Soules 2003)
        // for matrix m without row of var and column of val
        // assumes that each row of m sums to one
        double U3 = 1.0;
        double rSum, rMax, tmp;
        int tmpFloor, tmpCeil;

        for (int i = 0; i < dim; i++) {
            if (i != var) { // exclude row of var whose belief we are computing
                int j = varIndices[i];
                if (x[j].contains(val)) {
                    tmp = beliefRep.rep2std(outsideBelief(j, val));
                    rSum = 1.0 - tmp; // each row of m (beliefs) sums to one
                    rMax = (rowMax[i] == tmp ? rowMaxSecondBest[i] : rowMax[i]);
                } else {
                    rSum = 1.0;
                    rMax = rowMax[i];
                }
//                System.out.println(var+" "+val+"; "+rSum+" " + rMax);
                if (rMax == 0)
                    return 0;
                tmp = rSum / rMax;
                tmpFloor = (int) Math.floor(tmp);
                tmpCeil = (int) Math.ceil(tmp);
                U3 *= rMax * (gamma[tmpFloor] + (tmp - tmpFloor) * (gamma[tmpCeil] - gamma[tmpFloor]));
            }
        }
        return U3;
    }

    private double costBasedPermanent_exact(int var, int val, int dim) {
        // exact permanent for matrix m without row of var and column of val
        // to be used when m is not too large

        double tmp = swap(var, val, dim);

        // compute permanent of m without last row & column
        double p = permanent(beliefs, dim - 1);

        // swap back
        swapBack(var, val, dim, tmp);

        return p; // that value should actually be divided by (# dummy rows)!
    }

    public void swapRow(int i, int dim) {
        // CAUTION: this swaps references
        // use when you don't plan to mutate the array
        double[] tmp = beliefs[dim - 1];
        beliefs[dim - 1] = beliefs[i];
        beliefs[i] = tmp;
    }

    public double swap(int var, int val, int dim) {
        double tmp = beliefs[var][0];
        // swap row "var" and column "val" with last row & column
        for (int j = 0; j < dim; j++) { // swap rows
            tmp = beliefs[var][j];
            beliefs[var][j] = beliefs[dim - 1][j];
            beliefs[dim - 1][j] = tmp;
        }
        for (int i = 0; i < dim; i++) { // swap columns
            tmp = beliefs[i][val];
            beliefs[i][val] = beliefs[i][dim - 1];
            beliefs[i][dim - 1] = tmp;
        }
        return tmp;
    }

    public void swapBack(int var, int val, int dim, double tmp) {
        // swap back
        for (int j = 0; j < dim; j++) { // swap rows
            tmp = beliefs[var][j];
            beliefs[var][j] = beliefs[dim - 1][j];
            beliefs[dim - 1][j] = tmp;
        }
        for (int i = 0; i < dim; i++) { // swap columns
            tmp = beliefs[i][val];
            beliefs[i][val] = beliefs[i][dim - 1];
            beliefs[i][dim - 1] = tmp;
        }
    }

    // compute the permanent of a real matrix through a simple adaptation of Heap's Algorithm that generates all permutations
    private double permanent(double[][] A, int n) {
        double prod = 1.0;
        for (int i = 0; i < n; i++) {
            c[i] = 0;
            permutation[i] = i;
            prod *= A[i][i];
        }
        double perm = prod;
        int i = 0;
        while (i < n) {
            if (c[i] < i) {
                if (i % 2 == 0)
                    prod = swap(A, permutation, 0, i, n, prod);
                else
                    prod = swap(A, permutation, c[i], i, n, prod);
                perm += prod;
                c[i]++;
                i = 0;
            } else {
                c[i] = 0;
                i++;
            }
        }
        return perm;
    }

    // swap the elements at indices i and j in the permutation for Heap's algorithm
    // returns the new prod
    private double swap(double[][] A, int[] permutation, int i, int j, int n, double prod) {
        int e = permutation[i];
        permutation[i] = permutation[j];
        permutation[j] = e;
        double newFactor = A[i][permutation[i]] * A[j][permutation[j]];
        double oldFactor = A[i][permutation[j]] * A[j][permutation[i]];
        if (newFactor == 0)
            return 0;
        else if (oldFactor == 0) {
            // cannot divide it out -- compute from scratch
            double newProd = 1.0;
            for (int k = 0; k < n; k++)
                newProd *= A[k][permutation[k]];
            return newProd;
        } else
            return prod * newFactor / oldFactor;
    }

}
