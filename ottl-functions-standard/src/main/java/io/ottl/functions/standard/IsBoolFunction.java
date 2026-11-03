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

/** {@code IsBool(target)} — true only for a boolean. The string {@code "true"} is false. */
public final class IsBoolFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "IsBool";
    }

    @Override
    public String documentation() {
        return "True when target is a boolean. Nil is false.";
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
        return args.get(0).value() instanceof Boolean;
    }
}
