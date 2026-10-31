/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.internal;

import io.ottl.OttlCompilationException;
import io.ottl.OttlLimits;
import io.ottl.function.FunctionRegistry;
import io.ottl.function.OttlFunction;
import io.ottl.lang.ast.Condition;
import io.ottl.lang.ast.Expression;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.Statement;

/**
 * Compile-time validation: enforces complexity limits and checks that every function is allowlisted
 * with a valid arity, before a program is ever executed or activated.
 */
public final class Validator {

    private final FunctionRegistry functions;
    private final OttlLimits limits;

    public Validator(FunctionRegistry functions, OttlLimits limits) {
        this.functions = functions;
        this.limits = limits;
    }

    public void validate(OttlScript script) {
        if (script.statements().size() > limits.maxStatements()) {
            throw new OttlCompilationException("Too many statements: " + script.statements().size()
                    + " (limit " + limits.maxStatements() + ")");
        }
        for (Statement stmt : script.statements()) {
            validateCall(stmt.editor(), 1);
            stmt.condition().ifPresent(this::validateCondition);
        }
    }

    private void validateCondition(Condition condition) {
        switch (condition) {
            case Condition.And and -> {
                validateCondition(and.left());
                validateCondition(and.right());
            }
            case Condition.Or or -> {
                validateCondition(or.left());
                validateCondition(or.right());
            }
            case Condition.Not not -> validateCondition(not.condition());
            case Condition.Comparison cmp -> {
                validateExpression(cmp.left(), 1);
                validateExpression(cmp.right(), 1);
            }
            case Condition.ValueCondition vc -> validateExpression(vc.expression(), 1);
        }
    }

    private void validateExpression(Expression expr, int callDepth) {
        switch (expr) {
            case Expression.FunctionCall call -> validateCall(call, callDepth);
            case Expression.Path path -> validatePath(path);
            case Expression.ListLiteral list -> {
                for (Expression e : list.elements()) {
                    validateExpression(e, callDepth);
                }
            }
            default -> {
                // literals need no validation
            }
        }
    }

    private void validateCall(Expression.FunctionCall call, int callDepth) {
        if (callDepth > limits.maxCallDepth()) {
            throw new OttlCompilationException("Function call nesting too deep (limit "
                    + limits.maxCallDepth() + ") at '" + call.name() + "'");
        }
        OttlFunction fn = functions.require(call.name());
        int argc = call.arguments().size();
        if (argc < fn.minArgs() || argc > fn.maxArgs()) {
            throw new OttlCompilationException("Function '" + call.name() + "' expects between "
                    + fn.minArgs() + " and " + fn.maxArgs() + " arguments but got " + argc);
        }
        for (Expression arg : call.arguments()) {
            validateExpression(arg, callDepth + 1);
        }
    }

    private void validatePath(Expression.Path path) {
        if (path.segments().size() > limits.maxPathDepth()) {
            throw new OttlCompilationException("Path too deep (limit " + limits.maxPathDepth() + ")");
        }
    }
}
