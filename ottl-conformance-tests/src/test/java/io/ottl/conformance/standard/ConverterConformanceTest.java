/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.standard;

import io.ottl.OttlCompilationException;
import io.ottl.OttlEngine;
import io.ottl.OttlEvaluationException;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import io.ottl.conformance.model.Order;
import io.ottl.function.OttlFunction;
import io.ottl.functions.standard.StandardFunctions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The nine standard converters against collector behavior: {@code Concat}, {@code Double},
 * {@code Int}, {@code IsMatch}, {@code Len}, {@code Split}, {@code String}, {@code Substring},
 * and {@code Trim}. Differences from the collector are recorded in {@code docs/conformance.md}.
 */
class ConverterConformanceTest {

    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.STANDARD).build();

    @Test
    void lenCountsUtf8BytesListsMapsAndByteSlices() {
        assertThat(eval("Len(\"abc\")")).isEqualTo(3L);
        assertThat(eval("Len(\"é\")")).isEqualTo(2L);
        assertThat(eval("Len(\"一二三\")")).isEqualTo(9L);
        assertThat(eval("Len([1, 2, 3])")).isEqualTo(3L);
        assertThat(eval("Len({\"a\": 1, \"b\": 2})")).isEqualTo(2L);
        assertThat(eval("Len(0x0102)")).isEqualTo(2L);
    }

    @Test
    void concatJoinsStringFormsAndTurnsNilIntoASpace() {
        assertThat(eval("Concat([\"a\", \"b\"], \"-\")")).isEqualTo("a-b");
        assertThat(eval("Concat(delimiter=\"-\", vals=[\"a\", \"b\"])")).isEqualTo("a-b");
        assertThat(eval("Concat([\"a\", \"b\"], \"\")")).isEqualTo("ab");
        assertThat(eval("Concat([], \"-\")")).isEqualTo("");
        assertThat(eval("Concat([nil, \"a\"], \",\")")).isEqualTo(" ,a");
        assertThat(eval("Concat([nil], \"\")")).isEqualTo(" ");
        assertThat(eval("Concat([1, 2], \"-\")")).isEqualTo("1-2");
        assertThat(eval("Concat([0x6869, \"!\"], \"\")")).isEqualTo("6869!");
    }

    @Test
    void splitFollowsGoAndCanBeIndexed() {
        assertThat(eval("Split(\"a-b-c\", \"-\")[1]")).isEqualTo("b");
        assertThat(eval("Split(\"a,,b\", \",\")[1]")).isEqualTo("");
        assertThat(eval("Split(\"ab\", \"\")[1]")).isEqualTo("b");
        assertThat(eval("Split(\"\", \",\")")).isEqualTo(List.of(""));
        assertThat(eval("Split(\"\", \"\")")).isEqualTo(List.of());
        assertThat(eval("Split(\"aaa\", \"a\")")).isEqualTo(List.of("", "", "", ""));
    }

    @Test
    void substringUsesUtf8ByteOffsets() {
        assertThat(eval("Substring(\"123456789\", 0, 3)")).isEqualTo("123");
        assertThat(eval("Substring(\"一二三\", 0, 3)")).isEqualTo("一");
        assertThat(eval("Substring(\"一二三\", 0, 4, true)")).isEqualTo("一");
        assertThat(eval("Substring(\"一二三\", 0, 4, utf8_safe=true)")).isEqualTo("一");
        assertThat(eval("Substring(\"一\", 1, 1, true)")).isEqualTo("");
        assertThat(eval("Substring(\"一二三\", 0, 4)")).isEqualTo("一\uFFFD");
    }

    @Test
    void isMatchFindsAnUnanchoredMatchAndConvertsNonStrings() {
        assertThat(eval("IsMatch(\"string\", \".*ring\")")).isEqualTo(true);
        assertThat(eval("IsMatch(\"string\", \"^ring\")")).isEqualTo(false);
        assertThat(eval("IsMatch(nil, \"x\")")).isEqualTo(false);
        assertThat(eval("IsMatch(42, \"42\")")).isEqualTo(true);
        assertThat(eval("IsMatch(true, \"true\")")).isEqualTo(true);
        assertThat(eval("IsMatch(0x6869, \"aGk\")")).isEqualTo(true);
        assertThat(eval("IsMatch([1, 2], \"1\")")).isEqualTo(true);
    }

    @Test
    void trimRemovesCharactersInTheCutset() {
        assertThat(eval("Trim(\"  hi  \")")).isEqualTo("hi");
        assertThat(eval("Trim(\"\\thi\")")).isEqualTo("\thi");
        assertThat(eval("Trim(\"!!this is a test!!\", \"!!\")")).isEqualTo("this is a test");
        assertThat(eval("Trim(\"xxhello\", \"x\")")).isEqualTo("hello");
    }

    @Test
    void intAndDoubleFollowTheCollectorConversions() {
        assertThat(eval("Int(2.9)")).isEqualTo(2L);
        assertThat(eval("Int(-2.9)")).isEqualTo(-2L);
        assertThat(eval("Int(true)")).isEqualTo(1L);
        assertThat(eval("Int(false)")).isEqualTo(0L);
        assertThat(eval("Int(nil)")).isNull();
        assertThat(eval("Int(\"10\")")).isEqualTo(10L);
        assertThat(eval("Double(\"2.0\")")).isEqualTo(2.0d);
        assertThat(eval("Double(\"1e2\")")).isEqualTo(100.0d);
        assertThat(eval("Double(true)")).isEqualTo(1.0d);
        assertThat(eval("Double(false)")).isEqualTo(0.0d);
        assertThat(eval("Double(3)")).isEqualTo(3.0d);
        assertThat(eval("Double(nil)")).isNull();
        assertThat(eval("Double(\"Inf\")")).isEqualTo(Double.POSITIVE_INFINITY);

        Map<String, Object> amount = Map.of("amount", new BigDecimal("10.9"));
        assertThat(eval("Int(attributes[\"amount\"])", amount)).isEqualTo(10L);
        assertThat(eval("Int(attributes[\"amount\"])", Map.of("amount", new BigDecimal("-10.9")))).isEqualTo(-10L);
    }

    @Test
    void stringUsesHexForBytesAndJsonForCollections() {
        assertThat(eval("String(\"test\")")).isEqualTo("test");
        assertThat(eval("String(false)")).isEqualTo("false");
        assertThat(eval("String(1)")).isEqualTo("1");
        assertThat(eval("String(1.0)")).isEqualTo("1");
        assertThat(eval("String(1.5)")).isEqualTo("1.5");
        assertThat(eval("String(1e-7)")).isEqualTo("1e-7");
        assertThat(eval("String(nil)")).isNull();
        assertThat(eval("String(0x0A0B)")).isEqualTo("0a0b");
        assertThat(eval("String([1, nil, \"a\"])")).isEqualTo("[1,null,\"a\"]");
        assertThat(eval("String({\"b\": 1, \"a\": \"x\"})")).isEqualTo("{\"a\":\"x\",\"b\":1}");
        assertThat(eval("String([\"a<b\"])")).isEqualTo("[\"a\\u003cb\"]");
        assertThat(eval("String([0x6869])")).isEqualTo("[\"aGk=\"]");
    }

    @Test
    void javaProfileConvertsABigDecimalOnARecord() {
        OttlEngine javaEngine = OttlEngine.builder().profile(OttlProfile.JAVA).build();
        OttlProgram<Order> program = javaEngine.compile("""
                set(order.priority, String(Int(order.amount))) where Len(order.id) == 3
                """, Order.class);
        Order original = new Order("abc", new BigDecimal("10.9"), "NEW", "LOW");
        Order updated = program.apply(original);
        assertThat(updated.priority()).isEqualTo("10");
        assertThat(original.priority()).isEqualTo("LOW");
    }

    @Test
    void badValuesFailAtRuntimeAndWrongArityFailsAtCompileTime() {
        assertThatThrownBy(() -> eval("Len(1)")).isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> eval("Len(nil)")).isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> eval("Int(\"2.0\")")).isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> eval("Double(\"nope\")")).isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> eval("IsMatch(\"a\", \"[\")")).isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> eval("Split(1, \",\")")).isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> eval("Trim(nil)")).isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> eval("Substring(\"abc\", -1, 1)")).isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> eval("Substring(\"abc\", 0, 0)")).isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> eval("Substring(\"abc\", 2, 5)")).isInstanceOf(OttlEvaluationException.class);

        assertThatThrownBy(() -> engine.compile("set(attributes[\"out\"], Len())", mapType()))
                .isInstanceOf(OttlCompilationException.class);
        assertThatThrownBy(() -> engine.compile("set(attributes[\"out\"], Concat(\"a\"))", mapType()))
                .isInstanceOf(OttlCompilationException.class);
        assertThatThrownBy(() -> engine.compile("set(attributes[\"out\"], Substring(\"a\", 0))", mapType()))
                .isInstanceOf(OttlCompilationException.class);
    }

    @Test
    void serviceLoaderRegistersTheSameClassesAsTheExplicitList() {
        TreeSet<String> loaded = new TreeSet<>();
        ServiceLoader.load(OttlFunction.class, ConverterConformanceTest.class.getClassLoader())
                .forEach(function -> loaded.add(function.getClass().getName()));
        TreeSet<String> explicit = new TreeSet<>();
        StandardFunctions.all().forEach(function -> explicit.add(function.getClass().getName()));
        assertThat(loaded).isEqualTo(explicit);
    }

    private Object eval(String expression) {
        return eval(expression, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Object eval(String expression, Map<String, Object> attributes) {
        Map<String, Object> attrs = new LinkedHashMap<>(attributes);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("attributes", attrs);
        engine.compile("set(attributes[\"out\"], " + expression + ")", mapType()).apply(root);
        return ((Map<String, Object>) root.get("attributes")).get("out");
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapType() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }
}
