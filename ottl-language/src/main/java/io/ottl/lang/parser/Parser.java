/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.parser;

import io.ottl.lang.OttlDialect;
import io.ottl.lang.ast.Block;
import io.ottl.lang.ast.ComparisonOperator;
import io.ottl.lang.ast.Condition;
import io.ottl.lang.ast.Expression;
import io.ottl.lang.ast.IfStatement;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.ScriptItem;
import io.ottl.lang.ast.Statement;
import io.ottl.lang.ast.WhereBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A hand-written recursive-descent parser for the OTTL subset.
 *
 * <p>Editor statements are self-delimiting: each one is an invocation ({@code name(args)}) with an
 * optional trailing {@code where} condition. A {@code where} block groups several items under one
 * condition with braces; that condition is evaluated once for the whole block.
 *
 * <pre>
 * script      := item*
 * item        := block | ifStatement | whereBlock | statement
 * block       := "{" item* "}"                                 # OttlDialect.EXTENDED
 * ifStatement := "if" condition block
 *                ("else" "if" condition block)*
 *                ("else" block)?                               # OttlDialect.EXTENDED
 * whereBlock  := "where" condition "{" item* "}"
 * statement   := functionCall ("where" condition)?
 * condition   := orExpr
 * orExpr      := andExpr ("or" andExpr)*
 * andExpr     := unaryCond ("and" unaryCond)*
 * unaryCond   := "not" unaryCond | "(" condition ")" | comparison
 * comparison  := value (compOp value)?
 * value       := literal | list | functionCall | path
 * path        := IDENT ("." IDENT | "[" key "]")*
 * </pre>
 *
 * <p>{@code if} and {@code else} are statement keywords in {@link OttlDialect#EXTENDED}. They stay
 * ordinary identifiers in paths ({@code order.if}) and, in the {@link OttlDialect#OTTL} dialect,
 * {@code if(...)} remains an editor invocation. {@code where(...)} is always an editor invocation.
 */
public final class Parser {

    /**
     * Hard ceiling on {@code where}, block, and {@code if} nesting. The parser refuses to recurse
     * past this even if a caller asks for more, so a hostile script cannot overflow the stack.
     */
    public static final int MAX_NESTING = 64;

    private final List<Token> tokens;
    private final OttlDialect dialect;
    private final int maxNesting;
    private int index;
    private int depth;

    public Parser(List<Token> tokens) {
        this(tokens, OttlDialect.OTTL, MAX_NESTING);
    }

    public Parser(List<Token> tokens, OttlDialect dialect, int maxNesting) {
        this.tokens = tokens;
        this.dialect = dialect == null ? OttlDialect.OTTL : dialect;
        if (maxNesting < 1) {
            throw new IllegalArgumentException("maxNesting must be positive");
        }
        this.maxNesting = Math.min(maxNesting, MAX_NESTING);
    }

    /** Parse a full script in the {@link OttlDialect#OTTL} dialect. */
    public static OttlScript parse(String source) {
        return parse(source, OttlDialect.OTTL);
    }

    /** Parse a full script in {@code dialect}. Nesting is limited to {@link #MAX_NESTING}. */
    public static OttlScript parse(String source, OttlDialect dialect) {
        return parse(source, dialect, MAX_NESTING);
    }

    /**
     * Parse a full script. {@code maxNesting} is the configured complexity limit; values above
     * {@link #MAX_NESTING} are clamped.
     */
    public static OttlScript parse(String source, OttlDialect dialect, int maxNesting) {
        return new Parser(Lexer.tokenize(source), dialect, maxNesting).parseScript();
    }

    /** Parse a single OTTL statement (convenience for tests and single-rule compilation). */
    public static Statement parseStatement(String source) {
        Parser p = new Parser(Lexer.tokenize(source));
        Statement stmt = p.statement();
        p.expect(TokenType.EOF, "end of statement");
        return stmt;
    }

    public OttlScript parseScript() {
        List<ScriptItem> statements = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            statements.add(item());
        }
        return new OttlScript(statements);
    }

    // ----- statements -----

    private ScriptItem item() {
        if (check(TokenType.RBRACE)) {
            throw error(peek(), "Unexpected '}'");
        }
        if (checkKeyword("else")) {
            if (dialect != OttlDialect.EXTENDED) {
                throw extended("if/else");
            }
            throw error(peek(), "Unexpected 'else' without a preceding 'if'");
        }
        if (check(TokenType.LBRACE)) {
            if (dialect != OttlDialect.EXTENDED) {
                throw extended("block");
            }
            return block(true);
        }
        if (startsIf()) {
            if (dialect != OttlDialect.EXTENDED) {
                throw extended("if/else");
            }
            return ifStatement();
        }
        if (isWhereBlock()) {
            return whereBlock();
        }
        return statement();
    }

    /**
     * {@code if} starts a conditional in the extended dialect. In the OTTL dialect, {@code if(...)}
     * remains an editor call and any other {@code if} is the extended form, which is rejected.
     */
    private boolean startsIf() {
        if (!checkKeyword("if")) {
            return false;
        }
        return dialect == OttlDialect.EXTENDED || !nextIs(1, TokenType.LPAREN);
    }

    /** {@code where} followed by a condition and a block. {@code where(...)} stays an editor call. */
    private boolean isWhereBlock() {
        return checkKeyword("where") && !nextIs(1, TokenType.LPAREN);
    }

    private WhereBlock whereBlock() {
        Token whereTok = advance();
        enter(whereTok);
        try {
            Condition condition = condition();
            expect(TokenType.LBRACE, "'{' to open where block");
            List<ScriptItem> body = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                body.add(item());
            }
            expect(TokenType.RBRACE, "'}' to close where block");
            return new WhereBlock(condition, body, whereTok.line(), whereTok.column());
        } finally {
            depth--;
        }
    }

    /**
     * @param nest when {@code true}, the block itself consumes one nesting level. The braces that
     *             belong to {@code if} or {@code else} do not: the {@code if} already counted.
     */
    private Block block(boolean nest) {
        Token brace = expect(TokenType.LBRACE, "'{' to open block");
        if (nest) {
            enter(brace);
        }
        try {
            List<ScriptItem> body = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                body.add(item());
            }
            expect(TokenType.RBRACE, "'}' to close block");
            return new Block(body, brace.line(), brace.column());
        } finally {
            if (nest) {
                depth--;
            }
        }
    }

    private IfStatement ifStatement() {
        Token ifTok = advance();
        enter(ifTok);
        try {
            if (check(TokenType.LBRACE) || check(TokenType.EOF)) {
                throw error(peek(), "Expected a condition after 'if'");
            }
            Condition condition = condition();
            if (!check(TokenType.LBRACE)) {
                throw error(peek(), "Expected '{' to open if block");
            }
            Block thenBlock = block(false);
            Optional<ScriptItem> elseBranch = Optional.empty();
            if (checkKeyword("else")) {
                advance();
                if (checkKeyword("if")) {
                    elseBranch = Optional.of(ifStatement());
                } else if (check(TokenType.LBRACE)) {
                    elseBranch = Optional.of(block(false));
                } else {
                    throw error(peek(), "Expected '{' or 'if' after 'else'");
                }
            }
            return new IfStatement(condition, thenBlock, elseBranch, ifTok.line(), ifTok.column());
        } finally {
            depth--;
        }
    }

    private void enter(Token at) {
        if (depth + 1 > maxNesting) {
            throw error(at, "Nesting too deep (limit " + maxNesting + ")");
        }
        depth++;
    }

    private OttlParseException extended(String feature) {
        return error(peek(), "Language feature '" + feature + "' requires OttlDialect.EXTENDED");
    }

    private Statement statement() {
        Token nameTok = peek();
        if (nameTok.type() != TokenType.IDENT) {
            throw error(nameTok, "Expected a statement (an editor function call)");
        }
        Expression.FunctionCall editor = functionCall();
        Optional<Condition> where = Optional.empty();
        // Newlines do not end a statement, so `where <condition> {` after an editor is the next
        // block, not this statement's guard. A trailing where has no '{' after its condition.
        if (checkKeyword("where") && !introducesWhereBlock()) {
            advance();
            where = Optional.of(condition());
        }
        return new Statement(editor, where, nameTok.line(), nameTok.column());
    }

    /** True when the current token is {@code where <condition> '{'}. Parser position is unchanged. */
    private boolean introducesWhereBlock() {
        if (!checkKeyword("where") || nextIs(1, TokenType.LPAREN)) {
            return false;
        }
        int mark = index;
        try {
            advance();
            condition();
            return check(TokenType.LBRACE);
        } catch (OttlParseException e) {
            return false;
        } finally {
            index = mark;
        }
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
