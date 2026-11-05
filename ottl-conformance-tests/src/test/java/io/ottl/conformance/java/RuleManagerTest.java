/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.java;

import io.ottl.OttlCompilationException;
import io.ottl.OttlEngine;
import io.ottl.OttlProfile;
import io.ottl.conformance.model.Order;
import io.ottl.rules.InMemoryRuleRepository;
import io.ottl.rules.RuleDefinition;
import io.ottl.rules.RuleManager;
import io.ottl.rules.RuleRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runtime rule activation, replacement, versioning and validation. */
class RuleManagerTest {

    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.JAVA).build();

    @Test
    void ruleCanBeReplacedAtRuntimeWithoutRestart() {
        RuleRepository repo = new InMemoryRuleRepository();
        RuleManager<Order> rules = engine.ruleManager(Order.class, repo);

        rules.activate("order-rules", "set(order.status, \"REVIEW\") where order.amount > 10000");
        Order o = new Order("o-1", new BigDecimal("15000"), "NEW", "HIGH");
        assertThat(rules.apply("order-rules", o).status()).isEqualTo("REVIEW");
        assertThat(rules.active("order-rules").map(RuleDefinition::version)).contains(1);

        // Replace the rule at runtime.
        rules.activate("order-rules", "set(order.priority, \"URGENT\") where order.amount > 10000");
        Order after = rules.apply("order-rules", o);
        assertThat(after.priority()).isEqualTo("URGENT");
        assertThat(after.status()).isEqualTo("NEW"); // new rule no longer sets status
        assertThat(rules.active("order-rules").map(RuleDefinition::version)).contains(2);
    }

    @Test
    void invalidRuleRetainsPreviousVersion() {
        RuleManager<Order> rules = engine.ruleManager(Order.class, new InMemoryRuleRepository());
        rules.activate("r", "set(order.status, \"A\")");

        assertThatThrownBy(() -> rules.activate("r", "set(order.status, )")) // parse error
                .isInstanceOf(OttlCompilationException.class);

        // previous version is retained and still works
        assertThat(rules.active("r").map(RuleDefinition::version)).contains(1);
        Order o = new Order("o", new BigDecimal("1"), "NEW", "LOW");
        assertThat(rules.apply("r", o).status()).isEqualTo("A");
    }

    @Test
    void activationContinuesTheStoredVersion() {
        RuleRepository repo = new InMemoryRuleRepository();
        repo.save(new RuleDefinition("r", "set(order.status, \"A\")", 4, Instant.EPOCH));
        RuleManager<Order> rules = engine.ruleManager(Order.class, repo);

        rules.activate("r", "set(order.status, \"B\")");

        assertThat(rules.active("r")).hasValueSatisfying(def -> {
            assertThat(def.version()).isEqualTo(5);
            assertThat(def.source()).isEqualTo("set(order.status, \"B\")");
        });
        Order order = new Order("o", BigDecimal.ONE, "NEW", "LOW");
        assertThat(rules.apply("r", order).status()).isEqualTo("B");
    }

    @Test
    void failedActivationKeepsTheStoredRule() {
        RuleRepository repo = new InMemoryRuleRepository();
        repo.save(new RuleDefinition("r", "set(order.status, \"A\")", 4, Instant.EPOCH));
        RuleManager<Order> rules = engine.ruleManager(Order.class, repo);

        assertThatThrownBy(() -> rules.activate("r", "set(order.status, )"))
                .isInstanceOf(OttlCompilationException.class);

        assertThat(rules.active("r")).hasValueSatisfying(def -> {
            assertThat(def.version()).isEqualTo(4);
            assertThat(def.source()).isEqualTo("set(order.status, \"A\")");
        });
        Order order = new Order("o", BigDecimal.ONE, "NEW", "LOW");
        assertThat(rules.apply("r", order).status()).isEqualTo("A");
    }

    @Test
    void applyingUnknownRuleFails() {
        RuleManager<Order> rules = engine.ruleManager(Order.class, new InMemoryRuleRepository());
        Order o = new Order("o", BigDecimal.ONE, "NEW", "LOW");
        assertThatThrownBy(() -> rules.apply("missing", o))
                .hasMessageContaining("No active rule");
    }
}
