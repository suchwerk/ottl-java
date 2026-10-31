/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.function.Argument;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;

import java.util.List;

/**
 * {@code set(target, value)} — assigns {@code value} to the {@code target} path. Works for maps,
 * POJOs (mutating) and records (copy-on-write), via the engine's path write mechanism.
 */
public final class SetFunction implements OttlFunction {

    @Override
    public String name() {
        return "set";
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public int maxArgs() {
        return 2;
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Object value = args.get(1).value();
        ctx.setPath(args.get(0).asLValuePath(), value);
        return value;
    }
}
