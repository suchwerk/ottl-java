/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.OttlEvaluationException;
import io.ottl.function.Argument;
import io.ottl.function.DescribedFunction;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * {@code stringify_all(target)} — turns every non-string value of the map at {@code target} into
 * a string. Strings stay as they are. Nil becomes {@code ""}. A byte slice is standard Base64.
 * A list or map is JSON. Numbers use the same text as {@code String} for ints and the plain
 * decimal form for floats.
 */
public final class StringifyAllFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "stringify_all";
    }

    @Override
    public String documentation() {
        return "Converts every non-string map value to a string. A byte slice is standard Base64. A list or map is JSON. Nil becomes an empty string.";
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
        Map<String, Object> map = ReplaceAllMatchesFunction.stringKeyMap(args.get(0).value(), name());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof String)) {
                entry.setValue(text(entry.getValue()));
            }
        }
        ctx.setPath(args.get(0).asLValuePath(), map);
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean || value instanceof String) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Float || value instanceof Double) {
            return StandardValues.formatPlainDouble(((Number) value).doubleValue());
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof BigInteger integer) {
            return integer.toString();
        }
        if (value instanceof Number number) {
            return Long.toString(number.longValue());
        }
        if (value instanceof Map<?, ?> || value instanceof List<?> || value.getClass().isArray()) {
            return StandardValues.json(value);
        }
        throw new OttlEvaluationException("stringify_all: cannot convert " + value.getClass().getSimpleName());
    }
}
