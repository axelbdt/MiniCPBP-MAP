package minicpbp.engine.constraints;

import minicpbp.engine.core.AbstractConstraint;
import minicpbp.engine.core.IntVar;

/**
 * Objective Sum will maximize the sum of variables in its scope
 */
public class ObjectiveSum extends AbstractConstraint {
    IntVar[] x;

    public ObjectiveSum(IntVar[] x) {
        super(x[0].getSolver(), x);
        setName("ObjectiveSum");
        this.x = x;
    }

    /**
     * Set local belief according to the max sum of the values of the variables in its scope
     * mu(x = v) = max [ sum( v_i ) * prod( belief(x_i = v_i) ) ]
     * computed with a search tree
     */
    public void updateBelief() {
        int[][] varVals = new int[x.length][];
        for (int i = 0; i < x.length; i++) {
            varVals[i] = new int[x[i].size()];
            x[i].fillArray(varVals[i]);
        }

        for (int i = 0; i < x.length; i++) {
            if (x[i].isBound()) {
                setLocalBelief(i, x[i].min(), beliefRep.one());
            } else {
                double[] maxSums = new double[varVals[i].length];
                for (int j = 0; j < varVals[i].length; j++) {
                    int v = varVals[i][j];
                    // DFS to maximize the sum according to the other variables
                    int[] currentAssignment = new int[x.length - 1];
                    for (int k = 0; k < x.length - 1; k++) {
                        currentAssignment[k] = -1;
                    }
                    double maxSum = Double.NEGATIVE_INFINITY;
                    int index = 0;
                    while (index >= 0) {
                        if (index == x.length - 1) {
                            // complete assignment, evaluate the objective and update belief
                            int sum = v;
                            double belief = beliefRep.one();
                            for (int k = 0; k < x.length - 1; k++) {
                                int kk = k < i ? k : k + 1;
                                int val = varVals[kk][currentAssignment[k]];
                                sum += val;
                                belief = beliefRep.multiply(belief, outsideBelief(kk, val));
                            }
                            maxSum = Math.max(maxSum, beliefRep.multiply(sum, belief));
                            System.out.println(maxSum);
                            index--; // backtrack
                        } else {
                            int varIndex = index < i ? index : index + 1;
                            if (currentAssignment[index] < varVals[varIndex].length - 1) {
                                currentAssignment[index]++; // increment the current assignment
                                index++;
                            } else {
                                currentAssignment[index] = 0; // reset the current assignment
                                index--; // backtrack
                            }
                        }
                    }
                    if (i == 4) {
                        System.out.println("varVals[i].length = " + varVals[i].length);
                    }
                    if (maxSum <= 0) {
                        System.out.println("maxSum = " + maxSum);
                    }
                    maxSums[j] = maxSum;
                }
                double normalizingConstant = beliefRep.summation(maxSums, maxSums.length);
                for (int j = 0; j < maxSums.length; j++) {
                    double b = beliefRep.divide(maxSums[j], normalizingConstant);
                    if (Double.isNaN(b)) {
                        System.out.println("b = " + b);
                    }
                    setLocalBelief(i, varVals[i][j], b);
                }

            }
        }

    }
}
