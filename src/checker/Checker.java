package checker;

import ast.Ast;
import ast.Ast.*;
import ast.Ast.Class;
import ast.PrettyPrinter;
import control.Control;
import util.*;

import java.util.List;
import java.util.Objects;

public class Checker {
    // symbol table for all classes
    private final ClassTable classTable;
    // symbol table for each method
    private MethodTable methodTable;
    // the class name being checked
    private Id currentClass;

    public Checker() {
        this.classTable = new ClassTable();
        this.methodTable = new MethodTable();
        this.currentClass = null;
    }

    private void error(String s) {
        System.out.println("Error: type mismatch: " + s);
        System.exit(1);
    }

    private void error(String s, Type expected, Type got) {
        System.out.println("Error: type mismatch: " + s);
        Type.output(expected);
        Type.output(got);
        System.exit(1);
    }

    // /////////////////////////////////////////////////////
    // ast-id
    // 检查 id 存在性，先查形参或者局部变量，再查类字段
    private Type checkAstId(AstId aid) {
        boolean isClassField = false;
        // first search in current method table
        Tuple.Two<Ast.Type, Id> resultId = this.methodTable.get(aid.id);
        // not a local or formal
        if (resultId == null) {
            isClassField = true;
            resultId = this.classTable.getField(this.currentClass, aid.id);
        }
        if (resultId == null) {
            error("id");
        }
        assert resultId != null;
        // set up the fresh
        aid.freshId = resultId.second();
        aid.isClassField = isClassField;
        return resultId.first();
    }

    // /////////////////////////////////////////////////////
    // expressions
    // type check an expression will return its type.
    private Type checkExp(Exp e) {
        switch (e) {
            //
            case Exp.Call(
                    Exp theObject,
                    AstId methodId,
                    List<Exp> args,
                    Tuple.One<Id> calleeTy, // 保存接收者对象所属的类
                    Tuple.One<Type> retTy  // 保存方法返回类型
            ) -> {

                var typeOfTheObject = checkExp(theObject); // 就是点号左边返回的类型

                Id calleeClassId = null;
                // 检查typeOfTheObject是否为null，不是null就看是不是合法类型
                if (Objects.requireNonNull(typeOfTheObject) instanceof Type.ClassType(Id calleeClassId_)) {
                    calleeClassId = calleeClassId_;
                    // put the return type onto the AST
                    // 回填，因为构建ast的时候是正向的，返回类型未知，所以要回填
                    calleeTy.set(calleeClassId);
                }
                var resultMethodId = this.classTable.getMethod(calleeClassId, methodId.id);
                if (resultMethodId == null) {
                    error("method not found: " + calleeClassId + "." + methodId);
                }
                var resultArgs = args.stream().map(this::checkExp).toList();
                assert resultMethodId != null;
                methodId.freshId = resultMethodId.second();
                Ast.Type retType = resultMethodId.first().retType();
                // put the return type onto the AST
                retTy.set(retType);
                return retType;
            }
            case Exp.NewObject(Id classId) -> {
                var classBinding = this.classTable.getClass_(classId);
                return Type.getClassType(classId);
            }
            case Exp.Num(int n) -> {
                return Type.getInt();
            }
            case Exp.Bop(
                    Exp left,
                    String bop,
                    Exp right
            ) -> {
                var resultLeft = checkExp(left);
                var resultRight = checkExp(right);

                switch (bop) {
                    case "+", "-" -> {
                        if (Type.nonEquals(resultLeft, Type.getInt()) ||
                                Type.nonEquals(resultRight, Type.getInt())) {
                            error(bop);
                        }
                        return Type.getInt();
                    }
                    case "*" -> {
                        if (Type.nonEquals(resultLeft, Type.getInt()) ||
                                Type.nonEquals(resultRight, Type.getInt())) {
                            error("*");
                        }
                        return Type.getInt();
                    }
                    case "<" -> {
                        if (Type.nonEquals(resultLeft, Type.getInt()) ||
                                Type.nonEquals(resultRight, Type.getInt())) {
                            error("<");
                        }
                        return Type.getBool();
                    }
                    default -> throw new Todo();
                }
            }
            case Exp.ExpId(AstId aid) -> {
                return checkAstId(aid);
            }
            case Exp.This() -> {
                return Type.getClassType(this.currentClass);
            }
            case Exp.ArraySelect(Exp array, Exp index) -> {
                Type arrayType = checkExp(array);
                Type indexType = checkExp(index);
                if (Type.nonEquals(arrayType, Type.getIntArray())) {
                    error("array selection requires an int[]");
                }
                if (Type.nonEquals(indexType, Type.getInt())) {
                    error("array index requires an integer");
                }
                return Type.getInt();
            }
            
            // 返回bool类型的
            case Exp.BopBool(Exp left, String bop, Exp right) -> {
                Type leftType = checkExp(left);
                Type rightType = checkExp(right);
                if (!bop.equals("&&")) {
                    throw new Todo();
                }
                if (Type.nonEquals(leftType, Type.getBool())
                        || Type.nonEquals(rightType, Type.getBool())) {
                    error("&&");
                }
                return Type.getBool();
            }
            case Exp.False() -> {
                return Type.getBool();
            }
            case Exp.True() -> {
                return Type.getBool();
            }
            case Exp.Uop(String op, Exp exp) -> {
                Type expType = checkExp(exp);
                if (!op.equals("!")) {
                    throw new Todo();
                }
                if (Type.nonEquals(expType, Type.getBool())) {
                    error("!");
                }
                return Type.getBool();
            }

            case Exp.Length(Exp array) -> {
                Type arrayType = checkExp(array);
                if (Type.nonEquals(arrayType, Type.getIntArray())) {
                    error("length requires an int[]");
                }
                return Type.getInt();
            }
            case Exp.NewIntArray(Exp size) -> {
                Type sizeType = checkExp(size);
                if (Type.nonEquals(sizeType, Type.getInt())) {
                    error("array size requires an integer");
                }
                return Type.getIntArray();
            }
            
            default -> throw new Todo();
        }
    }

    // type check a statement
    private void checkStm(Stm s) {
        switch (s) {
            case Stm.If(
                    Exp cond,
                    Stm then_,
                    Stm else_
            ) -> {
                var resultCond = checkExp(cond);
                if (Type.nonEquals(resultCond, Type.getBool())) {
                    error("if require a boolean type");
                }
                checkStm(then_);
                checkStm(else_);
            }
            case Stm.Print(Exp exp) -> {
                var resultExp = checkExp(exp);
                if (Type.nonEquals(resultExp, Type.getInt())) {
                    error("print requires an integer type");
                }
            }
            case Stm.Assign(
                    AstId id,
                    Exp exp
            ) -> {
                // first lookup in the method table
                var resultAstId = checkAstId(id);
                var resultExp = checkExp(exp);
                if (Type.nonEquals(resultAstId, resultExp)) {
                    error("=");
                }
            }
            case Stm.AssignArray(AstId id, Exp index, Exp exp) -> {
                Type arrayType = checkAstId(id);
                Type indexType = checkExp(index);
                Type expType = checkExp(exp);

                if (Type.nonEquals(arrayType, Type.getIntArray())) {
                    error("array assignment requires an int[]");
                }
                if (Type.nonEquals(indexType, Type.getInt())) {
                    error("array index requires an integer");
                }
                if (Type.nonEquals(expType, Type.getInt())) {
                    error("array element requires an integer");
                }
            }
            case Stm.Block(List<Stm> stms) -> {
                stms.forEach(this::checkStm);
            }
            case Stm.While(Exp cond, Stm body) -> {
                Type condType = checkExp(cond);
                if (Type.nonEquals(condType, Type.getBool())) {
                    error("while require a boolean type");
                }
                checkStm(body);
            }
            default -> throw new Todo();
        }
    }

    // check type 检查声明类型是否合法
    public void checkType(Type t) {
        switch (t) {
            case Type.Int(),
                Type.Boolean(),
                Type.IntArray() -> {
            }

            case Type.ClassType(Id classId) -> {
                if (classTable.getClass_(classId) == null) {
                    error("undefined class: " + classId);
                }
            }
    }
    }

    // dec 检查声明是否合法，主要检查他的声明类型
    public void checkDec(Dec d) {
        Dec.Singleton dec = (Dec.Singleton) d;
        checkType(dec.type());
    }

    // method type
    private List<Type> genMethodArgType(List<Dec> decs) {
        return decs.stream().map(Dec::getType).toList();
    }

    // method
    private void checkMethod(Method mtd) {
        Method.Singleton m = (Method.Singleton) mtd;

        // 检查声明
        checkType(m.retType());
        m.formals().forEach(this::checkDec);
        m.locals().forEach(this::checkDec);

        // construct the method table
        this.methodTable = new MethodTable();
        this.methodTable.putFormalLocal(m.formals(), m.locals());

        m.stms().forEach(this::checkStm);

        var resultExp = checkExp(m.retExp());

        if (Type.nonEquals(resultExp, m.retType())) {
            error("ret type mismatch", m.retType(), resultExp);
        }
    }

    // class
    private void checkClass(Class c) {
        Class.Singleton cls = (Class.Singleton) c;
        this.currentClass = cls.classId();
        Id parentId = cls.extends_();
        if (parentId != null) {
            ClassTable.Binding binding = this.classTable.getClass_(parentId);
            // 判断父类是否存在
            if (binding == null) {
                error("parent class not found");
            }

            cls.parent().set(binding.self());
        }
        // 查声明
        cls.decs().forEach(this::checkDec);
        // 查函数
        cls.methods().forEach(this::checkMethod);
    }

    // main class
    private void checkMainClass(MainClass c) {
        MainClass.Singleton mainClass = (MainClass.Singleton) c;
        this.currentClass = mainClass.classId();
        // "main" method has an argument "arg" of type "String[]", but
        // MiniJava programs do not use it.
        // So we can safely create a fake one with integer type.
        this.methodTable = new MethodTable();
        this.methodTable.putFormalLocal(List.of(new Dec.Singleton(Type.getInt(), mainClass.arg())),
                List.of()); // no local variables
        checkStm(mainClass.stm());
    }

    // ////////////////////////////////////////////////////////
    // step 1: create class table for Main class
    private void buildMainClass(MainClass main) {
        // we do not put Main class into the class table.
        // so that no other class can inherit from it.
        // MainClass.Singleton mc = (MainClass.Singleton) main;
        //this.classTable.putClass(mc.classId(), null);
    }

    // create class table for each normal class
    private void buildClass(Class cls) {
        Class.Singleton c = (Class.Singleton) cls;
        this.classTable.putClass(c.classId(), c.extends_(), cls);

        // add all instance variables into the class table
        for (Dec dec : c.decs()) {
            Dec.Singleton d = (Dec.Singleton) dec;
            this.classTable.putField(c.classId(),
                    d.aid(),
                    d.type());
        }
        // add all methods into the class table
        for (Method method : c.methods()) {
            Method.Singleton m = (Method.Singleton) method;
            this.classTable.putMethod(c.classId(),
                    m.methodId(),
                    // for now, do not worry to check
                    // method formals, as we will check
                    // this during method table construction.
                    new ClassTable.MethodType(m.retType(),
                            genMethodArgType(m.formals())));
        }
    }

    private Program buildTable0(Program p) {
        Program.Singleton prog = (Program.Singleton) p;
        // ////////////////////////////////////////////////
        // a class table maps a class name to its class binding:
        // classTable: className -> Binding{extends_, fields, methods}
        buildMainClass(prog.mainClass());
        prog.classes().forEach(this::buildClass);
        return p;
    }

    private Program buildTable(Program p) {
        Trace<Program, Program> trace =
                new Trace<>("checker.Checker.buildTable",
                        this::buildTable0,
                        p,
                        (_) -> {
                            System.out.println("build class table:");
                        },
                        (_) -> {
                            this.classTable.dump();
                        });
        return trace.doit();
    }

    private Program checkIt0(Program p) {
        Program.Singleton prog = (Program.Singleton) p;
        checkMainClass(prog.mainClass());
        prog.classes().forEach(this::checkClass);
        return p;
    }

    private Program checkIt(Program p) {
        Trace<Program, Program> trace =
                new Trace<>("checker.Checker.checkClass",
                        this::checkIt0,
                        p,
                        (_) -> {
                            System.out.println("check class:");
                        },
                        (_) -> {
                            this.classTable.dump();
                        });
        return trace.doit();
    }

    // to check a program， 分了两个pass来解决
    private Program checkProgram(Program p) {
        // pass 1: build the class table
        Pass<Program, Program> buildTablePass =
                new Pass<>("build class table",
                        this::buildTable,
                        p,
                        Control.Verbose.L1);
        p = buildTablePass.apply();

        System.out.println("dump after build");
        // dump after build
        if (Control.Type.dumpClassTable) {
            this.classTable.dump();
        }
        if (Control.Type.dumpMethodTable) {
            this.methodTable.dump();
        }

        // ////////////////////////////////////////////////
        // pass 2: check each class in turn, under the class table built above.
        Pass<Program, Program> checkPass =
                new Pass<>("check class",
                        this::checkIt,
                        p,
                        Control.Verbose.L1);
        p = checkPass.apply();
        return p;
    }

    public Ast.Program check(Program ast) {
        PrettyPrinter pp = new PrettyPrinter();

        var traceCheckProgram = new Trace<>(
                "checker.Checker.check",
                this::checkProgram,
                ast,
                pp::ppProgram,
                pp::ppProgram);
        return traceCheckProgram.doit();
    }
}
