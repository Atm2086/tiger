package slp;

import java.util.List;

// the abstract syntax trees for the SLP language.
public class Slp {
    // ////////////////////////////////////////////////
    // expression
    public sealed interface Exp
            // the type
            permits Exp.Eseq, Exp.Id, Exp.Op, Exp.Num {

        // s, e
        public record Eseq(Stm stm,
                           Exp exp) implements Exp {
        }

        // x
        public record Id(String id) implements Exp {
        }

        // e bop e
        public record Op(Exp left,
                         String op,
                         Exp right) implements Exp {
        }

        // n
        public record Num(int num) implements Exp {
        }
    }
    // end of expression

    // ///////////////////////////////////////////////
    // statement
    public sealed interface Stm
            // the type
            permits Stm.Assign, Stm.Compound, Stm.Print {

        // x := e
        public record Assign(String id,
                             Exp exp) implements Stm {
        }

        // s1; s2
        public record Compound(Stm s1,
                               Stm s2) implements Stm {
        }

        // print(explist)
        public record Print(List<Exp> exps) implements Stm {
        }
    }
    // end of statement
}