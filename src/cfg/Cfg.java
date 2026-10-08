package cfg;

import util.Dot;
import util.Id;
import util.Label;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Cfg {

    // the pretty printer
    private static int indentLevel = 0;

    private static void indent() {
        indentLevel += 4;
    }

    private static void unIndent() {
        indentLevel -= 4;
    }

    private static void printSpaces() {
        printSpaces(System.out);
    }

    private static void printSpaces(PrintStream out) {
        int i = indentLevel;
        while (i-- != 0) {
            out.print(" ");
        }
    }

    private static <T> void sayln(T s) {
        System.out.println(s);
    }

    private static <T> void say(T s) {
        System.out.print(s);
    }

    //  ///////////////////////////////////////////////////////////
    //  type
    public static class Type {
        public sealed interface T
                permits ClassType, CodePtr, Int, IntArray {
        }// 对象引用、函数指针、int/boolean、int[]

        public record ClassType(Id id) implements T {
        }

        public record CodePtr() implements T {
        }

        public record Int() implements T {
        }

        public record IntArray() implements T {
        }

        public static void pp(T ty) {
            pp(ty, System.out);
        }

        public static void pp(T ty, PrintStream out) {
            switch (ty) {
                case ClassType(Id id) -> out.print(id);
                case CodePtr() -> out.print("CodePtr");
                case Int() -> out.print("int");
                case IntArray() -> out.print("int[]");
            }
        }
    }

    // ///////////////////////////////////////////////////
    // declaration
    public static class Dec {
        public sealed interface T
                permits Singleton {
        }

        public record Singleton(Type.T type,
                                Id id) implements T {
            // only compare "id"
            @Override
            public boolean equals(Object o) {
                if (o == null)
                    return false;
                if (!(o instanceof Singleton))
                    return false;
                return this.id().equals(((Dec.Singleton) o).id());
            }

            @Override
            public int hashCode() {
                return this.id().hashCode();
            }
        }

        public static void pp(T dec) {
            switch (dec) {
                case Singleton(Type.T type, Id id) -> {
                    Type.pp(type);
                    say(" " + id);
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // virtual function table
    public static class Vtable {
        public sealed interface T
                permits Singleton {
        }

        public record Entry(Type.T retType, // 返回类型
                            Id classId,     // 实现这个方法的类
                            Id functionId,  // 函数签名 和 参数声明列表
                            List<Dec.T> argTypes) {
        }

        public record Singleton(Id name,
                                List<Entry> funcTypes) implements T {
        }

        public static void pp(T vtable) {
            switch (vtable) {
                case Singleton(Id name, List<Entry> funcTypes) -> {
                    printSpaces();
                    sayln("struct V_" + name + " {");
                    // all entries
                    indent();
                    for (Entry e : funcTypes) { // 针对每个 func
                        printSpaces();
                        Type.pp(e.retType);
                        say(" " + e.functionId + "(");
                        for (Dec.T dec : e.argTypes) {
                            Dec.pp(dec);
                            say(", ");
                        }
                        sayln(");");
                    }
                    unIndent();
                    printSpaces();
                    sayln("} V_" + name + "_ = {");// 函数槽位指向谁的实现，因为有重写
                    indent();
                    for (Entry e : funcTypes) {
                        printSpaces();
                        say("." + e.functionId + " = " + e.classId + "_" + e.functionId);
                        say(",\n");
                    }
                    unIndent();
                    printSpaces();
                    say("};\n\n");
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // structures
    public static class Struct {
        public sealed interface T
                permits Singleton {
        }

        public record Singleton(Id className,
                                List<Cfg.Dec.T> fields) implements T {
        }

        public static void pp(T s) {
            switch (s) {
                case Singleton(
                        Id clsName,
                        List<Cfg.Dec.T> fields
                ) -> {
                    printSpaces();
                    sayln("struct S_" + clsName.toString() + " {");
                    indent();
                    // the first field is special
                    printSpaces();
                    sayln("struct V_" + clsName + " *vptr;");
                    for (Cfg.Dec.T dec : fields) {
                        printSpaces();
                        Dec.pp(dec);
                        sayln(";");
                    }
                    unIndent();
                    printSpaces();
                    sayln("} S_" + clsName + "_ = {");
                    indent();
                    printSpaces();
                    sayln(".vptr = &V_" + clsName + "_;");
                    unIndent();
                    printSpaces();
                    say("};\n\n");
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // expression
    public static class Exp {
        // 需要补充操作，ast里有但cfg里没有
        public sealed interface T
                permits Bop, Call, Eid, GetMethod, Int, New, Print,
                        GetField, NewIntArray, ArraySelect, Length {
        }

        public record Bop(String op,
                          List<Id> operands,
                          Type.T type) implements T {
        }

        public record Call(Id func,
                           List<Id> operands,
                           Type.T retType) implements T {
        }

        public record Eid(Id x,
                          Type.T type) implements T {
        }

        // get virtual method:
        // getMethod(objId, classId, methodId)
        public record GetMethod(Id objId,
                                Id classId,
                                Id methodId) implements T {
        }

        public record Int(int n) implements T {
        }

        public record New(Id classId) implements T {
        }

        public record Print(Id x) implements T {
        }

        public record GetField(Id object, Id field) implements T {
        }

        public record NewIntArray(Id size) implements T {
        }

        public record ArraySelect(Id array, Id index) implements T {
        }

        public record Length(Id array) implements T {
        }

        public static void pp(Exp.T t) {
            pp(t, System.out);
        }

        // 同一份 pp 逻辑既能输出到终端，也能写入 DOT 标签的缓冲区。
        public static void pp(Exp.T t, PrintStream out) {
            switch (t) {
                case Bop(String op, List<Id> operands, Type.T type) -> {
                    out.print(op + "(");
                    for (int i = 0; i < operands.size(); i++) {
                        if (i > 0) out.print(", ");
                        out.print(operands.get(i));
                    }
                    out.print(")  @ty:");
                    Type.pp(type, out);
                }
                case Call(Id func, List<Id> args, Type.T retType) -> {
                    out.print(func + "(");
                    for (int i = 0; i < args.size(); i++) {
                        if (i > 0) out.print(", ");
                        out.print(args.get(i));
                    }
                    out.print(")  @retType:");
                    Type.pp(retType, out);
                }
                case Eid(Id id, Type.T type) -> out.print(id);
                case GetMethod(Id objId, Id classId, Id methodId) ->
                        out.print("getMethod(" + objId + ", " + classId + ", " + methodId + ")");
                case Int(int n) -> out.print(n);
                case New(Id classId) -> out.print("new " + classId + "()");
                case Print(Id x) -> out.print("print(" + x + ")");
                case GetField(Id object, Id field) -> out.print(object + "." + field);
                case NewIntArray(Id size) -> out.print("new int[" + size + "]");
                case ArraySelect(Id array, Id index) -> out.print(array + "[" + index + "]");
                case Length(Id array) -> out.print(array + ".length");
            }
        }
    }
    // end of expression

    // /////////////////////////////////////////////////////////
    // statement
    public static class Stm {
        public sealed interface T
                permits Assign, PutField, AssignArray {
        }

        // assign
        // "x" should not be "null", even if the exp is not used.
        public record Assign(Id x,
                             Exp.T exp) implements T {
        }

        public record PutField(Id object, Id field, Id value) implements T {
        }

        public record AssignArray(Id array, Id index, Id value) implements T {
        }

        static void pp(Stm.T t) {
            pp(t, System.out);
        }

        static void pp(Stm.T t, PrintStream out) {
            printSpaces(out);
            switch (t) {
                case Assign(Id x, Exp.T exp) -> {
                    out.print(x + " = ");
                    Exp.pp(exp, out);
                }
                case PutField(Id object, Id field, Id value) ->
                        out.print(object + "." + field + " = " + value);
                case AssignArray(Id array, Id index, Id value) ->
                        out.print(array + "[" + index + "] = " + value);
            }
            out.println(";");
        }
    }
    // end of statement

    // /////////////////////////////////////////////////////////
    // transfer
    public static class Transfer {
        public sealed interface T
                permits If, Jmp, Ret {
        }

        public record If(Id x,
                         Block.T trueBlock,
                         Block.T falseBlock) implements T {
        }

        public record Jmp(Block.T target) implements T {
        }

        public record Ret(Id x) implements T {
        }

        public static void dot(Dot d, String from, T t) {
            switch (t) {
                case If(
                        _,
                        Block.T thenn,
                        Block.T elsee
                ) -> {
                    d.insert(from, Block.getLabel(thenn).toString(), "true");
                    d.insert(from, Block.getLabel(elsee).toString(), "false");
                }
                case Jmp(Block.T target) -> d.insert(from,
                        Block.getLabel(target).toString());
                case Ret(_) -> {
                }
            }
        }

        public static void pp(T t) {
            pp(t, System.out);
        }

        public static void pp(T t, PrintStream out) {
            printSpaces(out);
            switch (t) {
                case If(
                        Id x,
                        Block.T thenn,
                        Block.T elsee
                ) -> {
                    out.print("if(" + x + ", " + Block.getLabel(thenn)
                            + ", " + Block.getLabel(elsee) + ");");
                }
                case Jmp(Block.T target) -> {
                    out.print("jmp " + Block.getLabel(target) + ";");

                }
                case Ret(Id x) -> {
                    out.print("ret " + x + ";");
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // block
    public static class Block {
        public sealed interface T
                permits Singleton {
        }

        public record Singleton(Label label,
                                List<Stm.T> stms,
                                // this is a special hack
                                // the transfer field is final, so that
                                // we use a list instead of a singleton field
                                List<Transfer.T> transfer) implements T {
        }

        public static void add(T b, Stm.T s) {
            switch (b) {
                case Singleton(
                        _,
                        List<Stm.T> stms,
                        _
                ) -> stms.add(s);
            }
        }

        public static void addTransfer(T b, Transfer.T s) {
            switch (b) {
                case Singleton(
                        Label _,
                        List<Stm.T> _,
                        List<Transfer.T> transfer
                ) -> transfer.add(s);
            }
        }

        public static void dot(Block.T t, Dot d) {
            switch (t) {
                case Singleton(
                        Label label,
                        List<Stm.T> _,
                        List<Transfer.T> trans
                ) -> {
                    ByteArrayOutputStream text = new ByteArrayOutputStream();
                    try (PrintStream out = new PrintStream(text, false, StandardCharsets.UTF_8)) {
                        pp(t, out);
                    }
                    d.addNode(label.toString(), text.toString(StandardCharsets.UTF_8));
                    trans.forEach(tr -> Transfer.dot(d, label.toString(), tr));
                }
            }
        }

        public static Label getLabel(Block.T t) {
            switch (t) {
                case Singleton(
                        Label label,
                        List<Stm.T> _,
                        List<Transfer.T> _
                ) -> {
                    return label;
                }
            }
        }

        public static void pp(T b) {
            pp(b, System.out);
        }

        public static void pp(T b, PrintStream out) {
            switch (b) {
                case Singleton(
                        Label label,
                        List<Stm.T> stms,
                        List<Transfer.T> transfer
                ) -> {
                    printSpaces(out);
                    out.println(label + ":");
                    indent();
                    stms.forEach(stm -> Stm.pp(stm, out));
                    Transfer.pp(transfer.getFirst(), out);
                    unIndent();
                    out.println();
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // function
    public static class Function {
        public sealed interface T
                permits Singleton {
        }

        public record Singleton(Type.T retType,
                                Id classId,
                                Id functionId,
                                List<Dec.T> formals,
                                List<Dec.T> locals,
                                List<Block.T> blocks) implements T {
        }

        public static void addBlock(T func, Block.T block) {
            switch (func) {
                case Singleton(
                        Type.T _,
                        Id _,
                        Id _,
                        List<Dec.T> _,
                        List<Dec.T> _,
                        List<Block.T> blocks
                ) -> blocks.add(block);
            }
        }

        public static void addFirstFormal(T func, Dec.T formal) {
            switch (func) {
                case Singleton(
                        _,
                        _,
                        _,
                        List<Dec.T> formals,
                        _,
                        _
                ) -> formals.addFirst(formal);
            }
        }

        public static void addDecs(T func, List<Dec.T> decs) {
            switch (func) {
                case Singleton(
                        _,
                        _,
                        _,
                        _,
                        List<Dec.T> locals,
                        _
                ) -> locals.addAll(decs);
            }
        }

        public static void dot(T func) {
            switch (func) {
                case Singleton(
                        Type.T retType,
                        Id classId,
                        Id functionId,
                        List<Dec.T> formals,
                        List<Dec.T> locals,
                        List<Block.T> blocks
                ) -> {
                    Dot d = new util.Dot(classId.toString() + "-" + functionId.toString()); // QS-Sort.dot.png
                    blocks.forEach((b) -> Block.dot(b, d));
                    d.visualize();
                }
            }
        }

        public static void pp(T f) {
            switch (f) {
                case Singleton(
                        Type.T retType,
                        Id classId,
                        Id id,
                        List<Dec.T> formals,
                        List<Dec.T> locals,
                        List<Block.T> blocks
                ) -> {
                    printSpaces();
                    Type.pp(retType);
                    say(" " + id + "(");
                    formals.forEach(x -> {
                        Dec.pp(x);
                        say(", ");
                    });
                    say("){ @classId: " + classId.toString() + "\n");
                    indent();
                    locals.forEach(x -> {
                        printSpaces();
                        Dec.pp(x);
                        sayln(";");
                    });
                    blocks.forEach(Block::pp);
                    unIndent();
                    printSpaces();
                    say("}\n\n");
                }
            }
        }
    }

    // whole program
    public static class Program {
        public sealed interface T
                permits Singleton {
        }

        public record Singleton(Id mainClassId,
                                Id mainFuncId, // name of the entry function
                                List<Vtable.T> vtables, // 虚函数表，去找这个函数
                                List<Struct.T> structs, // 对象的存储布局
                                List<Function.T> functions) implements T {
        }

        public static void size(T prog) {
            switch (prog) {
                case Singleton(
                        _,
                        _,
                        _,
                        _,
                        List<Function.T> functions
                ) -> {
                    int totalMethods = functions.size();
                    int totalBlocks = 0;
                    int totalStms = 0;

                    sayln("<#methods, #blocks, #statements>");
                    sayln("---------------------------------");

                    for (Function.T function : functions) {
                        switch (function) {
                            case Function.Singleton(
                                    _,
                                    Id classId,
                                    Id functionId,
                                    _,
                                    _,
                                    List<Block.T> blocks
                            ) -> {
                                int numBlocks = blocks.size();
                                int numStms = 0;

                                for (Block.T block : blocks) {
                                    switch (block) {
                                        case Block.Singleton(_, List<Stm.T> stms, _) ->
                                                numStms += stms.size();
                                    }
                                }

                                totalBlocks += numBlocks;
                                totalStms += numStms;

                                sayln("<\"" + classId + "_" + functionId
                                        + "\", " + numBlocks
                                        + ", " + numStms + ">");
                            }
                        }
                    }

                    sayln("---------------------------------");
                    sayln("subtotal: "
                            + totalMethods + ", "
                            + totalBlocks + ", "
                            + totalStms);
                }
            }
        }

        public static void dot(T prog) {
            switch (prog) {
                case Singleton(
                        Id mainClassId,
                        Id mainFuncId,
                        List<Vtable.T> vtables,
                        List<Struct.T> structs,
                        List<Function.T> functions
                ) -> functions.forEach(Function::dot);
            }
        }

        public static void pp(T prog) {
            switch (prog) {
                case Singleton(
                        Id mainClassId,
                        Id mainFuncId,
                        List<Vtable.T> vtables,  // 虚函数表
                        List<Struct.T> structs,  //
                        List<Function.T> functions
                ) -> {
                    printSpaces();
                    sayln("// the entry function: " + mainClassId + ": " + mainFuncId);
                    // vtables
                    vtables.forEach(Vtable::pp);
                    // structs
                    structs.forEach(Struct::pp);
                    // functions:
                    functions.forEach(Function::pp);
                }
            }
        }
    }
}
