/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.rules;

import java.util.Optional;

/**
 * Storage for {@link RuleDefinition}s. The MVP ships an in-memory implementation; the interface is
 * intentionally small so a PostgreSQL- or config-server-backed implementation can be added later
 * without touching the engine.
 */
public interface RuleRepository {

    /** Find the currently stored definition for a rule id. */
    Optional<RuleDefinition> find(String id);

    /** Persist (or replace) a rule definition. */
    void save(RuleDefinition definition);
}
