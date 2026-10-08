package parser;

import ast.Ast;
import ast.PrettyPrinter;
import lexer.Lexer;
import lexer.Token;
import util.Id;
import util.Trace;
import util.Tuple;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static java.lang.System.exit;

public class Parser {
    String inputFileName;
    BufferedInputStream inputStream;
    Lexer lexer;
    Token current;

    // /////////////////////////////////////////////
    // First set of unterminals
    private final ArrayDeque<Token> lookaheadTokens = new ArrayDeque<>();

    private static final Set<Token.Kind> FIRST_TYPE =
            EnumSet.of(
                    Token.Kind.INT,
                    Token.Kind.BOOLEAN,
                    Token.Kind.ID
            );
    private static final Set<Token.Kind> FIRST_VARDECLARATION = FIRST_TYPE;
    private static final Set<Token.Kind> FIRST_METHODDECLARATION =
            EnumSet.of(Token.Kind.PUBLIC);
    private static final Set<Token.Kind> FIRST_STATEMENT =
            EnumSet.of(
                    Token.Kind.LBRACE,
                    Token.Kind.IF,
                    Token.Kind.WHILE,
                    Token.Kind.SYSTEM,
                    Token.Kind.ID
            );
    private static final Set<Token.Kind> FIRST_EXPRESSION =
            EnumSet.of(
                    Token.Kind.INTEGER_LITERAL,
                    Token.Kind.TRUE,
                    Token.Kind.FALSE,
                    Token.Kind.ID,
                    Token.Kind.THIS,
                    Token.Kind.NEW,
                    Token.Kind.NOT,
                    Token.Kind.LPAREN
            );

    private boolean isVarDeclStart() {
        if (!FIRST_VARDECLARATION.contains(current.kind)) {
            return false;
        }
        if (current.kind != Token.Kind.ID) {
            return true;
        }

        return peekToken(2).kind == Token.Kind.ID;
    }

    private boolean isStatementStart() {
        if (!FIRST_STATEMENT.contains(current.kind)) {
            return false;
        }
        if (current.kind != Token.Kind.ID) {
            return true;
        }

        Token tmp = peekToken(2);

        return tmp.kind == Token.Kind.ASSIGN ||
                tmp.kind == Token.Kind.LBRACKET;
    }

    public Parser(String fileName) {
        this.inputFileName = fileName;
    }

    // /////////////////////////////////////////////
    // utility methods to connect the lexer and the parser.
    private void advance() {
        if (!this.lookaheadTokens.isEmpty()) {
            current = lookaheadTokens.removeFirst();
        }else {
            current = lexer.nextToken();
        }
    }

    private Token peekToken(int n) {
        if(n < 1) {
            throw new IllegalArgumentException("n must be at least 1");
        }
        if (n == 1)
            return current;
        while (this.lookaheadTokens.size() < n-1) {
            lookaheadTokens.addLast(lexer.nextToken());
        }

        Iterator<Token> iterator = lookaheadTokens.iterator();
        Token result = null;

        for (int i = 1; i < n; i++) {
            result = iterator.next();
        }

        return result;
    }

    private String currentLexeme() {
        return current.lexeme.orElseThrow(
                () -> new IllegalStateException(
                        "token " + current.kind + " has no lexeme"
                )
        );
    }

    private Id currentId() {
        if (current.kind != Token.Kind.ID) {
            error("expected ID, but got " + current.kind);
            return null;
        }
        return Id.newName(currentLexeme());
    }

    private void eatToken(Token.Kind kind) {
        if (kind.equals(current.kind)) {
            advance();
            return;
        }
        System.out.println("Expects: " + kind);
        System.out.println("But got: " + current.kind);
        error("syntax error");
    }

    private void error(String errMsg) {
        System.out.println("Error: " + errMsg + ", compilation aborting...\n");
        System.out.println("Lexeme: " + current + "\n");
        System.out.println("rowNum: " + current.rowNum + ", colNum: " + current.colNum + "\n");
        exit(1);
    }

    // ////////////////////////////////////////////////////////////
    // The followings are methods for parsing.

    // A bunch of parsing methods to parse expressions.
    // The messy parts are to deal with precedence and associativity.

    // ExpList -> Exp ExpRest*
    // ->
    // ExpRest -> , Exp
    private List<Ast.Exp> parseExpList() {
        List<Ast.Exp> expressions = new ArrayList<>();

        if (current.kind == Token.Kind.RPAREN) {
            return expressions;
        }

        expressions.add(parseExp());
        while (current.kind == Token.Kind.COMMA) {
            eatToken(Token.Kind.COMMA);
            expressions.add(parseExp());
        }

        return expressions;
    }

    // AtomExp -> (exp)
    //         -> INTEGER_LITERAL
    //         -> true
    //         -> false
    //         -> this
    //         -> id
    //         -> new int [exp]
    //         -> new id ()
    private Ast.Exp parseAtomExp() {
        switch (current.kind) {
            case LPAREN -> {
                eatToken(Token.Kind.LPAREN);
                Ast.Exp expression = parseExp();
                eatToken(Token.Kind.RPAREN);
                return expression;
            }
            case INTEGER_LITERAL -> {
                int value;
                try {
                    value = Integer.parseInt(currentLexeme());
                } catch (NumberFormatException exception) {
                    error("integer literal is outside the range of int");
                    return null;
                }
                eatToken(Token.Kind.INTEGER_LITERAL);
                return new Ast.Exp.Num(value);
            }
            case TRUE -> {
                eatToken(Token.Kind.TRUE);
                return new Ast.Exp.True();
            }
            case FALSE -> {
                eatToken(Token.Kind.FALSE);
                return new Ast.Exp.False();
            }
            case THIS -> {
                eatToken(Token.Kind.THIS);
                return new Ast.Exp.This();
            }
            case ID -> {
                Id id = currentId();
                eatToken(Token.Kind.ID);
                return new Ast.Exp.ExpId(new Ast.AstId(id));
            }
            case NEW -> {
                eatToken(Token.Kind.NEW);

                if (current.kind == Token.Kind.INT) {
                    eatToken(Token.Kind.INT);
                    eatToken(Token.Kind.LBRACKET);
                    Ast.Exp size = parseExp();
                    eatToken(Token.Kind.RBRACKET);
                    return new Ast.Exp.NewIntArray(size);
                }

                if (current.kind == Token.Kind.ID) {
                    Id classId = currentId();
                    eatToken(Token.Kind.ID);
                    eatToken(Token.Kind.LPAREN);
                    eatToken(Token.Kind.RPAREN);
                    return new Ast.Exp.NewObject(classId);
                }

                error("expected int or a class name after new");
                return null;
            }
            default -> {
                error("expected an atomic expression, but got " + current.kind);
                return null;
            }
        }
    }

    // NotExp -> AtomExp
    //        -> AtomExp .id (expList)
    //        -> AtomExp [exp]
    //        -> AtomExp .length
    private Ast.Exp parseNotExp() {
        Ast.Exp expression = parseAtomExp();

        while (current.kind == Token.Kind.DOT
                || current.kind == Token.Kind.LBRACKET) {
            if (current.kind == Token.Kind.LBRACKET) {
                eatToken(Token.Kind.LBRACKET);
                Ast.Exp index = parseExp();
                eatToken(Token.Kind.RBRACKET);
                expression = new Ast.Exp.ArraySelect(expression, index);
                continue;
            }

            eatToken(Token.Kind.DOT);
            if (current.kind == Token.Kind.LENGTH) {
                eatToken(Token.Kind.LENGTH);
                expression = new Ast.Exp.Length(expression);
                continue;
            }

            if (current.kind == Token.Kind.ID) {
                Id methodId = currentId();
                eatToken(Token.Kind.ID);
                eatToken(Token.Kind.LPAREN);
                List<Ast.Exp> arguments = parseExpList();
                eatToken(Token.Kind.RPAREN);
                expression = new Ast.Exp.Call(
                        expression,
                        new Ast.AstId(methodId),
                        arguments,
                        new Tuple.One<>(),
                        new Tuple.One<>()
                );
                continue;
            }

            error("expected length or a method name after '.'");
            return null;
        }

        return expression;
    }

    // TimesExp -> ! TimesExp
    // -> NotExp
    private Ast.Exp parseTimesExp() {
        if (current.kind == Token.Kind.NOT) {
            eatToken(Token.Kind.NOT);
            return new Ast.Exp.Uop("!", parseTimesExp());
        }
        return parseNotExp();
    }

    // AddSubExp -> TimesExp * TimesExp
    //           -> TimesExp
    private Ast.Exp parseAddSubExp() {
        Ast.Exp left = parseTimesExp();

        while (current.kind == Token.Kind.TIMES) {
            eatToken(Token.Kind.TIMES);
            Ast.Exp right = parseTimesExp();
            left = new Ast.Exp.Bop(left, "*", right);
        }
        return left;
    }

    // LtExp -> AddSubExp + AddSubExp
    //       -> AddSubExp - AddSubExp
    //       -> AddSubExp
    private Ast.Exp parseLtExp() {
        Ast.Exp left = parseAddSubExp();

        while (current.kind == Token.Kind.ADD
                || current.kind == Token.Kind.MINUS) {
            String operator;
            if (current.kind == Token.Kind.ADD) {
                operator = "+";
                eatToken(Token.Kind.ADD);
            } else {
                operator = "-";
                eatToken(Token.Kind.MINUS);
            }

            Ast.Exp right = parseAddSubExp();
            left = new Ast.Exp.Bop(left, operator, right);
        }
        return left;
    }

    // AndExp -> LtExp < LtExp
    // -> LtExp
    private Ast.Exp parseAndExp() {
        Ast.Exp left = parseLtExp();

        if (current.kind == Token.Kind.LT) {
            eatToken(Token.Kind.LT);
            Ast.Exp right = parseLtExp();
            left = new Ast.Exp.Bop(left, "<", right);
        }
        return left;
    }

    // Exp -> AndExp && AndExp
    //     -> AndExp
    // 这些用了优先级
    private Ast.Exp parseExp() {
        Ast.Exp left = parseAndExp();

        while (current.kind == Token.Kind.AND) {
            eatToken(Token.Kind.AND);
            Ast.Exp right = parseAndExp();
            left = new Ast.Exp.BopBool(left, "&&", right);
        }
        return left;
    }

    // Statement -> { Statement* }
    // -> if ( Exp ) Statement else Statement
    // -> while ( Exp ) Statement
    // -> System.out.println ( Exp ) ;
    // -> id = Exp ;
    // -> id [ Exp ]= Exp ;
    private Ast.Stm parseStatement() {
        switch (current.kind) {
            case LBRACE -> {
                eatToken(Token.Kind.LBRACE);
                List<Ast.Stm> statements = parseStatements();
                eatToken(Token.Kind.RBRACE);
                return new Ast.Stm.Block(statements);
            }
            case IF -> {
                eatToken(Token.Kind.IF);
                eatToken(Token.Kind.LPAREN);
                Ast.Exp condition = parseExp();
                eatToken(Token.Kind.RPAREN);
                Ast.Stm thenBranch = parseStatement();
                eatToken(Token.Kind.ELSE);
                Ast.Stm elseBranch = parseStatement();
                return new Ast.Stm.If(condition, thenBranch, elseBranch);
            }
            case WHILE -> {
                eatToken(Token.Kind.WHILE);
                eatToken(Token.Kind.LPAREN);
                Ast.Exp condition = parseExp();
                eatToken(Token.Kind.RPAREN);
                Ast.Stm body = parseStatement();
                return new Ast.Stm.While(condition, body);
            }
            case SYSTEM -> {
                eatToken(Token.Kind.SYSTEM);
                eatToken(Token.Kind.DOT);
                eatToken(Token.Kind.OUT);
                eatToken(Token.Kind.DOT);
                eatToken(Token.Kind.PRINTLN);
                eatToken(Token.Kind.LPAREN);
                Ast.Exp expression = parseExp();
                eatToken(Token.Kind.RPAREN);
                eatToken(Token.Kind.SEMICOLON);
                return new Ast.Stm.Print(expression);
            }
            case ID -> {
                Ast.AstId astId = new Ast.AstId(currentId());
                eatToken(Token.Kind.ID);

                if (current.kind == Token.Kind.ASSIGN) {
                    eatToken(Token.Kind.ASSIGN);
                    Ast.Exp expression = parseExp();
                    eatToken(Token.Kind.SEMICOLON);
                    return new Ast.Stm.Assign(astId, expression);
                }

                if (current.kind == Token.Kind.LBRACKET) {
                    eatToken(Token.Kind.LBRACKET);
                    Ast.Exp index = parseExp();
                    eatToken(Token.Kind.RBRACKET);
                    eatToken(Token.Kind.ASSIGN);
                    Ast.Exp expression = parseExp();
                    eatToken(Token.Kind.SEMICOLON);
                    return new Ast.Stm.AssignArray(astId, index, expression);
                }

                error("expected '=' or '[' after an identifier");
                return null;
            }
            default -> {
                error("wrong token in Statement: " + current.kind);
                return null;
            }
        }
    }

    private List<Ast.Stm> parseStatements() {
        List<Ast.Stm> statements = new ArrayList<>();
        while (isStatementStart()) {
            statements.add(parseStatement());
        }
        return statements;
    }

    // Type -> int []
    // -> boolean
    // -> int
    // -> id
    private Ast.Type parseType() {
        switch (current.kind) {
            case INT -> {
                eatToken(Token.Kind.INT);
                if (current.kind == Token.Kind.LBRACKET) {
                    eatToken(Token.Kind.LBRACKET);
                    eatToken(Token.Kind.RBRACKET);
                    return Ast.Type.getIntArray();
                }
                return Ast.Type.getInt();
            }
            case BOOLEAN -> {
                eatToken(Token.Kind.BOOLEAN);
                return Ast.Type.getBool();
            }
            case ID -> {
                Id classId = currentId();
                eatToken(Token.Kind.ID);
                return Ast.Type.getClassType(classId);
            }
            default -> {
                error("wrong token in Type: " + current.kind);
                return null;
            }
        }
    }

    // VarDecl -> Type id ;
    private Ast.Dec parseVarDecl() {
        Ast.Type type = parseType();
        Id id = currentId();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.SEMICOLON);
        return new Ast.Dec.Singleton(type, new Ast.AstId(id));
    }

    private List<Ast.Dec> parseVarDecls() {
        List<Ast.Dec> declarations = new ArrayList<>();
        while (isVarDeclStart()) {
            declarations.add(parseVarDecl());
        }
        return declarations;
    }

    // FormalList -> Type id FormalRest*
    // ->
    // FormalRest -> , Type id
    private List<Ast.Dec> parseFormalList() {
        List<Ast.Dec> formals = new ArrayList<>();

        Ast.Type firstType = parseType();
        Id firstId = currentId();
        eatToken(Token.Kind.ID);
        formals.add(new Ast.Dec.Singleton(firstType, new Ast.AstId(firstId)));

        while (current.kind == Token.Kind.COMMA) {
            eatToken(Token.Kind.COMMA);
            Ast.Type type = parseType();
            Id id = currentId();
            eatToken(Token.Kind.ID);
            formals.add(new Ast.Dec.Singleton(type, new Ast.AstId(id)));
        }

        return formals;
    }

    // Method -> public Type id ( FormalList )
    // { VarDecl* Statement* return Exp ;}
    private Ast.Method parseMethod() {
        eatToken(Token.Kind.PUBLIC);
        Ast.Type returnType = parseType();
        Id methodId = currentId();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LPAREN);

        List<Ast.Dec> formals;
        if (FIRST_TYPE.contains(current.kind)) {
            formals = parseFormalList();
        } else {
            formals = new ArrayList<>();
        }

        eatToken(Token.Kind.RPAREN);
        eatToken(Token.Kind.LBRACE);
        List<Ast.Dec> locals = parseVarDecls();
        List<Ast.Stm> statements = parseStatements();
        eatToken(Token.Kind.RETURN);
        Ast.Exp returnExpression = parseExp();
        eatToken(Token.Kind.SEMICOLON);
        eatToken(Token.Kind.RBRACE);

        return new Ast.Method.Singleton(
                returnType,
                new Ast.AstId(methodId),
                formals,
                locals,
                statements,
                returnExpression
        );
    }

    private List<Ast.Method> parseMethodDecls() {
        List<Ast.Method> methods = new ArrayList<>();
        while (FIRST_METHODDECLARATION.contains(current.kind)) {
            methods.add(parseMethod());
        }
        return methods;
    }

    // ClassDecl -> class id { VarDecl* MethodDecl* }
    //           -> class id extends id { VarDecl* MethodDecl* }
    private Ast.Class parseClassDecl() {
        eatToken(Token.Kind.CLASS);
        Id classId = currentId();
        eatToken(Token.Kind.ID);

        Id parentId = null;
        if (current.kind == Token.Kind.EXTENDS) {
            eatToken(Token.Kind.EXTENDS);
            parentId = currentId();
            eatToken(Token.Kind.ID);
        }

        eatToken(Token.Kind.LBRACE);
        List<Ast.Dec> declarations = parseVarDecls();
        List<Ast.Method> methods = parseMethodDecls();
        eatToken(Token.Kind.RBRACE);

        return new Ast.Class.Singleton(
                classId,
                parentId,
                declarations,
                methods,
                new Tuple.One<>()
        );
    }

    private List<Ast.Class> parseClassDecls() {
        List<Ast.Class> classes = new ArrayList<>();
        while (current.kind == Token.Kind.CLASS) {
            classes.add(parseClassDecl());
        }
        return classes;
    }

    // MainClass -> class id {
    //   public static void main ( String [] id ) {
    //     Statement
    //   }
    // }
    private Ast.MainClass parseMainClass() {
        eatToken(Token.Kind.CLASS);
        Id classId = currentId();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LBRACE);
        eatToken(Token.Kind.PUBLIC);
        eatToken(Token.Kind.STATIC);
        eatToken(Token.Kind.VOID);
        eatToken(Token.Kind.MAIN);
        eatToken(Token.Kind.LPAREN);
        eatToken(Token.Kind.STRING);
        eatToken(Token.Kind.LBRACKET);
        eatToken(Token.Kind.RBRACKET);

        Id argumentId = currentId();
        eatToken(Token.Kind.ID);

        eatToken(Token.Kind.RPAREN);
        eatToken(Token.Kind.LBRACE);
        Ast.Stm statement = parseStatement();
        eatToken(Token.Kind.RBRACE);
        eatToken(Token.Kind.RBRACE);

        return new Ast.MainClass.Singleton(
                classId,
                new Ast.AstId(argumentId),
                statement
        );
    }

    // Program -> MainClass ClassDecl*
    private Ast.Program parseProgram(Object ignored) {
        Ast.MainClass mainClass = parseMainClass();
        List<Ast.Class> classes = parseClassDecls();
        eatToken(Token.Kind.EOF);
        return new Ast.Program.Singleton(mainClass, classes);
    }

    private void initParser() {
        try {
            inputStream = new BufferedInputStream(
                    new FileInputStream(inputFileName)
            );
        } catch (Exception e) {
            error("unable to open file " + inputFileName);
        }

        lexer = new Lexer(inputFileName, inputStream);
        current = lexer.nextToken();
    }

    private void finalizeParser() {
        try {
            inputStream.close();
        } catch (Exception e) {
            error("unable to close file");
        }
    }

    public Ast.Program parse() {
        initParser();
        Trace<Object, Ast.Program> trace =
                new Trace<>(
                        "parser.Parser.parse",
                        this::parseProgram,
                        inputFileName,
                        s -> System.out.println("parsing: " + s),
                        new PrettyPrinter()::ppProgram
                );
        Ast.Program ast = trace.doit();
        finalizeParser();
        return ast;
    }
}
