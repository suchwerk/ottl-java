/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.quarkus.OttlAccessible;

/** Example record used by the editor to demonstrate a fourth accessible context type. */
@OttlAccessible
public record StockMovement(String id, String sku, int quantity, String direction) {
}
