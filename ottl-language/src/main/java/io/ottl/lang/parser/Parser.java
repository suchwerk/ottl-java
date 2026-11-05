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
import io.ottl.lang.ast.MathOp;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.ScriptItem;
import io.ottl.lang.ast.Statement;
import io.ottl.lang.ast.WhereBlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A hand-written recursive-descent parser for OTTL.
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
 * statement   := editor ("where" condition)?
 * editor      := lowercaseName "(" arguments ")"
 * condition   := orExpr
 * orExpr      := andExpr ("or" andExpr)*
 * andExpr     := unaryCond ("and" unaryCond)*
 * unaryCond   := "not" unaryCond | comparison | "(" condition ")"
 * comparison  := expression (compOp expression)?
 * expression  := term (("+" | "-") term)*
 * term        := unary (("*" | "/") unary)*
 * unary       := ("+" | "-") unary | primary
 * primary     := literal | list | map | bytes | lambda | converter | path | "(" expression ")"
 * converter   := UppercaseName "(" arguments ")" ("[" expression "]")*
 * path        := IDENT ("." IDENT | "[" expression "]")*
 * map         := "{" (mapKey ":" expression ("," mapKey ":" expression)*)? ","? "}"
 * lambda      := "(" parameters ")" "=>" (expression | condition)
 * </pre>
 *
 * <p>{@code if} and {@code else} are statement keywords in {@link OttlDialect#EXTENDED}. They stay
 * ordinary identifiers in paths ({@code order.if}) and, in the {@link OttlDialect#OTTL} dialect,
 * {@code if(...)} remains an editor invocation. {@code where(...)} is always an editor invocation.
 */
public final class Parser {

    /**
     * Hard ceiling on recursive constructs: {@code where} / block / {@code if} nesting, and also
     * parentheses, lists, maps, calls, indexes, lambdas, and {@code not}. The parser refuses to
     * recurse past this even if a caller asks for more, so a hostile script cannot overflow the stack.
     * Configured {@code maxNesting} still limits {@code where}, blocks, and {@code if} only.
     */
    public static final int MAX_NESTING = 64;

    private final List<Token> tokens;
    private final OttlDialect dialect;
    private final int maxNesting;
    private int index;
    private int depth;
    /** Shared ceiling for every recursive value or condition construct. */
    private int valueDepth;

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
        return introducesWhereBlock();
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
        Expression.FunctionCall editor = functionCall(true);
        Optional<Condition> where = Optional.empty();
        // Newlines do not end a statement, so `where <condition> {` after an editor is the next
        // block, not this statement's guard. A trailing where has no '{' after its condition.
        if (checkKeyword("where") && !introducesWhereBlock()) {
            advance();
            where = Optional.of(condition());
        }
        return new Statement(editor, where, nameTok.line(), nameTok.column());
    }

    /**
     * True when the current token is {@code where <condition> '{'}. Parser position is unchanged.
     * {@code where(...)} is an editor call, including {@code where(x) { ... }}. A parenthesized
     * condition such as {@code where (b > 1) { ... }} is a where block because it is not a call.
     * Speculative parsing restores every mutable cursor, not only the token index.
     */
    private boolean introducesWhereBlock() {
        if (!checkKeyword("where") || isWhereEditorCall()) {
            return false;
        }
        int mark = index;
        int markDepth = depth;
        int markValue = valueDepth;
        try {
            advance();
            condition();
            return check(TokenType.LBRACE);
        } catch (OttlParseException e) {
            return false;
        } finally {
            index = mark;
            depth = markDepth;
            valueDepth = markValue;
        }
    }

    /** True when {@code where(...)} parses as an editor invocation. Position is unchanged. */
    private boolean isWhereEditorCall() {
        if (!checkKeyword("where") || !nextIs(1, TokenType.LPAREN)) {
            return false;
        }
        int mark = index;
        int markDepth = depth;
        int markValue = valueDepth;
        try {
            functionCall(true);
            return true;
        } catch (OttlParseException e) {
            return false;
        } finally {
            index = mark;
            depth = markDepth;
            valueDepth = markValue;
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
            Token notTok = advance();
            enterValue(notTok);
            try {
                return new Condition.Not(unaryCond());
            } finally {
                valueDepth--;
            }
        }
        // A leading '(' may group a boolean expression or a math expression. Try the value form
        // first so `(1 + 2) > 3` is a comparison. If that fails, group a boolean expression.
        int mark = index;
        OttlParseException valueFailure = null;
        try {
            Expression left = expression();
            ComparisonOperator op = comparisonOperator();
            if (op != null) {
                Expression right = expression();
                return new Condition.Comparison(left, op, right);
            }
            return new Condition.ValueCondition(left);
        } catch (OttlParseException ex) {
            valueFailure = ex;
            index = mark;
        }
        if (check(TokenType.LPAREN)) {
            Token open = advance();
            enterValue(open);
            try {
                Condition inner = condition();
                expect(TokenType.RPAREN, "')' to close condition group");
                return inner;
            } finally {
                valueDepth--;
            }
        }
        throw valueFailure;
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

    /** Addition and subtraction, left-associative and looser than {@link #term()}. */
    private Expression expression() {
        Expression left = term();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            MathOp op = advance().type() == TokenType.PLUS ? MathOp.ADD : MathOp.SUB;
            Expression right = term();
            left = new Expression.Math(left, op, right);
        }
        return left;
    }

    /** Multiplication and division, left-associative. */
    private Expression term() {
        Expression left = unary();
        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            MathOp op = advance().type() == TokenType.STAR ? MathOp.MUL : MathOp.DIV;
            Expression right = unary();
            left = new Expression.Math(left, op, right);
        }
        return left;
    }

    private Expression unary() {
        if (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token opTok = advance();
            MathOp op = opTok.type() == TokenType.PLUS ? MathOp.ADD : MathOp.SUB;
            return applyUnary(op, primary(), opTok);
        }
        return primary();
    }

    /** Fold {@code -3} and {@code +1.5} into literals. Keep a node when the operand is computed. */
    private Expression applyUnary(MathOp op, Expression operand, Token at) {
        if (op == MathOp.ADD) {
            if (operand instanceof Expression.IntLiteral || operand instanceof Expression.FloatLiteral) {
                return operand;
            }
            return new Expression.Unary(op, operand);
        }
        try {
            if (operand instanceof Expression.IntLiteral i) {
                return new Expression.IntLiteral(Math.negateExact(i.value()));
            }
        } catch (ArithmeticException ex) {
            throw error(at, "Integer overflow applying '-'");
        }
        if (operand instanceof Expression.FloatLiteral f) {
            return new Expression.FloatLiteral(-f.value());
        }
        return new Expression.Unary(op, operand);
    }

    private Expression primary() {
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
            case BYTES:
                advance();
                return new Expression.BytesLiteral(HexFormat.of().parseHex(t.text()));
            case LBRACKET:
                return listLiteral();
            case LBRACE:
                return mapLiteral();
            case LPAREN:
                if (looksLikeLambda()) {
                    return lambda();
                }
                return grouped();
            case IDENT:
                return identifierValue();
            default:
                throw error(t, "Expected a value");
        }
    }

    private Expression grouped() {
        Token open = advance();
        enterValue(open);
        try {
            Expression inner = expression();
            expect(TokenType.RPAREN, "')' to close group");
            return inner;
        } finally {
            valueDepth--;
        }
    }

    private Expression listLiteral() {
        Token open = advance(); // '['
        enterValue(open);
        try {
            List<Expression> elements = new ArrayList<>();
            if (!check(TokenType.RBRACKET)) {
                elements.add(expression());
                while (check(TokenType.COMMA)) {
                    advance();
                    elements.add(expression());
                }
            }
            expect(TokenType.RBRACKET, "']' to close list");
            return new Expression.ListLiteral(elements);
        } finally {
            valueDepth--;
        }
    }

    private Expression mapLiteral() {
        Token open = advance(); // '{'
        enterValue(open);
        try {
            List<Expression.MapEntry> entries = new ArrayList<>();
            if (!check(TokenType.RBRACE)) {
                entries.add(mapEntry());
                while (check(TokenType.COMMA)) {
                    advance();
                    if (check(TokenType.RBRACE)) {
                        break;
                    }
                    entries.add(mapEntry());
                }
            }
            expect(TokenType.RBRACE, "'}' to close map");
            return new Expression.MapLiteral(entries);
        } finally {
            valueDepth--;
        }
    }

    private Expression.MapEntry mapEntry() {
        Expression key = mapKey();
        expect(TokenType.COLON, "':' after map key");
        return new Expression.MapEntry(key, expression());
    }

    /** A map key is a string literal, a path, or a converter. */
    private Expression mapKey() {
        if (check(TokenType.STRING)) {
            Token key = advance();
            return new Expression.StringLiteral(key.text());
        }
        if (check(TokenType.IDENT) && nextIs(1, TokenType.LPAREN)) {
            return functionCall(false);
        }
        if (check(TokenType.IDENT) && !isBareLiteral(peek())) {
            return path();
        }
        throw error(peek(), "Map key must be a string, path, or converter");
    }

    private Expression identifierValue() {
        Token t = peek();
        String name = t.text();
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
            if (isEnumSymbol(name)) {
                advance();
                return new Expression.EnumSymbol(name);
            }
        }
        if (nextIs(1, TokenType.LPAREN)) {
            return functionCall(false);
        }
        return path();
    }

    /** OTTL enum symbols are a single run of uppercase letters, digits, and underscores. */
    private static boolean isEnumSymbol(String name) {
        if (name.isEmpty() || !isUpper(name.charAt(0))) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(isUpper(c) || isDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param editor {@code true} for a statement editor (lowercase name, no trailing indexes).
     *               {@code false} for a converter (uppercase name, optional indexes).
     */
    private Expression.FunctionCall functionCall(boolean editor) {
        Token nameTok = expect(TokenType.IDENT, "function name");
        String name = nameTok.text();
        if (editor) {
            if (!startsWithLower(name)) {
                throw error(nameTok, "Editor names must start with a lowercase letter but got '" + name + "'");
            }
        } else if (!startsWithUpper(name)) {
            throw error(nameTok, "Converter names must start with an uppercase letter but got '" + name + "'");
        }
        expect(TokenType.LPAREN, "'(' after function name");
        enterValue(nameTok);
        try {
            List<Expression> args = new ArrayList<>();
            List<Optional<String>> names = new ArrayList<>();
            boolean seenNamed = false;
            if (!check(TokenType.RPAREN)) {
                seenNamed = argument(args, names, seenNamed);
                while (check(TokenType.COMMA)) {
                    advance();
                    seenNamed = argument(args, names, seenNamed);
                }
            }
            expect(TokenType.RPAREN, "')' to close argument list");
            List<Expression> keys = new ArrayList<>();
            while (check(TokenType.LBRACKET)) {
                if (editor) {
                    throw error(peek(), "Only paths and converters may be indexed, not editors");
                }
                Token bracket = advance();
                enterValue(bracket);
                try {
                    keys.add(expression());
                    expect(TokenType.RBRACKET, "']' to close index");
                } finally {
                    valueDepth--;
                }
            }
            return new Expression.FunctionCall(name, args, names, keys);
        } finally {
            valueDepth--;
        }
    }

    /** @return {@code true} once a named argument has been seen */
    private boolean argument(List<Expression> args, List<Optional<String>> names, boolean seenNamed) {
        if (check(TokenType.IDENT) && nextIs(1, TokenType.ASSIGN) && isArgumentName(peek().text())) {
            Token name = advance();
            advance(); // '='
            seenNamed = true;
            names.add(Optional.of(name.text()));
            args.add(expression());
            return true;
        }
        if (seenNamed) {
            throw error(peek(), "Named arguments must come after positional arguments");
        }
        names.add(Optional.empty());
        args.add(expression());
        return false;
    }

    private Expression.Path path() {
        List<Expression.PathSegment> segments = new ArrayList<>();
        Token first = expect(TokenType.IDENT, "path segment");
        segments.add(new Expression.FieldSegment(first.text()));
        readIndexes(segments);
        while (check(TokenType.DOT)) {
            advance();
            Token field = expect(TokenType.IDENT, "field name after '.'");
            segments.add(new Expression.FieldSegment(field.text()));
            readIndexes(segments);
        }
        return new Expression.Path(segments);
    }

    private void readIndexes(List<Expression.PathSegment> segments) {
        while (check(TokenType.LBRACKET)) {
            Token bracket = advance();
            enterValue(bracket);
            Expression key;
            try {
                key = expression();
                expect(TokenType.RBRACKET, "']' to close key access");
            } finally {
                valueDepth--;
            }
            if (key instanceof Expression.StringLiteral s) {
                segments.add(new Expression.KeySegment(s.value()));
            } else if (key instanceof Expression.IntLiteral i) {
                segments.add(new Expression.KeySegment(i.value()));
            } else {
                segments.add(new Expression.DynamicKeySegment(key));
            }
        }
    }

    private Expression lambda() {
        Token open = expect(TokenType.LPAREN, "'(' to open lambda");
        enterValue(open);
        List<String> params = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            if (!check(TokenType.RPAREN)) {
                params.add(lambdaParam(seen));
                while (check(TokenType.COMMA)) {
                    advance();
                    params.add(lambdaParam(seen));
                }
            }
            expect(TokenType.RPAREN, "')' after lambda parameters");
            expect(TokenType.ARROW, "'=>' after lambda parameters");
            Expression.LambdaBody body = lambdaBody();
            if (body instanceof Expression.LambdaValue value && value.expression() instanceof Expression.Lambda) {
                throw error(open, "Lambda body cannot be another lambda");
            }
            return new Expression.Lambda(params, body);
        } finally {
            valueDepth--;
        }
    }

    private String lambdaParam(Set<String> seen) {
        Token name = expect(TokenType.IDENT, "lambda parameter");
        if (!isLambdaParam(name.text())) {
            throw error(name, "Lambda parameter must be a lowercase identifier or '_'");
        }
        if (!name.text().equals("_") && !seen.add(name.text())) {
            throw error(name, "Duplicate lambda parameter '" + name.text() + "'");
        }
        return name.text();
    }

    /**
     * A lambda body is a value when it is not a comparison or a boolean combination, and a
     * condition otherwise. {@code (n) => n + 1} is a value. {@code (a, b) => a == b} is a condition.
     */
    private Expression.LambdaBody lambdaBody() {
        Condition condition = condition();
        if (condition instanceof Condition.ValueCondition value) {
            return new Expression.LambdaValue(value.expression());
        }
        return new Expression.LambdaCondition(condition);
    }

    /** {@code (params) =>} or {@code () =>}, without consuming tokens. */
    private boolean looksLikeLambda() {
        if (!check(TokenType.LPAREN)) {
            return false;
        }
        int i = index + 1;
        if (i >= tokens.size()) {
            return false;
        }
        if (tokens.get(i).type() == TokenType.RPAREN) {
            return tokenIs(i + 1, TokenType.ARROW);
        }
        if (!isLambdaParam(tokens.get(i).text()) || tokens.get(i).type() != TokenType.IDENT) {
            return false;
        }
        i++;
        while (i < tokens.size() && tokens.get(i).type() == TokenType.COMMA) {
            i++;
            if (i >= tokens.size() || tokens.get(i).type() != TokenType.IDENT || !isLambdaParam(tokens.get(i).text())) {
                return false;
            }
            i++;
        }
        return i < tokens.size()
                && tokens.get(i).type() == TokenType.RPAREN
                && tokenIs(i + 1, TokenType.ARROW);
    }

    private static boolean isLambdaParam(String name) {
        if (name.equals("_")) {
            return true;
        }
        if (name.isEmpty() || !isLower(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(isLower(c) || isDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

/**
     * Named-argument names start with a lowercase letter and may contain capitals, matching
     * collector fields such as {@code resolveConflicts}. {@code _} is not a name.
     */
    private static boolean isArgumentName(String name) {
        if (name.isEmpty() || name.equals("_") || !isLower(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(isLower(c) || isUpper(c) || isDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithLower(String name) {
        return !name.isEmpty() && isLower(name.charAt(0));
    }

    private static boolean startsWithUpper(String name) {
        return !name.isEmpty() && isUpper(name.charAt(0));
    }

    private static boolean isBareLiteral(Token token) {
        return token.type() == TokenType.IDENT
                && (token.text().equals("true") || token.text().equals("false") || token.text().equals("nil"));
    }

    private void enterValue(Token at) {
        if (valueDepth + 1 > MAX_NESTING) {
            throw error(at, "Nesting too deep (limit " + MAX_NESTING + ")");
        }
        valueDepth++;
    }

    private static boolean isLower(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isUpper(char c) {
        return c >= 'A' && c <= 'Z';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
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
        return tokenIs(index + ahead, type);
    }

    private boolean tokenIs(int position, TokenType type) {
        return position < tokens.size() && tokens.get(position).type() == type;
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
