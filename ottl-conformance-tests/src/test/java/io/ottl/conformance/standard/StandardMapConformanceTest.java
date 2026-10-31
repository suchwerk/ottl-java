/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.standard;

import io.ottl.OttlEngine;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OTTL STANDARD-profile conformance over map "attributes", mirroring the reference OTTL editors
 * {@code set}, {@code delete_key} and {@code keep_keys} as documented at
 * https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/pkg/ottl .
 */
class StandardMapConformanceTest {

    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.STANDARD).build();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> newRecord() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("env", "dev");
        attributes.put("secret", "token");
        attributes.put("region", "eu");
        Map<String, Object> root = new HashMap<>();
        root.put("attributes", attributes);
        return root;
    }

    @Test
    void setAssignsNestedAttribute() {
        OttlProgram<Map<String, Object>> p =
                engine.compile("set(attributes[\"env\"], \"prod\")", (Class<Map<String, Object>>) (Class<?>) Map.class);
        Map<String, Object> root = newRecord();
        p.apply(root);
        assertThat(((Map<?, ?>) root.get("attributes")).get("env")).isEqualTo("prod");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteKeyRemovesEntry() {
        OttlProgram<Map<String, Object>> p =
                engine.compile("delete_key(attributes, \"secret\")", (Class<Map<String, Object>>) (Class<?>) Map.class);
        Map<String, Object> root = newRecord();
        p.apply(root);
        assertThat((Map<String, Object>) root.get("attributes")).doesNotContainKey("secret");
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepKeysRetainsOnlyListed() {
        OttlProgram<Map<String, Object>> p = engine.compile(
                "keep_keys(attributes, [\"env\", \"region\"])", (Class<Map<String, Object>>) (Class<?>) Map.class);
        Map<String, Object> root = newRecord();
        p.apply(root);
        assertThat((Map<String, Object>) root.get("attributes"))
                .containsOnlyKeys("env", "region");
    }

    @Test
    @SuppressWarnings("unchecked")
    void whereGuardsStatement() {
        OttlProgram<Map<String, Object>> p = engine.compile("""
                set(attributes["flag"], true) where attributes["env"] == "dev"
                set(attributes["skip"], true) where attributes["env"] == "prod"
                """, (Class<Map<String, Object>>) (Class<?>) Map.class);
        Map<String, Object> root = newRecord();
        p.apply(root);
        Map<String, Object> attrs = (Map<String, Object>) root.get("attributes");
        assertThat(attrs).containsEntry("flag", true).doesNotContainKey("skip");
    }

    @Test
    @SuppressWarnings("unchecked")
    void multipleStatementsRunSequentially() {
        OttlProgram<Map<String, Object>> p = engine.compile("""
                set(attributes["env"], "prod")
                delete_key(attributes, "secret")
                keep_keys(attributes, ["env", "region"])
                """, (Class<Map<String, Object>>) (Class<?>) Map.class);
        Map<String, Object> root = newRecord();
        p.apply(root);
        Map<String, Object> attrs = (Map<String, Object>) root.get("attributes");
        assertThat(attrs).containsOnlyKeys("env", "region");
        assertThat(attrs).containsEntry("env", "prod");
    }
}
