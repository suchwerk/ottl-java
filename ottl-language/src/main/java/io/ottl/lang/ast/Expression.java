/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A value-producing OTTL expression.
 *
 * <p>Expressions are immutable AST nodes produced by the parser and consumed by the engine.
 * They intentionally carry no evaluation logic; evaluation lives in the engine module so the
 * language module stays dependency-free.
 */
public sealed interface Expression
        permits Expression.StringLiteral, Expression.IntLiteral, Expression.FloatLiteral,
        Expression.BoolLiteral, Expression.NilLiteral, Expression.BytesLiteral,
        Expression.ListLiteral, Expression.MapLiteral, Expression.EnumSymbol,
        Expression.Path, Expression.FunctionCall, Expression.Math, Expression.Unary,
        Expression.Lambda {

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

    /**
     * A byte-slice literal ({@code 0x} followed by an even number of hex digits).
     * Equality compares the bytes, not the array identity.
     */
    record BytesLiteral(byte[] value) implements Expression {
        public BytesLiteral {
            value = value == null ? new byte[0] : value.clone();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof BytesLiteral other && Arrays.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "BytesLiteral[length=" + value.length + "]";
        }
    }

    /** A list literal, e.g. {@code ["a", "b"]}. */
    record ListLiteral(List<Expression> elements) implements Expression {}

    /** One entry of a {@link MapLiteral}. The key is a string, a path, or a converter. */
    record MapEntry(Expression key, Expression value) {}

    /** A map literal, e.g. {@code {"foo": "bar"}}. */
    record MapLiteral(List<MapEntry> entries) implements Expression {
        public MapLiteral {
            entries = List.copyOf(entries);
        }
    }

    /**
     * A bare OTTL enum symbol such as {@code SPAN_KIND_SERVER}. Dotted Java constants
     * ({@code OrderStatus.PENDING}) stay {@link Path}s.
     */
    record EnumSymbol(String name) implements Expression {}

    /**
     * A path expression such as {@code order.amount} or {@code attributes["env"]}.
     * The first {@link FieldSegment} may name the context root (see engine documentation).
     */
    record Path(List<PathSegment> segments) implements Expression {}

    /**
     * A function call. Editors are statements ({@code set(...)}); converters appear in value
     * position ({@code IsMatch(...)}). {@code keys} are the indexes that follow a converter,
     * as in {@code Split(field, ",")[1]}.
     *
     * @param argumentNames parallel to {@code arguments}; empty when that argument is positional
     */
    record FunctionCall(String name, List<Expression> arguments, List<Optional<String>> argumentNames,
                        List<Expression> keys) implements Expression {
        public FunctionCall(String name, List<Expression> arguments) {
            this(name, arguments, List.of(), List.of());
        }

        public FunctionCall {
            arguments = List.copyOf(arguments);
            keys = keys == null ? List.of() : List.copyOf(keys);
            if (argumentNames == null || argumentNames.isEmpty()) {
                argumentNames = arguments.stream().map(ignored -> Optional.<String>empty()).toList();
            } else {
                argumentNames = List.copyOf(argumentNames);
            }
            if (argumentNames.size() != arguments.size()) {
                throw new IllegalArgumentException(
                        "argument name count " + argumentNames.size() + " does not match arguments "
                                + arguments.size());
            }
        }
    }

    /** A binary arithmetic expression. {@code *} and {@code /} are parsed tighter than {@code +} and {@code -}. */
    record Math(Expression left, MathOp op, Expression right) implements Expression {}

    /** A unary {@code +} or {@code -} that was not folded into a numeric literal. */
    record Unary(MathOp op, Expression operand) implements Expression {}

    /**
     * A lambda, {@code (params) => body}. The body is either a value or a boolean expression.
     * Parameters are lowercase identifiers; {@code _} may be repeated and cannot be read.
     */
    record Lambda(List<String> parameters, LambdaBody body) implements Expression {
        public Lambda {
            parameters = List.copyOf(parameters);
        }
    }

    /** The body of a {@link Lambda}. */
    sealed interface LambdaBody permits LambdaValue, LambdaCondition {}

    /** A lambda body that evaluates to a value, e.g. {@code (value) => value + 1}. */
    record LambdaValue(Expression expression) implements LambdaBody {}

    /** A lambda body that evaluates to a boolean, e.g. {@code (a, b) => a == b}. */
    record LambdaCondition(Condition condition) implements LambdaBody {}

    /** A single segment of a {@link Path}. */
    sealed interface PathSegment permits FieldSegment, KeySegment, DynamicKeySegment {}

    /** A dotted field access, e.g. the {@code amount} in {@code order.amount}. */
    record FieldSegment(String name) implements PathSegment {}

    /**
     * A constant keyed/indexed access, e.g. the {@code "env"} in {@code attributes["env"]}.
     * The key is either a {@link String} or a {@link Long}.
     */
    record KeySegment(Object key) implements PathSegment {}

    /**
     * A computed index, e.g. {@code attributes[name]} or {@code items[i + 1]}.
     * The key expression is evaluated when the path is read or written.
     */
    record DynamicKeySegment(Expression key) implements PathSegment {}
}
