/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment.it;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.ottl.rules.RuleManager;
import io.quarkus.test.QuarkusUnitTest;

class ExplicitRuleManagerOverrideTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ProducedOrder.class, ProducedOrderRules.class));

    @Inject
    RuleManager<ProducedOrder> rules;

    @Test
    void applicationProducerReplacesTheDefaultBean() {
        assertThat(ProducedOrderRules.created).isTrue();
        rules.activate("r", "set(producedOrder.status, \"OK\")");
        assertThat(rules.apply("r", new ProducedOrder("1", "NEW")).status()).isEqualTo("OK");
    }
}
