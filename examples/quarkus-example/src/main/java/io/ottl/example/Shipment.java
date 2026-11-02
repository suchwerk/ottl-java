/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.quarkus.OttlAccessible;

/**
 * Example record with a nested {@link Address} record. Updating {@code shipment.address.city}
 * reconstructs both the {@code Address} and the enclosing {@code Shipment} (COPY bubbling).
 */
@OttlAccessible
public record Shipment(String id, Address address, String carrier) {
}
