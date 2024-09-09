package minicpbp.engine.constraints;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import org.junit.Test;

import static minicpbp.cp.Factory.makeIntVar;
import static minicpbp.cp.Factory.makeSolver;

public class MaximizeOracleTest {

    @Test
    public void maximizeTest() {
        Solver cp = makeSolver();
        IntVar x = makeIntVar(cp, 1, 10);

        cp.setTraceBPFlag(true);
        cp.post(new MaximizeOracle(x));
        cp.vanillaBP(10);
    }


}
