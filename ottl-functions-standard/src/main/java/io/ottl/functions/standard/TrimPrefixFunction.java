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

/** {@code TrimPrefix(target, prefix)} — {@code strings.TrimPrefix}. One leading copy is removed. */
public final class TrimPrefixFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "TrimPrefix";
    }

    @Override
    public String documentation() {
        return "Removes prefix from the start of target when it is present. Otherwise returns target.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "prefix");
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
        String target = StandardValues.requireString(args.get(0).value(), "TrimPrefix target");
        String prefix = StandardValues.requireString(args.get(1).value(), "TrimPrefix prefix");
        return target.startsWith(prefix) ? target.substring(prefix.length()) : target;
    }
}
