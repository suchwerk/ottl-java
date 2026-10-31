/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

/** REST view of an active rule. */
public record RuleView(String id, String source, int version) {
}
