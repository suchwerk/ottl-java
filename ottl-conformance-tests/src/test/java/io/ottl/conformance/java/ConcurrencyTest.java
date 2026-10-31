/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.java;

import io.ottl.OttlEngine;
import io.ottl.OttlProfile;
import io.ottl.conformance.model.Order;
import io.ottl.rules.InMemoryRuleRepository;
import io.ottl.rules.RuleManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Concurrent reads (apply) while rules are being replaced must be safe and consistent. */
class ConcurrencyTest {

    @Test
    void concurrentApplyAndActivateAreThreadSafe() throws Exception {
        OttlEngine engine = OttlEngine.builder().profile(OttlProfile.JAVA).build();
        RuleManager<Order> rules = engine.ruleManager(Order.class, new InMemoryRuleRepository());
        rules.activate("r", "set(order.status, \"V1\")");

        int readers = 8;
        int iterations = 5_000;
        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        AtomicInteger errors = new AtomicInteger();

        // Writer thread continuously swaps the rule.
        Future<?> writer = pool.submit(() -> {
            for (int i = 0; i < 500; i++) {
                try {
                    rules.activate("r", "set(order.status, \"V" + (i % 3) + "\")");
                } catch (RuntimeException e) {
                    errors.incrementAndGet();
                }
            }
        });

        // Reader threads continuously apply the active rule.
        Future<?>[] futures = new Future<?>[readers];
        for (int t = 0; t < readers; t++) {
            futures[t] = pool.submit(() -> {
                Order order = new Order("o", new BigDecimal("1"), "NEW", "LOW");
                for (int i = 0; i < iterations; i++) {
                    Order result = rules.apply("r", order);
                    // Every active version sets status to "V0".."V2" (or the initial "V1").
                    if (result.status() == null || !result.status().startsWith("V")) {
                        errors.incrementAndGet();
                    }
                }
            });
        }

        writer.get(60, TimeUnit.SECONDS);
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertThat(errors.get()).isZero();
    }
}
