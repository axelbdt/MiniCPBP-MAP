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
 */

package minicpbp.engine.constraints;

import minicpbp.engine.core.AbstractConstraint;
import minicpbp.engine.core.IntVar;
import minicpbp.util.Log;

import java.util.BitSet;

/**
 * Table constraint with short tuples (having {@code *} entries)
 */
public class ShortTableCT extends AbstractConstraint {

    private final IntVar[] x; //variables
    private int xLength;
    private final int[][] table; //the table
    private int tableLength;
    private final int star; //the "any" value
    private int[] ofs; //offsets for each variable's domain
    //supports[i][v] is the set of tuples supported by x[i]=v
    private BitSet[][] supports;
    //beliefs[i][v] is the accumulated weight of tuples supported by x[i]=v
    private double[][] beliefs;
    //supportedTuples is the set of tuples supported by the current domains of the variables
    private BitSet supportedTuples;
    private BitSet supporti;
    // scratch for updateBelief (lazily allocated): per-position scaled outside
    // beliefs, the scaled factor of a star at each position, and per-tuple
    // prefix/suffix products for leave-one-out weights
    private double[][] scaledBelief;
    private double[] starFactor;
    private double[] tuplePrefix;
    private double[] tupleSuffix;

    /**
     * Create a Table constraint with short tuples.
     * <p>Assignment of {@code x_0=v_0, x_1=v_1,...} only valid if there exists a
     * row {@code (v_0|*,v_1|*, ...)} in the table.
     *
     * @param x     the variables to constraint. x must be non empty.
     * @param table the array of valid solutions (second dimension must be of same size as the array x)
     * @param star  the {@code *} symbol representing "any" value in the table
     */
    public ShortTableCT(IntVar[] x, int[][] table, int star) {
        super(x[0].getSolver(), x);
        setName("ShortTableCT");
        this.x = x;
        this.xLength = x.length;
        this.table = table;
        this.tableLength = table.length;
        assert xLength == table[0].length;
	    this.star = star;
        ofs = new int[xLength];
        supportedTuples = new BitSet(tableLength);
        supporti = new BitSet(tableLength);

        // Allocate supports and beliefs
        supports = new BitSet[x.length][];
        beliefs = new double[xLength][];
        for (int i = 0; i < x.length; i++) {
            ofs[i] = x[i].min(); // offsets map the variables' domain to start at 0 for supports[][] and beliefs[][]
            supports[i] = new BitSet[x[i].max() - x[i].min() + 1];
            beliefs[i] = new double[x[i].max() - x[i].min() + 1];
            for (int j = 0; j < supports[i].length; j++)
                supports[i][j] = new BitSet();
        }

        // Set values in supports, which contains all the tuples supported by each var-val pair
        for (int t = 0; t < table.length; t++) { //t is the index of the tuple (in table)
            for (int i = 0; i < x.length; i++) { //i is the index of the current variable (in x)
                if (table[t][i] == star) {
                    for (int v = 0; v < supports[i].length; v++) {
                        supports[i][v].set(t);
                    }
                } else if (x[i].contains(table[t][i])) {
                    supports[i][table[t][i] - ofs[i]].set(t);
                }
            }
        }
        setExactWCounting(true);
    }

    @Override
    public void post() {
        switch (getSolver().getMode()) {
            case BP:
                break;
            case SP:
            case SBP:
                for (IntVar var : x)
                    var.propagateOnDomainChange(this);
        }
        propagate();
    }

    @Override
    public void propagate() {

        supportedTuples.set(0, tableLength); // set them all to true

        for (int i = 0; i < x.length; i++) {
            supporti.clear(); // set them all to false
            int s = x[i].fillArray(domainValues);
            for (int j = 0; j < s; j++) {
                supporti.or(supports[i][domainValues[j] - ofs[i]]);
            }
            supportedTuples.and(supporti);
        }

        for (int i = 0; i < x.length; i++) {
            int s = x[i].fillArray(domainValues);
            for (int j = 0; j < s; j++) {
                // The condition for removing value v from x[i] is to check if
                // there is no intersection between supportedTuples and the support[i][v]
                int v = domainValues[j];
                if (!supports[i][v - ofs[i]].intersects(supportedTuples)) {
                    x[i].remove(v);
                }
            }
        }
    }

    @Override
    public void updateBelief() {

        // Compute supportedTuples as
        // supportedTuples = (supports[0][x[0].min()] | ... | supports[0][x[0].max()] ) & ... &
        //                   (supports[x.length][x[0].min()] | ... | supports[x.length][x[0].max()] )
        // Initialize beliefs as well.
        supportedTuples.set(0, tableLength); // set them all to true
        for (int i = 0; i < xLength; i++) {
            supporti.clear(); // set them all to false
            int s = x[i].fillArray(domainValues);
            for (int j = 0; j < s; j++) {
                supporti.or(supports[i][domainValues[j] - ofs[i]]);
                beliefs[i][domainValues[j] - ofs[i]] = beliefRep.zero();
            }
            supportedTuples.and(supporti);
        }

        if (scaledBelief == null) {
            scaledBelief = new double[xLength][];
            for (int i = 0; i < xLength; i++)
                scaledBelief[i] = new double[supports[i].length];
            starFactor = new double[xLength];
            tuplePrefix = new double[xLength + 1];
            tupleSuffix = new double[xLength + 1];
        }

        // Same circuit as TableCT.updateBelief: scale each position's outside
        // beliefs so their max is one (a per-position constant that the
        // per-variable message normalization cancels) and take leave-one-out
        // weights from per-tuple prefix/suffix products, with no division.
        // A star stands for every domain value, so its factor is the sum of the
        // position's beliefs: one before scaling, the same scaled sum after,
        // which keeps the scale constant across starred and unstarred tuples.
        for (int i = 0; i < xLength; i++) {
            int s = x[i].fillArray(domainValues);
            double mx = beliefRep.zero();
            for (int j = 0; j < s; j++) {
                double ob = outsideBelief(i, domainValues[j]);
                if (ob > mx) mx = ob;
            }
            starFactor[i] = beliefRep.zero();
            for (int j = 0; j < s; j++) {
                int v = domainValues[j];
                double b = beliefRep.isZero(mx) ? beliefRep.zero() : beliefRep.divide(outsideBelief(i, v), mx);
                scaledBelief[i][v - ofs[i]] = b;
                starFactor[i] = beliefRep.add(starFactor[i], b);
            }
        }

        // one pass over the supported tuples (every non-star position of a
        // supported tuple is in the current domain by construction of supportedTuples)
        for (int k = supportedTuples.nextSetBit(0); k >= 0; k = supportedTuples.nextSetBit(k + 1)) {
            tuplePrefix[0] = beliefRep.one();
            for (int i = 0; i < xLength; i++)
                tuplePrefix[i + 1] = beliefRep.multiply(tuplePrefix[i], factor(k, i));
            tupleSuffix[xLength] = beliefRep.one();
            for (int i = xLength - 1; i >= 0; i--)
                tupleSuffix[i] = beliefRep.multiply(factor(k, i), tupleSuffix[i + 1]);
            for (int i = 0; i < xLength; i++) {
                double w = beliefRep.multiply(tuplePrefix[i], tupleSuffix[i + 1]);
                if (table[k][i] != star) {
                    int idx = table[k][i] - ofs[i];
                    beliefs[i][idx] = beliefRep.add(beliefs[i][idx], w);
                } else { // the "any" value: the tuple supports every domain value of x[i]
                    int s = x[i].fillArray(domainValues);
                    for (int j = 0; j < s; j++) {
                        int idx = domainValues[j] - ofs[i];
                        beliefs[i][idx] = beliefRep.add(beliefs[i][idx], w);
                    }
                }
            }
        }

        for (int i = 0; i < xLength; i++) {
            int s = x[i].fillArray(domainValues);
            for (int j = 0; j < s; j++) {
                int v = domainValues[j];
                setLocalBelief(i, v, beliefs[i][v - ofs[i]]);
            }
        }
    }

    // scaled factor of tuple k at position i
    private double factor(int k, int i) {
        return table[k][i] == star ? starFactor[i] : scaledBelief[i][table[k][i] - ofs[i]];
    }

    @Override
    public double weightedCounting() {

        // Compute supportedTuples as
        // supportedTuples = (supports[0][x[0].min()] | ... | supports[0][x[0].max()] ) & ... &
        //                   (supports[x.length][x[0].min()] | ... | supports[x.length][x[0].max()] )
        //
        supportedTuples.set(0, tableLength); // set them all to true
        for (int i = 0; i < xLength; i++) {
            supporti.clear(); // set them all to false
            int s = x[i].fillArray(domainValues);
            for (int j = 0; j < s; j++) {
                supporti.or(supports[i][domainValues[j] - ofs[i]]);
            }
            supportedTuples.and(supporti);
        }

        double weightedCount = beliefRep.zero();

        // Each tuple has its own weight given by the product of the outside_belief of its elements.
        // Compute these products, but only for supported tuples.
        for (int k = supportedTuples.nextSetBit(0); k >= 0; k = supportedTuples.nextSetBit(k + 1)) {
            double tupleWeight = beliefRep.one();
            for (int i = 0; i < xLength; i++) {
                if (table[k][i] != star) // otherwise it is the "any" value and we would multiply by one
                    tupleWeight = beliefRep.multiply(tupleWeight, outsideBelief(i, table[k][i]));
            }
            weightedCount = beliefRep.add(weightedCount, tupleWeight);
        }
        Log.constraint("weighted count for "+this.getName()+" constraint: "+weightedCount);
        return weightedCount;
    }

}
