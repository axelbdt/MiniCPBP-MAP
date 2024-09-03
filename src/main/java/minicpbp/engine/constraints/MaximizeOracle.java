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

/**
 * Oracle unary constraint providing fixed marginals (possibly through ML)
 * Does not perform any filtering
 */
public class MaximizeOracle extends AbstractConstraint {
    private IntVar x;

    /**
     * @param x the variable
     *          Note: any domain value not appearing in v will be assigned a zero marginal
     */
    public MaximizeOracle(IntVar x) {
        super(x.getSolver(), new IntVar[]{x});
        setName("Max Oracle");
        this.x = x;
        setExactWCounting(true);
    }

    @Override
    public void post() {
    }

    @Override
    public void propagate() {
    }

    @Override
    public void updateBelief() {
        if (x.isBound()) {
            setLocalBelief(0, x.min(), 1.0);
            return;
        }
        float sum = 0;
        int L = x.min();
        int U = x.max();
        for (int v = L; v <= U; v++) {
            if (x.contains(v)) {
                sum += ((float) (v - L + 1) / (U - L)) / ((float) (Math.abs(U - L)));
                // v = L -> b = 1 / (U - L + 1) -> 0
                // v = U -> b = (U - L + 1) / (U - L) -> b = 1
            }
        }
        for (int val = L; val <= U; val++) {
            if (x.contains(val)) {
                setLocalBelief(0, val, val / sum);
            }
        }
    }
}
