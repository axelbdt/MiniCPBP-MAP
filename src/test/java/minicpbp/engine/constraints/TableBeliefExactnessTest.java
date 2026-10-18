package minicpbp.engine.constraints;

import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.util.exception.InconsistencyException;
import org.junit.Test;

import java.util.Random;

import static minicpbp.cp.Factory.*;
import static org.junit.Assert.assertEquals;

/**
 * TableCT, ShortTableCT and NegTableCT messages against exact weighted counting.
 * A chain of arity-3 tables, consecutive tables sharing one variable, is a tree,
 * so BP marginals must equal the enumerated ones. In BP mode a table is not
 * re-propagated after a later post filters its variables, so values with an
 * exactly zero outside belief stay in the domains.
 */
public class TableBeliefExactnessTest {

    private static final int STAR = -1;

    @Test
    public void tableCT() { check("table"); }

    @Test
    public void shortTableCT() { check("short"); }

    @Test
    public void negTableCT() { check("neg"); }

    private static void check(String kind) {
        boolean star = kind.equals("short"), neg = kind.equals("neg");
        for (Solver.PropaMode mode : new Solver.PropaMode[]{Solver.PropaMode.SBP, Solver.PropaMode.BP}) {
            for (int seed = 0; seed < 100; seed++) {
                Random r = new Random(seed);
                int m = mode == Solver.PropaMode.BP ? 4 : 2, nv = 2 * m + 1;
                int d = 2 + r.nextInt(mode == Solver.PropaMode.BP ? 2 : 3);
                int n = neg ? 1 + r.nextInt(6) : 3 + r.nextInt(8);
                int[][][] t = new int[m][n][3];
                for (int[][] tab : t)
                    for (int[] row : tab)
                        for (int i = 0; i < 3; i++)
                            row[i] = star && r.nextInt(4) == 0 ? STAR : r.nextInt(d);
                Solver cp = makeSolver();
                cp.setMode(mode);
                IntVar[] x = new IntVar[nv];
                for (int i = 0; i < nv; i++)
                    x[i] = makeIntVar(cp, 0, d - 1);
                try {
                    for (int f = 0; f < m; f++) {
                        IntVar[] s = {x[2 * f], x[2 * f + 1], x[2 * f + 2]};
                        Constraint c = kind.equals("table") ? table(s, t[f])
                                : star ? shortTable(s, t[f], STAR) : negTable(s, t[f]);
                        cp.post(c);
                    }
                } catch (InconsistencyException e) {
                    continue;
                }
                cp.setMaxIter(2 * m + 2);
                cp.vanillaBP(2 * m + 2);

                // exact weighted counting: each matching tuple counts once (NegTableCT removes duplicates)
                double[][] cnt = new double[nv][d];
                double total = 0;
                int[] v = new int[nv];
                for (int c = 0; c < (int) Math.pow(d, nv); c++) {
                    boolean inDom = true;
                    for (int i = 0, q = c; i < nv; i++, q /= d) {
                        v[i] = q % d;
                        inDom &= x[i].contains(v[i]);
                    }
                    if (!inDom)
                        continue;
                    double w = 1;
                    for (int f = 0; f < m && w > 0; f++) {
                        int matches = 0;
                        for (int[] row : t[f]) {
                            boolean ok = true;
                            for (int i = 0; i < 3 && ok; i++)
                                ok = (star && row[i] == STAR) || row[i] == v[2 * f + i];
                            if (ok)
                                matches++;
                        }
                        w *= neg ? (matches == 0 ? 1 : 0) : matches;
                    }
                    if (w > 0) {
                        total += w;
                        for (int i = 0; i < nv; i++)
                            cnt[i][v[i]] += w;
                    }
                }
                if (total == 0)
                    continue;
                for (int i = 0; i < nv; i++)
                    for (int val = 0; val < d; val++)
                        if (x[i].contains(val))
                            assertEquals(kind + " " + mode + " seed " + seed + " x" + i + "=" + val,
                                    cnt[i][val] / total, cp.getBeliefRep().rep2std(x[i].marginal(val)), 1e-9);
            }
        }
    }
}
