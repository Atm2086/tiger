package lexer;

import util.Todo;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Map;

import static control.Control.Lexer.dumpToken;

public class Lexer {
    private String fileName;
    private PushbackInputStream fileStream;
    private int rowNum;
    private int colNum;
    private static final Map<String, Token.Kind> KEYWORDS = Map.ofEntries(
            Map.entry("class", Token.Kind.CLASS),
            Map.entry("public", Token.Kind.PUBLIC),
            Map.entry("static", Token.Kind.STATIC),
            Map.entry("void", Token.Kind.VOID),
            Map.entry("main", Token.Kind.MAIN),
            Map.entry("new", Token.Kind.NEW),
            Map.entry("extends", Token.Kind.EXTENDS),
            Map.entry("return", Token.Kind.RETURN),
            Map.entry("this", Token.Kind.THIS),
            Map.entry("System", Token.Kind.SYSTEM),
            Map.entry("out", Token.Kind.OUT),
            Map.entry("println", Token.Kind.PRINTLN),

            Map.entry("int", Token.Kind.INT),
            Map.entry("String", Token.Kind.STRING),
            Map.entry("boolean", Token.Kind.BOOLEAN),
            Map.entry("true", Token.Kind.TRUE),
            Map.entry("false", Token.Kind.FALSE),

            Map.entry("if", Token.Kind.IF),
            Map.entry("else", Token.Kind.ELSE),
            Map.entry("while", Token.Kind.WHILE),

            Map.entry("length", Token.Kind.LENGTH)
    );

    public Lexer (String fileName,
                  InputStream fileStream) {
        this.fileName = fileName;
        this.fileStream = new PushbackInputStream(fileStream, 1);
        this.rowNum = 1;
        this.colNum = 1;
    }
    // When called, return the next token (refer to the code "Token.java")
    // from the input stream.
    // Return TOKEN_EOF when reaching the end of the input stream.
    private Token nextToken0() throws Exception {

        int lookahead = peek();
        int c;
        // skip all kinds of "blanks"
        // think carefully about how to set up "colNum" and "rowNum" correctly?
        while (' ' == lookahead || '\t' == lookahead || '\n' == lookahead) {
            c = consume();
            lookahead = peek();
        } // used to jump

        if (lookahead == -1) {
            return new Token(Token.Kind.EOF, rowNum, colNum);
        }

        if (isDigit(lookahead)) {
            return scanNumber();
        }

        if (isLetter(lookahead)) {
            return scanIdentificationorKeywords();
        }

        return scanOperatororPunctuation();

    }

    private int peek() throws IOException {
        int lookahead = this.fileStream.read();

        if (lookahead != -1) {
            this.fileStream.unread(lookahead);
        }

        return lookahead;
    }

    private int consume() throws IOException {
        int c = this.fileStream.read();

        if (c == '\n') {
            rowNum += 1;
            colNum = 1;
        } else {
          colNum += 1;
        }

        return c;
    }

    private void consumeExpected(String expected) throws IOException {
        for (int i = 0; i < expected.length(); i++) {
            int c = peek();

            if (c != expected.charAt(i)) {
                throw new IllegalArgumentException(
                        "Expected \"" + expected + "\" at "
                                + rowNum + ":" + colNum
                );
            }

            consume();
        }
    }

    private boolean isDigit(int c) {
        return (c >= '0' && c <= '9');
    }

    private boolean isLetter(int c) {
        return (c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
    }

    private Token scanNumber() throws IOException{
        StringBuilder lexeme = new StringBuilder();
        while (isDigit(peek())) {
            lexeme.append((char) consume());
        }
        return new Token(Token.Kind.INTEGER_LITERAL, lexeme.toString(), rowNum, colNum);
    }

    private Token scanIdentificationorKeywords() throws IOException{

        StringBuilder lexeme = new StringBuilder();
        while (isDigit(peek()) || isLetter(peek())) {
            lexeme.append((char) consume());
        }
        String s = lexeme.toString();

        Token.Kind kind = KEYWORDS.get(s);
        if (kind != null) {
            return new Token(kind, rowNum, colNum);
        }

        return new Token(Token.Kind.ID, lexeme.toString(), rowNum, colNum);

    }

    private Token scanOperatororPunctuation() throws IOException{
        int c = consume();

        return switch(c) {
            // operator
            case '=' -> new Token(Token.Kind.ASSIGN, rowNum, colNum);
            case '+' -> new Token(Token.Kind.ADD, rowNum, colNum);
            case '-' -> new Token(Token.Kind.MINUS, rowNum, colNum);
            case '*' -> new Token(Token.Kind.TIMES, rowNum, colNum);
            case '<' -> new Token(Token.Kind.LT, rowNum, colNum);
            case '!' -> new Token(Token.Kind.NOT, rowNum, colNum);
            case '&' -> {
                if (peek() != '&') {
                    throw new IllegalArgumentException(
                            "Unexpected '&' at "
                                    + rowNum + ":" + colNum
                    );
                }
                c = consume();   // 消耗第二个 &
                yield new Token(Token.Kind.AND, rowNum, colNum);
            }
            case '/' -> {
                if (peek() == '/') {
                    while (peek() != '\n') {
                        consume();
                    }
                    consume();
                    yield nextToken();
                }
                c = consume();   // 消耗第二个 &
                yield new Token(Token.Kind.DIV, rowNum, colNum);
            }
            // punctuation
            case ',' -> new Token(Token.Kind.COMMA, rowNum, colNum);
            case '.' -> new Token(Token.Kind.DOT, rowNum, colNum);
            case ';' -> new Token(Token.Kind.SEMICOLON, rowNum, colNum);
            case '[' -> new Token(Token.Kind.LBRACKET, rowNum, colNum);
            case ']' -> new Token(Token.Kind.RBRACKET, rowNum, colNum);
            case '(' -> new Token(Token.Kind.LPAREN, rowNum, colNum);
            case ')' -> new Token(Token.Kind.RPAREN, rowNum, colNum);
            case '{' -> new Token(Token.Kind.LBRACE, rowNum, colNum);
            case '}' -> new Token(Token.Kind.RBRACE, rowNum, colNum);
            
            default -> throw new IllegalArgumentException(
                    "Unexpected character '" + (char) c
                    + "' at " + rowNum + ":" + colNum
            );
        };
                
    }

    public Token nextToken() {
        Token t = null;

        try {
            t = this.nextToken0();
        } catch (Exception e) {
            //e.printStackTrace();
            System.exit(1);
        }
        if (dumpToken) {
            System.out.println(t);
        }
        return t;
    }
}
