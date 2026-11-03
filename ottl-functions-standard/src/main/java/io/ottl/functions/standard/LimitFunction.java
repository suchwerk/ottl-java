/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.OttlCompilationException;
import io.ottl.OttlEvaluationException;
import io.ottl.function.Argument;
import io.ottl.function.DescribedFunction;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;
import io.ottl.lang.ast.Expression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code limit(target, limit, priorityKeys)} — drops keys until the map has at most
 * {@code limit} entries. Priority keys are kept. Other keys are kept in UTF-8 order until the
 * limit is reached, which is the order a pdata map walks. {@code limit} is an int literal and
 * must be at least 0 and at least the number of priority keys.
 */
public final class LimitFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "limit";
    }

    @Override
    public String documentation() {
        return "Drops map entries until at most limit remain. priorityKeys are kept. Other keys are kept in UTF-8 order.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "limit", "priorityKeys");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("path", "int", "list");
    }

    @Override
    public int minArgs() {
        return 3;
    }

    @Override
    public int maxArgs() {
        return 3;
    }

    @Override
    public void validateArguments(List<Expression> arguments) {
        Expression limit = arguments.get(1);
        if (!(limit instanceof Expression.IntLiteral literal)) {
            throw new OttlCompilationException("limit: limit must be an int literal");
        }
        if (literal.value() < 0) {
            throw new OttlCompilationException(
                    "invalid limit for limit function, " + literal.value() + " cannot be negative");
        }
        Expression keys = arguments.get(2);
        if (keys instanceof Expression.ListLiteral list) {
            int count = 0;
            for (Expression element : list.elements()) {
                if (!(element instanceof Expression.StringLiteral)) {
                    return;
                }
                count++;
            }
            if (literal.value() < count) {
                throw new OttlCompilationException(
                        "invalid limit for limit function, " + literal.value()
                                + " cannot be less than number of priority attributes " + count);
            }
        }
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Map<String, Object> map = ReplaceAllMatchesFunction.stringKeyMap(args.get(0).value(), name());
        long limit = StandardValues.requireLong(args.get(1).value(), "limit");
        if (limit < 0) {
            throw new OttlEvaluationException(
                    "invalid limit for limit function, " + limit + " cannot be negative");
        }
        List<String> priority = priorityKeys(args.get(2).value());
        if (limit < priority.size()) {
            throw new OttlEvaluationException(
                    "invalid limit for limit function, " + limit
                            + " cannot be less than number of priority attributes " + priority.size());
        }
        if (map.size() <= limit) {
            return null;
        }
        long count = 0;
        for (String key : priority) {
            if (map.containsKey(key)) {
                count++;
            }
        }
        Set<String> keep = new HashSet<>(priority);
        List<String> ordered = new ArrayList<>(map.keySet());
        ordered.sort(StandardValues::compareUtf8);
        for (String key : ordered) {
            if (keep.contains(key)) {
                continue;
            }
            if (count < limit) {
                count++;
            } else {
                map.remove(key);
            }
        }
        ctx.setPath(args.get(0).asLValuePath(), map);
        return null;
    }

    private static List<String> priorityKeys(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new OttlEvaluationException("limit: priorityKeys must be a list");
        }
        List<String> keys = new ArrayList<>(list.size());
        for (Object element : list) {
            keys.add(StandardValues.requireString(element, "limit priority key"));
        }
        return keys;
    }
}
