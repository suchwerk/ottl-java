/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.model;

import java.math.BigDecimal;

/** Immutable domain record used to validate COPY semantics. */
public record Order(String id, BigDecimal amount, String status, String priority) {
}
