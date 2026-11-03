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
import java.util.Map;

/** {@code IsMap(target)} — true for a map. A list is false. */
public final class IsMapFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "IsMap";
    }

    @Override
    public String documentation() {
        return "True when target is a map. Nil is false.";
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
        return args.get(0).value() instanceof Map<?, ?>;
    }
}
