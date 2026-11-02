/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

/** Build-time failure while validating or generating an {@code @OttlAccessible} accessor. */
final class AccessorGenerationException extends RuntimeException {

    AccessorGenerationException(String message) {
        super(message);
    }

    AccessorGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
