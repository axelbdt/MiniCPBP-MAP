package minicpbp.engine.core;

import minicpbp.engine.SolverTest;
import minicpbp.engine.constraints.ObjectiveSum;
import org.junit.Test;

import java.util.Set;

import static minicpbp.cp.Factory.makeIntVar;

public class ObjectiveSumTest extends SolverTest {

    @Test
    public void testObjectiveSum() {
        Solver cp = solverFactory.get();

        IntVar x = makeIntVar(cp, Set.of(1, 2));
        IntVar y = makeIntVar(cp, Set.of(10, 100));

        ObjectiveSum c = new ObjectiveSum(new IntVar[]{x, y});
        cp.post(c);

        // c.setLocalBelief(1, 100, 1.0);

        cp.post(new ObjectiveSum(new IntVar[]{x, y}),true);

        cp.beliefPropa();

        System.out.println("Hello");
        System.out.println(c.localBelief(0, 1));
        System.out.println(c.localBelief(0, 2));

        System.out.println(c.localBelief(1, 10));
        System.out.println(c.localBelief(1, 100));
    }
}
