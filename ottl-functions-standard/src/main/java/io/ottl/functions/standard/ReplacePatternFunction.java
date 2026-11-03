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

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * {@code replace_pattern(target, regex_pattern, replacement)} — replaces every match in the string
 * at {@code target}. Group references follow Go's {@code regexp.Expand}: {@code $1}, {@code ${name}},
 * and {@code $$}. A nil or non-string target is left unchanged.
 *
 * <p>Patterns are Java regular expressions, not RE2. The optional converter argument is not supported.
 */
public final class ReplacePatternFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "replace_pattern";
    }

    @Override
    public String documentation() {
        return "Replaces every regex match in the string at target. $1 and ${name} are groups; $$ is a literal dollar. Patterns are Java regular expressions.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "regex_pattern", "replacement");
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
        compileLiteral(arguments.get(1), name());
    }

    static void compileLiteral(Expression pattern, String function) {
        if (pattern instanceof Expression.StringLiteral literal) {
            compile(literal.value(), function, true);
        }
    }

    static Pattern compile(String pattern, String function, boolean atCompileTime) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            String message = function + ": invalid pattern '" + pattern + "'";
            if (atCompileTime) {
                throw new OttlCompilationException(message, e);
            }
            throw new io.ottl.OttlEvaluationException(message, e);
        }
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Object value = args.get(0).value();
        String pattern = StandardValues.requireString(args.get(1).value(), "replace_pattern regex_pattern");
        String replacement = StandardValues.requireString(args.get(2).value(), "replace_pattern replacement");
        if (value instanceof String text) {
            String updated = RegexReplace.replaceAll(compile(pattern, name(), false), text, replacement);
            if (updated != text) {
                ctx.setPath(args.get(0).asLValuePath(), updated);
            }
        }
        return null;
    }
}
