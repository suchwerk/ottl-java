/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** REST view of an active rule. Registered for reflection so Jackson can serialize it natively. */
@RegisterForReflection
public record RuleView(String id, String source, int version) {
}
