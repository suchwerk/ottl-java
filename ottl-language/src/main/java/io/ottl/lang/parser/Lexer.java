/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * A small, allocation-light, hand-written lexer for the OTTL subset.
 *
 * <p>The lexer is whitespace- and newline-insensitive (statements are self-delimiting in the
 * grammar) and supports {@code #} line comments for authoring convenience.
 */
public final class Lexer {

    private final String src;
    private int pos;
    private int line = 1;
    private int col = 1;

    public Lexer(String source) {
        this.src = source == null ? "" : source;
    }

    public static List<Token> tokenize(String source) {
        return new Lexer(source).scan();
    }

    public List<Token> scan() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = next();
            tokens.add(t);
        } while (t.type() != TokenType.EOF);
        return tokens;
    }

    private Token next() {
        skipTrivia();
        if (atEnd()) {
            return new Token(TokenType.EOF, "", line, col);
        }
        int startLine = line;
        int startCol = col;
        char c = peek();

        if (c == '"') {
            return string(startLine, startCol);
        }
        if (Character.isDigit(c)) {
            return number(startLine, startCol);
        }
        if (isIdentStart(c)) {
            return identifier(startLine, startCol);
        }
        return symbol(startLine, startCol);
    }

    private Token symbol(int startLine, int startCol) {
        char c = advance();
        switch (c) {
            case '(':
                return new Token(TokenType.LPAREN, "(", startLine, startCol);
            case ')':
                return new Token(TokenType.RPAREN, ")", startLine, startCol);
            case '[':
                return new Token(TokenType.LBRACKET, "[", startLine, startCol);
            case ']':
                return new Token(TokenType.RBRACKET, "]", startLine, startCol);
            case '{':
                return new Token(TokenType.LBRACE, "{", startLine, startCol);
            case '}':
                return new Token(TokenType.RBRACE, "}", startLine, startCol);
            case ',':
                return new Token(TokenType.COMMA, ",", startLine, startCol);
            case '.':
                return new Token(TokenType.DOT, ".", startLine, startCol);
            case '-':
                return new Token(TokenType.MINUS, "-", startLine, startCol);
            case '=':
                expect('=', startLine, startCol, "'=' (did you mean '=='?)");
                return new Token(TokenType.EQ, "==", startLine, startCol);
            case '!':
                expect('=', startLine, startCol, "'!' (did you mean '!='?)");
                return new Token(TokenType.NE, "!=", startLine, startCol);
            case '<':
                if (!atEnd() && peek() == '=') {
                    advance();
                    return new Token(TokenType.LE, "<=", startLine, startCol);
                }
                return new Token(TokenType.LT, "<", startLine, startCol);
            case '>':
                if (!atEnd() && peek() == '=') {
                    advance();
                    return new Token(TokenType.GE, ">=", startLine, startCol);
                }
                return new Token(TokenType.GT, ">", startLine, startCol);
            default:
                throw new OttlParseException("Unexpected character '" + c + "'", startLine, startCol);
        }
    }

    private void expect(char expected, int l, int c, String what) {
        if (atEnd() || peek() != expected) {
            throw new OttlParseException("Unexpected " + what, l, c);
        }
        advance();
    }

    private Token string(int startLine, int startCol) {
        advance(); // opening quote
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && peek() != '"') {
            char c = advance();
            if (c == '\\') {
                if (atEnd()) {
                    break;
                }
                char esc = advance();
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        if (atEnd()) {
            throw new OttlParseException("Unterminated string literal", startLine, startCol);
        }
        advance(); // closing quote
        return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    private Token number(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && Character.isDigit(peek())) {
            sb.append(advance());
        }
        boolean isFloat = false;
        if (!atEnd() && peek() == '.' && isDigit(peekNext())) {
            isFloat = true;
            sb.append(advance()); // '.'
            while (!atEnd() && Character.isDigit(peek())) {
                sb.append(advance());
            }
        }
        if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
            isFloat = true;
            sb.append(advance());
            if (!atEnd() && (peek() == '+' || peek() == '-')) {
                sb.append(advance());
            }
            while (!atEnd() && Character.isDigit(peek())) {
                sb.append(advance());
            }
        }
        return new Token(isFloat ? TokenType.FLOAT : TokenType.INT, sb.toString(), startLine, startCol);
    }

    private Token identifier(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && isIdentPart(peek())) {
            sb.append(advance());
        }
        return new Token(TokenType.IDENT, sb.toString(), startLine, startCol);
    }

    private void skipTrivia() {
        while (!atEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
            } else if (c == '#') {
                while (!atEnd() && peek() != '\n') {
                    advance();
                }
            } else {
                return;
            }
        }
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean atEnd() {
        return pos >= src.length();
    }

    private char peek() {
        return src.charAt(pos);
    }

    private char peekNext() {
        return pos + 1 < src.length() ? src.charAt(pos + 1) : '\0';
    }

    private char advance() {
        char c = src.charAt(pos++);
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return c;
    }
}
