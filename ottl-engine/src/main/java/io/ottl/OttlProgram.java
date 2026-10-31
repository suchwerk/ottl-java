/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl;

/**
 * An immutable, thread-safe compiled OTTL program. Compilation happens once; {@link #apply(Object)}
 * only evaluates the pre-parsed plan, so expressions are never recompiled per execution.
 *
 * @param <T> the root/context type the program was compiled for
 */
public interface OttlProgram<T> {

    /**
     * Apply the program to {@code input}. For {@link ExecutionMode#MUTATE} the input is modified and
     * returned; for {@link ExecutionMode#COPY} (e.g. records) an updated copy is returned and the
     * input is left untouched.
     */
    T apply(T input);

    /** The original OTTL source. */
    String source();

    /** The execution mode chosen for this program. */
    ExecutionMode mode();

    /** The context name used to strip a leading root reference from paths. */
    String contextName();
}
