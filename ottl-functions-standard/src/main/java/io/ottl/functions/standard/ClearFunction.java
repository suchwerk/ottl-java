/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.function.Argument;
import io.ottl.function.DescribedFunction;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * {@code clear(target)} — writes the zero value of whatever {@code target} currently holds.
 * A string becomes {@code ""}, an int {@code 0}, a float {@code 0}, and a bool {@code false}.
 * A map becomes an empty map and a list becomes an empty list, matching {@code pcommon.Map} and
 * {@code pcommon.Slice}. A byte slice becomes nil. Nil stays nil. Any other object becomes nil.
 */
public final class ClearFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "clear";
    }

    @Override
    public String documentation() {
        return "Writes the zero value of target. A map becomes an empty map and a list becomes an empty list. A byte slice becomes nil.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("path");
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
        ctx.setPath(args.get(0).asLValuePath(), zero(args.get(0).value()));
        return null;
    }

    private static Object zero(Object value) {
        if (value == null || value instanceof byte[]) {
            return null;
        }
        if (value instanceof String) {
            return "";
        }
        if (value instanceof Boolean) {
            return false;
        }
        if (value instanceof BigDecimal) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigInteger) {
            return BigInteger.ZERO;
        }
        if (value instanceof Float) {
            return 0.0f;
        }
        if (value instanceof Double) {
            return 0.0d;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return 0;
        }
        if (value instanceof Long) {
            return 0L;
        }
        if (value instanceof java.util.Map<?, ?>) {
            return new LinkedHashMap<>();
        }
        if (value instanceof List<?>) {
            return new ArrayList<>();
        }
        return null;
    }
}
