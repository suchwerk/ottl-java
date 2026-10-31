/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.rules;

import java.time.Instant;

/**
 * An immutable, versioned rule definition.
 *
 * @param id        logical rule identifier (e.g. {@code "order-rules"})
 * @param source    the OTTL source
 * @param version   monotonically increasing version, starting at 1
 * @param updatedAt when this version was activated
 */
public record RuleDefinition(String id, String source, int version, Instant updatedAt) {
}
