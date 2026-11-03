/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.function;

import io.ottl.OttlEvaluationException;
import io.ottl.lang.ast.Expression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A lambda value produced by {@code (params) => body}. Calling {@link #apply} evaluates the body
 * with read-only parameter bindings. No standard editor takes a lambda; a registered function
 * can accept one and call {@link #apply}.
 */
public final class OttlLambda {

    private final Expression.Lambda lambda;

    public OttlLambda(Expression.Lambda lambda) {
        this.lambda = lambda;
    }

    public List<String> parameters() {
        return lambda.parameters();
    }

    /** Evaluate the body. {@code arguments} aligns with {@link #parameters()} by position. */
    public Object apply(ExecutionContext ctx, List<Object> arguments) {
        List<String> params = lambda.parameters();
        if (arguments.size() != params.size()) {
            throw new OttlEvaluationException(
                    "Lambda expects " + params.size() + " arguments but got " + arguments.size());
        }
        Map<String, Object> bindings = new LinkedHashMap<>();
        for (int i = 0; i < params.size(); i++) {
            String name = params.get(i);
            if (!name.equals("_")) {
                bindings.put(name, arguments.get(i));
            }
        }
        ctx.pushLocals(bindings);
        try {
            return switch (lambda.body()) {
                case Expression.LambdaValue value -> ctx.evaluate(value.expression());
                case Expression.LambdaCondition condition -> Evaluator.evalCondition(ctx, condition.condition());
            };
        } finally {
            ctx.popLocals();
        }
    }
}
