package parser;

import ast.Ast;
import ast.PrettyPrinter;
import lexer.Lexer;
import lexer.Token;
import util.Todo;
import util.Trace;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.EnumSet;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Iterator;

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
            EnumSet.of(
                    Token.Kind.PUBLIC
            );
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
    private void parseExpList() {
        if (current.kind.equals(Token.Kind.RPAREN))
            return;
        parseExp();
        while (current.kind.equals(Token.Kind.COMMA)) {
            advance();
            parseExp();
        }
    }

    // AtomExp -> (exp)
    //         -> INTEGER_LITERAL
    //         -> true
    //         -> false
    //         -> this
    //         -> id
    //         -> new int [exp]
    //         -> new id ()
    private void parseAtomExp() {
        switch (current.kind) {
            case LPAREN:
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                return;
            case INTEGER_LITERAL:
                eatToken(Token.Kind.INTEGER_LITERAL);
                return;
            case TRUE:
                eatToken(Token.Kind.TRUE);
                return;
            case FALSE:
                eatToken(Token.Kind.FALSE);
                return;
            case THIS:
                eatToken(Token.Kind.THIS);
                return;
            case ID:
                eatToken(Token.Kind.ID);
                return;
            case NEW: {
                eatToken(Token.Kind.NEW);
                switch (current.kind) {
                    case INT:
                        eatToken(Token.Kind.INT);
                        eatToken(Token.Kind.LBRACKET);
                        parseExp();
                        eatToken(Token.Kind.RBRACKET);
                        return;
                    case ID:
                        eatToken(Token.Kind.ID);
                        eatToken(Token.Kind.LPAREN);
                        eatToken(Token.Kind.RPAREN);
                        return;
                    default:
                        error("expected an atomic expression, but got " + current.kind);
                }
            }
            default:
                error("expected an atomic expression, but got " + current.kind);
        }
    }

    // NotExp -> AtomExp
    //        -> AtomExp .id (expList)
    //        -> AtomExp [exp]
    //        -> AtomExp .length
    private void parseNotExp() {
        parseAtomExp();
        switch (current.kind) {
            case Token.Kind.DOT -> {
                eatToken(Token.Kind.DOT);
                switch (current.kind) {
                    case Token.Kind.ID -> {
                        eatToken(Token.Kind.ID);
                        eatToken(Token.Kind.LPAREN);
                        parseExpList();
                        eatToken(Token.Kind.RPAREN);
                    }
                    case Token.Kind.LENGTH -> {
                        eatToken(Token.Kind.LENGTH);
                    }
                }
            }
            case Token.Kind.LBRACKET -> {
                eatToken(Token.Kind.LBRACKET);
                parseExp();
                eatToken(Token.Kind.RBRACKET);
            }
        }
    }

    // TimesExp -> ! TimesExp
    // -> NotExp
    private void parseTimesExp() {
        if (current.kind.equals(Token.Kind.NOT)) {
            eatToken(Token.Kind.NOT);
            parseTimesExp();
            return;
        }
        parseNotExp();
    }

    // AddSubExp -> TimesExp * TimesExp
    //           -> TimesExp
    private void parseAddSubExp() {
        parseTimesExp();
        while (current.kind.equals(Token.Kind.TIMES)) {
            eatToken(Token.Kind.TIMES);
            parseTimesExp();
        }
    }

    // LtExp -> AddSubExp + AddSubExp
    //       -> AddSubExp - AddSubExp
    //       -> AddSubExp
    private void parseLtExp() {
        parseAddSubExp();
        while (current.kind.equals(Token.Kind.ADD) || current.kind.equals(Token.Kind.MINUS)) {
            switch(current.kind) {
                case Token.Kind.ADD -> {
                    eatToken(Token.Kind.ADD);
                }

                case Token.Kind.MINUS -> {
                    eatToken(Token.Kind.MINUS);
                }
            }
            parseAddSubExp();
        }
    }

    // AndExp -> LtExp < LtExp
    // -> LtExp
    private void parseAndExp() {
        parseLtExp();
        if (current.kind.equals(Token.Kind.LT)) {
            eatToken(Token.Kind.LT);
            parseLtExp();
        }
    }

    // Exp -> AndExp && AndExp
    //     -> AndExp
    // 这些用了优先级
    private void parseExp() {
        parseAndExp();
        while (current.kind.equals(Token.Kind.AND)) {
            eatToken(Token.Kind.AND);
            parseAndExp();
        }
    }

    // Statement -> { Statement* }
    // -> if ( Exp ) Statement else Statement
    // -> while ( Exp ) Statement
    // -> System.out.println ( Exp ) ;
    // -> id = Exp ;
    // -> id [ Exp ]= Exp ;
    private void parseStatement() {
        // to parse a statement.
        switch (current.kind) {
            case Token.Kind.LBRACE -> {
                eatToken(Token.Kind.LBRACE);
                parseStatements();
                eatToken(Token.Kind.RBRACE);
            }
            case Token.Kind.IF -> {
                eatToken(Token.Kind.IF);
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                parseStatement();
                eatToken(Token.Kind.ELSE);
                parseStatement();
            }
            case Token.Kind.WHILE -> {
                eatToken(Token.Kind.WHILE);
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                parseStatement();
            }
            case Token.Kind.SYSTEM -> {
                eatToken(Token.Kind.SYSTEM);
                eatToken(Token.Kind.DOT);
                eatToken(Token.Kind.OUT);
                eatToken(Token.Kind.DOT);
                eatToken(Token.Kind.PRINTLN);
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                eatToken(Token.Kind.SEMICOLON);
            }
            case Token.Kind.ID -> {
                eatToken(Token.Kind.ID);
                if (current.kind.equals(Token.Kind.ASSIGN)) {
                    eatToken(Token.Kind.ASSIGN);
                }else {
                    eatToken(Token.Kind.LBRACKET);
                    parseExp();
                    eatToken(Token.Kind.RBRACKET);
                    eatToken(Token.Kind.ASSIGN);
                }
                parseExp();
                eatToken(Token.Kind.SEMICOLON);
            }
            default -> {
                error("wrong token in STATEMENT");
            }
        }
    }

    // Statements -> Statement Statements
    // ->
    private void parseStatements() {
        while (isStatementStart()) {
            parseStatement();
        }
    }

    // Type -> int []
    // -> boolean
    // -> int
    // -> id
    private void parseType() {
        // to parse a type.
        switch (current.kind) {
            case Token.Kind.INT -> {
                eatToken(Token.Kind.INT);
                if (current.kind.equals(Token.Kind.LBRACKET)) {
                    eatToken(Token.Kind.LBRACKET);
                    eatToken(Token.Kind.RBRACKET);
                }
            }
            case Token.Kind.BOOLEAN -> {
                eatToken(Token.Kind.BOOLEAN);
            }
            case Token.Kind.ID -> {
                eatToken(Token.Kind.ID);
            }
            default -> {
                error("wrong token in TYPE");
            }
        }
    }

    // VarDecl -> Type id ;
    private void parseVarDecl()  {
        // to parse the "Type" non-terminal in this method,
        // instead of writing a fresh one.
        parseType();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.SEMICOLON);
    }

    // VarDecls -> VarDecl VarDecls
    // ->
    private void parseVarDecls()  {
        while (isVarDeclStart()) {
            parseVarDecl();
        }
    }

    // FormalList -> Type id FormalRest*
    // ->
    // FormalRest -> , Type id
    // 用在methoddeclaration里的一串
    private void parseFormalList() {
        parseType();
        eatToken(Token.Kind.ID);
        while (current.kind.equals(Token.Kind.COMMA)) {
            eatToken(Token.Kind.COMMA);
            parseType();
            eatToken(Token.Kind.ID);
        }
    }

    // Method -> public Type id ( FormalList )
    // { VarDecl* Statement* return Exp ;}
    private void parseMethod() {
        // to parse a method.
        eatToken(Token.Kind.PUBLIC);
        parseType();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LPAREN);
        if (FIRST_TYPE.contains(current.kind)) {
            parseFormalList();
        }
        eatToken(Token.Kind.RPAREN);
        eatToken(Token.Kind.LBRACE);
        // 这里可能会出现L2上的区分了，因为 vardecls 含 id开头的，statement也要含id，那么对于id时候应该走哪条线就有问题
        parseVarDecls();
        parseStatements();
        eatToken(Token.Kind.RETURN);
        parseExp();
        eatToken(Token.Kind.SEMICOLON);
        eatToken(Token.Kind.RBRACE);
    }

    // MethodDecls -> MethodDecl MethodDecls
    // ->
    private void parseMethodDecls() {
        while (FIRST_METHODDECLARATION.contains(current.kind)) {
            parseMethod();
        }
    }

    // ClassDecl -> class id { VarDecl* MethodDecl* }
    //           -> class id extends id { VarDecl* MethodDecl* }
    private void parseClassDecl() {
        eatToken(Token.Kind.CLASS);
        eatToken(Token.Kind.ID);
        if (current.kind.equals(Token.Kind.EXTENDS)) {
            advance();
            eatToken(Token.Kind.ID);
        }
        eatToken(Token.Kind.LBRACE);

        // 这里对 VarDecl 和 MethodDecl 有 LL1 的判别，所以这里应该没问题

        parseVarDecls();
        parseMethodDecls();

        eatToken(Token.Kind.RBRACE);

    }

    // ClassDecls -> ClassDecl ClassDecls
    //            ->
    private void parseClassDecls() {
        while (current.kind.equals(Token.Kind.CLASS)) {
            parseClassDecl();
        }
    }

    // MainClass -> class id {
    //   public static void main ( String [] id ) {
    //     Statement
    //   }
    // }
    private void parseMainClass() {
        // Lab 1. Exercise 11: Fill in the missing code
        // to parse a main class as described by the
        // grammar above.
        eatToken(Token.Kind.CLASS);
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
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.RPAREN);
        eatToken(Token.Kind.LBRACE);
        parseStatement();
        eatToken(Token.Kind.RBRACE);
        eatToken(Token.Kind.RBRACE);
    }

    // Program -> MainClass ClassDecl*
    private Ast.Program parseProgram(Object obj) {
        parseMainClass();
        parseClassDecls();
        eatToken(Token.Kind.EOF);
        return null;
    }

    private void initParser() {
        try {
            this.inputStream = new BufferedInputStream(new FileInputStream(this.inputFileName));
        } catch (Exception e) {
            error("unable to open file" + this.inputFileName);
        }

        this.lexer = new Lexer(this.inputFileName, this.inputStream);
        this.current = lexer.nextToken();
    }

    private void finalizeParser() {
        try {
            this.inputStream.close();
        } catch (Exception e) {
            error("unable to close file");
        }
    }

    public Ast.Program parse() {
        initParser();
        Trace<Object, Ast.Program> trace =
                new Trace<>("parser.Parser.parse",
                        this::parseProgram,
                        this.inputFileName,
                        (s) -> System.out.println("parsing: " + s),
                        new PrettyPrinter()::ppProgram);
        Ast.Program ast = trace.doit();
        finalizeParser();
        return ast;
    }
}
