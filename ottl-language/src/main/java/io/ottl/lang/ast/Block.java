/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

import java.util.List;

/**
 * An ordered sequence of statements that runs against the same execution context.
 *
 * <p>An empty block is valid. Nested blocks do not create a new root object. A block is the
 * future lexical scope for {@code let} bindings; it does not introduce bindings today.
 *
 * @param statements the items to run, in source order
 * @param line       1-based line of the opening {@code '{'}
 * @param column     1-based column of the opening {@code '{'}
 */
public record Block(List<ScriptItem> statements, int line, int column) implements ScriptItem {

    public Block {
        statements = List.copyOf(statements);
    }
}
