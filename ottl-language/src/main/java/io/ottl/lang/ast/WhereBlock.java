/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

import java.util.List;

/**
 * A condition evaluated once, then a sequence of statements and nested blocks.
 *
 * <p>Example:
 * <pre>
 * where stockMovement.quantity &lt; 0 {
 *     set(stockMovement.quantity, 0)
 *     set(stockMovement.direction, nil)
 * }
 * </pre>
 *
 * <p>The condition is not re-checked between body items. A statement may change a value the
 * condition reads, and later statements in the block still run. This is an ottl-java extension;
 * a trailing {@code where} on a single editor statement is unchanged.
 *
 * @param condition the guard, evaluated once before the body
 * @param body      the items to run when the condition is true
 * @param line      1-based line of the {@code where} keyword
 * @param column    1-based column of the {@code where} keyword
 */
public record WhereBlock(Condition condition, List<ScriptItem> body, int line, int column)
        implements ScriptItem {

    public WhereBlock {
        body = List.copyOf(body);
    }
}
