/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.java;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ottl.OttlEngine;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** JAVA-profile: Jackson JsonNode trees. */
class JacksonTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.JAVA).build();

    @Test
    void transformsObjectNode() throws Exception {
        ObjectNode order = (ObjectNode) mapper.readTree(
                "{\"id\":\"o-1\",\"amount\":15000,\"status\":\"NEW\"}");

        OttlProgram<JsonNode> program = engine.compile(
                "set(order.status, \"REVIEW\") where order.amount > 10000", JsonNode.class, "order");
        JsonNode result = program.apply(order);

        assertThat(result.get("status").asText()).isEqualTo("REVIEW");
    }

    @Test
    void deleteKeyAndKeepKeysOnJson() throws Exception {
        ObjectNode node = (ObjectNode) mapper.readTree(
                "{\"env\":\"prod\",\"secret\":\"x\",\"region\":\"eu\"}");
        OttlProgram<JsonNode> program = engine.compile("""
                delete_key(root, "secret")
                keep_keys(root, ["env", "region"])
                """, JsonNode.class, "root");
        JsonNode result = program.apply(node);
        assertThat(result.has("secret")).isFalse();
        assertThat(result.has("env")).isTrue();
        assertThat(result.has("region")).isTrue();
    }
}
