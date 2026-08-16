package util;

import ast.Ast;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

// 收集错误，定义节点保存 节点和错误的关系
public class ErrorReporter {
    public record Diagnostic(
            Object node, // AST node
            String message, //  描述
            Ast.Type expected, // 期望类型和实际类型
            Ast.Type actual) {
    }

    private final IdentityHashMap<Object, List<Diagnostic>> errorsByNode;
    private final List<Diagnostic> errors;

    public ErrorReporter() {
        this.errorsByNode = new IdentityHashMap<>();
        this.errors = new ArrayList<>();
    }

    public void report(Object node, String message) {
        report(node, message, null, null);
    }

    public void report(Object node,
                       String message,
                       Ast.Type expected,
                       Ast.Type actual) {
        Diagnostic diagnostic =
                new Diagnostic(node, message, expected, actual);
        this.errors.add(diagnostic);
        this.errorsByNode
                .computeIfAbsent(node, ignored -> new ArrayList<>())
                .add(diagnostic);
    }

    public List<Diagnostic> getErrors(Object node) {
        List<Diagnostic> result = this.errorsByNode.get(node);
        return result == null ? List.of() : List.copyOf(result);
    }

    public int errorCount() {
        return this.errors.size();
    }

    public boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    public static String formatType(Ast.Type type) {
        if (type == null) {
            return "<unknown>";
        }

        return switch (type) {
            case Ast.Type.Boolean() -> "boolean";
            case Ast.Type.ClassType(Id id) -> id.toString();
            case Ast.Type.Error() -> "<error>";
            case Ast.Type.Int() -> "int";
            case Ast.Type.IntArray() -> "int[]";
        };
    }
}
