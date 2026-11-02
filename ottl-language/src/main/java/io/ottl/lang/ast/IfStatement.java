/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

import java.util.Optional;

/**
 * A conditional. The condition is evaluated once. The first matching branch runs; skipped branches
 * are not evaluated. {@code else if} is another {@code IfStatement} in {@link #elseBranch()}.
 * {@code else} is a {@link Block}.
 *
 * <p>The braces that belong to the {@code if} or {@code else} are required. There is no
 * single-statement form. Every branch is part of the AST so validators can reject invalid
 * statements even when that branch will not run.
 *
 * @param condition   the guard for this branch
 * @param thenBlock   the statements run when the condition is true
 * @param elseBranch  a {@link Block} or a nested {@link IfStatement}, when present
 * @param line        1-based line of the {@code if} keyword
 * @param column      1-based column of the {@code if} keyword
 */
public record IfStatement(Condition condition, Block thenBlock, Optional<ScriptItem> elseBranch,
                          int line, int column) implements ScriptItem {

    public IfStatement {
        if (elseBranch == null) {
            elseBranch = Optional.empty();
        }
        elseBranch.ifPresent(branch -> {
            if (!(branch instanceof Block) && !(branch instanceof IfStatement)) {
                throw new IllegalArgumentException("else branch must be a block or an if statement");
            }
        });
    }
}
