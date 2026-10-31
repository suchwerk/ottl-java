/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.rules;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** A thread-safe, in-memory {@link RuleRepository} for the MVP and tests. */
public final class InMemoryRuleRepository implements RuleRepository {

    private final ConcurrentMap<String, RuleDefinition> store = new ConcurrentHashMap<>();

    @Override
    public Optional<RuleDefinition> find(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void save(RuleDefinition definition) {
        store.put(definition.id(), definition);
    }
}
