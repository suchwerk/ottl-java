/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.internal;

import io.ottl.OttlCompilationException;
import io.ottl.OttlLimits;
import io.ottl.function.CallBinder;
import io.ottl.function.FunctionRegistry;
import io.ottl.function.OttlFunction;
import io.ottl.lang.ast.Block;
import io.ottl.lang.ast.Condition;
import io.ottl.lang.ast.Expression;
import io.ottl.lang.ast.IfStatement;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.ScriptItem;
import io.ottl.lang.ast.Statement;
import io.ottl.lang.ast.WhereBlock;

import java.util.List;

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
        int count = countStatements(script.statements());
        if (count > limits.maxStatements()) {
            throw new OttlCompilationException("Too many statements: " + count
                    + " (limit " + limits.maxStatements() + ")");
        }
        validateItems(script.statements(), 0);
    }

    private int countStatements(List<ScriptItem> items) {
        int count = 0;
        for (ScriptItem item : items) {
            count += countStatements(item);
        }
        return count;
    }

    private int countStatements(ScriptItem item) {
        return switch (item) {
            case Statement ignored -> 1;
            case WhereBlock block -> countStatements(block.body());
            case Block block -> countStatements(block.statements());
            case IfStatement branch -> {
                int count = countStatements(branch.thenBlock().statements());
                if (branch.elseBranch().isPresent()) {
                    ScriptItem other = branch.elseBranch().get();
                    count += other instanceof Block elseBlock
                            ? countStatements(elseBlock.statements())
                            : countStatements(other);
                }
                yield count;
            }
        };
    }

    private void validateItems(List<ScriptItem> items, int blockDepth) {
        for (ScriptItem item : items) {
            validateItem(item, blockDepth);
        }
    }

    private void validateItem(ScriptItem item, int blockDepth) {
        switch (item) {
            case Statement stmt -> {
                validateCall(stmt.editor(), 1);
                stmt.condition().ifPresent(this::validateCondition);
            }
            case WhereBlock block -> {
                int here = nested(blockDepth);
                validateCondition(block.condition());
                validateItems(block.body(), here);
            }
            case Block block -> {
                int here = nested(blockDepth);
                validateItems(block.statements(), here);
            }
            case IfStatement branch -> {
                int here = nested(blockDepth);
                validateCondition(branch.condition());
                validateItems(branch.thenBlock().statements(), here);
                branch.elseBranch().ifPresent(other -> {
                    if (other instanceof Block elseBlock) {
                        validateItems(elseBlock.statements(), here);
                    } else {
                        validateItem(other, here);
                    }
                });
            }
        }
    }

    private int nested(int blockDepth) {
        int here = blockDepth + 1;
        if (here > limits.maxCallDepth()) {
            throw new OttlCompilationException("Nesting too deep (limit " + limits.maxCallDepth() + ")");
        }
        return here;
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
            case Expression.MapLiteral map -> {
                for (Expression.MapEntry entry : map.entries()) {
                    validateExpression(entry.key(), callDepth);
                    validateExpression(entry.value(), callDepth);
                }
            }
            case Expression.Math math -> {
                validateExpression(math.left(), callDepth);
                validateExpression(math.right(), callDepth);
            }
            case Expression.Unary unary -> validateExpression(unary.operand(), callDepth);
            case Expression.Lambda lambda -> validateLambda(lambda, callDepth);
            case Expression.StringLiteral ignored -> {
            }
            case Expression.IntLiteral ignored -> {
            }
            case Expression.FloatLiteral ignored -> {
            }
            case Expression.BoolLiteral ignored -> {
            }
            case Expression.NilLiteral ignored -> {
            }
            case Expression.BytesLiteral ignored -> {
            }
            case Expression.EnumSymbol ignored -> {
            }
        }
    }

    private void validateLambda(Expression.Lambda lambda, int callDepth) {
        switch (lambda.body()) {
            case Expression.LambdaValue value -> validateExpression(value.expression(), callDepth);
            case Expression.LambdaCondition condition -> validateCondition(condition.condition());
        }
    }

    private void validateCall(Expression.FunctionCall call, int callDepth) {
        if (callDepth > limits.maxCallDepth()) {
            throw new OttlCompilationException("Function call nesting too deep (limit "
                    + limits.maxCallDepth() + ") at '" + call.name() + "'");
        }
        OttlFunction fn = functions.require(call.name());
        List<Expression> bound = CallBinder.expressions(fn, call);
        int argc = bound.size();
        if (argc < fn.minArgs() || argc > fn.maxArgs()) {
            throw new OttlCompilationException("Function '" + call.name() + "' expects between "
                    + fn.minArgs() + " and " + fn.maxArgs() + " arguments but got " + argc);
        }
        fn.validateArguments(bound);
        for (Expression arg : call.arguments()) {
            validateExpression(arg, callDepth + 1);
        }
        for (Expression key : call.keys()) {
            validateExpression(key, callDepth + 1);
        }
    }

    private void validatePath(Expression.Path path) {
        if (path.segments().size() > limits.maxPathDepth()) {
            throw new OttlCompilationException("Path too deep (limit " + limits.maxPathDepth() + ")");
        }
    }
}
