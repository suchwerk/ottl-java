/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.OttlEvaluationException;
import io.ottl.function.Argument;
import io.ottl.function.DescribedFunction;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code delete_index(target, startIndex, endIndex)} — removes list elements from
 * {@code startIndex} inclusive to {@code endIndex} exclusive. {@code endIndex} defaults to
 * {@code startIndex + 1}. An equal pair deletes nothing. Deleting the whole list replaces it
 * with an empty list.
 */
public final class DeleteIndexFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "delete_index";
    }

    @Override
    public String documentation() {
        return "Removes list elements from startIndex inclusive to endIndex exclusive. endIndex defaults to the next index.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "startIndex", "endIndex");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("path", "int", "int");
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public int maxArgs() {
        return 3;
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Object current = args.get(0).value();
        if (!(current instanceof List<?> list)) {
            throw new OttlEvaluationException("delete_index: target must be a list");
        }
        long start = StandardValues.requireLong(args.get(1).value(), "startIndex");
        long end = start + 1;
        if (AppendFunction.present(args, 2)) {
            end = StandardValues.requireLong(args.get(2).value(), "endIndex");
        }
        long size = list.size();
        if (start == 0 && end == size) {
            ctx.setPath(args.get(0).asLValuePath(), new ArrayList<>());
            return null;
        }
        if (start < 0 || start >= size) {
            throw new OttlEvaluationException(
                    "startIndex " + start + " out of bounds for slice of length " + size);
        }
        if (end < start) {
            throw new OttlEvaluationException(
                    "endIndex " + end + " cannot be less than startIndex " + start);
        }
        if (end > size) {
            throw new OttlEvaluationException(
                    "deletion range [" + start + ":" + end + "] out of bounds for slice of length " + size);
        }
        if (start == end) {
            return null;
        }
        List<Object> kept = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (i < start || i >= end) {
                kept.add(list.get(i));
            }
        }
        ctx.setPath(args.get(0).asLValuePath(), kept);
        return null;
    }
}
