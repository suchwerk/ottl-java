/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.model;

/** Nested records used to validate copy-on-write bubbling through immutable structures. */
public record Shipment(String id, Address address) {

    public record Address(String city, String zip) {
    }
}
