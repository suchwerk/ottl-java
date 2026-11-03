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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code flatten(target, prefix, depth, resolveConflicts)} — lifts nested maps and lists up to
 * {@code target}. Keys are visited in UTF-8 order, which is how a pdata map iterates.
 * {@code prefix}, {@code depth}, and {@code resolveConflicts} are literals. {@code depth} must be
 * greater than 0; the default walks the whole value. With conflict resolution, the first value
 * keeps the key and later ones are written at {@code key.0}, {@code key.1}, and so on.
 */
public final class FlattenFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "flatten";
    }

    @Override
    public String documentation() {
        return "Lifts nested maps and lists onto target. depth must be greater than 0. resolveConflicts suffixes duplicate keys with .0, .1, and so on.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "prefix", "depth", "resolveConflicts");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("path", "string", "int", "bool");
    }

    @Override
    public int minArgs() {
        return 1;
    }

    @Override
    public int maxArgs() {
        return 4;
    }

    @Override
    public void validateArguments(List<Expression> arguments) {
        Expression prefix = AppendFunction.argument(arguments, 1);
        if (prefix != null && !(prefix instanceof Expression.StringLiteral)) {
            throw new OttlCompilationException("flatten: prefix must be a string literal");
        }
        Expression depth = AppendFunction.argument(arguments, 2);
        if (depth != null) {
            if (!(depth instanceof Expression.IntLiteral literal)) {
                throw new OttlCompilationException(
                        "invalid depth 'non-literal' for flatten function, must be greater than 0");
            }
            if (literal.value() < 1) {
                throw new OttlCompilationException(
                        "invalid depth '" + literal.value() + "' for flatten function, must be greater than 0");
            }
        }
        Expression conflicts = AppendFunction.argument(arguments, 3);
        if (conflicts != null && !(conflicts instanceof Expression.BoolLiteral)) {
            throw new OttlCompilationException("flatten: resolveConflicts must be a bool literal");
        }
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Map<String, Object> target = ReplaceAllMatchesFunction.stringKeyMap(args.get(0).value(), name());
        String prefix = "";
        if (AppendFunction.present(args, 1)) {
            prefix = StandardValues.requireString(args.get(1).value(), "flatten prefix");
        }
        long depth = Long.MAX_VALUE;
        if (AppendFunction.present(args, 2)) {
            depth = StandardValues.requireLong(args.get(2).value(), "depth");
            if (depth < 1) {
                throw new OttlEvaluationException(
                        "invalid depth '" + depth + "' for flatten function, must be greater than 0");
            }
        }
        boolean resolve = false;
        if (AppendFunction.present(args, 3)) {
            resolve = StandardValues.requireBool(args.get(3).value(), "resolveConflicts");
        }
        Flattener flattener = new Flattener(depth, resolve);
        flattener.flattenMap(target, prefix, 0);
        ctx.setPath(args.get(0).asLValuePath(), flattener.result);
        return null;
    }

    private static final class Flattener {
        private final long maxDepth;
        private final boolean resolveConflicts;
        private final Map<String, Object> result = new LinkedHashMap<>();
        private final Map<String, Integer> existingKeys = new LinkedHashMap<>();
        private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();

        private Flattener(long maxDepth, boolean resolveConflicts) {
            this.maxDepth = maxDepth;
            this.resolveConflicts = resolveConflicts;
        }

        private void flattenMap(Map<String, Object> map, String prefix, long currentDepth) {
            if (seen.put(map, Boolean.TRUE) != null) {
                throw new OttlEvaluationException("flatten: cyclic value");
            }
            try {
                String dotted = prefix.isEmpty() ? "" : prefix + ".";
                List<String> keys = new ArrayList<>(map.keySet());
                keys.sort(StandardValues::compareUtf8);
                for (String key : keys) {
                    flattenValue(key, map.get(key), currentDepth, dotted);
                }
            } finally {
                seen.remove(map);
            }
        }

        private void flattenSlice(List<?> list, String prefix, long currentDepth) {
            if (seen.put(list, Boolean.TRUE) != null) {
                throw new OttlEvaluationException("flatten: cyclic value");
            }
            try {
                for (int i = 0; i < list.size(); i++) {
                    flattenValue(Integer.toString(i), list.get(i), currentDepth + 1, prefix);
                }
            } finally {
                seen.remove(list);
            }
        }

        private void flattenValue(String key, Object value, long currentDepth, String prefix) {
            if (value instanceof Map<?, ?> nested && currentDepth < maxDepth) {
                flattenMap(ReplaceAllMatchesFunction.stringKeyMap(nested, "flatten"), prefix + key, currentDepth + 1);
                return;
            }
            if (value instanceof List<?> list && currentDepth < maxDepth) {
                if (seen.put(list, Boolean.TRUE) != null) {
                    throw new OttlEvaluationException("flatten: cyclic value");
                }
                try {
                    for (int i = 0; i < list.size(); i++) {
                        Object element = list.get(i);
                        if (element instanceof Map<?, ?> nested && currentDepth + 1 < maxDepth) {
                            flattenMap(ReplaceAllMatchesFunction.stringKeyMap(nested, "flatten"),
                                    prefix + key + "." + i, currentDepth + 2);
                        } else if (element instanceof List<?> inner && currentDepth + 1 < maxDepth) {
                            flattenSlice(inner, prefix + key + "." + i, currentDepth + 2);
                        } else if (resolveConflicts) {
                            handleConflict(prefix + key, element);
                        } else {
                            put(prefix + key + "." + i, element);
                        }
                    }
                } finally {
                    seen.remove(list);
                }
                return;
            }
            if (resolveConflicts) {
                handleConflict(prefix + key, value);
            } else {
                put(prefix + key, value);
            }
        }

        private void handleConflict(String key, Object value) {
            if (result.containsKey(key)) {
                int suffix = existingKeys.getOrDefault(key, 0);
                put(key + "." + suffix, value);
                existingKeys.put(key, suffix + 1);
            } else {
                existingKeys.put(key, 0);
                put(key, value);
            }
        }

        private void put(String key, Object value) {
            result.put(key, StandardValues.copy(value, seen));
        }
    }
}
