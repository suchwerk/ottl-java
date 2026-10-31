/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.rules.RuleManager;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/** Activates a default rule at startup so the app is useful out of the box. */
@ApplicationScoped
public class RuleBootstrap {

    static final String DEFAULT_RULE =
            "set(order.status, \"REVIEW\") where IsHighValue(order.amount)";

    void onStart(@Observes StartupEvent event, RuleManager<Order> rules) {
        rules.activate("order-rules", DEFAULT_RULE);
    }
}
