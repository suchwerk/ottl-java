/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl;

/**
 * Thrown when OTTL source cannot be compiled (parse errors, unknown functions, invalid arity,
 * exceeded complexity limits, etc.). Used to validate rule source before activation.
 */
public class OttlCompilationException extends OttlException {
    public OttlCompilationException(String message) {
        super(message);
    }

    public OttlCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
