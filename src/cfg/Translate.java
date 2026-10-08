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

    private Id newTemp(Cfg.Type.T type) {
        Id id = Id.newNoname();
        emitDec(new Cfg.Dec.Singleton(type, id));
        return id;
    }

    // 生成 t = exp，并返回结果变量 t。
    private Id emitValue(Cfg.Exp.T exp, Cfg.Type.T type) {
        Id id = newTemp(type);
        emit(new Cfg.Stm.Assign(id, exp));
        return id;
    }

    // 创建并登记基本块；由调用方决定何时切换 currentBlock。
    private Cfg.Block.T newBlock() {
        Cfg.Block.T block = new Cfg.Block.Singleton(new Label(),
                new LinkedList<>(), new LinkedList<>());
        Cfg.Function.addBlock(this.currentFunction, block);
        return block;
    }

    private Id readId(Ast.AstId aid) {
        if (aid.isClassField)
            return emitValue(new Cfg.Exp.GetField(this.currentThis, aid.freshId),
                    doitType(aid.type));
        return aid.freshId;
    }

    // translate an expression, returning the ID holding its value
    private Id doitExp(Ast.Exp exp) {
        return switch (exp) {
            case Ast.Exp.Num(int n) -> emitValue(new Cfg.Exp.Int(n), new Cfg.Type.Int());
            case Ast.Exp.True() -> emitValue(new Cfg.Exp.Int(1), new Cfg.Type.Int());
            case Ast.Exp.False() -> emitValue(new Cfg.Exp.Int(0), new Cfg.Type.Int());
            case Ast.Exp.This() -> this.currentThis;
            case Ast.Exp.ExpId(Ast.AstId aid) -> readId(aid);
            case Ast.Exp.Bop(var left, String op, var right) -> {
                Id lhs = doitExp(left);
                Id rhs = doitExp(right);
                yield emitValue(new Cfg.Exp.Bop(op, List.of(lhs, rhs), new Cfg.Type.Int()),
                        new Cfg.Type.Int());
            }
            case Ast.Exp.Uop(String op, var operand) ->
                    emitValue(new Cfg.Exp.Bop(op, List.of(doitExp(operand)), new Cfg.Type.Int()),
                            new Cfg.Type.Int());
            case Ast.Exp.BopBool(var left, String _, var right) -> {
                // MiniJava 只有 &&；左侧为假时直接进入汇合块。
                Id lhs = doitExp(left);
                Id result = newTemp(new Cfg.Type.Int());
                emit(new Cfg.Stm.Assign(result, new Cfg.Exp.Int(0)));
                Cfg.Block.T rhsBlock = newBlock();
                Cfg.Block.T join = newBlock();
                emitTransfer(new Cfg.Transfer.If(lhs, rhsBlock, join));
                this.currentBlock = rhsBlock;
                Id rhs = doitExp(right);
                emit(new Cfg.Stm.Assign(result, new Cfg.Exp.Eid(rhs, new Cfg.Type.Int())));
                emitTransfer(new Cfg.Transfer.Jmp(join));
                this.currentBlock = join;
                yield result;
            }
            case Ast.Exp.NewObject(Id classId) ->
                    emitValue(new Cfg.Exp.New(classId), new Cfg.Type.ClassType(classId));
            case Ast.Exp.NewIntArray(var size) ->
                    emitValue(new Cfg.Exp.NewIntArray(doitExp(size)), new Cfg.Type.IntArray());
            case Ast.Exp.ArraySelect(var array, var index) -> {
                Id a = doitExp(array);
                Id i = doitExp(index);
                yield emitValue(new Cfg.Exp.ArraySelect(a, i), new Cfg.Type.Int());
            }
            case Ast.Exp.Length(var array) ->
                    emitValue(new Cfg.Exp.Length(doitExp(array)), new Cfg.Type.Int());
            case Ast.Exp.Call(var receiver, var methodId, var args, var objectType, var retType) -> {
                Id obj = doitExp(receiver);
                var operands = new LinkedList<Id>();
                operands.add(obj);
                // 接收者和实参按源码顺序求值；接收者是显式的第一个参数。
                for (Ast.Exp arg : args)
                    operands.add(doitExp(arg));
                // 静态类型和方法 ID 定位槽位，运行时查实际对象的虚方法表。
                Id func = emitValue(new Cfg.Exp.GetMethod(obj, objectType.get(), methodId.freshId),
                        new Cfg.Type.CodePtr());
                Cfg.Type.T type = doitType(retType.get());
                yield emitValue(new Cfg.Exp.Call(func, operands, type), type);
            }
        };
    }

    /////////////////////////////
    // translate a statement
    // this function does not return its result,
    // but saved the result into "currentBlock"
    private void doitStm(Ast.Stm stm) {
        switch (stm) {
            case Ast.Stm.Assign(var aid, var exp) -> {
                Id value = doitExp(exp);
                if (aid.isClassField)
                    emit(new Cfg.Stm.PutField(this.currentThis, aid.freshId, value));
                else
                    emit(new Cfg.Stm.Assign(aid.freshId, new Cfg.Exp.Eid(value, doitType(aid.type))));
            }
            case Ast.Stm.AssignArray(var aid, var index, var exp) -> {
                Id array = readId(aid);
                Id i = doitExp(index);
                Id value = doitExp(exp);
                emit(new Cfg.Stm.AssignArray(array, i, value));
            }
            case Ast.Stm.Block(var stms) -> stms.forEach(this::doitStm);
            case Ast.Stm.Print(var exp) ->
                    emitValue(new Cfg.Exp.Print(doitExp(exp)), new Cfg.Type.Int());
            case Ast.Stm.If(var cond, var thenn, var elsee) -> {
                Id condition = doitExp(cond);
                Cfg.Block.T trueBlock = newBlock();
                Cfg.Block.T falseBlock = newBlock();
                Cfg.Block.T join = newBlock();
                emitTransfer(new Cfg.Transfer.If(condition, trueBlock, falseBlock));
                this.currentBlock = trueBlock;
                doitStm(thenn);
                // 嵌套语句可能改变 currentBlock，从翻译后的当前块接入汇合点。
                emitTransfer(new Cfg.Transfer.Jmp(join));
                this.currentBlock = falseBlock;
                doitStm(elsee);
                emitTransfer(new Cfg.Transfer.Jmp(join));
                this.currentBlock = join;
            }
            case Ast.Stm.While(var cond, var body) -> {
                Cfg.Block.T test = newBlock();
                Cfg.Block.T loop = newBlock();
                Cfg.Block.T exit = newBlock();
                emitTransfer(new Cfg.Transfer.Jmp(test));
                this.currentBlock = test;
                Id condition = doitExp(cond);
                emitTransfer(new Cfg.Transfer.If(condition, loop, exit));
                this.currentBlock = loop;
                doitStm(body);
                emitTransfer(new Cfg.Transfer.Jmp(test));
                this.currentBlock = exit;
            }
        }
    }

    // translate a method
    private Cfg.Function.T doitMethod(Ast.Method method) {
        Ast.Method.Singleton m = (Ast.Method.Singleton) method;
        var formals = new LinkedList<>(doitDecList(m.formals()));
        this.currentThis = null;
        if (m.methodId().freshId != this.mainFunctionId) {
            this.currentThis = Id.newName("this").newSameOrigName();
            formals.addFirst(new Cfg.Dec.Singleton(
                    new Cfg.Type.ClassType(this.currentClassId), this.currentThis));
        }
        this.newDecs = new LinkedList<>();
        this.currentFunction = new Cfg.Function.Singleton(doitType(m.retType()),
                this.currentClassId, m.methodId().freshId, formals,
                doitDecList(m.locals()), new LinkedList<>());
        this.currentBlock = newBlock();
        m.stms().forEach(this::doitStm);
        Id result = doitExp(m.retExp());
        emitTransfer(new Cfg.Transfer.Ret(result));
        Cfg.Function.addDecs(this.currentFunction, this.newDecs);
        return this.currentFunction;
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
            Cfg.Function.Singleton function = (Cfg.Function.Singleton) doitMethod(method);
            this.functions.add(function);

            // 主入口是静态方法，不加入对象的虚方法表。
            if (isMain)
                continue;
            var entry = new Cfg.Vtable.Entry(function.retType(), this.currentClassId,
                    functionId, function.formals());
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
