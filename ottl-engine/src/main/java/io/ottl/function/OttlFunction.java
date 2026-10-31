/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.function;

import java.util.List;

/**
 * A callable OTTL function. This single abstraction covers both <em>editors</em> (statement-level
 * functions such as {@code set}, {@code delete_key} that mutate the context and typically return
 * {@code null}) and <em>converters</em> (value-level functions such as {@code Concat} used inside
 * expressions).
 *
 * <p>Only functions present in the {@link FunctionRegistry} can be invoked, so the registry acts as
 * the allowlist of callable behavior. There is no way to call an arbitrary Java method from source.
 */
public interface OttlFunction {

    /** The name used to invoke this function in OTTL source (case-sensitive). */
    String name();

    /** Execute the function. Editors mutate {@code ctx}; converters return a value. */
    Object invoke(ExecutionContext ctx, List<Argument> args);

    /** Minimum number of arguments (inclusive), validated at compile time. */
    default int minArgs() {
        return 0;
    }

    /** Maximum number of arguments (inclusive), validated at compile time. */
    default int maxArgs() {
        return Integer.MAX_VALUE;
    }
}
