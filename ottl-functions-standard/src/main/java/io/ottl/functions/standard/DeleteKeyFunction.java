/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.function.Argument;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;

import java.util.List;

/**
 * {@code delete_key(target, key)} — removes {@code key} from the map referenced by {@code target}.
 */
public final class DeleteKeyFunction implements OttlFunction {

    @Override
    public String name() {
        return "delete_key";
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
        Object key = args.get(1).value();
        ctx.deleteKeyAtPath(args.get(0).asLValuePath(), key);
        return null;
    }
}
