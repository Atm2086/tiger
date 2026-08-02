package checker;

import ast.Ast;
import ast.Ast.Type;
import util.Id;
import util.Tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ClassTable {

    private static void error(String msg) {
        System.out.println(msg);
        System.exit(1);
    }

    // a special type for method: argument and return types
    public record MethodType(Type retType,
                             List<Ast.Type> argsType) {
        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("(");
            for (Type type : this.argsType) {
                stringBuilder.append(type.toString() + ", ");
            }
            stringBuilder.append(") -> " + this.retType.toString());
            return stringBuilder.toString();
        }
    }

    // the binding for a class
    public record Binding(
            // null for empty extends
            Id extends_, // 父类
            Ast.Class self, // 自己的AST树
            // the field in a class: its type and fresh id
            java.util.HashMap<Id, Tuple.Two<Type, Id>> fields,
            // the method in a class: its type and fresh id
            java.util.HashMap<Id, Tuple.Two<MethodType, Id>> methods) {

        public void putField(Id fieldId, Type type, Id freshId) {
            if (this.fields.get(fieldId) != null) {
                error("duplicated class field: " + fieldId);
            }
            this.fields.put(fieldId, new Tuple.Two<>(type, freshId));
        }

        public void putMethod(Id mid, MethodType methodType, Id freshId) {
            if (this.methods.get(mid) != null) {
                error("duplicated class method: " + mid);
            }
            this.methods.put(mid, new Tuple.Two<>(methodType, freshId));
        }

        @Override
        public String toString() {
            System.out.print("extends: ");
            System.out.println(Objects.requireNonNullElse(this.extends_, "<>"));
            System.out.println("\nfields:\n  ");
            System.out.println(fields.toString());
            System.out.println("\nmethods:\n  ");
            System.out.println(methods.toString());
            return "";
        }
    }

    // map each class, to its corresponding class binding.
    // 这个是主要的成员
    private final java.util.HashMap<Id, Binding> classTable;

    public ClassTable() {
        this.classTable = new java.util.HashMap<>();
    }

    // Duplication is not allowed
    public void putClass(Id classId, Id extends_, Ast.Class self) {
        if (this.classTable.get(classId) != null) {
            error("duplicated class: " + classId);
        }
        Binding classBinding = new Binding(extends_,
                self,
                new HashMap<>(),
                new HashMap<>());
        this.classTable.put(classId, classBinding);
    }

    // put a field into class table
    // Duplication is not allowed
    public void putField(Id classId, Ast.AstId fieldId, Type type) {
        Binding classBinding = this.classTable.get(classId);
        Id freshId = fieldId.genFreshId();
        classBinding.putField(fieldId.id, type, freshId);
    }

    // put a method into class table
    // Duplication is not allowed.
    // Also note that MiniJava does NOT allow overloading.
    public void putMethod(Id classId, Ast.AstId methodId, MethodType type) {
        Binding classBinding = this.classTable.get(classId);
        Id freshId = methodId.genFreshId();
        classBinding.putMethod(methodId.id, type, freshId);
    }

    // return null for non-existing class
    // "getClass" is a library API, so we use "getClass_"
    public Binding getClass_(Id classId) {
        return this.classTable.get(classId);
    }

    // get type of some field
    // return null for non-existing field.
    public Tuple.Two<Type, Id> getField(Id classId, Id fieldId) {
        Binding classBinding = this.classTable.get(classId);
        var result = classBinding.fields.get(fieldId);
        while (result == null) { // search all parent classes until found or fail
            if (classBinding.extends_ == null)
                return null;
            classBinding = this.classTable.get(classBinding.extends_);
            result = classBinding.fields.get(fieldId);
        }
        return result;
    }

    // get type of given method
    // return null for non-existing method
    public Tuple.Two<MethodType, Id> getMethod(Id classId, Id methodId) {
        Binding classBinding = this.classTable.get(classId);
        var result = classBinding.methods.get(methodId);
        while (result == null) { // search all parent classes until found or fail
            if (classBinding.extends_ == null)
                return null;
            classBinding = this.classTable.get(classBinding.extends_);
            result = classBinding.methods.get(methodId);
        }
        return result;
    }

    // lab 2, exercise 7:
    public void dump() {
        System.out.println("ClassTable:");

        if (this.classTable.isEmpty()) {
            System.out.println("  <empty>");
            return;
        }

        this.classTable.entrySet().stream()
                .sorted((left, right) -> left.getKey().toString()
                        .compareTo(right.getKey().toString()))
                .forEach(classEntry -> {
                    Id classId = classEntry.getKey();
                    Binding binding = classEntry.getValue();

                    System.out.println("  class " + classId);
                    System.out.println("    extends: "
                            + Objects.requireNonNullElse(binding.extends_(), "<none>"));

                    System.out.println("    fields:");
                    if (binding.fields().isEmpty()) {
                        System.out.println("      <empty>");
                    } else {
                        binding.fields().entrySet().stream()
                                .sorted((left, right) -> left.getKey().toString()
                                        .compareTo(right.getKey().toString()))
                                .forEach(fieldEntry -> {
                                    Tuple.Two<Type, Id> field = fieldEntry.getValue();
                                    System.out.println("      " + fieldEntry.getKey()
                                            + " : " + formatType(field.first())
                                            + " (fresh: " + field.second() + ")");
                                });
                    }

                    System.out.println("    methods:");
                    if (binding.methods().isEmpty()) {
                        System.out.println("      <empty>");
                    } else {
                        binding.methods().entrySet().stream()
                                .sorted((left, right) -> left.getKey().toString()
                                        .compareTo(right.getKey().toString()))
                                .forEach(methodEntry -> {
                                    Tuple.Two<MethodType, Id> method = methodEntry.getValue();
                                    System.out.println("      " + methodEntry.getKey()
                                            + " : " + formatMethodType(method.first())
                                            + " (fresh: " + method.second() + ")");
                                });
                    }
                });
    }

    static String formatType(Type type) {
        return switch (type) {
            case Type.Boolean() -> "boolean";
            case Type.ClassType(Id id) -> id.toString();
            case Type.Int() -> "int";
            case Type.IntArray() -> "int[]";
        };
    }

    private static String formatMethodType(MethodType methodType) {
        StringBuilder result = new StringBuilder("(");
        for (int i = 0; i < methodType.argsType().size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(formatType(methodType.argsType().get(i)));
        }
        result.append(") -> ");
        result.append(formatType(methodType.retType()));
        return result.toString();
    }

    @Override
    public String toString() {
        return this.classTable.toString();
    }
}







