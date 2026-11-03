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
 * {@code Int(target)} — an int64. Floats and {@code BigDecimal} truncate toward zero. Booleans
 * become 1 and 0. Strings are parsed as base-10 integers, so {@code Int("2.0")} is an error even
 * though the collector README lists that call as an example; the collector implementation uses
 * {@code strconv.ParseInt}, which rejects it. Nil stays nil.
 */
public final class IntFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "Int";
    }

    @Override
    public String documentation() {
        return "Converts a value to an int. Floats truncate toward zero. Nil stays nil. A string must be a base-10 integer.";
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
        return StandardValues.toInt(args.get(0).value());
    }
}
