package checker;

import ast.Ast;
import ast.Ast.*;
import ast.Ast.Class;
import ast.PrettyPrinter;
import control.Control;
import util.*;

import java.util.HashSet;
import java.util.List;

public class Checker {
    // symbol table for all classes
    private final ClassTable classTable;
    // symbol table for each method
    private MethodTable methodTable;
    // the class name being checked
    private Id currentClass;
    // 关联当前AST节点记录错误
    private final ErrorReporter errorReporter;

    public Checker() {
        this.classTable = new ClassTable();
        this.methodTable = new MethodTable();
        this.currentClass = null;
        this.errorReporter = new ErrorReporter();
    }


    // 两种report
    private void error(Object node, String message) {
        this.errorReporter.report(node, message);
    }

    private void error(Object node,
                       String message,
                       Type expected,
                       Type got) {
        this.errorReporter.report(node, message, expected, got);
    }

    private boolean isError(Type type) {
        return type instanceof Type.Error;
    }

    private boolean isAssignable(Type actual, Type expected) {
        if (!Type.nonEquals(actual, expected)) {
            return true;
        }

        if (!(actual instanceof Type.ClassType(Id actualClassId))
                || !(expected instanceof Type.ClassType(Id expectedClassId))) {
            return false;
        }

        HashSet<Id> visited = new HashSet<>();
        while (actualClassId != null && visited.add(actualClassId)) {
            if (actualClassId == expectedClassId) {
                return true;
            }
            ClassTable.Binding binding =
                    this.classTable.getClass_(actualClassId);
            if (binding == null) {
                return false;
            }
            actualClassId = binding.extends_();
        }
        return false;
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
            ClassTable.Binding currentBinding =
                    this.classTable.getClass_(this.currentClass);
            if (currentBinding != null) {
                resultId = this.classTable.getField(this.currentClass, aid.id);
                isClassField = resultId != null;
            }
        }
        if (resultId == null) {
            error(aid, "undefined identifier: " + aid.id);
            aid.type = Type.getError();
            return Type.getError();
        }
        // set up the fresh
        aid.freshId = resultId.second();
        aid.type = resultId.first();
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
                Type objectType = checkExp(theObject);
                List<Type> actualTypes =
                        args.stream().map(this::checkExp).toList();

                if (isError(objectType)) {
                    return Type.getError();
                }
                if (!(objectType instanceof Type.ClassType(Id calleeClassId))) {
                    error(e, "method call requires an object type");
                    return Type.getError();
                }
                if (this.classTable.getClass_(calleeClassId) == null) {
                    error(e, "undefined class: " + calleeClassId);
                    return Type.getError();
                }

                Tuple.Two<ClassTable.MethodType, Id> methodBinding =
                        this.classTable.getMethod(calleeClassId, methodId.id);
                if (methodBinding == null) {
                    error(e, "method not found: "
                            + calleeClassId + "." + methodId.id);
                    return Type.getError();
                }

                ClassTable.MethodType methodType = methodBinding.first();
                List<Type> formalTypes = methodType.argsType();
                if (actualTypes.size() != formalTypes.size()) {
                    error(e, "wrong number of arguments for method "
                            + methodId.id + ": expected " + formalTypes.size()
                            + ", actual " + actualTypes.size());
                }

                int count = Math.min(actualTypes.size(), formalTypes.size());
                boolean hasArgumentError = false;
                for (int i = 0; i < count; i++) {
                    Type actualType = actualTypes.get(i);
                    Type formalType = formalTypes.get(i);
                    if (!isError(actualType)
                            && !isAssignable(actualType, formalType)) {
                        error(e, "argument " + (i + 1)
                                        + " of method " + methodId.id
                                        + " has the wrong type",
                                formalType,
                                actualType);
                        hasArgumentError = true;
                    }
                }

                calleeTy.set(calleeClassId);
                methodId.freshId = methodBinding.second();
                retTy.set(methodType.retType());
                if (hasArgumentError
                        || actualTypes.size() != formalTypes.size()) {
                    return Type.getError();
                }
                return methodType.retType();
            }
            case Exp.NewObject(Id classId) -> {
                if (this.classTable.getClass_(classId) == null) {
                    error(e, "undefined class: " + classId);
                    return Type.getError();
                }
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
                
                // 二元运算，子错误向上直接传递
                if (isError(resultLeft) || isError(resultRight)) {
                    return Type.getError();
                }

                switch (bop) {
                    case "+", "-" -> {
                        if (Type.nonEquals(resultLeft, Type.getInt()) ||
                                Type.nonEquals(resultRight, Type.getInt())) {
                            Type actual = Type.nonEquals(resultLeft, Type.getInt())
                                    ? resultLeft : resultRight;
                            error(e, bop + " requires integer operands",
                                    Type.getInt(), actual);
                            return Type.getError();
                        }
                        return Type.getInt();
                    }
                    case "*" -> {
                        if (Type.nonEquals(resultLeft, Type.getInt()) ||
                                Type.nonEquals(resultRight, Type.getInt())) {
                            Type actual = Type.nonEquals(resultLeft, Type.getInt())
                                    ? resultLeft : resultRight;
                            error(e, "* requires integer operands",
                                    Type.getInt(), actual);
                            return Type.getError();
                        }
                        return Type.getInt();
                    }
                    case "<" -> {
                        if (Type.nonEquals(resultLeft, Type.getInt()) ||
                                Type.nonEquals(resultRight, Type.getInt())) {
                            Type actual = Type.nonEquals(resultLeft, Type.getInt())
                                    ? resultLeft : resultRight;
                            error(e, "< requires integer operands",
                                    Type.getInt(), actual);
                            return Type.getError();
                        }
                        return Type.getBool();
                    }
                    default -> {
                        error(e, "unknown binary operator: " + bop);
                        return Type.getError();
                    }
                }
            }
            case Exp.ExpId(AstId aid) -> {
                return checkAstId(aid);
            }
            case Exp.This() -> {
                if (this.classTable.getClass_(this.currentClass) == null) {
                    error(e, "this cannot be used in the static main method");
                    return Type.getError();
                }
                return Type.getClassType(this.currentClass);
            }
            case Exp.ArraySelect(Exp array, Exp index) -> {
                Type arrayType = checkExp(array);
                Type indexType = checkExp(index);
                if (isError(arrayType) || isError(indexType)) {
                    return Type.getError();
                }
                if (Type.nonEquals(arrayType, Type.getIntArray())) {
                    error(e, "array selection requires an int[]",
                            Type.getIntArray(), arrayType);
                    return Type.getError();
                }
                if (Type.nonEquals(indexType, Type.getInt())) {
                    error(e, "array index requires an integer",
                            Type.getInt(), indexType);
                    return Type.getError();
                }
                return Type.getInt();
            }
            
            // 返回bool类型的
            case Exp.BopBool(Exp left, String bop, Exp right) -> {
                Type leftType = checkExp(left);
                Type rightType = checkExp(right);
                if (isError(leftType) || isError(rightType)) {
                    return Type.getError();
                }
                if (!bop.equals("&&")) {
                    error(e, "unknown boolean operator: " + bop);
                    return Type.getError();
                }
                if (Type.nonEquals(leftType, Type.getBool())
                        || Type.nonEquals(rightType, Type.getBool())) {
                    Type actual = Type.nonEquals(leftType, Type.getBool())
                            ? leftType : rightType;
                    error(e, "&& requires boolean operands",
                            Type.getBool(), actual);
                    return Type.getError();
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
                if (isError(expType)) {
                    return Type.getError();
                }
                if (!op.equals("!")) {
                    error(e, "unknown unary operator: " + op);
                    return Type.getError();
                }
                if (Type.nonEquals(expType, Type.getBool())) {
                    error(e, "! requires a boolean operand",
                            Type.getBool(), expType);
                    return Type.getError();
                }
                return Type.getBool();
            }

            case Exp.Length(Exp array) -> {
                Type arrayType = checkExp(array);
                if (isError(arrayType)) {
                    return Type.getError();
                }
                if (Type.nonEquals(arrayType, Type.getIntArray())) {
                    error(e, "length requires an int[]",
                            Type.getIntArray(), arrayType);
                    return Type.getError();
                }
                return Type.getInt();
            }
            case Exp.NewIntArray(Exp size) -> {
                Type sizeType = checkExp(size);
                if (isError(sizeType)) {
                    return Type.getError();
                }
                if (Type.nonEquals(sizeType, Type.getInt())) {
                    error(e, "array size requires an integer",
                            Type.getInt(), sizeType);
                    return Type.getError();
                }
                return Type.getIntArray();
            }
            
            default -> {
                error(e, "unsupported expression");
                return Type.getError();
            }
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
                if (!isError(resultCond)
                        && Type.nonEquals(resultCond, Type.getBool())) {
                    error(s, "if requires a boolean condition",
                            Type.getBool(), resultCond);
                }
                checkStm(then_);
                checkStm(else_);
            }
            case Stm.Print(Exp exp) -> {
                var resultExp = checkExp(exp);
                if (!isError(resultExp)
                        && Type.nonEquals(resultExp, Type.getInt())) {
                    error(s, "print requires an integer expression",
                            Type.getInt(), resultExp);
                }
            }
            case Stm.Assign(
                    AstId id,
                    Exp exp
            ) -> {
                // first lookup in the method table
                var resultAstId = checkAstId(id);
                var resultExp = checkExp(exp);
                if (!isError(resultAstId)
                        && !isError(resultExp)
                        && !isAssignable(resultExp, resultAstId)) {
                    error(s, "assignment has incompatible types",
                            resultAstId, resultExp);
                }
            }
            case Stm.AssignArray(AstId id, Exp index, Exp exp) -> {
                Type arrayType = checkAstId(id);
                Type indexType = checkExp(index);
                Type expType = checkExp(exp);

                if (!isError(arrayType)
                        && Type.nonEquals(arrayType, Type.getIntArray())) {
                    error(s, "array assignment requires an int[]",
                            Type.getIntArray(), arrayType);
                }
                if (!isError(indexType)
                        && Type.nonEquals(indexType, Type.getInt())) {
                    error(s, "array index requires an integer",
                            Type.getInt(), indexType);
                }
                if (!isError(expType)
                        && Type.nonEquals(expType, Type.getInt())) {
                    error(s, "array element requires an integer",
                            Type.getInt(), expType);
                }
            }
            case Stm.Block(List<Stm> stms) -> {
                stms.forEach(this::checkStm);
            }
            case Stm.While(Exp cond, Stm body) -> {
                Type condType = checkExp(cond);
                if (!isError(condType)
                        && Type.nonEquals(condType, Type.getBool())) {
                    error(s, "while requires a boolean condition",
                            Type.getBool(), condType);
                }
                checkStm(body);
            }
            default -> error(s, "unsupported statement");
        }
    }

    // check type 检查声明类型是否合法
    public void checkType(Type t) {
        checkType(t, t);
    }

    private void checkType(Type t, Object node) {
        switch (t) {
            case Type.Int(),
                Type.Boolean(),
                Type.IntArray() -> {
            }

            case Type.Error() -> {
            }

            case Type.ClassType(Id classId) -> {
                if (classTable.getClass_(classId) == null) {
                    error(node, "undefined class: " + classId);
                }
            }
        }
    }

    // dec 检查声明是否合法，主要检查他的声明类型
    public void checkDec(Dec d) {
        Dec.Singleton dec = (Dec.Singleton) d;
        checkType(dec.type(), d);
    }

    // method type
    private List<Type> genMethodArgType(List<Dec> decs) {
        return decs.stream().map(Dec::getType).toList();
    }

    // method
    private void checkMethod(Method mtd) {
        Method.Singleton m = (Method.Singleton) mtd;

        // 检查声明
        checkType(m.retType(), mtd);
        m.formals().forEach(this::checkDec);
        m.locals().forEach(this::checkDec);

        // construct the method table
        this.methodTable = new MethodTable();
        this.methodTable.putFormalLocal(m.formals(), m.locals());

        m.stms().forEach(this::checkStm);

        var resultExp = checkExp(m.retExp());

        if (!isError(resultExp)
                && !isAssignable(resultExp, m.retType())) {
            error(mtd, "return type mismatch", m.retType(), resultExp);
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
                error(c, "parent class not found: " + parentId);
            } else {
                cls.parent().set(binding.self());
            }
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
        Ast.Program checkedAst = traceCheckProgram.doit();
        
        // 最后统一结束
        if (this.errorReporter.hasErrors()) {
            System.out.println("type checking result with errors:");
            new PrettyPrinter(this.errorReporter).ppProgram(checkedAst);
            System.out.println(this.errorReporter.errorCount()
                    + " type error(s) found");
            System.exit(1);
        }

        return checkedAst;
    }
}
