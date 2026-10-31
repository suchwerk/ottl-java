/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl;

/** Base type for all OTTL engine errors. */
public class OttlException extends RuntimeException {
    public OttlException(String message) {
        super(message);
    }

    public OttlException(String message, Throwable cause) {
        super(message, cause);
    }
}
