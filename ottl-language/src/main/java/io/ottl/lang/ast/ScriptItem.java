/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

/**
 * One unit of an OTTL script: an editor {@link Statement}, a {@link WhereBlock}, a {@link Block},
 * or an {@link IfStatement}.
 *
 * <p>Blocks and {@code if}/{@code else} are available only in {@link io.ottl.lang.OttlDialect#EXTENDED}.
 * A block is the future lexical scope for {@code let}; it does not introduce bindings and it does
 * not allocate a new root object.
 */
public sealed interface ScriptItem permits Statement, WhereBlock, Block, IfStatement {
}
