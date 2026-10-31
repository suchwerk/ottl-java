/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.rules.RuleManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Applies the active {@code order-rules} to orders. */
@ApplicationScoped
public class OrderService {

    @Inject
    RuleManager<Order> rules;

    public Order process(Order order) {
        return rules.apply("order-rules", order);
    }
}
