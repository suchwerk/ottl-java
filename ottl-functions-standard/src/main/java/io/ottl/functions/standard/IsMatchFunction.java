/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.function.Argument;
import io.ottl.function.DescribedFunction;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;

import java.util.List;

/**
 * {@code IsMatch(target, pattern)} — true when {@code pattern} matches anywhere in the target,
 * the same unanchored search as Go's {@code regexp.MatchString}. Nil is false.
 *
 * <p>Patterns are Java regular expressions, not RE2. Named groups use {@code (?<name>...)}, and a
 * hostile pattern can backtrack for a long time. A pattern that fails to compile is an evaluation
 * error.
 */
public final class IsMatchFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "IsMatch";
    }

    @Override
    public String documentation() {
        return "True if the Java regular expression matches anywhere in the target. Nil is false. This is not RE2.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "pattern");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("any", "string");
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
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Object target = args.get(0).value();
        if (target == null) {
            return Boolean.FALSE;
        }
        String pattern = StandardValues.requireString(args.get(1).value(), "IsMatch pattern");
        return StandardValues.matches(StandardValues.matchText(target), pattern);
    }
}
