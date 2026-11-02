/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment.it;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.ottl.OttlEngine;
import io.ottl.rules.RuleManager;
import io.ottl.rules.RuleRepository;

@ApplicationScoped
public class ProducedOrderRules {

    static final AtomicBoolean created = new AtomicBoolean();

    @Produces
    @Singleton
    public RuleManager<ProducedOrder> rules(OttlEngine engine, RuleRepository repository) {
        created.set(true);
        return engine.ruleManager(ProducedOrder.class, repository);
    }
}
