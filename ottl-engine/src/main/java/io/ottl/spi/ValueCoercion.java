/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.spi;

import io.ottl.OttlEvaluationException;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Narrow, explicit, reflection-free value coercions used when writing OTTL values (which are
 * {@code Long}, {@code Double}, {@code String}, {@code Boolean}, ...) into typed Java
 * fields/components. Shared by the reflective resolvers and by build-time generated accessors.
 *
 * <p>For a primitive {@code target}, the corresponding boxed value is returned (callers unbox).
 */
public final class ValueCoercion {

    private ValueCoercion() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object coerce(Object value, Class<?> target) {
        if (target == Object.class) {
            return value;
        }
        if (value == null) {
            if (target.isPrimitive()) {
                throw new OttlEvaluationException("Cannot assign nil to primitive " + target.getName());
            }
            return null;
        }
        if (!target.isPrimitive() && target.isInstance(value)) {
            return value;
        }
        if (value instanceof Number n) {
            Object coerced = coerceNumber(n, target);
            if (coerced != null) {
                return coerced;
            }
        }
        if ((target == boolean.class || target == Boolean.class) && value instanceof Boolean b) {
            return b;
        }
        if (target == String.class) {
            return value.toString();
        }
        if (target.isEnum() && value instanceof String s) {
            return Enum.valueOf((Class<? extends Enum>) target, s);
        }
        if ((target == char.class || target == Character.class)
                && value instanceof String s && s.length() == 1) {
            return s.charAt(0);
        }
        throw new OttlEvaluationException(
                "Cannot coerce " + value.getClass().getSimpleName() + " to " + target.getName());
    }

    private static Object coerceNumber(Number n, Class<?> target) {
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
        if (target == BigDecimal.class) {
            return new BigDecimal(n.toString());
        }
        if (target == BigInteger.class) {
            return new BigDecimal(n.toString()).toBigInteger();
        }
        return null;
    }
}
