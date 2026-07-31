package ast;

import ast.Ast.*;
import slp.Slp;
import util.Id;
import util.Todo;
import util.Tuple;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class PrettyPrinter {
    // 缩进
    private int indentLevel = 4;

    public PrettyPrinter() {
        this.indentLevel = 0;
    }

    // 执行缩进
    private void indent() {
        this.indentLevel += 4;
    }
    // 缩进回退
    private void unIndent() {
        this.indentLevel -= 4;
    }

    private void printSpaces() {
        int i = this.indentLevel;
        while (i-- > 0)
            System.out.print(" ");
    }

    private <T> void say(T s) {
        printSpaces();
        System.out.print(s);
    }

    private <T> void sayln(T s) {
        printSpaces();
        System.out.println(s);
    }

    private <T> void sayLocal(T s) {
        System.out.print(s);
    }

    private <T> void printSeparated(
            List<T> elements,  // 要打印的元素
            String separator,  // 分隔符
            Consumer<T> printer // 每个元素具体怎么打印
    ){
        Iterator<T> iterator = elements.iterator();

        while (iterator.hasNext()) {
            T element = iterator.next();
            printer.accept(element);

            if (iterator.hasNext()) {
                sayLocal(separator);
            }
        }
    }

    // /////////////////////////////////////////////////////
    // ast id
    public void ppAstId(AstId aid) {
        sayLocal(aid.freshId);
    }

    // /////////////////////////////////////////////////////
    // expressions
    public void ppExp(Exp e) {
        switch (e) {
            case Exp.ExpId(AstId aid) -> ppAstId(aid);
            case Exp.Call(
                    Exp callee,
                    AstId methodId,
                    List<Exp> args,
                    Tuple.One<Id> theObjectType,
                    Tuple.One<Type> retType
            ) -> {
                ppExp(callee);
                sayLocal(".");
                ppAstId(methodId);
                sayLocal("(");
                printSeparated(args,", ", this::ppExp);
                sayLocal(")");
            }
            case Exp.NewObject(Id id) -> {
                sayLocal("new " + id.toString() + "()");
            }
            case Exp.Num(int n) -> sayLocal(n);
            case Exp.Bop(
                    Exp left,
                    String bop,
                    Exp right
            ) -> {
                sayLocal("(");
                ppExp(left);
                sayLocal(" " + bop + " ");
                ppExp(right);
                sayLocal(")");
            }
            case Exp.This() -> sayLocal("this");
            case Exp.ArraySelect(Exp array, Exp index) -> {
                ppExp(array);
                sayLocal("[");
                ppExp(index);
                sayLocal("]");
            }

            case Exp.BopBool(Exp left, String op, Exp right) -> {
                sayLocal("(");
                ppExp(left);
                sayLocal(" " + op + " ");
                ppExp(right);
                sayLocal(")");
            }

            case Exp.False() -> sayLocal("false");

            case Exp.True() -> sayLocal("true");

            case Exp.Length(Exp array) -> {
                ppExp(array);
                sayLocal(".length");
            }

            case Exp.NewIntArray(Exp size) -> {
                sayLocal("new int[");
                ppExp(size);
                sayLocal("]");
            }

            case Exp.Uop(String op, Exp exp) -> {
                sayLocal(op);
                sayLocal("(");
                ppExp(exp);
                sayLocal(")");
            }
            default -> throw new Todo();
        }
    }

    // statement
    public void ppStm(Stm s) {
        switch (s) {
            case Stm.If(
                    Exp cond,
                    Stm then_,
                    Stm else_
            ) -> {
                say("if(");
                ppExp(cond);
                sayLocal("){\n");
                indent();
                ppStm(then_);
                unIndent();
                sayln("}else{");
                indent();
                ppStm(else_);
                unIndent();
                sayln("}");
            }
            case Stm.Print(Exp exp) -> {
                say("System.out.println(");
                ppExp(exp);
                sayLocal(");\n");
            }
            case Stm.Assign(
                    AstId aid,
                    Exp exp
            ) -> {
                say("");
                ppAstId(aid);
                sayLocal(" = ");
                ppExp(exp);
                sayLocal(";\n");
            }
            case Stm.AssignArray(
                    AstId id,
                    Exp index,
                    Exp exp
            ) -> {
                say("");
                ppAstId(id);
                sayLocal("[");
                ppExp(index);
                sayLocal("] = ");
                ppExp(exp);
                sayLocal(";\n");
            }
            case Stm.Block(
                    List<Stm> stms
            ) -> {
                sayln("{");
                indent();

                stms.forEach(this::ppStm);

                unIndent();
                sayln("}");
            }
            case Stm.While(
                    Exp cond,
                    Stm body
            ) -> {
                say("while (");
                ppExp(cond);
                sayLocal(") ");

                if (body instanceof Stm.Block) {
                    ppStm(body);
                } else {
                    sayLocal("\n");
                    indent();
                    ppStm(body);
                    unIndent();
                }
            }
            default -> throw new Todo();
        }
    }

    // type
    public void ppType(Type t) {
        switch (t) {
            case Type.Int() -> sayLocal("int");
            case Type.Boolean() -> sayLocal("boolean");
            case Type.IntArray() -> sayLocal("int[]");
            case Type.ClassType(Id id) -> sayLocal(id.toString());
            default -> throw new Todo();
        }
    }

    // dec
    public void ppDec(Dec dec) {
        Dec.Singleton d = (Dec.Singleton) dec;
        ppType(d.type());
        sayLocal(" ");
        ppAstId(d.aid());
    }

    // method
    public void ppMethod(Method mtd) {
        Method.Singleton m = (Method.Singleton) mtd;
        this.say("public ");
        ppType(m.retType());
        this.sayLocal(" ");
        ppAstId(m.methodId());
        this.sayLocal("(");

        printSeparated(m.formals(), ", ", this::ppDec);

        this.sayLocal("){\n");
        indent();
        m.locals().forEach(x -> {
            this.say("");
            ppDec(x);
            this.sayLocal(";\n");
        });
        this.sayln("");
        m.stms().forEach(this::ppStm);
        this.say("return ");
        ppExp(m.retExp());
        this.sayLocal(";\n");
        unIndent();
        this.sayln("}");
    }

    // class
    public void ppOneClass(Ast.Class cls) {
        Ast.Class.Singleton c = (Ast.Class.Singleton) cls;
        this.say("class " + c.classId());
        if (c.extends_() != null) {
            this.sayLocal(" extends " + c.extends_());
        } else {
            this.sayLocal("");
        }
        sayLocal("{\n");
        indent();

        for (Dec dec : c.decs()) {
            say("");
            ppDec(dec);
            sayLocal(";\n");
        }
        c.methods().forEach(this::ppMethod);

        unIndent();
        this.sayln("}");
    }

    // main class
    public void ppMainClass(MainClass m) {
        MainClass.Singleton mc = (MainClass.Singleton) m;
        this.sayln("class " + mc.classId() + "{");
        indent();
        this.say("public static void main(String[] ");
        ppAstId(mc.arg());
        sayLocal("){\n");
        indent();
        ppStm(mc.stm());
        unIndent();
        this.sayln("}");
        unIndent();
        this.sayln("}");
    }

    // program
    public void ppProgram(Program prog) {
        Program.Singleton p = (Program.Singleton) prog;
        ppMainClass(p.mainClass());
        this.sayln("");
        p.classes().forEach(this::ppOneClass);
        this.sayln("\n");
    }

}

