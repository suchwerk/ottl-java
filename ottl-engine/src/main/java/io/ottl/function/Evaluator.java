/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.function;

import io.ottl.OttlEvaluationException;
import io.ottl.lang.ast.ComparisonOperator;
import io.ottl.lang.ast.Condition;
import io.ottl.lang.ast.Expression;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stateless evaluator for OTTL expressions and conditions. All state lives in the supplied
 * {@link ExecutionContext}, keeping compiled programs immutable and thread-safe.
 */
public final class Evaluator {

    private Evaluator() {
    }

    public static Object eval(ExecutionContext ctx, Expression expr) {
        return switch (expr) {
            case Expression.StringLiteral s -> s.value();
            case Expression.IntLiteral i -> i.value();
            case Expression.FloatLiteral f -> f.value();
            case Expression.BoolLiteral b -> b.value();
            case Expression.NilLiteral ignored -> null;
            case Expression.ListLiteral list -> evalList(ctx, list);
            case Expression.Path path -> {
                Optional<Object> enumConstant = ctx.enumConstant(path);
                yield enumConstant.isPresent() ? enumConstant.get() : ctx.getPath(ctx.strip(path));
            }
            case Expression.FunctionCall call -> evalCall(ctx, call);
        };
    }

    private static List<Object> evalList(ExecutionContext ctx, Expression.ListLiteral list) {
        List<Object> out = new ArrayList<>(list.elements().size());
        for (Expression e : list.elements()) {
            out.add(eval(ctx, e));
        }
        return out;
    }

    private static Object evalCall(ExecutionContext ctx, Expression.FunctionCall call) {
        OttlFunction fn = ctx.functions().require(call.name());
        List<Argument> args = new ArrayList<>(call.arguments().size());
        for (Expression arg : call.arguments()) {
            args.add(new Argument(arg, ctx));
        }
        return fn.invoke(ctx, args);
    }

    public static boolean evalCondition(ExecutionContext ctx, Condition condition) {
        return switch (condition) {
            case Condition.And and -> evalCondition(ctx, and.left()) && evalCondition(ctx, and.right());
            case Condition.Or or -> evalCondition(ctx, or.left()) || evalCondition(ctx, or.right());
            case Condition.Not not -> !evalCondition(ctx, not.condition());
            case Condition.Comparison cmp -> compare(eval(ctx, cmp.left()), cmp.operator(), eval(ctx, cmp.right()));
            case Condition.ValueCondition vc -> toBoolean(eval(ctx, vc.expression()));
        };
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw new OttlEvaluationException(
                "Condition did not evaluate to a boolean: " + describe(value));
    }

    static boolean compare(Object left, ComparisonOperator op, Object right) {
        return switch (op) {
            case EQ -> equalValues(left, right);
            case NE -> !equalValues(left, right);
            case LT -> order(left, right) < 0;
            case LE -> order(left, right) <= 0;
            case GT -> order(left, right) > 0;
            case GE -> order(left, right) >= 0;
        };
    }

    private static boolean equalValues(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return toBigDecimal(na).compareTo(toBigDecimal(nb)) == 0;
        }
        return Objects.equals(a, b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int order(Object a, Object b) {
        if (a == null || b == null) {
            throw new OttlEvaluationException("Cannot order-compare null (" + describe(a) + ", " + describe(b) + ")");
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return toBigDecimal(na).compareTo(toBigDecimal(nb));
        }
        if (a instanceof Comparable ca && a.getClass().isInstance(b)) {
            return ca.compareTo(b);
        }
        throw new OttlEvaluationException(
                "Values are not order-comparable: " + describe(a) + " and " + describe(b));
    }

    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal bd) {
            return bd;
        }
        if (n instanceof Double || n instanceof Float) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (n instanceof java.math.BigInteger bi) {
            return new BigDecimal(bi);
        }
        return BigDecimal.valueOf(n.longValue());
    }

    private static String describe(Object value) {
        if (value == null) {
            return "nil";
        }
        return value.getClass().getSimpleName() + "(" + value + ")";
    }
}
