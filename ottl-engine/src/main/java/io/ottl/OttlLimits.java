/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl;

import io.ottl.lang.parser.Parser;

/**
 * Bounds on rule complexity, enforced at compile time, to limit the blast radius of untrusted
 * rule authors.
 *
 * @param maxSourceLength maximum number of source characters
 * @param maxStatements   maximum number of editor statements in a script, including statements nested
 *                        in blocks, {@code where} blocks, and {@code if} branches
 * @param maxPathDepth    maximum number of segments in any path
 * @param maxCallDepth    maximum nesting of function calls, and of {@code where} blocks, bare blocks,
 *                        and {@code if} / {@code else if} statements. Each of those constructs counts
 *                        as one level. The braces that belong to an {@code if} or {@code else} do not
 *                        add a second level. The value must be between 1 and {@link Parser#MAX_NESTING}
 */
public record OttlLimits(int maxSourceLength, int maxStatements, int maxPathDepth, int maxCallDepth) {

    public OttlLimits {
        if (maxCallDepth < 1 || maxCallDepth > Parser.MAX_NESTING) {
            throw new IllegalArgumentException(
                    "maxCallDepth must be between 1 and " + Parser.MAX_NESTING + " (was " + maxCallDepth + ")");
        }
    }

    public static OttlLimits defaults() {
        return new OttlLimits(10_000, 100, 16, 16);
    }
}
