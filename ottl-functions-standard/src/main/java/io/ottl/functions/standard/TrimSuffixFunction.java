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

/** {@code TrimSuffix(target, suffix)} — {@code strings.TrimSuffix}. One trailing copy is removed. */
public final class TrimSuffixFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "TrimSuffix";
    }

    @Override
    public String documentation() {
        return "Removes suffix from the end of target when it is present. Otherwise returns target.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "suffix");
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
        String target = StandardValues.requireString(args.get(0).value(), "TrimSuffix target");
        String suffix = StandardValues.requireString(args.get(1).value(), "TrimSuffix suffix");
        return target.endsWith(suffix) ? target.substring(0, target.length() - suffix.length()) : target;
    }
}
