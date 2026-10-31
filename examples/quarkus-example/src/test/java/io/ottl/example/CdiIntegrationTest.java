/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.OttlEngine;
import io.ottl.rules.RuleManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the extension's CDI wiring: the engine, the rule manager and custom functions. */
@QuarkusTest
class CdiIntegrationTest {

    @Inject
    OttlEngine engine;

    @Inject
    RuleManager<Order> rules;

    @Inject
    OrderService service;

    @Test
    void extensionBeansAreInjectable() {
        assertThat(engine).isNotNull();
        assertThat(rules).isNotNull();
        assertThat(service).isNotNull();
    }

    @Test
    void customOttlFunctionIsRegistered() {
        assertThat(engine.functions().contains("IsHighValue")).isTrue();
    }

    @Test
    void serviceAppliesActiveRule() {
        Order result = service.process(new Order("o", new BigDecimal("20000"), "NEW", "LOW"));
        assertThat(result.status()).isEqualTo("REVIEW");
    }
}
