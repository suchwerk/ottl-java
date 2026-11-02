/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.quarkus.OttlAccessible;

/**
 * Status values a rule can name as {@code OrderStatus.PENDING}. The enum is not an editor context
 * and has no properties; only its constants are available in expressions.
 */
@OttlAccessible
public enum OrderStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    CANCELLED
}
