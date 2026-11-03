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
 * {@code Split(target, delimiter)} — {@code strings.Split}. An empty delimiter splits the target
 * into Unicode characters. The result is a list and can be indexed.
 */
public final class SplitFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "Split";
    }

    @Override
    public String documentation() {
        return "Splits a string by a delimiter and returns the list of pieces. An empty delimiter splits into characters.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "delimiter");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("string", "string");
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
        String target = StandardValues.requireString(args.get(0).value(), "Split target");
        String delimiter = StandardValues.requireString(args.get(1).value(), "Split delimiter");
        return StandardValues.split(target, delimiter);
    }
}
