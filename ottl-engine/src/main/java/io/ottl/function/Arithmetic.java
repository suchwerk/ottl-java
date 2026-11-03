/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.function;

import io.ottl.OttlEvaluationException;
import io.ottl.lang.ast.MathOp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

/**
 * OTTL arithmetic. {@code int64} and {@code float64} are not mixed. Integer division truncates
 * toward zero. {@link BigDecimal} arithmetic is a Java-profile addition so domain amounts can
 * be computed; a {@code BigDecimal} combines with integers and other decimals, and not with
 * floats. {@link Instant} and {@link Duration} support {@code +} and {@code -} only.
 */
final class Arithmetic {

    private Arithmetic() {
    }

    static Object eval(MathOp op, Object left, Object right) {
        if (left == null || right == null) {
            throw new OttlEvaluationException("Math expression cannot use nil");
        }
        Object temporal = temporal(op, left, right);
        if (temporal != null || isTemporal(left) || isTemporal(right)) {
            if (temporal == null) {
                throw new OttlEvaluationException(
                        "Time and duration values only support '+' and '-' ("
                                + describe(left) + " " + op.symbol() + " " + describe(right) + ")");
            }
            return temporal;
        }
        if (left instanceof BigDecimal || right instanceof BigDecimal) {
            return decimal(op, left, right);
        }
        if (isIntegral(left) && isIntegral(right)) {
            return integral(op, ((Number) left).longValue(), ((Number) right).longValue());
        }
        if (isFloat(left) && isFloat(right)) {
            return floating(op, ((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        throw new OttlEvaluationException(
                "Cannot mix int and float in a math expression ("
                        + describe(left) + " " + op.symbol() + " " + describe(right) + ")");
    }

    static Object unary(MathOp op, Object value) {
        if (value == null) {
            throw new OttlEvaluationException("Math expression cannot use nil");
        }
        if (op == MathOp.ADD) {
            if (isNumber(value) || value instanceof Duration) {
                return value;
            }
            throw new OttlEvaluationException("Unary '+' requires a number but was " + describe(value));
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.negate();
        }
        if (isIntegral(value)) {
            try {
                return Math.negateExact(((Number) value).longValue());
            } catch (ArithmeticException ex) {
                throw new OttlEvaluationException("Integer overflow", ex);
            }
        }
        if (isFloat(value)) {
            return -((Number) value).doubleValue();
        }
        if (value instanceof Duration duration) {
            return duration.negated();
        }
        throw new OttlEvaluationException("Unary '-' requires a number but was " + describe(value));
    }

    private static Object temporal(MathOp op, Object left, Object right) {
        if (left instanceof Instant li && right instanceof Instant ri && op == MathOp.SUB) {
            return Duration.between(ri, li);
        }
        if (left instanceof Instant li && right instanceof Duration rd) {
            return switch (op) {
                case ADD -> li.plus(rd);
                case SUB -> li.minus(rd);
                default -> null;
            };
        }
        if (left instanceof Duration ld && right instanceof Instant ri && op == MathOp.ADD) {
            return ri.plus(ld);
        }
        if (left instanceof Duration ld && right instanceof Duration rd) {
            return switch (op) {
                case ADD -> ld.plus(rd);
                case SUB -> ld.minus(rd);
                default -> null;
            };
        }
        return null;
    }

    private static BigDecimal decimal(MathOp op, Object left, Object right) {
        if (isFloat(left) || isFloat(right)) {
            throw new OttlEvaluationException(
                    "Cannot mix decimal and float in a math expression ("
                            + describe(left) + " " + op.symbol() + " " + describe(right) + ")");
        }
        BigDecimal a = toDecimal(left);
        BigDecimal b = toDecimal(right);
        return switch (op) {
            case ADD -> a.add(b);
            case SUB -> a.subtract(b);
            case MUL -> a.multiply(b);
            case DIV -> {
                if (b.signum() == 0) {
                    throw new OttlEvaluationException("Division by zero");
                }
                try {
                    yield a.divide(b);
                } catch (ArithmeticException ex) {
                    yield a.divide(b, java.math.MathContext.DECIMAL64);
                }
            }
        };
    }

    private static long integral(MathOp op, long left, long right) {
        try {
            return switch (op) {
                case ADD -> Math.addExact(left, right);
                case SUB -> Math.subtractExact(left, right);
                case MUL -> Math.multiplyExact(left, right);
                case DIV -> {
                    if (right == 0L) {
                        throw new OttlEvaluationException("Division by zero");
                    }
                    yield left / right;
                }
            };
        } catch (ArithmeticException ex) {
            throw new OttlEvaluationException("Integer overflow", ex);
        }
    }

    private static double floating(MathOp op, double left, double right) {
        double result = switch (op) {
            case ADD -> left + right;
            case SUB -> left - right;
            case MUL -> left * right;
            case DIV -> {
                if (right == 0.0d) {
                    throw new OttlEvaluationException("Division by zero");
                }
                yield left / right;
            }
        };
        return result;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (isIntegral(value)) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        throw new OttlEvaluationException("Math expression requires a number but was " + describe(value));
    }

    private static boolean isTemporal(Object value) {
        return value instanceof Instant || value instanceof Duration;
    }

    private static boolean isNumber(Object value) {
        return value instanceof BigDecimal || isIntegral(value) || isFloat(value);
    }

    private static boolean isIntegral(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger;
    }

    private static boolean isFloat(Object value) {
        return value instanceof Float || value instanceof Double;
    }

    private static String describe(Object value) {
        if (value == null) {
            return "nil";
        }
        return value.getClass().getSimpleName();
    }
}
