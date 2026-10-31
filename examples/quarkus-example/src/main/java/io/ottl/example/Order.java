/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;

/**
 * Example immutable domain record. Registered for reflection so the record resolver works in a
 * Quarkus native image.
 */
@RegisterForReflection
public record Order(String id, BigDecimal amount, String status, String priority) {
}
