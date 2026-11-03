/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.standard;

import io.ottl.OttlEngine;
import io.ottl.OttlEvaluationException;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import io.ottl.conformance.model.Order;
import io.ottl.function.Argument;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;
import io.ottl.function.OttlLambda;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Execution of the OTTL expression syntax: arithmetic, maps, byte slices, indexes, named
 * arguments, and lambdas. The standard converter library is still not bundled.
 */
class OttlSyntaxConformanceTest {

    @SuppressWarnings("unchecked")
    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.STANDARD).build();

    @Test
    @SuppressWarnings("unchecked")
    void mathFollowsOttlNumericRules() {
        Map<String, Object> root = attributes(Map.of("left", 7L, "right", 2L));
        apply("""
                set(attributes["sum"], 1 + 2 * 3)
                set(attributes["quot"], attributes["left"] / attributes["right"])
                set(attributes["flag"], true) where (1 + 2) > 2 and not false
                """, root);
        Map<String, Object> attrs = (Map<String, Object>) root.get("attributes");
        assertThat(attrs).containsEntry("sum", 7L).containsEntry("quot", 3L).containsEntry("flag", true);

        assertThatThrownBy(() -> apply("set(attributes[\"n\"], 1 + 1.5)", attributes(Map.of())))
                .isInstanceOf(OttlEvaluationException.class)
                .hasMessageContaining("mix int and float");
        assertThatThrownBy(() -> apply("set(attributes[\"n\"], 1 / 0)", attributes(Map.of())))
                .isInstanceOf(OttlEvaluationException.class)
                .hasMessageContaining("Division by zero");
    }

    @Test
    @SuppressWarnings("unchecked")
    void bigDecimalAndTimeArithmetic() {
        Map<String, Object> root = attributes(new LinkedHashMap<>(Map.of("amount", new BigDecimal("10.50"))));
        apply("set(attributes[\"total\"], attributes[\"amount\"] + 2)", root);
        assertThat(((Map<String, Object>) root.get("attributes")).get("total"))
                .isEqualTo(new BigDecimal("12.50"));

        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Map<String, Object> times = new LinkedHashMap<>();
        times.put("start", start);
        times.put("end", start.plus(Duration.ofHours(2)));
        apply("set(elapsed, end - start)", times);
        assertThat(times.get("elapsed")).isEqualTo(Duration.ofHours(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsBytesIndexesAndNamedArguments() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("env", "dev");
        attrs.put("items", new ArrayList<>(List.of("a", "b", "c")));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("attributes", attrs);

        OttlEngine indexed = OttlEngine.builder()
                .profile(OttlProfile.STANDARD)
                .function(new Words())
                .build();
        OttlProgram<Map<String, Object>> program = indexed.compile("""
                set(value={"foo": "bar", "n": 1}, target=attributes["bag"])
                set(attributes["second"], Words("a,b,c")[1])
                set(attributes["picked"], attributes["items"][0])
                set(attributes["copied"], attributes[attributes["env"]])
                set(attributes["raw"], 0x0001)
                set(attributes["same"], true) where attributes["raw"] == 0x0001
                """, mapType());
        program.apply(root);

        assertThat((Map<String, Object>) attrs.get("bag")).containsEntry("foo", "bar").containsEntry("n", 1L);
        assertThat(attrs).containsEntry("second", "b").containsEntry("picked", "a").containsEntry("same", true);
        assertThat((byte[]) attrs.get("raw")).containsExactly(0x00, 0x01);
        // attributes["env"] is the string "dev", and there is no key "dev" on the map.
        assertThat(attrs.get("copied")).isNull();
    }

    @Test
    void dynamicMapKeyReadsAnotherAttribute() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("name", "env");
        attrs.put("env", "prod");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("attributes", attrs);
        apply("set(attributes[\"copied\"], attributes[attributes[\"name\"]])", root);
        assertThat(attrs.get("copied")).isEqualTo("prod");
    }

    @Test
    void lambdaAppliesWithOuterCapture() {
        OttlEngine withApply = OttlEngine.builder()
                .profile(OttlProfile.STANDARD)
                .function(new Apply())
                .build();
        Map<String, Object> root = new LinkedHashMap<>();
        OttlProgram<Map<String, Object>> program = withApply.compile("""
                set(out, Apply((value) => value + 1, 2))
                set(nested, Apply((outer) => Apply((inner) => outer + inner, 4), 3))
                """, mapType());
        program.apply(root);
        assertThat(root).containsEntry("out", 3L).containsEntry("nested", 7L);
    }

    @Test
    void javaProfileAddsBigDecimalOnRecords() {
        OttlEngine javaEngine = OttlEngine.builder().profile(OttlProfile.JAVA).build();
        OttlProgram<Order> program = javaEngine.compile("""
                set(order.priority, "HIGH") where order.amount + 1 > 10
                """, Order.class);
        Order updated = program.apply(new Order("1", new BigDecimal("10"), "NEW", "LOW"));
        assertThat(updated.priority()).isEqualTo("HIGH");
        assertThat(updated.amount()).isEqualByComparingTo("10");
    }

    @SuppressWarnings("unchecked")
    private void apply(String source, Map<String, Object> root) {
        engine.compile(source, mapType()).apply(root);
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapType() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    private static Map<String, Object> attributes(Map<String, Object> values) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("attributes", new LinkedHashMap<>(values));
        return root;
    }

    /** Test converter: {@code Words("a,b,c")[1]} indexes the returned list. */
    private static final class Words implements OttlFunction {
        @Override
        public String name() {
            return "Words";
        }

        @Override
        public int minArgs() {
            return 1;
        }

        @Override
        public int maxArgs() {
            return 1;
        }

        @Override
        public Object invoke(ExecutionContext ctx, List<Argument> args) {
            return List.of(String.valueOf(args.get(0).value()).split(",", -1));
        }
    }

    /** Test converter that calls a one-argument lambda. */
    private static final class Apply implements OttlFunction {
        @Override
        public String name() {
            return "Apply";
        }

        @Override
        public int minArgs() {
            return 2;
        }

        @Override
        public int maxArgs() {
            return 2;
        }

        @Override
        public Object invoke(ExecutionContext ctx, List<Argument> args) {
            Object lambda = args.get(0).value();
            if (!(lambda instanceof OttlLambda function)) {
                throw new OttlEvaluationException("Apply expects a lambda");
            }
            return function.apply(ctx, List.of(args.get(1).value()));
        }
    }
}
