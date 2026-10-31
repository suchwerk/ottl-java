/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

/** The comparison operators supported in OTTL conditions. */
public enum ComparisonOperator {
    EQ("=="),
    NE("!="),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">=");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
