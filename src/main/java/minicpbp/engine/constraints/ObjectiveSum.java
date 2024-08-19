package minicpbp.engine.constraints;

import minicpbp.engine.core.AbstractConstraint;
import minicpbp.engine.core.IntVar;
import minicpbp.state.StateSparseSet;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 Objective Sum will maximize the sum of variables in its scope
 */
public class ObjectiveSum extends AbstractConstraint {
    IntVar[] x;
    private StateSparseSet freeVars; // holds an index for vars
    private StateSparseSet freeVals; // holds the actual values of freeVals
    private double[][] beliefs;

    private int[] varIndices;
    private int[] vals;

    public ObjectiveSum(IntVar[] x) {
        super(x[0].getSolver(), x);
        setName("ObjectiveSum");
        this.x = x;

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
        varIndices = new int[freeVars.size()];
        vals = new int[freeVals.size()];
    }

    /**
     * Set local belief according to the max sum of the values of the variables in its scope
     * mu(x = v) = max [ sum( v_i ) * prod( belief(x_i = v_i) ) ]
     * computed with a search tree
     */
    public void updateBelief() {
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

        // compute beliefs
        for (int i = 0; i < nbVar; i++) {
            for (int j = 0; j < nbVal; j++) {
                // binding var i to val j
                int v = vals[j];
                if(x[i].contains(v)) {
                    // DFS to maximize the sum according to the other variables
                    int[] currentAssignment = new int[nbVar - 1];
                    double maxSum = Double.NEGATIVE_INFINITY;
                    int index = 0;

                    while (index >= 0) {
                        if (index == nbVar - 1) {
                            // complete assignment, evaluate the objective
                            int sum = v;
                            double belief = beliefRep.rep2std(beliefRep.one());
                            for (int k = 0; k < nbVar - 1; k++) {
                                int val = vals[currentAssignment[k]];
                                sum += val;
                                int var = k < i ? varIndices[k] : varIndices[k + 1];
                                belief *= beliefRep.rep2std(outsideBelief(var, val));
                            }

                            maxSum = Math.max(maxSum, sum * belief);
                            index--; // backtrack
                        } else if (currentAssignment[index] < nbVal - 1) {
                            currentAssignment[index]++;
                            int varIndex = index < i ? varIndices[index] : varIndices[index + 1];
                            IntVar var = vars[varIndex];
                            int val = vals[currentAssignment[index]];
                            if (var.contains(val)) {
                                index++;
                            }
                        } else {
                            currentAssignment[index] = 0;
                            index--;
                        }
                    }
                    // update belief
                    setLocalBelief(i, v, beliefRep.std2rep(maxSum));
                }
            }
        }

    }
}
