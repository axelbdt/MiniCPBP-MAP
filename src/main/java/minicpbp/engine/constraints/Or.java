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
import minicpbp.engine.core.BoolVar;
import minicpbp.state.StateInt;

import static minicpbp.util.exception.InconsistencyException.INCONSISTENCY;

/**
 * Logical or constraint {@code  x1 or x2 or ... xn}
 */
public class Or extends AbstractConstraint { // x1 or x2 or ... xn

    private final BoolVar[] x;
    private final int n;
    private StateInt wL; // watched literal left
    private StateInt wR; // watched literal right


    /**
     * Creates a logical or constraint: at least one variable is true:
     * {@code  x1 or x2 or ... xn}
     *
     * @param x the variables in the scope of the constraint
     */
    public Or(BoolVar[] x) {
        super(x[0].getSolver(), x);
        setName("Or");
        this.x = x;
        this.n = x.length;
        wL = getSolver().getStateManager().makeStateInt(0);
        wR = getSolver().getStateManager().makeStateInt(n - 1);
        setExactWCounting(true);
    }

    @Override
    public void post() {
        propagate();
    }


    @Override
    public void propagate() {
        // update watched literals
        int i = wL.value();
        while (i < n && x[i].isBound()) {
            if (x[i].isTrue()) {
                setActive(false);
                return;
            }
            i += 1;
        }
        wL.setValue(i);
        i = wR.value();
        while (i >= 0 && x[i].isBound() && i >= wL.value()) {
            if (x[i].isTrue()) {
                setActive(false);
                return;
            }
            i -= 1;
        }
        wR.setValue(i);

        if (wL.value() > wR.value()) {
            throw INCONSISTENCY;
        } else if (wL.value() == wR.value()) { // only one unassigned var
            x[wL.value()].assign(true);
            setActive(false);
        } else {
            assert (wL.value() != wR.value());
            assert (!x[wL.value()].isBound());
            assert (!x[wR.value()].isBound());
            switch (getSolver().getMode()) {
                case BP:
                    break;
                case SP:
                case SBP:
                    x[wL.value()].propagateOnBind(this);
                    x[wR.value()].propagateOnBind(this);
            }
        }
    }

    @Override
    public void updateBeliefSumProduct() {
        double beliefAllFalse = beliefRep.one();
        for (int i = wL.value(); i <= wR.value(); i++) {
            beliefAllFalse = beliefRep.multiply(beliefAllFalse, outsideBelief(i, 0));
        }
        for (int i = wL.value(); i <= wR.value(); i++) {
            if (!x[i].isBound()) {
                assert (!beliefRep.isZero(outsideBelief(i, 0)));
                // will be normalized
                setLocalBelief(i, 1, beliefRep.one());
                setLocalBelief(i, 0, beliefRep.complement(beliefRep.divide(beliefAllFalse, outsideBelief(i, 0))));
            }
        }
    }

    @Override
    public void updateBeliefMaxProduct() {
        int maxTrueVar = -1;
        int secondMaxTrueVar = -1;
        double maxTrueBel = Double.NEGATIVE_INFINITY;
        double secondMaxTrueBel = Double.NEGATIVE_INFINITY;
        double maxBelief = beliefRep.one();
        int trueMaxNumber = 0;

        // compute the max assignment
        // count true variables in max assignment
        for (int i = wL.value(); i <= wR.value(); i++) {
            if (outsideBelief(i, 1) >= maxTrueBel) {
                secondMaxTrueBel = maxTrueBel;
                secondMaxTrueVar = maxTrueVar;
                maxTrueVar = i;
                maxTrueBel = outsideBelief(i, 1);
            } else if (outsideBelief(i, 1) > secondMaxTrueBel) {
                secondMaxTrueVar = i;
                secondMaxTrueBel = outsideBelief(i, 1);
            }
            trueMaxNumber += x[i].valueWithMaxMarginal();
            maxBelief = beliefRep.multiply(maxBelief, x[i].maxMarginal());
        }

        // if 0 or 1 variable is true in max assignment,
        // we have to add true to the max assignment to satisfy the constraint
        if (trueMaxNumber <= 1) {
            // set at maxTrueVar to true
            if (trueMaxNumber == 0) {
                maxBelief = beliefRep.multiply(
                        beliefRep.divide(
                                maxBelief,
                                outsideBelief(maxTrueVar, 0))
                        , outsideBelief(maxTrueVar, 1));
            }

            for (int i = wL.value(); i <= wR.value(); i++) {
                if (!x[i].isBound()) {
                    if (i == maxTrueVar) {
                        // if there are more than one unbound variable
                        if (secondMaxTrueVar != -1) {
                            setLocalBelief(i, 1, beliefRep.divide(maxBelief, outsideBelief(i, 1)));
                            // if maxTrueVar is set to false, set the second max variable to true
                            setLocalBelief(i, 0,
                                    beliefRep.multiply(outsideBelief(secondMaxTrueVar, 1),
                                            beliefRep.divide(
                                                    beliefRep.divide(maxBelief, outsideBelief(i, 1)),
                                                    outsideBelief(secondMaxTrueVar, 0))));
                        } else { // if there is only one unbound variable
                            setLocalBelief(i, 1, beliefRep.one());
                            setLocalBelief(i, 0, beliefRep.zero());

                        }
                    } else {
                        setLocalBelief(i, 1, beliefRep.divide(maxBelief, outsideBelief(i, 0))); // set to false
                        setLocalBelief(i, 0, beliefRep.divide(maxBelief, outsideBelief(i, 0))); // set to false
                    }
                }
            }
            return;
        }

        // if more than one variable is true in max assignment,
        // we use max assignment in all messages
        for (int i = wL.value(); i <= wR.value(); i++) {
            if (!x[i].isBound()) {
                setLocalBelief(i, 1, beliefRep.divide(maxBelief, x[i].maxMarginal()));
                setLocalBelief(i, 0, beliefRep.divide(maxBelief, x[i].maxMarginal()));
            }
        }
    }

}
