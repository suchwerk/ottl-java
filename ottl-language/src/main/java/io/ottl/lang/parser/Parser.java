/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.parser;

import io.ottl.lang.ast.ComparisonOperator;
import io.ottl.lang.ast.Condition;
import io.ottl.lang.ast.Expression;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A hand-written recursive-descent parser for the OTTL subset.
 *
 * <p>Statements are self-delimiting: each statement is an editor invocation
 * ({@code name(args)}) with an optional {@code where} condition. Because every statement begins
 * with an identifier followed by {@code (}, no explicit statement terminator is required, which
 * matches how the reference OTTL implementation treats each statement as an independent unit.
 *
 * <pre>
 * script      := statement*
 * statement   := functionCall ("where" condition)?
 * condition   := orExpr
 * orExpr      := andExpr ("or" andExpr)*
 * andExpr     := unaryCond ("and" unaryCond)*
 * unaryCond   := "not" unaryCond | "(" condition ")" | comparison
 * comparison  := value (compOp value)?
 * value       := literal | list | functionCall | path
 * path        := IDENT ("." IDENT | "[" key "]")*
 * </pre>
 */
public final class Parser {

    private final List<Token> tokens;
    private int index;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** Parse a full script. */
    public static OttlScript parse(String source) {
        return new Parser(Lexer.tokenize(source)).parseScript();
    }

    /** Parse a single OTTL statement (convenience for tests and single-rule compilation). */
    public static Statement parseStatement(String source) {
        Parser p = new Parser(Lexer.tokenize(source));
        Statement stmt = p.statement();
        p.expect(TokenType.EOF, "end of statement");
        return stmt;
    }

    public OttlScript parseScript() {
        List<Statement> statements = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            statements.add(statement());
        }
        return new OttlScript(statements);
    }

    // ----- statements -----

    private Statement statement() {
        Token nameTok = peek();
        if (nameTok.type() != TokenType.IDENT) {
            throw error(nameTok, "Expected a statement (an editor function call)");
        }
        Expression.FunctionCall editor = functionCall();
        Optional<Condition> where = Optional.empty();
        if (checkKeyword("where")) {
            advance();
            where = Optional.of(condition());
        }
        return new Statement(editor, where, nameTok.line(), nameTok.column());
    }

    // ----- conditions -----

    private Condition condition() {
        return orExpr();
    }

    private Condition orExpr() {
        Condition left = andExpr();
        while (checkKeyword("or")) {
            advance();
            Condition right = andExpr();
            left = new Condition.Or(left, right);
        }
        return left;
    }

    private Condition andExpr() {
        Condition left = unaryCond();
        while (checkKeyword("and")) {
            advance();
            Condition right = unaryCond();
            left = new Condition.And(left, right);
        }
        return left;
    }

    private Condition unaryCond() {
        if (checkKeyword("not")) {
            advance();
            return new Condition.Not(unaryCond());
        }
        if (check(TokenType.LPAREN)) {
            advance();
            Condition inner = condition();
            expect(TokenType.RPAREN, "')' to close condition group");
            return inner;
        }
        Expression left = value();
        ComparisonOperator op = comparisonOperator();
        if (op == null) {
            return new Condition.ValueCondition(left);
        }
        Expression right = value();
        return new Condition.Comparison(left, op, right);
    }

    private ComparisonOperator comparisonOperator() {
        Token t = peek();
        ComparisonOperator op = switch (t.type()) {
            case EQ -> ComparisonOperator.EQ;
            case NE -> ComparisonOperator.NE;
            case LT -> ComparisonOperator.LT;
            case LE -> ComparisonOperator.LE;
            case GT -> ComparisonOperator.GT;
            case GE -> ComparisonOperator.GE;
            default -> null;
        };
        if (op != null) {
            advance();
        }
        return op;
    }

    // ----- values -----

    private Expression value() {
        Token t = peek();
        switch (t.type()) {
            case STRING:
                advance();
                return new Expression.StringLiteral(t.text());
            case INT:
                advance();
                return new Expression.IntLiteral(parseLong(t));
            case FLOAT:
                advance();
                return new Expression.FloatLiteral(parseDouble(t));
            case MINUS:
                return negativeNumber();
            case LBRACKET:
                return listLiteral();
            case IDENT:
                return identifierValue();
            default:
                throw error(t, "Expected a value");
        }
    }

    private Expression negativeNumber() {
        Token minus = advance();
        Token t = peek();
        if (t.type() == TokenType.INT) {
            advance();
            return new Expression.IntLiteral(-parseLong(t));
        }
        if (t.type() == TokenType.FLOAT) {
            advance();
            return new Expression.FloatLiteral(-parseDouble(t));
        }
        throw error(minus, "Unary '-' must be followed by a number");
    }

    private Expression listLiteral() {
        advance(); // '['
        List<Expression> elements = new ArrayList<>();
        if (!check(TokenType.RBRACKET)) {
            elements.add(value());
            while (check(TokenType.COMMA)) {
                advance();
                elements.add(value());
            }
        }
        expect(TokenType.RBRACKET, "']' to close list");
        return new Expression.ListLiteral(elements);
    }

    private Expression identifierValue() {
        Token t = peek();
        String name = t.text();
        // Reserved literals (only when not used as a call/path head).
        if (!nextIs(1, TokenType.LPAREN) && !nextIs(1, TokenType.DOT) && !nextIs(1, TokenType.LBRACKET)) {
            switch (name) {
                case "true":
                    advance();
                    return new Expression.BoolLiteral(true);
                case "false":
                    advance();
                    return new Expression.BoolLiteral(false);
                case "nil":
                    advance();
                    return new Expression.NilLiteral();
                default:
                    break;
            }
        }
        if (nextIs(1, TokenType.LPAREN)) {
            return functionCall();
        }
        return path();
    }

    private Expression.FunctionCall functionCall() {
        Token nameTok = expect(TokenType.IDENT, "function name");
        expect(TokenType.LPAREN, "'(' after function name");
        List<Expression> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            args.add(value());
            while (check(TokenType.COMMA)) {
                advance();
                args.add(value());
            }
        }
        expect(TokenType.RPAREN, "')' to close argument list");
        return new Expression.FunctionCall(nameTok.text(), args);
    }

    private Expression.Path path() {
        List<Expression.PathSegment> segments = new ArrayList<>();
        Token first = expect(TokenType.IDENT, "path segment");
        segments.add(new Expression.FieldSegment(first.text()));
        while (true) {
            if (check(TokenType.DOT)) {
                advance();
                Token field = expect(TokenType.IDENT, "field name after '.'");
                segments.add(new Expression.FieldSegment(field.text()));
            } else if (check(TokenType.LBRACKET)) {
                advance();
                Token key = peek();
                if (key.type() == TokenType.STRING) {
                    advance();
                    segments.add(new Expression.KeySegment(key.text()));
                } else if (key.type() == TokenType.INT) {
                    advance();
                    segments.add(new Expression.KeySegment(parseLong(key)));
                } else {
                    throw error(key, "Map/index key must be a string or integer");
                }
                expect(TokenType.RBRACKET, "']' to close key access");
            } else {
                break;
            }
        }
        return new Expression.Path(segments);
    }

    // ----- helpers -----

    private long parseLong(Token t) {
        try {
            return Long.parseLong(t.text());
        } catch (NumberFormatException e) {
            throw error(t, "Invalid integer literal '" + t.text() + "'");
        }
    }

    private double parseDouble(Token t) {
        try {
            return Double.parseDouble(t.text());
        } catch (NumberFormatException e) {
            throw error(t, "Invalid float literal '" + t.text() + "'");
        }
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private boolean checkKeyword(String keyword) {
        Token t = peek();
        return t.type() == TokenType.IDENT && t.text().equals(keyword);
    }

    private boolean nextIs(int ahead, TokenType type) {
        int i = index + ahead;
        return i < tokens.size() && tokens.get(i).type() == type;
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token advance() {
        Token t = tokens.get(index);
        if (t.type() != TokenType.EOF) {
            index++;
        }
        return t;
    }

    private Token expect(TokenType type, String what) {
        Token t = peek();
        if (t.type() != type) {
            throw error(t, "Expected " + what);
        }
        return advance();
    }

    private OttlParseException error(Token t, String message) {
        String found = t.type() == TokenType.EOF ? "end of input" : "'" + t.text() + "'";
        return new OttlParseException(message + " but found " + found, t.line(), t.column());
    }
}
