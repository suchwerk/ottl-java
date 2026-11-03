/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.function;

import io.ottl.OttlCompilationException;
import io.ottl.lang.ast.Expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Binds a call's positional and named arguments to the function's parameter list.
 *
 * <p>When every argument is positional, the argument list is returned unchanged, including
 * variadic editors such as {@code keep_keys}. Named arguments are reordered to the function's
 * {@link OttlFunction#parameterNames()} and may follow positional ones. A function with no
 * declared parameter names rejects named arguments.
 */
public final class CallBinder {

    private CallBinder() {
    }

    /** Argument expressions in parameter order, after named arguments have been placed. */
    public static List<Expression> expressions(OttlFunction fn, Expression.FunctionCall call) {
        if (call.argumentNames().stream().noneMatch(Optional::isPresent)) {
            return call.arguments();
        }
        List<String> params = fn.parameterNames();
        if (params.isEmpty()) {
            throw new OttlCompilationException(
                    "Function '" + fn.name() + "' does not accept named arguments");
        }
        Expression[] slots = new Expression[params.size()];
        boolean[] filled = new boolean[params.size()];
        int positional = 0;
        boolean seenNamed = false;
        List<Expression> args = call.arguments();
        List<Optional<String>> names = call.argumentNames();
        for (int i = 0; i < args.size(); i++) {
            Optional<String> name = names.get(i);
            if (name.isEmpty()) {
                if (seenNamed) {
                    throw new OttlCompilationException(
                            "Named arguments must come after positional arguments in '" + fn.name() + "'");
                }
                if (positional >= params.size()) {
                    throw new OttlCompilationException(
                            "Too many arguments for '" + fn.name() + "'");
                }
                slots[positional] = args.get(i);
                filled[positional] = true;
                positional++;
            } else {
                seenNamed = true;
                int index = params.indexOf(name.get());
                if (index < 0) {
                    throw new OttlCompilationException(
                            "Unknown argument '" + name.get() + "' for function '" + fn.name() + "'");
                }
                if (filled[index]) {
                    throw new OttlCompilationException(
                            "Duplicate argument '" + name.get() + "' for function '" + fn.name() + "'");
                }
                slots[index] = args.get(i);
                filled[index] = true;
            }
        }
        for (int i = 0; i < params.size(); i++) {
            if (!filled[i]) {
                if (i < fn.minArgs()) {
                    throw new OttlCompilationException(
                            "Missing argument '" + params.get(i) + "' for function '" + fn.name() + "'");
                }
                for (int j = i + 1; j < params.size(); j++) {
                    if (filled[j]) {
                        throw new OttlCompilationException(
                                "Missing argument '" + params.get(i) + "' for function '" + fn.name() + "'");
                    }
                }
                break;
            }
        }
        List<Expression> bound = new ArrayList<>();
        for (int i = 0; i < params.size() && filled[i]; i++) {
            bound.add(slots[i]);
        }
        return List.copyOf(bound);
    }

    /** Bound arguments ready to pass to {@link OttlFunction#invoke}. */
    public static List<Argument> bind(OttlFunction fn, Expression.FunctionCall call, ExecutionContext ctx) {
        List<Expression> bound = expressions(fn, call);
        List<Argument> args = new ArrayList<>(bound.size());
        for (Expression expression : bound) {
            args.add(new Argument(expression, ctx));
        }
        return args;
    }
}
