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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Map and list editors: {@code append}, {@code clear}, {@code delete_index},
 * {@code delete_matching_keys}, {@code keep_matching_keys}, {@code flatten}, {@code limit},
 * {@code merge_maps}, {@code stringify_all}, and {@code truncate_all}.
 */
class CollectionEditorConformanceTest {

    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.STANDARD).build();

    @Test
    void appendExtendsCopiesAndWrapsScalars() {
        Map<String, Object> attrs = apply("""
                append(attributes["tags"], "prod")
                append(attributes["fresh"], values=["a", "b"])
                append(attributes["both"], "one", ["two"])
                append(attributes["host"], ".example")
                """, Map.of(
                "tags", new ArrayList<>(List.of("dev")),
                "host", "api"));
        assertThat(attrs.get("tags")).isEqualTo(List.of("dev", "prod"));
        assertThat(attrs.get("fresh")).isEqualTo(List.of("a", "b"));
        assertThat(attrs.get("both")).isEqualTo(List.of("one", "two"));
        assertThat(attrs.get("host")).isEqualTo(List.of("api", ".example"));

        List<String> original = new ArrayList<>(List.of("dev"));
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("tags", original);
        apply("append(attributes[\"tags\"], \"prod\")", shared);
        assertThat(original).containsExactly("dev");
    }

    @Test
    void appendRejectsAMapAndAMissingValue() {
        assertThatThrownBy(() -> apply("append(attributes[\"bag\"], \"x\")", Map.of("bag", Map.of("a", 1))))
                .isInstanceOf(OttlEvaluationException.class)
                .hasMessageContaining("unsupported type");
        assertThatThrownBy(() -> engine.compile("append(attributes[\"tags\"])", mapType()))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("value");
        assertThatThrownBy(() -> engine.compile("append(values=[\"a\"])", mapType()))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("target");
    }

    @Test
    void clearWritesZeroValues() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("s", "hello");
        input.put("n", 5L);
        input.put("f", 1.5d);
        input.put("b", true);
        input.put("amount", new BigDecimal("2.50"));
        input.put("m", new LinkedHashMap<>(Map.of("a", 1)));
        input.put("list", new ArrayList<>(List.of("a")));
        input.put("bytes", new byte[] {1, 2});
        input.put("obj", new Object());
        Map<String, Object> attrs = apply("""
                clear(attributes["s"])
                clear(attributes["n"])
                clear(attributes["f"])
                clear(attributes["b"])
                clear(attributes["amount"])
                clear(attributes["m"])
                clear(attributes["list"])
                clear(attributes["bytes"])
                clear(attributes["obj"])
                clear(attributes["missing"])
                """, input);
        assertThat(attrs)
                .containsEntry("s", "")
                .containsEntry("n", 0L)
                .containsEntry("f", 0.0d)
                .containsEntry("b", false)
                .containsEntry("amount", BigDecimal.ZERO)
                .containsEntry("bytes", null)
                .containsEntry("obj", null)
                .containsEntry("missing", null);
        assertThat(attrs.get("m")).isEqualTo(Map.of());
        assertThat(attrs.get("list")).isEqualTo(List.of());
    }

    @Test
    void deleteIndexRemovesAHalfOpenRange() {
        Map<String, Object> attrs = apply("""
                delete_index(attributes["tags"], 0)
                delete_index(attributes["tail"], Len(attributes["tail"]) - 1)
                delete_index(attributes["mid"], 0, 3)
                delete_index(attributes["same"], 1, 1)
                delete_index(attributes["all"], 0, Len(attributes["all"]))
                """, mapOf(
                "tags", new ArrayList<>(List.of("a", "b", "c")),
                "tail", new ArrayList<>(List.of("a", "b", "c")),
                "mid", new ArrayList<>(List.of("a", "b", "c", "d")),
                "same", new ArrayList<>(List.of("a", "b")),
                "all", new ArrayList<>(List.of("a", "b"))));
        assertThat(attrs.get("tags")).isEqualTo(List.of("b", "c"));
        assertThat(attrs.get("tail")).isEqualTo(List.of("a", "b"));
        assertThat(attrs.get("mid")).isEqualTo(List.of("d"));
        assertThat(attrs.get("same")).isEqualTo(List.of("a", "b"));
        assertThat(attrs.get("all")).isEqualTo(List.of());

        assertThatThrownBy(() -> apply("delete_index(attributes[\"tags\"], 3)", Map.of("tags", List.of("a"))))
                .isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> apply("delete_index(attributes[\"tags\"], 1, 0)", Map.of("tags", List.of("a", "b"))))
                .isInstanceOf(OttlEvaluationException.class);
        assertThatThrownBy(() -> apply("delete_index(attributes[\"n\"], 0)", Map.of("n", 1)))
                .isInstanceOf(OttlEvaluationException.class);
    }

    @Test
    void matchingKeysUseAnUnanchoredPattern() {
        Map<String, Object> deleted = apply("""
                delete_matching_keys(attributes, "(?i).*password.*")
                """, Map.of("password", "secret", "db_Password_hash", "x", "user", "ada"));
        assertThat(deleted).containsOnlyKeys("user");

        Map<String, Object> kept = apply("""
                keep_matching_keys(attributes, "(?i).*version.*")
                """, Map.of("service.version", "1", "Version", "2", "name", "api"));
        assertThat(kept).containsOnlyKeys("service.version", "Version");

        assertThatThrownBy(() -> engine.compile("delete_matching_keys(attributes, \"(\")", mapType()))
                .isInstanceOf(OttlCompilationException.class);
    }

    @Test
    void flattenLiftsMapsAndLists() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("street", "first");
        address.put("house", 1234L);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "test");
        body.put("address", address);
        body.put("occupants", new ArrayList<>(List.of("user 1", "user 2")));

        assertThat(apply("flatten(attributes)", body))
                .containsEntry("name", "test")
                .containsEntry("address.street", "first")
                .containsEntry("address.house", 1234L)
                .containsEntry("occupants.0", "user 1")
                .containsEntry("occupants.1", "user 2");

        assertThat(apply("flatten(attributes, \"app\")", copyBody(body)))
                .containsEntry("app.name", "test")
                .containsEntry("app.address.street", "first")
                .containsEntry("app.occupants.0", "user 1")
                .containsEntry("app.occupants.1", "user 2");

        Map<String, Object> deep = nested("0", nested("1", nested("2", nested("3", "value"))));
        assertThat(apply("flatten(attributes, depth=2)", deep))
                .containsEntry("0.1.2", Map.of("3", "value"));

        assertThat(apply("flatten(attributes, resolveConflicts=true)", copyBody(body)))
                .containsEntry("name", "test")
                .containsEntry("address.street", "first")
                .containsEntry("occupants", "user 1")
                .containsEntry("occupants.0", "user 2");
    }

    @Test
    void flattenResolvesConflictsInUtf8KeyOrder() {
        Map<String, Object> street = new LinkedHashMap<>();
        street.put("number", "first");
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("street", street);
        address.put("house", "1234");
        Map<String, Object> streetConflict = new LinkedHashMap<>();
        streetConflict.put("number", new ArrayList<>(List.of("second", "third")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("address", address);
        body.put("address.street", streetConflict);
        body.put("address.street.number", "fourth");
        body.put("occupants", new ArrayList<>(List.of("user 1", "user 2")));

        assertThat(apply("flatten(attributes, resolveConflicts=true)", body))
                .containsEntry("address.street.number", "first")
                .containsEntry("address.house", "1234")
                .containsEntry("address.street.number.0", "second")
                .containsEntry("address.street.number.1", "third")
                .containsEntry("address.street.number.2", "fourth")
                .containsEntry("occupants", "user 1")
                .containsEntry("occupants.0", "user 2");

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("leaf", "kept");
        Map<String, Object> mid = new LinkedHashMap<>();
        mid.put("inner", inner);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("child", mid);
        Map<String, Object> flat = apply("flatten(attributes, depth=1)", source);
        inner.put("leaf", "changed");
        assertThat(flat).containsEntry("child.inner", Map.of("leaf", "kept"));

        assertThatThrownBy(() -> engine.compile("flatten(attributes, depth=0)", mapType()))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("greater than 0");
        assertThatThrownBy(() -> engine.compile("flatten(attributes, attributes[\"p\"])", mapType()))
                .isInstanceOf(OttlCompilationException.class);
    }

    @Test
    void limitKeepsPriorityKeysThenUtf8Order() {
        Map<String, Object> attrs = apply("""
                limit(attributes, 2, ["c"])
                """, Map.of("a", 1, "b", 2, "c", 3, "d", 4));
        assertThat(attrs).containsOnlyKeys("a", "c");

        Map<String, Object> under = apply("""
                limit(attributes, 10, [])
                """, Map.of("a", 1, "b", 2));
        assertThat(under).containsOnlyKeys("a", "b");

        assertThatThrownBy(() -> engine.compile("limit(attributes, 1, [\"a\", \"b\"])", mapType()))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("priority attributes");
        assertThatThrownBy(() -> engine.compile("limit(attributes, -1, [])", mapType()))
                .isInstanceOf(OttlCompilationException.class);
    }

    @Test
    void mergeMapsFollowsTheStrategy() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("n", 1);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("b", 9);
        source.put("c", nested);
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("a", 1);
        target.put("b", 2);
        target.put("src", source);

        assertThat(apply("merge_maps(attributes, attributes[\"src\"], \"insert\")", copyMerge(target)))
                .containsEntry("a", 1)
                .containsEntry("b", 2)
                .containsEntry("c", Map.of("n", 1));
        assertThat(apply("merge_maps(attributes, attributes[\"src\"], \"update\")", copyMerge(target)))
                .containsEntry("b", 9)
                .doesNotContainKey("c");
        Map<String, Object> upserted = apply(
                "merge_maps(attributes, attributes[\"src\"], \"upsert\")", copyMerge(target));
        assertThat(upserted).containsEntry("b", 9).containsEntry("c", Map.of("n", 1));
        nested.put("n", 5);
        assertThat(upserted.get("c")).isEqualTo(Map.of("n", 1));

        assertThatThrownBy(() -> engine.compile(
                "merge_maps(attributes, attributes[\"src\"], \"replace\")", mapType()))
                .isInstanceOf(OttlCompilationException.class);
    }

    @Test
    void stringifyAllConvertsNonStrings() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("s", "keep");
        input.put("n", 42L);
        input.put("f", 3.14d);
        input.put("b", true);
        input.put("empty", null);
        input.put("bytes", new byte[] {1, 2});
        input.put("bag", Map.of("b", 1L, "a", "x"));
        Map<String, Object> attrs = apply("stringify_all(attributes)", input);
        assertThat(attrs)
                .containsEntry("s", "keep")
                .containsEntry("n", "42")
                .containsEntry("f", "3.14")
                .containsEntry("b", "true")
                .containsEntry("empty", "")
                .containsEntry("bytes", "AQI=")
                .containsEntry("bag", "{\"a\":\"x\",\"b\":1}");
    }

    @Test
    void truncateAllCutsOnUtf8Bytes() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("test", "hello world");
        input.put("n", 3);
        input.put("emoji", "ab😀c");
        input.put("mark", "hell😀 w😀rld");
        Map<String, Object> attrs = apply("""
                truncate_all(attributes, 1)
                """, Map.of("test", "hello world", "n", 3));
        assertThat(attrs).containsEntry("test", "h").containsEntry("n", 3);

        assertThat(apply("truncate_all(attributes, 4)", Map.of("emoji", "ab😀c")))
                .containsEntry("emoji", "ab");
        assertThat(apply("truncate_all(attributes, 6)", Map.of("emoji", "ab😀c")))
                .containsEntry("emoji", "ab😀");
        assertThat(apply("truncate_all(attributes, 10)", Map.of("cluster", "👩🏾‍🦳")))
                .containsEntry("cluster", "👩🏾");

        assertThat(apply("truncate_all(attributes, 7, truncationMarker=\"(...)\")", Map.of("test", "hello world")))
                .containsEntry("test", "he(...)");
        assertThat(apply(
                "truncate_all(attributes, 16, true, \"(...)\")", Map.of("mark", "hell😀 w😀rld")))
                .containsEntry("mark", "hell😀 w(...)");
        assertThat(apply(
                "truncate_all(attributes, 16, false, \"(...)\")", Map.of("mark", "hell😀 w😀rld")))
                .containsEntry("mark", "hell😀 w\uFFFD(...)");

        assertThatThrownBy(() -> engine.compile(
                "truncate_all(attributes, 3, truncationMarker=\"long_marker\")", mapType()))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("length of marker");
        assertThatThrownBy(() -> engine.compile(
                "truncate_all(attributes, 3, truncationMarker=\"[✄]\")", mapType()))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("length of marker 5");
    }

    @Test
    void javaProfileRunsTheEditors() {
        OttlEngine javaEngine = OttlEngine.builder().profile(OttlProfile.JAVA).build();
        OttlProgram<Map<String, Object>> program = javaEngine.compile("""
                append(attributes["tags"], "prod")
                limit(attributes, 2, ["tags"])
                """, mapType());
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("tags", new ArrayList<>(List.of("dev")));
        attributes.put("a", 1);
        attributes.put("b", 2);
        root.put("attributes", attributes);
        program.apply(root);
        assertThat(attributes).containsEntry("tags", List.of("dev", "prod")).containsOnlyKeys("tags", "a");
    }

    private Map<String, Object> copyBody(Map<String, Object> body) {
        return new LinkedHashMap<>(body);
    }

    private static Map<String, Object> copyMerge(Map<String, Object> target) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("a", target.get("a"));
        copy.put("b", target.get("b"));
        copy.put("src", target.get("src"));
        return copy;
    }

    private static Map<String, Object> nested(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                                             String k4, Object v4, String k5, Object v5) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> apply(String source, Map<String, Object> attributes) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("attributes", new LinkedHashMap<>(attributes));
        engine.compile(source, mapType()).apply(root);
        return (Map<String, Object>) root.get("attributes");
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapType() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }
}
