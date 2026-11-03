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
import java.util.List;

/**
 * {@code append(target, value, values)} — appends one value, each element of a list, or both.
 * A scalar target becomes the first element. A missing target becomes a new list. A list target
 * is copied, then extended. At least one of {@code value} or {@code values} is required. A map
 * target is rejected.
 */
public final class AppendFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "append";
    }

    @Override
    public String documentation() {
        return "Appends value and each element of values to the list at target. A scalar target becomes the first element. At least one of value or values is required.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "value", "values");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("path", "any", "list");
    }

    @Override
    public int minArgs() {
        return 1;
    }

    @Override
    public int maxArgs() {
        return 3;
    }

    @Override
    public void validateArguments(List<Expression> arguments) {
        if (argument(arguments, 1) == null && argument(arguments, 2) == null) {
            throw new OttlCompilationException(
                    "at least one of the optional arguments ('value' or 'values') must be provided");
        }
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Object current = args.get(0).value();
        List<Object> result = new ArrayList<>();
        if (current instanceof List<?> list) {
            for (Object element : list) {
                result.add(StandardValues.copy(element));
            }
        } else if (current != null) {
            if (!isScalar(current)) {
                throw new OttlEvaluationException("append: unsupported type of target field");
            }
            result.add(current);
        }
        if (present(args, 1)) {
            result.add(StandardValues.copy(args.get(1).value()));
        }
        if (present(args, 2)) {
            Object values = args.get(2).value();
            if (!(values instanceof List<?> list)) {
                throw new OttlEvaluationException("append: values must be a list");
            }
            for (Object element : list) {
                result.add(StandardValues.copy(element));
            }
        }
        ctx.setPath(args.get(0).asLValuePath(), result);
        return null;
    }

    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Boolean || value instanceof Number;
    }

    static Expression argument(List<Expression> arguments, int index) {
        return index < arguments.size() ? arguments.get(index) : null;
    }

    static boolean present(List<Argument> args, int index) {
        return index < args.size() && args.get(index).isPresent();
    }
}
