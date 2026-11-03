/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.function.OttlFunction;

import java.util.List;

/**
 * The editors and converters shipped in this module.
 *
 * <p>The plain engine discovers the same classes through
 * {@code META-INF/services/io.ottl.function.OttlFunction}. The Quarkus recorder does not use
 * {@code ServiceLoader}; it registers {@link #all()} itself.
 * Those two lists are the same set of classes; a conformance test checks that they stay aligned.
 */
public final class StandardFunctions {

    private StandardFunctions() {
    }

    public static List<OttlFunction> all() {
        return List.of(
                new SetFunction(),
                new DeleteKeyFunction(),
                new KeepKeysFunction(),
                new ConcatFunction(),
                new DoubleFunction(),
                new IntFunction(),
                new IsMatchFunction(),
                new LenFunction(),
                new SplitFunction(),
                new StringFunction(),
                new SubstringFunction(),
                new TrimFunction());
    }
}
