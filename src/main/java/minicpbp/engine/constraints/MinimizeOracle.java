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
public class MinimizeOracle extends AbstractConstraint {
    private IntVar x;
    // the reference value used to compute the marginal
    // must be lower than the min of the domain of x
    private int reference;

    /**
     * @param x the variable
     *          Note: any domain value not appearing in v will be assigned a zero marginal
     */
    public MinimizeOracle(IntVar x) {
        this(x, x.max() + 1);
    }

    public MinimizeOracle(IntVar x, int reference) {
        super(x.getSolver(), new IntVar[]{x});
        setName("Max Oracle");
        this.x = x;
        this.reference = reference;
        setExactWCounting(true);
    }

    @Override
    public void post() {
        if (x.max() >= reference) {
            throw new IllegalArgumentException("The reference value must be lower than the min of the domain of x");
        }
    }

    @Override
    public void propagate() {
    }

    @Override
    public void updateBelief() {
        // System.out.println("Min Oracle");
        if (x.isBound()) {
            setLocalBelief(0, x.min(), 1.0);
            return;
        }
        float sum = 0;
        for (int val = x.min(); val <= x.max(); val++) {
            if (x.contains(val)) {
                sum += reference - val;
            }
        }
        for (int val = x.min(); val <= x.max(); val++) {
            if (x.contains(val)) {
                setLocalBelief(0, val, (reference - val) / sum);
                // System.out.println("Min Oracle to " + x.getName() + " : " + val + " with " + (reference - val) / sum);
            }
        }
        // System.out.println("Min Oracle Propagated");
    }
}
