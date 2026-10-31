/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.function.Argument;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code keep_keys(target, keys)} — retains only {@code keys} in the map referenced by
 * {@code target}. Keys may be given as a single list argument ({@code keep_keys(attrs, ["a","b"])})
 * or as individual arguments ({@code keep_keys(attrs, "a", "b")}).
 */
public final class KeepKeysFunction implements OttlFunction {

    @Override
    public String name() {
        return "keep_keys";
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        List<Object> keys = new ArrayList<>();
        if (args.size() == 2 && args.get(1).value() instanceof List<?> list) {
            keys.addAll(list);
        } else {
            for (int i = 1; i < args.size(); i++) {
                keys.add(args.get(i).value());
            }
        }
        ctx.keepKeysAtPath(args.get(0).asLValuePath(), keys);
        return null;
    }
}
