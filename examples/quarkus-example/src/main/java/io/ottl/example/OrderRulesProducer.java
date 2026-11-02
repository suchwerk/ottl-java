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
 * Explicit CDI registration for the parameterized {@code RuleManager<...>} beans.
 *
 * <p>Generic beans cannot be synthesized automatically by the extension, so the application declares
 * these producers. They depend on the extension-provided {@link OttlEngine} and the default (or a
 * custom) {@link RuleRepository}. One manager per domain type keeps each rule set type-checked
 * against the correct {@code @OttlAccessible} type.
 */
@ApplicationScoped
public class OrderRulesProducer {

    @Produces
    @ApplicationScoped
    public RuleManager<Order> orderRules(OttlEngine engine, RuleRepository repository) {
        return engine.ruleManager(Order.class, repository);
    }

    @Produces
    @ApplicationScoped
    public RuleManager<Customer> customerRules(OttlEngine engine, RuleRepository repository) {
        return engine.ruleManager(Customer.class, repository);
    }

    @Produces
    @ApplicationScoped
    public RuleManager<Shipment> shipmentRules(OttlEngine engine, RuleRepository repository) {
        return engine.ruleManager(Shipment.class, repository);
    }

    @Produces
    @ApplicationScoped
    public RuleManager<StockMovement> stockRules(OttlEngine engine, RuleRepository repository) {
        return engine.ruleManager(StockMovement.class, repository);
    }
}
