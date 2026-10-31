/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl;

/**
 * Bounds on rule complexity, enforced at compile time, to limit the blast radius of untrusted
 * rule authors.
 *
 * @param maxSourceLength maximum number of source characters
 * @param maxStatements   maximum number of statements in a script
 * @param maxPathDepth    maximum number of segments in any path
 * @param maxCallDepth    maximum nesting depth of function calls in an expression
 */
public record OttlLimits(int maxSourceLength, int maxStatements, int maxPathDepth, int maxCallDepth) {

    public static OttlLimits defaults() {
        return new OttlLimits(10_000, 100, 16, 16);
    }
}
