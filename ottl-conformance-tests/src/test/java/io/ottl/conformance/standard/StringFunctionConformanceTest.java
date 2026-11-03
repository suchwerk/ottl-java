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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code replace_match}, {@code replace_all_matches}, {@code replace_pattern},
 * {@code replace_all_patterns}, {@code TrimPrefix}, {@code TrimSuffix}, {@code ConvertCase}, and
 * the type checks. Glob {@code *} matches across {@code /}, which is what the collector's
 * gobwas/glob compile does.
 */
class StringFunctionConformanceTest {

    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.STANDARD).build();

    @Test
    void replaceMatchReplacesAWholeGlobMatch() {
        Map<String, Object> attrs = apply("""
                replace_match(attributes["path"], "/user/*/list/*", "/user/{userId}/list/{listId}")
                replace_match(attributes["other"], "/user/*/list/*", "nope")
                replace_match(attributes["n"], "1", "nope")
                """, Map.of(
                "path", "/user/42/list/7",
                "other", "/health",
                "n", 1));
        assertThat(attrs)
                .containsEntry("path", "/user/{userId}/list/{listId}")
                .containsEntry("other", "/health")
                .containsEntry("n", 1);

        assertThat(apply("""
                replace_match(attributes["v"], "{foo,bar}", "yes")
                replace_match(attributes["w"], "[abc]", "Z")
                replace_match(attributes["x"], "[!0-9]", "letter")
                """, Map.of("v", "bar", "w", "b", "x", "a")))
                .containsEntry("v", "yes")
                .containsEntry("w", "Z")
                .containsEntry("x", "letter");
    }

    @Test
    void replaceAllMatchesRewritesStringValuesOnly() {
        Map<String, Object> attrs = apply("""
                replace_all_matches(attributes, "/user/*/list/*", "/user/{userId}/list/{listId}")
                """, Map.of("path", "/user/42/list/7", "n", 1, "other", "/health"));
        assertThat(attrs)
                .containsEntry("path", "/user/{userId}/list/{listId}")
                .containsEntry("n", 1)
                .containsEntry("other", "/health");
    }

    @Test
    void replacePatternExpandsGroups() {
        Map<String, Object> attrs = apply("""
                replace_pattern(attributes["name"], "^kube_([0-9A-Za-z]+_)", "k8s.$1.")
                replace_pattern(attributes["dollar"], "a", "$$")
                replace_pattern(attributes["miss"], "zzz", "nope")
                """, Map.of("name", "kube_foo_bar", "dollar", "a", "miss", "keep"));
        assertThat(attrs)
                .containsEntry("name", "k8s.foo_.bar")
                .containsEntry("dollar", "$")
                .containsEntry("miss", "keep");
    }

    @Test
    void replaceAllPatternsRewritesValuesOrKeys() {
        Map<String, Object> values = apply("""
                replace_all_patterns(attributes, "value", "/account/[0-9]{4}", "/account/{accountId}")
                """, Map.of("account", "/account/1234", "n", 2));
        assertThat(values)
                .containsEntry("account", "/account/{accountId}")
                .containsEntry("n", 2);

        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("kube_foo_bar", "kept");
        keys.put("plain", 1);
        Map<String, Object> renamed = apply("""
                replace_all_patterns(attributes, "key", "^kube_([0-9A-Za-z]+_)", "k8s.$1.")
                """, keys);
        assertThat(renamed).containsEntry("k8s.foo_.bar", "kept").containsEntry("plain", 1);
    }

    @Test
    void trimPrefixAndSuffixRemoveOneCopy() {
        assertThat(eval("TrimPrefix(\"ingest_service\", \"ingest_\")")).isEqualTo("service");
        assertThat(eval("TrimPrefix(prefix=\"pre\", target=\"prepreX\")")).isEqualTo("preX");
        assertThat(eval("TrimPrefix(\"service\", \"ingest_\")")).isEqualTo("service");
        assertThat(eval("TrimSuffix(\"ingest_service\", \"_service\")")).isEqualTo("ingest");
        assertThat(eval("TrimSuffix(\"Xsufsuf\", \"suf\")")).isEqualTo("Xsuf");
        assertThat(eval("TrimSuffix(\"ingest\", \"_service\")")).isEqualTo("ingest");
    }

    @Test
    void convertCaseFollowsStrcase() {
        assertThat(eval("ConvertCase(\"MY_METRIC\", \"lower\")")).isEqualTo("my_metric");
        assertThat(eval("ConvertCase(\"my_metric\", \"upper\")")).isEqualTo("MY_METRIC");
        assertThat(eval("ConvertCase(\"myMetric\", \"snake\")")).isEqualTo("my_metric");
        assertThat(eval("ConvertCase(\"JSONData\", \"snake\")")).isEqualTo("json_data");
        assertThat(eval("ConvertCase(\"my_metric\", \"camel\")")).isEqualTo("MyMetric");
        assertThat(eval("ConvertCase(\"\", \"lower\")")).isEqualTo("");
    }

    @Test
    void typeChecksRejectLookalikes() {
        assertThat(eval("IsString(\"a\")")).isEqualTo(true);
        assertThat(eval("IsString(1)")).isEqualTo(false);
        assertThat(eval("IsString(nil)")).isEqualTo(false);
        assertThat(eval("IsBool(true)")).isEqualTo(true);
        assertThat(eval("IsBool(\"true\")")).isEqualTo(false);
        assertThat(eval("IsBool(1)")).isEqualTo(false);
        assertThat(eval("IsDouble(1.5)")).isEqualTo(true);
        assertThat(eval("IsDouble(1)")).isEqualTo(false);
        assertThat(eval("IsList([1, 2])")).isEqualTo(true);
        assertThat(eval("IsList(0x0102)")).isEqualTo(true);
        assertThat(eval("IsList(\"ab\")")).isEqualTo(false);
        assertThat(eval("IsMap({\"a\": 1})")).isEqualTo(true);
        assertThat(eval("IsMap([1])")).isEqualTo(false);
        assertThat(eval("IsMap(nil)")).isEqualTo(false);

        Map<String, Object> amount = Map.of("amount", new BigDecimal("1.5"));
        assertThat(eval("IsDouble(attributes[\"amount\"])", amount)).isEqualTo(false);
    }

    @Test
    void javaProfileConvertsARecordField() {
        OttlEngine javaEngine = OttlEngine.builder().profile(OttlProfile.JAVA).build();
        OttlProgram<Order> program = javaEngine.compile("""
                set(order.status, ConvertCase(order.status, "snake")) where IsString(order.status)
                """, Order.class);
        Order updated = program.apply(new Order("abc", BigDecimal.ONE, "PendingReview", "LOW"));
        assertThat(updated.status()).isEqualTo("pending_review");
    }

    @Test
    void invalidLiteralsFailAtCompileTime() {
        assertThatThrownBy(() -> engine.compile("set(attributes[\"a\"], ConvertCase(\"A\", \"title\"))", mapType()))
                .isInstanceOf(OttlCompilationException.class);
        assertThatThrownBy(() -> engine.compile(
                "replace_all_patterns(attributes, \"both\", \"a\", \"b\")", mapType()))
                .isInstanceOf(OttlCompilationException.class);
        assertThatThrownBy(() -> engine.compile(
                "replace_match(attributes[\"a\"], \"[\", \"x\")", mapType()))
                .isInstanceOf(OttlCompilationException.class);
        assertThatThrownBy(() -> engine.compile(
                "replace_pattern(attributes[\"a\"], \"(\", \"x\")", mapType()))
                .isInstanceOf(OttlCompilationException.class);
        assertThatThrownBy(() -> eval("TrimPrefix(nil, \"a\")")).isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> eval("ConvertCase(1, \"lower\")")).isInstanceOf(OttlEvaluationException.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> apply(String source, Map<String, Object> attributes) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("attributes", new LinkedHashMap<>(attributes));
        engine.compile(source, mapType()).apply(root);
        return (Map<String, Object>) root.get("attributes");
    }

    private Object eval(String expression) {
        return eval(expression, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Object eval(String expression, Map<String, Object> attributes) {
        return apply("set(attributes[\"out\"], " + expression + ")", attributes).get("out");
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapType() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }
}
