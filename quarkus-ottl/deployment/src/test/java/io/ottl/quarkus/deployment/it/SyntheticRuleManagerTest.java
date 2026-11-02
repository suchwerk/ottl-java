/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment.it;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.ottl.rules.RuleManager;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

class SyntheticRuleManagerTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(SampleOrder.class));

    @Inject
    RuleManager<SampleOrder> rules;

    @Test
    void injectsAManagerForTheAccessibleType() {
        rules.activate("r", "set(sampleOrder.status, \"OK\")");
        SampleOrder updated = rules.apply("r", new SampleOrder("1", "NEW"));
        assertThat(updated.status()).isEqualTo("OK");
        assertThat(new SampleOrder("1", "NEW").status()).isEqualTo("NEW");
    }
}
