/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.function;

import io.ottl.OttlEvaluationException;
import io.ottl.lang.ast.Expression;

import java.util.List;

/**
 * A single argument passed to an {@link OttlFunction}. It can be evaluated as an r-value
 * ({@link #value()}) or, when it is a path, used as an l-value target ({@link #asLValuePath()}).
 */
public final class Argument {

    private final Expression expression;
    private final ExecutionContext ctx;

    private static final Argument ABSENT = new Argument(null, null);

    public Argument(Expression expression, ExecutionContext ctx) {
        this.expression = expression;
        this.ctx = ctx;
    }

    /** An optional parameter that the caller omitted while naming a later one. */
    public static Argument absent() {
        return ABSENT;
    }

    public Expression expression() {
        return expression;
    }

    /** False when this slot was an omitted optional parameter. */
    public boolean isPresent() {
        return expression != null;
    }

    /** Evaluate the argument to a value. */
    public Object value() {
        if (expression == null) {
            throw new OttlEvaluationException("optional argument was not provided");
        }
        return ctx.evaluate(expression);
    }

    public boolean isPath() {
        return expression instanceof Expression.Path;
    }

    /**
     * @return the path segments (with the context-name prefix stripped) when this argument is a
     *     path l-value, e.g. the first argument of {@code set(...)}.
     * @throws OttlEvaluationException if the argument is not a path
     */
    public List<Expression.PathSegment> asLValuePath() {
        if (expression == null) {
            throw new OttlEvaluationException("optional argument was not provided");
        }
        if (!(expression instanceof Expression.Path p)) {
            throw new OttlEvaluationException(
                    "Expected a path (assignable target) but got " + expression.getClass().getSimpleName());
        }
        return ctx.strip(p);
    }
}
