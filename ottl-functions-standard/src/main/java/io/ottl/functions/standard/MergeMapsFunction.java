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

import java.util.List;
import java.util.Map;

/**
 * {@code merge_maps(target, source, strategy)} — copies entries from {@code source} into
 * {@code target}. {@code strategy} is the literal {@code insert}, {@code update}, or
 * {@code upsert}. Nested maps and lists are copied, so the two maps do not share them.
 */
public final class MergeMapsFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "merge_maps";
    }

    @Override
    public String documentation() {
        return "Merges source into target. strategy is insert, update, or upsert.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "source", "strategy");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("path", "map", "string");
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
        if (!(arguments.get(2) instanceof Expression.StringLiteral literal)) {
            throw new OttlCompilationException(
                    "invalid value for strategy, non-literal, must be 'insert', 'update' or 'upsert'");
        }
        if (!known(literal.value())) {
            throw new OttlCompilationException(
                    "invalid value for strategy, " + literal.value() + ", must be 'insert', 'update' or 'upsert'");
        }
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Map<String, Object> target = ReplaceAllMatchesFunction.stringKeyMap(args.get(0).value(), name());
        Map<String, Object> source = ReplaceAllMatchesFunction.stringKeyMap(args.get(1).value(), name());
        String strategy = StandardValues.requireString(args.get(2).value(), "merge_maps strategy");
        if (!known(strategy)) {
            throw new OttlEvaluationException(
                    "invalid value for strategy, " + strategy + ", must be 'insert', 'update' or 'upsert'");
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            boolean exists = target.containsKey(entry.getKey());
            boolean write = switch (strategy) {
                case "insert" -> !exists;
                case "update" -> exists;
                default -> true;
            };
            if (write) {
                target.put(entry.getKey(), StandardValues.copy(entry.getValue()));
            }
        }
        ctx.setPath(args.get(0).asLValuePath(), target);
        return null;
    }

    private static boolean known(String strategy) {
        return "insert".equals(strategy) || "update".equals(strategy) || "upsert".equals(strategy);
    }
}
