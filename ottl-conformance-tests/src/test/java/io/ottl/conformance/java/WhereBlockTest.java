/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.java;

import io.ottl.OttlEngine;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ottl-java extension: a {@code where} block evaluates its condition once, then runs every item in
 * the body. A later statement still runs when an earlier one changes a value the condition reads.
 */
class WhereBlockTest {

    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.JAVA).build();

    /** Immutable stand-in for the example {@code StockMovement} record. */
    public record StockMovement(String id, int quantity, String direction) {
    }

    @Test
    void blockRunsEveryStatementEvenWhenAnEarlierOneClearsTheCondition() {
        OttlProgram<StockMovement> program = engine.compile("""
                where stockMovement.quantity < 0 {
                    set(stockMovement.quantity, 0)
                    set(stockMovement.direction, nil)
                }
                """, StockMovement.class);
        StockMovement original = new StockMovement("m-1", -4, "OUT");
        StockMovement result = program.apply(original);

        assertThat(original).isEqualTo(new StockMovement("m-1", -4, "OUT"));
        assertThat(result).isEqualTo(new StockMovement("m-1", 0, null));
    }

    @Test
    void blockIsSkippedWhenTheConditionIsFalse() {
        OttlProgram<StockMovement> program = engine.compile("""
                where stockMovement.quantity < 0 {
                    set(stockMovement.quantity, 0)
                    set(stockMovement.direction, nil)
                }
                """, StockMovement.class);
        StockMovement original = new StockMovement("m-2", 3, "IN");
        assertThat(program.apply(original)).isEqualTo(original);
    }

    @Test
    @SuppressWarnings("unchecked")
    void trailingWhereInsideABlockStillGuardsThatStatement() {
        OttlProgram<Map<String, Object>> program = engine.compile("""
                where attributes["quantity"] < 0 {
                    set(attributes["quantity"], 0)
                    set(attributes["flag"], true) where attributes["env"] == "prod"
                }
                """, (Class<Map<String, Object>>) (Class<?>) Map.class);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("quantity", -1);
        attributes.put("env", "dev");
        Map<String, Object> root = new HashMap<>();
        root.put("attributes", attributes);

        program.apply(root);

        assertThat(attributes).containsEntry("quantity", 0L).doesNotContainKey("flag");
    }
}
