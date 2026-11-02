/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.quarkus.OttlAccessible;

import java.math.BigDecimal;

/**
 * Example immutable domain record. {@code @OttlAccessible} makes the extension generate a
 * reflection-free property accessor at build time and register the type for Jackson serialization,
 * so it works in a Quarkus native image with COPY (immutable) semantics.
 */
@OttlAccessible
public record Order(String id, BigDecimal amount, String status, String priority) {
}
