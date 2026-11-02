/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.OttlEngine;
import io.ottl.OttlProgram;
import io.ottl.lang.OttlDialect;
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
    void extendedDialectCompilesConditionalRules() {
        assertThat(engine.dialect()).isEqualTo(OttlDialect.EXTENDED);
        OttlProgram<Order> program = engine.compile("""
                if order.amount > 10000 {
                    set(order.status, OrderStatus.CANCELLED)
                } else {
                    set(order.priority, "LOW")
                }
                """, Order.class);
        Order high = program.apply(new Order("o", new BigDecimal("15000"), OrderStatus.PENDING, "HIGH"));
        assertThat(high.status()).isEqualTo(OrderStatus.CANCELLED);
        Order low = program.apply(new Order("o", BigDecimal.TEN, OrderStatus.PENDING, "HIGH"));
        assertThat(low.priority()).isEqualTo("LOW");
        assertThat(low.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void serviceAppliesActiveRule() {
        Order result = service.process(new Order("o", new BigDecimal("20000"), OrderStatus.PENDING, "LOW"));
        assertThat(result.status()).isEqualTo(OrderStatus.PROCESSING);
    }
}
