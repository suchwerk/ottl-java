/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

import java.util.List;

/**
 * A value-producing OTTL expression.
 *
 * <p>Expressions are immutable AST nodes produced by the parser and consumed by the engine.
 * They intentionally carry no evaluation logic; evaluation lives in the engine module so the
 * language module stays dependency-free.
 */
public sealed interface Expression
        permits Expression.StringLiteral, Expression.IntLiteral, Expression.FloatLiteral,
        Expression.BoolLiteral, Expression.NilLiteral, Expression.ListLiteral,
        Expression.Path, Expression.FunctionCall {

    /** A double-quoted string literal, e.g. {@code "REVIEW"}. */
    record StringLiteral(String value) implements Expression {}

    /** A 64-bit signed integer literal, e.g. {@code 10000}. */
    record IntLiteral(long value) implements Expression {}

    /** A 64-bit floating-point literal, e.g. {@code 3.14}. */
    record FloatLiteral(double value) implements Expression {}

    /** A boolean literal, {@code true} or {@code false}. */
    record BoolLiteral(boolean value) implements Expression {}

    /** The {@code nil} literal. */
    record NilLiteral() implements Expression {}

    /** A list literal, e.g. {@code ["a", "b"]}. */
    record ListLiteral(List<Expression> elements) implements Expression {}

    /**
     * A path expression such as {@code order.amount} or {@code attributes["env"]}.
     * The first {@link FieldSegment} may name the context root (see engine documentation).
     */
    record Path(List<PathSegment> segments) implements Expression {}

    /** A function/converter call such as {@code Concat("a", "b")}. */
    record FunctionCall(String name, List<Expression> arguments) implements Expression {}

    /** A single segment of a {@link Path}. */
    sealed interface PathSegment permits FieldSegment, KeySegment {}

    /** A dotted field access, e.g. the {@code amount} in {@code order.amount}. */
    record FieldSegment(String name) implements PathSegment {}

    /**
     * A keyed/indexed access, e.g. the {@code "env"} in {@code attributes["env"]}.
     * The key is either a {@link String} or a {@link Long}.
     */
    record KeySegment(Object key) implements PathSegment {}
}
