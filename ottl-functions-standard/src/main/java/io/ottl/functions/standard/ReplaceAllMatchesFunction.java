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
import io.ottl.lang.ast.Expression;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code replace_all_matches(target, pattern, replacement)} — replaces every string value of the
 * map at {@code target} that matches the glob completely. Non-string values stay. Nested maps are
 * not walked. The optional converter argument is not supported.
 */
public final class ReplaceAllMatchesFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "replace_all_matches";
    }

    @Override
    public String documentation() {
        return "Replaces every string value in a map that matches a glob. * matches across '/'.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "pattern", "replacement");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("path", "string", "string");
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
        ReplaceMatchFunction.compileLiteral(arguments.get(1));
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Map<String, Object> map = stringKeyMap(args.get(0).value(), "replace_all_matches");
        String pattern = StandardValues.requireString(args.get(1).value(), "replace_all_matches pattern");
        String replacement = StandardValues.requireString(args.get(2).value(), "replace_all_matches replacement");
        Pattern glob = GlobPatterns.compile(pattern, false);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof String text && GlobPatterns.matches(glob, text)) {
                entry.setValue(replacement);
            }
        }
        ctx.setPath(args.get(0).asLValuePath(), map);
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> stringKeyMap(Object value, String function) {
        if (!(value instanceof Map<?, ?> map)) {
            String got = value == null ? "nil" : value.getClass().getSimpleName();
            throw new OttlEvaluationException(function + ": target must be a map but got " + got);
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new OttlEvaluationException(function + ": map key must be a string");
            }
        }
        return (Map<String, Object>) map;
    }
}
