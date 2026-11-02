/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.java;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ottl.OttlCompilationException;
import io.ottl.OttlEngine;
import io.ottl.OttlEvaluationException;
import io.ottl.OttlLimits;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import io.ottl.conformance.model.Customer;
import io.ottl.conformance.model.Order;
import io.ottl.conformance.model.Shipment;
import io.ottl.function.Argument;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;
import io.ottl.lang.OttlDialect;
import io.ottl.rules.InMemoryRuleRepository;
import io.ottl.rules.RuleDefinition;
import io.ottl.rules.RuleManager;
import io.ottl.spi.DefaultPropertyAccessorRegistry;
import io.ottl.spi.PropertyAccessor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Blocks and {@code if}/{@code else} in {@link OttlDialect#EXTENDED}. */
class ControlFlowTest {

    private final OttlEngine extended = OttlEngine.builder()
            .profile(OttlProfile.JAVA)
            .dialect(OttlDialect.EXTENDED)
            .build();
    private final OttlEngine standardExtended = OttlEngine.builder()
            .profile(OttlProfile.STANDARD)
            .dialect(OttlDialect.EXTENDED)
            .build();
    private final OttlEngine ottl = OttlEngine.builder().profile(OttlProfile.JAVA).build();

    @Test
    void defaultDialectRejectsExtendedSyntaxAndKeepsThePreviousRule() {
        assertThat(ottl.dialect()).isEqualTo(OttlDialect.OTTL);
        assertThatThrownBy(() -> ottl.compile("""
                if order.amount > 1 {
                    set(order.status, "REVIEW")
                }
                """, Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Language feature 'if/else' requires OttlDialect.EXTENDED");

        RuleManager<Order> rules = ottl.ruleManager(Order.class, new InMemoryRuleRepository());
        rules.activate("r", "set(order.status, \"A\")");
        assertThatThrownBy(() -> rules.activate("r", """
                if order.amount > 1 {
                    set(order.status, "B")
                }
                """)).isInstanceOf(OttlCompilationException.class);
        Order order = new Order("o", BigDecimal.ONE, "NEW", "LOW");
        assertThat(rules.apply("r", order).status()).isEqualTo("A");
        assertThat(rules.active("r").map(RuleDefinition::version)).contains(1);
    }

    @Test
    void ifElseRunsOnlyTheMatchingBranch() {
        OttlProgram<Order> program = extended.compile("""
                if order.amount > 10000 {
                    set(order.status, "REVIEW")
                } else {
                    set(order.status, "APPROVED")
                }
                """, Order.class);
        Order high = new Order("o-1", new BigDecimal("15000"), "NEW", "LOW");
        Order low = new Order("o-2", new BigDecimal("500"), "NEW", "LOW");

        assertThat(program.apply(high).status()).isEqualTo("REVIEW");
        assertThat(high.status()).isEqualTo("NEW");
        assertThat(program.apply(low).status()).isEqualTo("APPROVED");
        assertThat(low.status()).isEqualTo("NEW");
    }

    @Test
    void elseIfSelectsTheFirstMatchAndPreservesOrder() {
        String source = """
                if order.amount > 10000 {
                    set(order.status, "REVIEW")
                    set(order.priority, "HIGH")
                } else if order.amount > 5000 {
                    set(order.priority, "MEDIUM")
                } else {
                    set(order.priority, "LOW")
                }
                """;
        assertThat(apply(source, "15000").status()).isEqualTo("REVIEW");
        assertThat(apply(source, "15000").priority()).isEqualTo("HIGH");
        assertThat(apply(source, "6000").priority()).isEqualTo("MEDIUM");
        assertThat(apply(source, "6000").status()).isEqualTo("NEW");
        assertThat(apply(source, "100").priority()).isEqualTo("LOW");
    }

    @Test
    void nestedBlocksAndConditionsShareOneRoot() {
        OttlProgram<Order> program = extended.compile("""
                {
                    set(order.status, "REVIEW")
                    {
                        if order.amount > 1000 {
                            set(order.priority, "HIGH")
                        } else {
                            set(order.priority, "LOW")
                        }
                    }
                }
                """, Order.class);
        Order result = program.apply(new Order("o", new BigDecimal("1500"), "NEW", "MEDIUM"));
        assertThat(result.status()).isEqualTo("REVIEW");
        assertThat(result.priority()).isEqualTo("HIGH");
    }

    @Test
    void emptyBlockLeavesTheValueUnchanged() {
        OttlProgram<Order> program = extended.compile("{ }", Order.class);
        Order original = new Order("o", BigDecimal.ONE, "NEW", "LOW");
        assertThat(program.apply(original)).isEqualTo(original);
    }

    @Test
    void skippedBranchesAreNotEvaluatedAndConditionsShortCircuit() {
        Probe boom = new Probe("Boom", true);
        OttlEngine engine = OttlEngine.builder()
                .profile(OttlProfile.JAVA)
                .dialect(OttlDialect.EXTENDED)
                .function(boom)
                .build();
        OttlProgram<Order> program = engine.compile("""
                if order.amount > 10000 and Boom() {
                    set(order.status, "REVIEW")
                } else {
                    set(order.status, "APPROVED") where Boom()
                }
                """, Order.class);

        Order high = new Order("h", new BigDecimal("15000"), "NEW", "LOW");
        assertThatThrownBy(() -> program.apply(high))
                .isInstanceOf(OttlEvaluationException.class)
                .hasMessageContaining("Boom failed");
        assertThat(high.status()).isEqualTo("NEW");
        assertThat(boom.calls.get()).isEqualTo(1);

        boom.calls.set(0);
        Order low = new Order("l", new BigDecimal("10"), "NEW", "LOW");
        assertThatThrownBy(() -> program.apply(low))
                .isInstanceOf(OttlEvaluationException.class)
                .hasMessageContaining("Boom failed");
        assertThat(boom.calls.get()).isEqualTo(1);

        boom.calls.set(0);
        OttlProgram<Order> shortCircuit = engine.compile("""
                if order.amount > 10000 and Boom() {
                    set(order.status, "NO")
                } else {
                    set(order.status, "YES")
                }
                """, Order.class);
        assertThat(shortCircuit.apply(low).status()).isEqualTo("YES");
        assertThat(boom.calls.get()).isZero();

        OttlProgram<Order> orSkips = engine.compile("""
                if order.amount > 0 or Boom() {
                    set(order.status, "YES")
                }
                """, Order.class);
        assertThat(orSkips.apply(low).status()).isEqualTo("YES");
        assertThat(boom.calls.get()).isZero();
    }

    @Test
    void takenBranchExceptionPropagatesAndSkippedUnknownPathDoesNotRun() {
        OttlProgram<Order> taken = extended.compile("""
                if order.amount > 0 {
                    set(order.notAField, "X")
                }
                """, Order.class);
        Order order = new Order("o", BigDecimal.TEN, "NEW", "LOW");
        assertThatThrownBy(() -> taken.apply(order)).isInstanceOf(OttlEvaluationException.class);
        assertThat(order.status()).isEqualTo("NEW");

        OttlProgram<Order> skipped = extended.compile("""
                if order.amount > 0 {
                    set(order.status, "REVIEW")
                } else {
                    set(order.notAField, "X")
                }
                """, Order.class);
        assertThat(skipped.apply(order).status()).isEqualTo("REVIEW");
    }

    @Test
    void pojoMutatesAndRecordCopyStillBubblesInsideConditionals() {
        OttlProgram<Customer> pojo = extended.compile("""
                if customer.premium == true {
                    if customer.name == "Ada" {
                        set(customer.tier, "GOLD")
                    } else {
                        set(customer.tier, "SILVER")
                    }
                } else {
                    set(customer.tier, "BASIC")
                }
                """, Customer.class);
        Customer ada = new Customer("Ada", true, "BRONZE");
        assertThat(pojo.apply(ada)).isSameAs(ada);
        assertThat(ada.getTier()).isEqualTo("GOLD");
        Customer bob = new Customer("Bob", false, "BRONZE");
        pojo.apply(bob);
        assertThat(bob.getTier()).isEqualTo("BASIC");

        OttlProgram<Shipment> shipment = extended.compile("""
                if shipment.address.city == "Munich" {
                    set(shipment.address.city, "Berlin")
                }
                """, Shipment.class);
        Shipment original = new Shipment("s-1", new Shipment.Address("Munich", "80331"));
        Shipment result = shipment.apply(original);
        assertThat(result).isNotSameAs(original);
        assertThat(result.address().city()).isEqualTo("Berlin");
        assertThat(result.address().zip()).isEqualTo("80331");
        assertThat(original.address().city()).isEqualTo("Munich");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsAndJsonUseTheSameConditionalExecution() throws Exception {
        OttlProgram<Map<String, Object>> maps = standardExtended.compile("""
                if attributes["amount"] > 10000 {
                    set(attributes["status"], "REVIEW")
                } else {
                    set(attributes["status"], "APPROVED")
                }
                """, (Class<Map<String, Object>>) (Class<?>) Map.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("amount", 15000);
        attributes.put("status", "NEW");
        Map<String, Object> root = new HashMap<>();
        root.put("attributes", attributes);
        maps.apply(root);
        assertThat(attributes).containsEntry("status", "REVIEW");

        ObjectNode json = (ObjectNode) new ObjectMapper().readTree(
                "{\"amount\":500,\"status\":\"NEW\"}");
        OttlProgram<JsonNode> program = extended.compile("""
                if order.amount > 10000 {
                    set(order.status, "REVIEW")
                } else {
                    set(order.status, "APPROVED")
                }
                """, JsonNode.class, "order");
        assertThat(program.apply(json).get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void nestedInvalidStatementsFailBeforeActivation() {
        DefaultPropertyAccessorRegistry accessors = new DefaultPropertyAccessorRegistry();
        accessors.register(Order.class, descriptor(
                "id:java.lang.String:r;amount:java.math.BigDecimal:r;status:java.lang.String:rw;priority:java.lang.String:rw"));
        OttlEngine strict = OttlEngine.builder()
                .profile(OttlProfile.JAVA)
                .dialect(OttlDialect.EXTENDED)
                .accessorRegistry(accessors)
                .strictTypes(true)
                .build();
        assertThatThrownBy(() -> strict.compile("""
                if order.amount > 10000 {
                    set(order.unknownProperty, "INVALID")
                } else {
                    set(order.status, "APPROVED")
                }
                """, Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Unknown property 'unknownProperty'");
        assertThatThrownBy(() -> strict.compile("""
                if false {
                    set(order.status, "OK")
                } else {
                    set(order.amount, 1)
                }
                """, Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("not writable");
    }

    @Test
    void extendedRulesHotSwapAndKeepThePreviousVersionOnFailure() {
        RuleManager<Order> rules = extended.ruleManager(Order.class, new InMemoryRuleRepository());
        rules.activate("r", """
                if order.amount > 10000 {
                    set(order.status, "REVIEW")
                } else {
                    set(order.status, "APPROVED")
                }
                """);
        Order high = new Order("o", new BigDecimal("15000"), "NEW", "LOW");
        assertThat(rules.apply("r", high).status()).isEqualTo("REVIEW");

        assertThatThrownBy(() -> rules.activate("r", """
                if order.amount > 1 {
                    nope(order.status, "X")
                }
                """)).isInstanceOf(OttlCompilationException.class);
        assertThat(rules.apply("r", high).status()).isEqualTo("REVIEW");
        assertThat(rules.active("r").map(RuleDefinition::version)).contains(1);

        rules.activate("r", """
                if order.amount > 10000 {
                    set(order.priority, "URGENT")
                }
                """);
        assertThat(rules.apply("r", high).priority()).isEqualTo("URGENT");
        assertThat(rules.apply("r", high).status()).isEqualTo("NEW");
        assertThat(rules.active("r").map(RuleDefinition::version)).contains(2);
    }

    @Test
    void limitsCountNestedEditorsAndStructuredDepth() {
        OttlEngine limited = OttlEngine.builder()
                .profile(OttlProfile.JAVA)
                .dialect(OttlDialect.EXTENDED)
                .limits(new OttlLimits(10_000, 2, 16, 1))
                .build();
        assertThatThrownBy(() -> limited.compile("""
                if order.amount > 0 {
                    set(order.status, "A")
                    set(order.status, "B")
                    set(order.status, "C")
                }
                """, Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Too many statements");
        assertThatThrownBy(() -> limited.compile("""
                {
                    {
                        set(order.status, "A")
                    }
                }
                """, Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Nesting too deep");
        assertThatThrownBy(() -> limited.compile("""
                if order.amount > 0 {
                    if order.amount > 1 {
                        set(order.status, "A")
                    }
                }
                """, Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Nesting too deep");
        limited.compile("""
                if order.amount > 0 {
                    set(order.status, "A")
                } else {
                    set(order.priority, "LOW")
                }
                """, Order.class);
    }

    @Test
    void concurrentApplyOnOneProgramIsIsolated() throws Exception {
        OttlProgram<Order> program = extended.compile("""
                if order.amount > 10000 {
                    set(order.status, "REVIEW")
                } else {
                    set(order.status, "APPROVED")
                }
                """, Order.class);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 200; i++) {
                boolean high = i % 2 == 0;
                futures.add(pool.submit(() -> program.apply(new Order(
                        "o", high ? new BigDecimal("15000") : BigDecimal.ONE, "NEW", "LOW")).status()));
            }
            for (int i = 0; i < futures.size(); i++) {
                String expected = i % 2 == 0 ? "REVIEW" : "APPROVED";
                assertThat(futures.get(i).get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Order apply(String source, String amount) {
        return extended.compile(source, Order.class)
                .apply(new Order("o", new BigDecimal(amount), "NEW", "OLD"));
    }

    private static PropertyAccessor descriptor(String descriptor) {
        return new PropertyAccessor() {
            @Override
            public Object get(Object instance, String property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object set(Object instance, String property, Object value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String descriptor() {
                return descriptor;
            }
        };
    }

    private static final class Probe implements OttlFunction {
        private final String name;
        private final boolean fail;
        private final AtomicInteger calls = new AtomicInteger();

        private Probe(String name, boolean fail) {
            this.name = name;
            this.fail = fail;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Object invoke(ExecutionContext ctx, List<Argument> args) {
            calls.incrementAndGet();
            if (fail) {
                throw new OttlEvaluationException(name + " failed");
            }
            return true;
        }

        @Override
        public int minArgs() {
            return 0;
        }

        @Override
        public int maxArgs() {
            return 0;
        }
    }
}
