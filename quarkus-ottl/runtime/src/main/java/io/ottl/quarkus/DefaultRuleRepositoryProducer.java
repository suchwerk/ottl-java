/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus;

import io.ottl.rules.InMemoryRuleRepository;
import io.ottl.rules.RuleRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Provides a default in-memory {@link RuleRepository}. Applications can override this by declaring
 * their own {@code @ApplicationScoped RuleRepository} bean (e.g. backed by PostgreSQL).
 */
public class DefaultRuleRepositoryProducer {

    @Produces
    @DefaultBean
    @ApplicationScoped
    public RuleRepository ruleRepository() {
        return new InMemoryRuleRepository();
    }
}
