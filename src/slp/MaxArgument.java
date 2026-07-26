package slp;

import slp.Slp.Exp;
import slp.Slp.Stm;
import util.Todo;

import java.util.Iterator;
import java.util.List;

public class MaxArgument {
    // ///////////////////////////////////////////
    // expression
    private int maxExp(Exp exp) {
        switch (exp) {
            case Exp.Num(int n) -> { return 1; }
            case Exp.Id(String x) -> { return 1; }
            case Exp.Op(
                    Exp left,
                    String bop,
                    Exp right
            ) -> {
                return maxExp(left) + maxExp(right);
            }
            case Exp.Eseq(Stm stm, Exp e) -> {
                return Math.max(maxStm(stm), maxExp(e));
            }
        }
    }

    // ///////////////////////////////////////////
    // statement
    public int maxStm(Stm stm) {
        switch (stm) {
            case Stm.Compound(
                    Stm s1,
                    Stm s2
            ) -> {
                return Math.max(maxStm(s1), maxStm(s2));
            }
            case Stm.Assign(
                    String x,
                    Exp e
            ) -> {
                return maxExp(e);
            }
            case Stm.Print(List<Exp> exps) -> {
                int max_argument = 0;
                exps.forEach(element -> Math.max(max_argument, maxExp(element)));
                return max_argument;
            }
        }
    }
}
