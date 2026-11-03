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
 * {@code String(target)} — the collector's string conversion. A string is unchanged, a byte slice
 * is lowercase hex, and a list or map is JSON with sorted keys and no extra spaces. Nil stays nil.
 */
public final class StringFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "String";
    }

    @Override
    public String documentation() {
        return "Converts a value to a string. A byte slice is lowercase hex. A list or map is JSON. Nil stays nil.";
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
        Object value = args.get(0).value();
        if (value == null) {
            return null;
        }
        return StandardValues.stringLike(value);
    }
}
