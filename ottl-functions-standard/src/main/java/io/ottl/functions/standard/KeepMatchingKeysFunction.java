/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.function.Argument;
import io.ottl.function.DescribedFunction;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;
import io.ottl.lang.ast.Expression;

import java.util.List;
import java.util.Map;

/**
 * {@code keep_matching_keys(target, pattern)} — keeps every key of the map at {@code target}
 * that matches {@code pattern} and removes the rest. The match is unanchored. Patterns are Java
 * regular expressions, not RE2.
 */
public final class KeepMatchingKeysFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "keep_matching_keys";
    }

    @Override
    public String documentation() {
        return "Keeps map keys that match a regular expression and removes the rest. Patterns are Java regular expressions.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "pattern");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("path", "string");
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public int maxArgs() {
        return 2;
    }

    @Override
    public void validateArguments(List<Expression> arguments) {
        ReplacePatternFunction.compileLiteral(arguments.get(1), name());
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Map<String, Object> map = ReplaceAllMatchesFunction.stringKeyMap(args.get(0).value(), name());
        String pattern = StandardValues.requireString(args.get(1).value(), name() + " pattern");
        KeyFilters.retain(map, ReplacePatternFunction.compile(pattern, name(), false), true);
        ctx.setPath(args.get(0).asLValuePath(), map);
        return null;
    }
}
