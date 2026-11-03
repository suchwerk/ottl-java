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
 * {@code Concat(vals, delimiter)} — joins the string form of each list item, placing
 * {@code delimiter} between items. A nil item contributes a single space, matching the collector.
 * An empty delimiter concatenates the items directly.
 */
public final class ConcatFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "Concat";
    }

    @Override
    public String documentation() {
        return "Joins the string form of each list item, with the delimiter between items. A nil item contributes a space.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("vals", "delimiter");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("list", "string");
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
        List<?> vals = StandardValues.requireList(args.get(0).value(), "Concat");
        String delimiter = StandardValues.requireString(args.get(1).value(), "Concat delimiter");
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Object item : vals) {
            if (!first) {
                builder.append(delimiter);
            }
            first = false;
            builder.append(item == null ? " " : StandardValues.stringLike(item));
        }
        return builder.toString();
    }
}
