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

/**
 * {@code ConvertCase(target, toCase)} — {@code lower}, {@code upper}, {@code snake}, or
 * {@code camel}. {@code toCase} is a string literal and is rejected at compile time when it is
 * anything else, matching the collector factory.
 */
public final class ConvertCaseFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "ConvertCase";
    }

    @Override
    public String documentation() {
        return "Converts a string to lower, upper, snake, or camel case. camel is PascalCase: my_metric becomes MyMetric.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "toCase");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("string", "string");
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
        String toCase = arguments.get(1) instanceof Expression.StringLiteral literal ? literal.value() : null;
        if (toCase == null || !CaseStyles.NAMES.contains(toCase)) {
            throw new OttlCompilationException("invalid case: " + (toCase == null ? "non-literal" : toCase)
                    + ", allowed cases are: lower, upper, snake, camel");
        }
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        String target = StandardValues.requireString(args.get(0).value(), "ConvertCase target");
        String toCase = StandardValues.requireString(args.get(1).value(), "ConvertCase toCase");
        if (!CaseStyles.NAMES.contains(toCase)) {
            throw new OttlEvaluationException("invalid case: " + toCase
                    + ", allowed cases are: lower, upper, snake, camel");
        }
        return CaseStyles.convert(target, toCase);
    }
}
