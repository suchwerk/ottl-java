/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

/**
 * One unit of an OTTL script, at the top level or nested inside a {@link WhereBlock}: an editor
 * {@link Statement} or a {@code where} block.
 */
public sealed interface ScriptItem permits Statement, WhereBlock {
}
