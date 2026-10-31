/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl;

/** Thrown when a compiled program fails at execution time. */
public class OttlEvaluationException extends OttlException {
    public OttlEvaluationException(String message) {
        super(message);
    }

    public OttlEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
