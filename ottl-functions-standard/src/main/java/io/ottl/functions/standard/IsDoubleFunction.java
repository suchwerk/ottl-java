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
 * {@code IsDouble(target)} — true for a float. An int is false, and so is a {@code BigDecimal},
 * which standard OTTL does not have.
 */
public final class IsDoubleFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "IsDouble";
    }

    @Override
    public String documentation() {
        return "True when target is a float. Integers, strings, and nil are false.";
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
        return value instanceof Double || value instanceof Float;
    }
}
