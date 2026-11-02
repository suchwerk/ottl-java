/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.quarkus.OttlAccessible;

/** Nested immutable record used to demonstrate nested property access and COPY bubbling. */
@OttlAccessible
public record Address(String street, String city, String zip) {
}
