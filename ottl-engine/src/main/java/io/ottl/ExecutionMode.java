/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl;

/**
 * How a program applies changes to its input.
 *
 * <ul>
 *   <li>{@link #MUTATE} — mutate the supplied object in place and return it.</li>
 *   <li>{@link #COPY} — never mutate the input; return an updated copy. Required for Java records,
 *       which are immutable.</li>
 * </ul>
 */
public enum ExecutionMode {
    MUTATE,
    COPY
}
