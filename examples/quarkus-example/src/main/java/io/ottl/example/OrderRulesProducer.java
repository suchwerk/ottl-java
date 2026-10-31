/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.OttlEngine;
import io.ottl.rules.RuleManager;
import io.ottl.rules.RuleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Explicit CDI registration for the parameterized {@code RuleManager<Order>}.
 *
 * <p>Generic beans cannot be synthesized automatically by the extension, so the application declares
 * this producer. It depends on the extension-provided {@link OttlEngine} and the default (or a custom)
 * {@link RuleRepository}.
 */
@ApplicationScoped
public class OrderRulesProducer {

    @Produces
    @ApplicationScoped
    public RuleManager<Order> orderRules(OttlEngine engine, RuleRepository repository) {
        return engine.ruleManager(Order.class, repository);
    }
}
