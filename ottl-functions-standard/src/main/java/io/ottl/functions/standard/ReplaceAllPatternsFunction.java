/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.OttlCompilationException;
import io.ottl.function.Argument;
import io.ottl.function.DescribedFunction;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;
import io.ottl.lang.ast.Expression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code replace_all_patterns(target, mode, regex_pattern, replacement)} — replaces regex matches
 * in every string value ({@code mode} {@code "value"}) or key ({@code mode} {@code "key"}) of the
 * map at {@code target}. {@code mode} is a string literal, checked when the rule is compiled.
 * The optional converter argument is not supported.
 */
public final class ReplaceAllPatternsFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "replace_all_patterns";
    }

    @Override
    public String documentation() {
        return "Replaces regex matches in every map key or string value. mode is \"key\" or \"value\". Patterns are Java regular expressions.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "mode", "regex_pattern", "replacement");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("path", "string", "string", "string");
    }

    @Override
    public int minArgs() {
        return 4;
    }

    @Override
    public int maxArgs() {
        return 4;
    }

    @Override
    public void validateArguments(List<Expression> arguments) {
        String mode = arguments.get(1) instanceof Expression.StringLiteral literal ? literal.value() : null;
        if (!"key".equals(mode) && !"value".equals(mode)) {
            throw new OttlCompilationException(
                    "invalid mode " + (mode == null ? "non-literal" : mode) + ", must be either 'key' or 'value'");
        }
        ReplacePatternFunction.compileLiteral(arguments.get(2), name());
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Map<String, Object> map = ReplaceAllMatchesFunction.stringKeyMap(args.get(0).value(), name());
        String mode = StandardValues.requireString(args.get(1).value(), "replace_all_patterns mode");
        String pattern = StandardValues.requireString(args.get(2).value(), "replace_all_patterns regex_pattern");
        String replacement = StandardValues.requireString(args.get(3).value(), "replace_all_patterns replacement");
        Pattern compiled = ReplacePatternFunction.compile(pattern, name(), false);
        if ("value".equals(mode)) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() instanceof String text) {
                    String updated = RegexReplace.replaceAll(compiled, text, replacement);
                    if (updated != text) {
                        entry.setValue(updated);
                    }
                }
            }
            ctx.setPath(args.get(0).asLValuePath(), map);
        } else if ("key".equals(mode)) {
            Map<String, Object> updated = new LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = RegexReplace.replaceAll(compiled, entry.getKey(), replacement);
                changed |= key != entry.getKey();
                updated.put(key, entry.getValue());
            }
            ctx.setPath(args.get(0).asLValuePath(), changed ? updated : map);
        } else {
            throw new io.ottl.OttlEvaluationException(
                    "invalid mode " + mode + ", must be either 'key' or 'value'");
        }
        return null;
    }
}
