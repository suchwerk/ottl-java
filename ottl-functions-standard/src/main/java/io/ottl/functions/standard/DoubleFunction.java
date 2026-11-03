/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.function.Argument;
import io.ottl.function.DescribedFunction;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;

import java.util.List;

/**
 * {@code Double(target)} — a float64. Strings are parsed with {@link Double#parseDouble(String)},
 * which also accepts {@code Inf} and {@code Infinity}. Booleans become 1 and 0. Nil stays nil.
 */
public final class DoubleFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "Double";
    }

    @Override
    public String documentation() {
        return "Converts a value to a float. Nil stays nil. A string must parse as a floating-point number.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("any");
    }

    @Override
    public int minArgs() {
        return 1;
    }

    @Override
    public int maxArgs() {
        return 1;
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        return StandardValues.toDouble(args.get(0).value());
    }
}
