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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * {@code truncate_all(target, limit, utf8Safe, truncationMarker)} — shortens every string value
 * of the map at {@code target} to at most {@code limit} UTF-8 bytes. Non-strings are ignored.
 * {@code utf8Safe} defaults to true and backs up to a character boundary. {@code truncationMarker}
 * defaults to empty and counts against the limit. {@code limit}, {@code utf8Safe}, and
 * {@code truncationMarker} are literals.
 */
public final class TruncateAllFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "truncate_all";
    }

    @Override
    public String documentation() {
        return "Truncates every string value to at most limit UTF-8 bytes. utf8Safe defaults to true. truncationMarker counts against the limit.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "limit", "utf8Safe", "truncationMarker");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("path", "int", "bool", "string");
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public int maxArgs() {
        return 4;
    }

    @Override
    public void validateArguments(List<Expression> arguments) {
        Expression limitExpr = arguments.get(1);
        if (!(limitExpr instanceof Expression.IntLiteral limit)) {
            throw new OttlCompilationException("truncate_all: limit must be an int literal");
        }
        if (limit.value() < 0) {
            throw new OttlCompilationException(
                    "invalid limit for truncate_all function, " + limit.value() + " cannot be negative");
        }
        Expression utf8Safe = AppendFunction.argument(arguments, 2);
        if (utf8Safe != null && !(utf8Safe instanceof Expression.BoolLiteral)) {
            throw new OttlCompilationException("truncate_all: utf8Safe must be a bool literal");
        }
        Expression marker = AppendFunction.argument(arguments, 3);
        if (marker != null && !(marker instanceof Expression.StringLiteral)) {
            throw new OttlCompilationException("truncate_all: truncationMarker must be a string literal");
        }
        if (marker instanceof Expression.StringLiteral literal) {
            int markerBytes = literal.value().getBytes(StandardCharsets.UTF_8).length;
            if (markerBytes > limit.value()) {
                throw new OttlCompilationException(
                        "invalid truncation marker for truncate_all function, length of marker " + markerBytes
                                + " cannot be greater than limit " + limit.value());
            }
        }
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Map<String, Object> map = ReplaceAllMatchesFunction.stringKeyMap(args.get(0).value(), name());
        long limit = StandardValues.requireLong(args.get(1).value(), "limit");
        if (limit < 0) {
            throw new OttlEvaluationException(
                    "invalid limit for truncate_all function, " + limit + " cannot be negative");
        }
        boolean utf8Safe = true;
        if (AppendFunction.present(args, 2)) {
            utf8Safe = StandardValues.requireBool(args.get(2).value(), "utf8Safe");
        }
        String marker = "";
        if (AppendFunction.present(args, 3)) {
            marker = StandardValues.requireString(args.get(3).value(), "truncate_all truncationMarker");
        }
        int markerBytes = marker.getBytes(StandardCharsets.UTF_8).length;
        if (markerBytes > limit) {
            throw new OttlEvaluationException(
                    "invalid truncation marker for truncate_all function, length of marker " + markerBytes
                            + " cannot be greater than limit " + limit);
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof String text) {
                String updated = StandardValues.truncate(text, limit, utf8Safe, marker);
                if (!updated.equals(text)) {
                    entry.setValue(updated);
                }
            }
        }
        ctx.setPath(args.get(0).asLValuePath(), map);
        return null;
    }
}
