package ast;

import util.Id;
import util.Tuple;

import java.util.HashMap;
import java.util.List;


public class Ast {
    // /////////////////////////////////////////////////////////
    // ast-id
    public static class AstId {
        public Id id;
        public Id freshId;
        public Type type;
        public boolean isClassField;

        public AstId(Id id) {
            this.id = id;
            // set the initial value to "id"
            this.freshId = id;
            this.type = null;
            this.isClassField = false;
        }

        public Id genFreshId() {
            this.freshId = this.id.newSameOrigName();
            return this.freshId;
        }
    }

    //  ///////////////////////////////////////////////////////////
    //  type 新增错误类型，用于错误类型向上层传播，避免空指针和连锁误报。
    public sealed interface Type
                permits Type.Boolean, Type.ClassType, Type.Error, Type.Int, Type.IntArray {
        
        // Error
        public record Error() implements Type {
        }

        // boolean
        public record Boolean() implements Type {
        }

        // class "id"
        public record ClassType(Id id) implements Type {
        }

        // int
        public record Int() implements Type {
        }

        // int[]
        public record IntArray() implements Type {
        }

        // singleton design pattern
        Type boolTy = new Type.Boolean();
        Type errorTy = new Type.Error();
        Type intTy = new Type.Int();
        Type intArrayTy = new Type.IntArray();
        HashMap<Id, Type> classTyContainer = new HashMap<>();

        public static Type getInt() {
            return intTy;
        }

        public static Type getBool() {
            return boolTy;
        }

        public static Type getError() {
            return errorTy;
        }

        public static Type getIntArray() {
            return intArrayTy;
        }

        public static Type getClassType(Id id) {
            Type ty = classTyContainer.get(id);
            if (ty == null) {
                ty = new ClassType(id);
                classTyContainer.put(id, ty);
            }
            return ty;
        }

        // do not confuse with the "equals" method from Object.
        public static boolean nonEquals(Type ty1, Type ty2) {
            // compare the two references' value
            return ty1 != ty2;
        }

        public static void output(Type ty) {
            switch (ty) {
                case Type.Boolean() -> System.out.println("boolean");
                case Type.ClassType(Id id) -> System.out.print(id);
                case Type.Error() -> System.out.print("<error>");
                case Type.Int() -> System.out.print("int");
                case Type.IntArray() -> System.out.print("int[]");
            }
        }

        public static String convertString(Type ty) {
            switch (ty) {
                case Type.Boolean() -> {
                    return "boolean";
                }
                case Type.ClassType(Id id) -> {
                    return id.toString();
                }
                case Type.Error() -> {
                    return "<error>";
                }
                case Type.Int() -> {
                    return "int";
                }
                case Type.IntArray() -> {
                    return "int[]";
                }
            }
        }
    }

    // ///////////////////////////////////////////////////
    // declaration
    public sealed interface Dec
                permits Dec.Singleton {


        public record Singleton(Type type,
                                AstId aid) implements Dec {
        }

        public static Type getType(Dec dec) {
            switch (dec) {
                case Singleton(Type type, _) -> {
                    return type;
                }
            }
        }
    }


    // /////////////////////////////////////////////////////////
    // expression
    public sealed interface Exp
        // alphabetically-ordered
                permits Exp.ArraySelect, Exp.Bop, Exp.BopBool, Exp.Call, Exp.ExpId,
                        Exp.False, Exp.Length, Exp.NewIntArray,
                        Exp.NewObject, Exp.Num, Exp.This, Exp.True, Exp.Uop {


        // ArraySelect
        public record ArraySelect(Exp array,
                                  Exp index) implements Exp {
        }

        // binary operations
        public record Bop(Exp left,
                          String op,
                          Exp right) implements Exp {
        }

        // op is a boolean operator
        public record BopBool(Exp left,
                              String op,
                              Exp right) implements Exp {
        }

        // Call
        public record Call(Exp exp,  // 接收者对象表达式，点号左边的exp
                           AstId methodId,
                           List<Exp> args,
                           // type of object "exp"
                           // we use "Id" instead of "Type", as it must be class
                           Tuple.One<Id> theObjectType,
                           Tuple.One<Type> retType) implements Exp {
        }

        // ExpId
        public record ExpId(AstId id) implements Exp {
        }

        // False
        public record False() implements Exp {
        }

        // length
        public record Length(Exp array) implements Exp {
        }

        // new int [e]
        public record NewIntArray(Exp exp) implements Exp {
        }

        // new A();
        public record NewObject(Id id) implements Exp {
        }

        // number
        public record Num(int num) implements Exp {
        }

        // this
        public record This() implements Exp {
        }

        // True
        public record True() implements Exp {
        }

        // !
        public record Uop(String op,
                          Exp exp) implements Exp {
        }
    }
    // end of expression

    // /////////////////////////////////////////////////////////
    // statement
    public sealed interface  Stm
        // alphabetically-ordered
                permits Stm.Assign, Stm.AssignArray, Stm.Block, Stm.If,
                        Stm.Print, Stm.While {

        // assign: id = exp;
        public record Assign(AstId aid,
                             Exp exp) implements Stm {
        }

        // assign-array: id[exp] = exp
        public record AssignArray(AstId id,
                                  Exp index,
                                  Exp exp) implements Stm {
        }

        // block
        public record Block(List<Stm> stms) implements Stm {
        }

        // if
        public record If(Exp cond,
                         Stm thenn,
                         Stm elsee) implements Stm {
        }

        // System.out.println
        public record Print(Exp exp) implements Stm {
        }

        // while
        public record While(Exp cond,
                            Stm body) implements Stm {
        }
    }
    // end of statement

    // /////////////////////////////////////////////////////////
    // method
    public sealed interface Method
                permits Method.Singleton {

        public record Singleton(Type retType,
                                AstId methodId,
                                List<Dec> formals,
                                List<Dec> locals,
                                List<Stm> stms,
                                Exp retExp) implements Method {
        }
    }

    // class
    public sealed interface Class
                permits Class.Singleton {

        public record Singleton(Id classId,
                                Id extends_, // "null" for non-existing "extends"
                                List<Dec> decs,
                                List<ast.Ast.Method> methods,
                                // contain null element for non-existing parent
                                Tuple.One<Class> parent) implements Class {
        }
    }

    // main class
    public sealed interface MainClass
                permits MainClass.Singleton {

        public record Singleton(Id classId,
                                AstId arg,
                                Stm stm) implements MainClass {
        }
    }

    // whole program
    public sealed interface Program
                permits Program.Singleton {

        public record Singleton(MainClass mainClass,
                                List<Class> classes) implements Program {
        }
    }
}
