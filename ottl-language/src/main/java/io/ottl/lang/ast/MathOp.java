/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

/** Arithmetic operators. {@code *} and {@code /} bind tighter than {@code +} and {@code -}. */
public enum MathOp {
    ADD("+"),
    SUB("-"),
    MUL("*"),
    DIV("/");

    private final String symbol;

    MathOp(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
