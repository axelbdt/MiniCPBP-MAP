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
import minicpbp.state.StateSparseSet;
import minicpbp.util.GraphUtil;
import minicpbp.util.GraphUtil.Graph;
import minicpbp.util.HungarianAlgorithm;
import minicpbp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Domain Consistent AllDifferent Constraint
 * <p>
 * Algorithm described in "A filtering algorithm for constraints of difference
 * in CSPs" J-C. Régin, AAAI-94
 */
public class AllDifferentDCMAP extends AbstractConstraint {
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

    public AllDifferentDCMAP(IntVar... x) {
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
        freeVals = new StateSparseSet(getSolver().getStateManager(),
                allVals.last().intValue() - allVals.first().intValue() + 1, allVals.first().intValue());
        // remove missing intermediate values from interval domain
        for (int i = allVals.first().intValue() + 1; i < allVals.last().intValue(); i++) {
            if (!allVals.contains(i))
                freeVals.remove(i);
        } // from now on freeVals will be maintained as a superset of the available
        // values,
        // only removing values as they are taken on by a variable

        // allocate enough space for the data structures, even though we will need less
        // and less as we go down the search tree
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
            setExactWCounting(false); // actually, it will be exact below the threshold, which may happen lower in the
            // search tree
        }
        // precompute_gamma(freeVals.size());
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
        // Initialize cost matrix for Hungarian Algorithm and max matching
        // fill array
        if (nbVar > 1) {
            for (int j = 0; j < nbVar; j++) {
                int i = varIndices[j];
                for (int k = 0; k < nbVal; k++) {
                    int val = vals[k];
                    if (x[i].contains(val)) {
                        double[][] costs = createCostMatrix(j, k, nbVar, nbVal);
                        double matchingCost = HungarianAlgorithm.hgAlgorithm(costs, "min");
                        double newBelief = matchingCost == Double.MAX_VALUE ? beliefRep.zero()
                                : beliefRep.log2rep(-matchingCost);
                        assert !Double.isNaN(newBelief);
                        setLocalBelief(i, val, newBelief);
                    }
                }
            }
        }

        // may need to add dummy rows in order to make the beliefs matrix square
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

                            costs[jj][kk] = !beliefRep.isZero(b) ? -beliefRep.rep2log(b) : Double.MAX_VALUE;
                        } else {
                            costs[jj][kk] = Double.MAX_VALUE;
                        }
                    }
                }
            }
        }

        return costs;
    }

    @Override
    public double weightedCounting() {
        double weightedCount = 1.0;
        return beliefRep.std2rep(weightedCount); // put beliefs back to their original representation
    }
}
