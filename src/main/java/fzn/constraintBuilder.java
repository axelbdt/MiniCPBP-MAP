package fzn;

import minicpbp.cp.Factory;
import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.BoolVar;
import minicpbp.engine.core.IntVar;
import minicpbp.util.exception.NotImplementedException;
import minicpbp.engine.core.Solver;


import minicpbp.util.exception.InconsistencyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;


import static minicpbp.cp.Factory.*;

public class constraintBuilder {

    private Solver minicp;

    public constraintBuilder(Solver minicp) {
        this.minicp = minicp;
    }

    public void makeIntLinNe(int[] as, IntVar[] bs, int c) {
        Factory.sum(as, bs).remove(c);
    }

    public void makeIntLinNeReif(int[] as, IntVar[] bs, int c, BoolVar r) {
        minicp.post(Factory.notEqual(Factory.isEqual(Factory.sum(as,bs), c), r));
    }

    public void makeIntLinEq(int[] as, IntVar[] bs, int c) {
        Factory.sum(as, bs).assign(c);
    }

    public void makeIntLinEqReif(int[] as, IntVar[] bs, int c, BoolVar r) {
        minicp.post(Factory.equal(Factory.isEqual(Factory.sum(as,bs), c), r));
    }

    public void makeIntLinLe(int[] as, IntVar[] bs, int c) {
        Factory.sum(as,bs).removeAbove(c);
    }

    public void makeIntLinLeReif(int[] as, IntVar[] bs, int c, BoolVar r) {
        minicp.post(Factory.equal(Factory.isLessOrEqual(Factory.sum(as,bs), c), r));
    }

    public void makeIntEq(IntVar a, IntVar b) {
       minicp.post(Factory.equal(a, b));
    }

    public void makeIntEqReif(IntVar a, IntVar b, BoolVar r) {
        minicp.post(Factory.equal(Factory.isEqual(a, b),r));
    }

    public void makeIntNe(IntVar a, IntVar b) {
        minicp.post(Factory.notEqual(a, b));
    }

    public void makeIntNeReif(IntVar a, IntVar b, BoolVar r) {
        minicp.post(Factory.equal(Factory.isNotEqual(a, b),r));
    }

    public void makeIntLe(IntVar a, IntVar b) {
        minicp.post(Factory.lessOrEqual(a, b));
    }

    public void makeIntLeReif(IntVar a, IntVar b, BoolVar r) {
        minicp.post(Factory.equal(Factory.isLessOrEqual(a, b),r));
    }

    public void makeIntLt(IntVar a, IntVar b) {
        minicp.post(Factory.less(a, b));
    }

    public void makeIntLtReif(IntVar a, IntVar b, BoolVar r) {
        minicp.post(Factory.equal(Factory.isLess(a, b),r));
    }

    public void makeBoolEq(BoolVar a, BoolVar b) {
        minicp.post(Factory.equal(a, b));
    }

    public void makeBoolLt(BoolVar a, BoolVar b) {
        minicp.post(Factory.less(a, b));
    }

    public void makeBoolOr(BoolVar a, BoolVar b, BoolVar r) {
        BoolVar array[] = {a,b};
        minicp.post(Factory.isOr(r, array));
    }

    public void makeBoolAnd(BoolVar a, BoolVar b, BoolVar r) {
        BoolVar array[] = {Factory.not(a), Factory.not(b)};
        minicp.post(Factory.isOr(not(r), array));
    }

    public void makeArrayBoolXorReif(BoolVar r, BoolVar... as) {
        minicp.post(Factory.equal(Factory.sumModP(as, 2),r));
    }

    public void makeBoolLe(BoolVar a, BoolVar b) {
        minicp.post(Factory.lessOrEqual(a, b));
    }

    public void makeBoolNot(BoolVar a, BoolVar b) {
        minicp.post(Factory.notEqual(a, b));
    }    

    public void makeArrayBoolOr(BoolVar[] as, BoolVar r) {
        minicp.post(Factory.isOr(r, as));
    }

    public void makeArrayBoolXor(BoolVar... as) {
        minicp.post(Factory.sumModP(as, 1, 2));
    }

    public void makeArrayBoolAnd(BoolVar[] as, BoolVar r) {
        BoolVar asOppose[] = new BoolVar[as.length];
        for(int i = 0; i < as.length; i++) {
            asOppose[i] = Factory.not(as[i]);
        }
        BoolVar rOppose = Factory.not(r);
        minicp.post(Factory.isOr(rOppose, asOppose));
    }

    public void makeBoolClause(BoolVar[] as, BoolVar[] bs) {
        BoolVar array[] = new BoolVar[bs.length+ as.length];
        for(int i = 0; i < as.length;i++){
            array[i] = as[i];
        }
        for(int i = 0; i < bs.length; i++) {
            array[as.length+i] = Factory.not(bs[i]);
            array[as.length+i].setName("not_" + bs[i].getName());
        }
        minicp.post(Factory.or(array));
    }

    public void makeBool2Int(BoolVar a, IntVar b) {
        minicp.post(Factory.equal(a, b));
    }

    public void makeArrayIntElement(IntVar b, int[] as, IntVar c) {
        minicp.post(Factory.element(as, Factory.minus(b,1), c));
    }

    public void makeArrayVarIntElement(IntVar b, IntVar[] as, IntVar c) {
        minicp.post(Factory.element(as, Factory.minus(b,1), c));
    }

    public void makeArrayBoolElement(IntVar b, boolean[] as, BoolVar c) {
        int asInt[] = new int[as.length];
        for(int i = 0; i < as.length; i++)
            asInt[i] = as[i] ? 1 : 0;
        minicp.post(Factory.element(asInt, Factory.minus(b,1), c));
    }

    public void makeArrayVarBoolElement(IntVar b, BoolVar[] as, BoolVar c) {
        minicp.post(Factory.element(as, Factory.minus(b,1), c));
    }

    public void makeCount() {
        throw new NotImplementedException();
    }

    public void makeAllDifferentInt(IntVar[] a) {
        minicp.post(Factory.allDifferent(a));
    }

    public void makeIntPlus(IntVar a, IntVar b, IntVar c) {
        minicp.post(Factory.equal(Factory.sum(a,b), c));
    }

    public void makeIntMax(IntVar a, IntVar b, IntVar c) {
        minicp.post(Factory.equal(maximum(a,b), c));
    }

    public void makeIntMin(IntVar a, IntVar b, IntVar c) {
        minicp.post(Factory.equal(minimum(a,b), c));
    }

    public void makeIntAbs(IntVar a, IntVar b) {
        minicp.post(Factory.equal(Factory.abs(a), b));
    }

    public void makeMaximumInt(IntVar m, IntVar[] x) {
        minicp.post(Factory.equal(m, Factory.maximum(x)));
    }
    public void makeMinimumInt(IntVar m, IntVar[] x) {
        minicp.post(Factory.equal(m, Factory.minimum(x)));
    }

    public void makeExactlyInt(int n, IntVar[] x, int v) {
        minicp.post(Factory.exactly(x, v, n));
    }

    public void makeAtLeastInt(int n, IntVar[] x, int v) {
        minicp.post(Factory.atleast(x, v, n));
    }

    public void makeAtMostInt(int n, IntVar[] x, int v) {
        minicp.post(Factory.atmost(x, v, n));
    }

    public void makeGlobalCardinality(IntVar[] x, int[] cover, IntVar[] counts) {
        minicp.post(Factory.cardinality(x, cover, counts));
    }

    public void makeGlobalCardinalityLowUp(IntVar[] x, int[] cover, int[] lbound, int[] ubound) {
        minicp.post(Factory.cardinality(x, cover, lbound, ubound));
    }

    public void makeCircuit(IntVar[] x, int offset) {
	if (offset == 0)
	    minicp.post(Factory.circuit(x));
	else
	    minicp.post(Factory.circuit(x,offset));
    }

    public void makeAmong(IntVar[] x, int[] V, IntVar o) {
        minicp.post(Factory.among(x, V, o));
    }

    public void makeTable(IntVar[] x, int[][] table) {
        minicp.post(Factory.table(x, table));
    }

    public void makeDisjunctive(IntVar[] start, int[] duration) {
        minicp.post(Factory.disjunctive(start, duration));
    }

    public void makeCumulative(IntVar[] start, int[] duration, int[] demand, int capa) {
        minicp.post(Factory.cumulative(start, duration, demand, capa));
    }

    /**
     * FlatZinc regular: states 1..Q, symbols 1..S, d row-major Q x S with
     * 0 as the failure state. MiniCPBP's Regular wants 0-based states and
     * symbols and a negative entry for a missing transition.
     */
    public void makeRegular(IntVar[] x, int Q, int S, int[] d, int q0, Set<Integer> F) {
        IntVar[] symbols = new IntVar[x.length];
        for (int i = 0; i < x.length; i++) {
            x[i].removeBelow(1);
            x[i].removeAbove(S);
            symbols[i] = Factory.minus(x[i], 1);
        }
        int[][] A = new int[Q][S];
        for (int q = 0; q < Q; q++)
            for (int s = 0; s < S; s++)
                A[q][s] = d[q * S + s] - 1;
        List<Integer> finals = new ArrayList<>();
        for (int q : F)
            if (q >= 1 && q <= Q)
                finals.add(q - 1);
        minicp.post(Factory.regular(symbols, A, q0 - 1, finals));
    }

    /** bin_packing: bins are the values the bin variables can take; every load is at most c. */
    public void makeBinPacking(int c, IntVar[] bin, int[] w) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (IntVar b : bin) {
            lo = Math.min(lo, b.min());
            hi = Math.max(hi, b.max());
        }
        int[] capa = new int[hi - lo + 1];
        Arrays.fill(capa, c);
        makeBinPackingCapa(capa, bin, w, lo);
    }

    /** bin_packing_capa: bin j (counting from firstBin) has capacity c[j]. */
    public void makeBinPackingCapa(int[] c, IntVar[] bin, int[] w, int firstBin) {
        IntVar[] load = new IntVar[c.length];
        for (int j = 0; j < c.length; j++) {
            if (c[j] < 0)
                throw InconsistencyException.INCONSISTENCY;
            load[j] = Factory.makeIntVar(minicp, 0, c[j]);
        }
        makeBinPackingLoad(load, bin, w, firstBin);
    }

    /** bin_packing_load: load[j] is the weight in bin j (counting from firstBin). */
    public void makeBinPackingLoad(IntVar[] load, IntVar[] bin, int[] w, int firstBin) {
        IntVar[] b = new IntVar[bin.length];
        for (int i = 0; i < bin.length; i++)
            b[i] = Factory.minus(bin[i], firstBin);
        minicp.post(Factory.binPacking(b, w, load));
    }

    public void makeIntTimes(IntVar a, IntVar b, IntVar c) {
        minicp.post(Factory.product(a, b, c));
    }

    public void makeIntDiv(IntVar a, IntVar b, IntVar c) {
        minicp.post(Factory.quotient(a, b, c));
    }

    public void makeIntMod(IntVar a, IntVar b, IntVar c) {
        minicp.post(Factory.modulo(a, b, c));
    }

    public void makeIntPow(IntVar a, IntVar b, IntVar c) {
        minicp.post(Factory.pow(a,b,c));
    }

    public void makeSetIn(IntVar x, Set<Integer> s) {
        x.removeOutside(s);
    }

    public void makeSetInReif(IntVar x, Set<Integer> s, BoolVar r) {
        // r <-> x in s, as a binary table over (x, r): one tuple per value of x
        int[] dom = new int[x.size()];
        x.fillArray(dom);
        int[][] tuples = new int[dom.length][];
        for (int i = 0; i < dom.length; i++)
            tuples[i] = new int[]{dom[i], s.contains(dom[i]) ? 1 : 0};
        minicp.post(Factory.table(new IntVar[]{x, r}, tuples));
    }

    public void makeBoolLinEq(int[] as, BoolVar[] bs, IntVar c) {
        minicp.post(Factory.equal(Factory.sum(as, bs), c));
    }

    public void makeTable(IntVar[] x, int[] t) {
        int t2D[][] = new int[t.length/x.length][x.length];
        for(int i = 0; i < t2D.length; i ++) 
            for(int j = 0; j < t2D[i].length; j++) 
                t2D[i][j] = t[i*t2D[0].length + j];
        
        minicp.post(Factory.table(x, t2D));
    }

    public void makeInverse(IntVar[] f, IntVar[] invf, int offset) {
	if (offset == 0)
	    minicp.post(Factory.inverse(f,invf));
	else
	    minicp.post(Factory.inverse(f,invf,offset));
    }

    public void makeLexLess(IntVar[] x, IntVar[] y) {
        minicp.post(Factory.lexLess(x,y));
    }

    public void makeLexLesseq(IntVar[] x, IntVar[] y) {
        minicp.post(Factory.lexLessOrEqual(x,y));
    }

}
