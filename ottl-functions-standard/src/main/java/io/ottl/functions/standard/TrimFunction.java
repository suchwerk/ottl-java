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
 * {@code Trim(target, replacement)} — {@code strings.Trim}. {@code replacement} is a set of
 * characters to remove from both ends, not a prefix. When it is omitted the cutset is a single
 * space, not the Unicode whitespace set.
 */
public final class TrimFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "Trim";
    }

    @Override
    public String documentation() {
        return "Removes leading and trailing characters that appear in the cutset. The default cutset is one space.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "replacement");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("string", "string");
    }

    @Override
    public int minArgs() {
        return 1;
    }

    @Override
    public int maxArgs() {
        return 2;
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        String target = StandardValues.requireString(args.get(0).value(), "Trim target");
        String cutset = args.size() > 1
                ? StandardValues.requireString(args.get(1).value(), "Trim replacement")
                : " ";
        return StandardValues.trim(target, cutset);
    }
}
