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
 * {@code Len(target)} — UTF-8 byte length of a string, or the number of entries in a list, map, or
 * byte slice. Other types are an evaluation error. For well-formed text this matches Go's
 * {@code len(string)}, which counts bytes rather than characters.
 */
public final class LenFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "Len";
    }

    @Override
    public String documentation() {
        return "UTF-8 byte length of a string, or the number of entries in a list, map, or byte slice.";
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
        return StandardValues.length(args.get(0).value());
    }
}
