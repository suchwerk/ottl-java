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

/**
 * {@code replace_match(target, pattern, replacement)} — when the string at {@code target} matches
 * the glob {@code pattern} completely, replace it. A nil or non-string target is left unchanged.
 *
 * <p>The optional converter argument and {@code replacementFormat} are not supported. A literal
 * pattern is checked when the rule is compiled.
 */
public final class ReplaceMatchFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "replace_match";
    }

    @Override
    public String documentation() {
        return "Replaces the whole string at target when it matches a glob. * matches across '/'.";
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
        compileLiteral(arguments.get(1));
    }

    static void compileLiteral(Expression pattern) {
        if (pattern instanceof Expression.StringLiteral literal) {
            GlobPatterns.compile(literal.value(), true);
        }
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Object value = args.get(0).value();
        String pattern = StandardValues.requireString(args.get(1).value(), "replace_match pattern");
        String replacement = StandardValues.requireString(args.get(2).value(), "replace_match replacement");
        if (value instanceof String text && GlobPatterns.matches(GlobPatterns.compile(pattern, false), text)) {
            ctx.setPath(args.get(0).asLValuePath(), replacement);
        }
        return null;
    }
}
