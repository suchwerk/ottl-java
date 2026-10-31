/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

/**
 * A boolean-valued OTTL condition, used in {@code where} clauses.
 */
public sealed interface Condition
        permits Condition.Comparison, Condition.And, Condition.Or, Condition.Not,
        Condition.ValueCondition {

    /** A binary comparison such as {@code order.amount > 10000}. */
    record Comparison(Expression left, ComparisonOperator operator, Expression right)
            implements Condition {}

    /** Logical conjunction. */
    record And(Condition left, Condition right) implements Condition {}

    /** Logical disjunction. */
    record Or(Condition left, Condition right) implements Condition {}

    /** Logical negation. */
    record Not(Condition condition) implements Condition {}

    /**
     * A bare value (typically a function call or path) used directly as a boolean,
     * e.g. {@code IsPremiumCustomer(order.customer)}.
     */
    record ValueCondition(Expression expression) implements Condition {}
}
