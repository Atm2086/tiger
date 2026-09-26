package cfg;

import ast.Ast;
import control.Control;
import util.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

public class Translate {
    // the generated results:
    private final Vector<Cfg.Vtable.T> vtables;
    private final Vector<Cfg.Struct.T> structs;
    private final Vector<Cfg.Function.T> functions;
    // for bookkeeping purpose:
    private Id currentClassId = null;
    private Id currentThis = null;
    private Cfg.Function.T currentFunction = null;
    private Cfg.Block.T currentBlock = null;
    private LinkedList<Cfg.Dec.T> newDecs = new LinkedList<>();
    // for main function
    private Id mainClassId = null;
    private Id mainFunctionId = null;

    public Translate() {
        this.vtables = new Vector<>();
        this.structs = new Vector<>();
        this.functions = new Vector<>();
    }

    /////////////////////////////
    // translate a type
    private Cfg.Type.T doitType(Ast.Type ty) {
        switch (ty) {
            case Ast.Type.ClassType(Id id) -> {
                return new Cfg.Type.ClassType(id);
            }
            case Ast.Type.Boolean() -> {
                return new Cfg.Type.Int();
            }
            case Ast.Type.IntArray() -> {
                return new Cfg.Type.IntArray();
            }
            case Ast.Type.Int() -> {
                return new Cfg.Type.Int();
            }
            case Ast.Type.Error() ->
                    throw new IllegalStateException("error type cannot be translated to CFG");
        }
    }

    private Cfg.Dec.T doitDec(Ast.Dec dec) {
        switch (dec) {
            case Ast.Dec.Singleton(Ast.Type type, Ast.AstId aid) -> {
                return new Cfg.Dec.Singleton(doitType(type), aid.freshId);
            }
        }
    }
    // 把 AST 的 Dec 转换成 CFG 的 Dec
    private List<Cfg.Dec.T> doitDecList(List<Ast.Dec> decs) {
        return decs.stream().map(this::doitDec).collect(Collectors.toList());
    }

    private void emit(Cfg.Stm.T s) {
        Cfg.Block.add(this.currentBlock, s);
    }

    private void emitTransfer(Cfg.Transfer.T s) {
        Cfg.Block.addTransfer(this.currentBlock, s);
    }

    private void emitDec(Cfg.Dec.T dec) {
        this.newDecs.add(dec);
    }

    /////////////////////////////
    // translate an expression
    // TODO: lab3, exercise 8.
    private Id doitExp(Ast.Exp exp) {
        throw new Todo();
    }

    /////////////////////////////
    // translate a statement
    // this function does not return its result,
    // but saved the result into "currentBlock"
    // TODO: lab3, exercise 8.
    private void doitStm(Ast.Stm stm) {
        throw new Todo();
    }

    // translate a method
    // TODO: lab3, exercise 8.
    private Cfg.Function.T doitMethod(Ast.Method method) {
        throw new Todo();
    }

    // the prefixing algorithm
    // Cfg.Dec 是 字段声明
    // Vtable 是 虚方法声明
    private Tuple.Two<Vector<Cfg.Dec.T>,
            Vector<Cfg.Vtable.Entry>> prefixOneClass(Ast.Class cls, // 当前类自己的ast
                                                     Tuple.Two<Vector<Cfg.Dec.T>,
                                                             Vector<Cfg.Vtable.Entry>> decsAndFunctions) {
        Ast.Class.Singleton currentClass = (Ast.Class.Singleton) cls;
        this.currentClassId = currentClass.classId();

        // 每个类使用自己的列表，保留父类字段的 ID 和排列顺序。
        var fields = new Vector<>(decsAndFunctions.first());
        fields.addAll(doitDecList(currentClass.decs()));
        this.structs.add(new Cfg.Struct.Singleton(this.currentClassId, fields));

        var entries = new Vector<>(decsAndFunctions.second());
        for (Ast.Method method : currentClass.methods()) {
            Ast.Method.Singleton m = (Ast.Method.Singleton) method;
            Id functionId = m.methodId().freshId; // 要取实际的，后面替换

            boolean isMain = functionId == this.mainFunctionId;
            var formals = new LinkedList<>(doitDecList(m.formals())); // formals 形参声明列表

            this.currentThis = null;
            if (!isMain) {
                this.currentThis = Id.newName("this").newSameOrigName();
                formals.addFirst(new Cfg.Dec.Singleton(
                        new Cfg.Type.ClassType(this.currentClassId), this.currentThis));
            }

            // Ex7：先生成空函数，方法体的基本块留给 Ex8 翻译。
            var retType = doitType(m.retType());
            this.functions.add(new Cfg.Function.Singleton(retType,
                    this.currentClassId, functionId, formals,
                    doitDecList(m.locals()), new LinkedList<>()));

            // 主入口是静态方法，不加入对象的虚方法表。
            if (isMain)
                continue;
            var entry = new Cfg.Vtable.Entry(retType, this.currentClassId,
                    functionId, formals);
            int slot = 0;
            while (slot < entries.size()
                    && !Id.originalEquals(entries.get(slot).functionId(), functionId)) {
                slot++;
            }
            if (slot < entries.size())
                entries.set(slot, entry);
            else
                entries.add(entry);
        }
        this.vtables.add(new Cfg.Vtable.Singleton(this.currentClassId, entries));
        return new Tuple.Two<>(fields, entries);
    }

    // build an inherit tree
    // 组织类之间关系
    private Tree<Ast.Class> buildInheritTree0(Ast.Program ast) {
        Tree<Ast.Class> tree = new Tree<>("inheritTree");
        tree.addRoot(new Ast.Class.Singleton(Id.newName("Object"), null,
                new LinkedList<>(), new LinkedList<>(), new Tuple.One<>()));

        Ast.Program.Singleton program = (Ast.Program.Singleton) ast;
        LinkedList<Ast.Class> classes = new LinkedList<>(program.classes()); // 临时集合

        // 取出单独存放的主类，包装成普通类，统一放入继承树。
        Ast.MainClass.Singleton main = (Ast.MainClass.Singleton) program.mainClass();
        Ast.AstId mainMethodId = new Ast.AstId(Id.newName("main"));
        Ast.Method mainMethod = new Ast.Method.Singleton(Ast.Type.getInt(),
                mainMethodId, new LinkedList<>(), new LinkedList<>(),
                List.of(main.stm()), new Ast.Exp.Num(0));
        classes.add(new Ast.Class.Singleton(main.classId(), null,
                new LinkedList<>(), List.of(mainMethod), new Tuple.One<>()));
        this.mainClassId = main.classId();
        this.mainFunctionId = mainMethodId.genFreshId();

        // 先创建全部节点，避免依赖源码中父类和子类的声明顺序。
        classes.forEach(tree::addNode);
        for (Ast.Class c : classes) {
            Ast.Class.Singleton cls = (Ast.Class.Singleton) c;
            var parentNode = cls.extends_() == null
                    ? tree.root
                    : tree.lookupNode(cls.parent().get());
            tree.addEdge(parentNode, tree.lookupNode(cls));
        }
        return tree;
    }

    private Tree<Ast.Class> buildInheritTree(Ast.Program ast) {
        Trace<Ast.Program, Tree<Ast.Class>> trace =
                new Trace<>("cfg.Translate.buildInheritTree",
                        this::buildInheritTree0,
                        ast,
                        new ast.PrettyPrinter()::ppProgram,
                        (tree) -> {
                            // visualize the tree
                            if (Control.Dot.beingDotted("inheritTree"))
                                tree.dot(cls -> Ast.Class.getClassId(cls).toString());
                        });
        return trace.doit();
    }

    private Cfg.Program.T doitProgram0(Ast.Program ast) {
        // Step #1: build the inheritance tree，和之前的 List<class>相区别
        Tree<Ast.Class> tree = buildInheritTree(ast);
        // Step #2: perform prefixing via a level-order traversal
        // we also translate each method during this traversal.
        tree.levelOrder(tree.root,
                this::prefixOneClass,
                new Tuple.Two<>(new Vector<>(),
                        new Vector<>()));

        return new Cfg.Program.Singleton(this.mainClassId,
                this.mainFunctionId,
                this.vtables,
                this.structs,
                this.functions);
    }

    // given an abstract syntax tree, lower it down
    // to a corresponding control-flow graph.
    // 包了一层Trace，方便打印翻译前后的结果
    public Cfg.Program.T doitProgram(Ast.Program ast) {
        Trace<Ast.Program, Cfg.Program.T> trace =
                new Trace<>("cfg.Translate.doitProgram",
                        this::doitProgram0,
                        ast,
                        new ast.PrettyPrinter()::ppProgram,
                        (x) -> {
                            Cfg.Program.pp(x);
                            if (Control.Dot.beingDotted("cfg")) {
                                Cfg.Program.dot(x);
                            }
                        });
        return trace.doit();
    }
}
