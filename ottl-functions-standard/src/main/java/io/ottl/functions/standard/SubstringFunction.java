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
 * {@code Substring(target, start, length, utf8_safe)} — a byte window into the UTF-8 encoding of
 * {@code target}. {@code start} must be at least 0 and {@code length} must be greater than 0, and
 * {@code start + length} must not run past the byte length. {@code utf8_safe} defaults to false.
 * A window that splits a character is decoded with Java's UTF-8 decoder, which replaces an
 * incomplete sequence with U+FFFD. Go keeps those raw bytes.
 */
public final class SubstringFunction implements OttlFunction, DescribedFunction {

    @Override
    public String name() {
        return "Substring";
    }

    @Override
    public String documentation() {
        return "Returns the UTF-8 byte window [start, start+length). utf8_safe defaults to false and snaps a mid-character window onto character boundaries.";
    }

    @Override
    public List<String> parameterNames() {
        return List.of("target", "start", "length", "utf8_safe");
    }

    @Override
    public List<String> parameterTypeNames() {
        return List.of("string", "int", "int", "bool");
    }

    @Override
    public int minArgs() {
        return 3;
    }

    @Override
    public int maxArgs() {
        return 4;
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        String target = StandardValues.requireString(args.get(0).value(), "Substring target");
        long start = StandardValues.requireLong(args.get(1).value(), "start");
        long length = StandardValues.requireLong(args.get(2).value(), "length");
        boolean utf8Safe = args.size() > 3 && StandardValues.requireBool(args.get(3).value(), "utf8_safe");
        return StandardValues.substring(target, start, length, utf8Safe);
    }
}
