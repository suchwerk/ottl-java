/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

import java.util.Optional;

/**
 * A single OTTL statement: an editor invocation with an optional {@code where} guard.
 *
 * <p>Example: {@code set(order.status, "REVIEW") where order.amount > 10000}.
 *
 * @param editor    the editor function invocation (e.g. {@code set(...)})
 * @param condition the optional {@code where} condition
 * @param line      1-based line of the editor in the source (for diagnostics)
 * @param column    1-based column of the editor in the source (for diagnostics)
 */
public record Statement(Expression.FunctionCall editor, Optional<Condition> condition,
                        int line, int column) implements ScriptItem {
}
