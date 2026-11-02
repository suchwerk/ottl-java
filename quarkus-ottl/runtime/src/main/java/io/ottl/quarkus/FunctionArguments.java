/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus;

/**
 * Converts evaluated OTTL arguments to the parameter types of a {@link OttlFunction} method.
 * Shared by the reflective adapter and by build-time generated invokers so both paths coerce
 * values the same way.
 */
public final class FunctionArguments {

    private FunctionArguments() {
    }

    /**
     * Light coercion used before a function call. Numeric targets accept any {@link Number}.
     * {@code String} accepts any value via {@link String#valueOf(Object)}-style {@code toString()}.
     * Values that are already assignment-compatible are returned unchanged. This method does not
     * invent conversions for arbitrary domain types.
     */
    public static Object coerce(Object value, Class<?> target) {
        if (value == null || target.isInstance(value) || target == Object.class) {
            return value;
        }
        if (value instanceof Number n) {
            if (target == int.class || target == Integer.class) {
                return n.intValue();
            }
            if (target == long.class || target == Long.class) {
                return n.longValue();
            }
            if (target == double.class || target == Double.class) {
                return n.doubleValue();
            }
            if (target == float.class || target == Float.class) {
                return n.floatValue();
            }
            if (target == short.class || target == Short.class) {
                return n.shortValue();
            }
            if (target == byte.class || target == Byte.class) {
                return n.byteValue();
            }
        }
        if ((target == boolean.class || target == Boolean.class) && value instanceof Boolean b) {
            return b;
        }
        if (target == String.class) {
            return value.toString();
        }
        return value;
    }
}
