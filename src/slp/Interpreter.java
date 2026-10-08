package slp;

import slp.Slp.Exp;
import slp.Slp.Stm;
import util.Todo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

// an interpreter for the SLP language.
public class Interpreter {
    // an abstract memory mapping each variable to its value
    HashMap<String, Integer> memory = new HashMap<>();

    // ///////////////////////////////////////////
    // interpret an expression
    private int interpExp(Exp exp) {
        switch (exp) {
            case Exp.Num(int n) -> { return n; }
            case Exp.Id(String x) -> { return memory.get(x); }
            case Exp.Op(
                    Exp left,
                    String bop,
                    Exp right
            ) -> {
                switch (bop) {
                    case "+" -> {
                        return interpExp(left) + interpExp(right);
                    }
                    case "-" -> {
                        return interpExp(left) - interpExp(right);
                    }
                    case "*" -> {
                        return interpExp(left) * interpExp(right);
                    }
                    case "/" -> {
                        if ( interpExp(right) == 0 ) throw new ArithmeticException("/ by zero");
                        return interpExp(left) / interpExp(right);
                    }
                    default -> throw new RuntimeException("unknown operator:" + bop);
                }
            }
            case Exp.Eseq(Stm stm, Exp e) -> {
                interpStm(stm);
                return interpExp(e);
            }
        }
    }

    // ///////////////////////////////////////////
    // interpret a statement
    public void interpStm(Stm stm) {
        switch (stm) {
            case Stm.Compound(
                    Stm s1,
                    Stm s2
            ) -> {
                interpStm(s1);
                interpStm(s2);
            }
            case Stm.Assign(
                    String x,
                    Exp e
            ) -> {
                memory.put(x, interpExp(e));
            }
            case Stm.Print(List<Exp> exps) -> {
                Iterator<Exp> iterator = exps.iterator();
                while ( iterator.hasNext() ) {
                    Exp exp = iterator.next();
                    if ( !iterator.hasNext() ) {
                        System.out.println(interpExp(exp));
                    } else {
                        System.out.print(interpExp(exp));
                        System.out.print(" ");
                    }
                }
            }
        }
    }
}