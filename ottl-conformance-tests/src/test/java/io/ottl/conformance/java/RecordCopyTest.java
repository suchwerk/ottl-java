/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.java;

import io.ottl.ExecutionMode;
import io.ottl.OttlEngine;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import io.ottl.conformance.model.Order;
import io.ottl.conformance.model.Shipment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** JAVA-profile: immutable Java records transformed with COPY semantics. */
class RecordCopyTest {

    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.JAVA).build();

    @Test
    void recordUsesCopySemanticsAndLeavesOriginalUntouched() {
        OttlProgram<Order> program = engine.compile("""
                set(order.status, "REVIEW")
                  where order.amount > 10000
                """, Order.class);

        assertThat(program.mode()).isEqualTo(ExecutionMode.COPY);

        Order original = new Order("o-1", new BigDecimal("15000"), "NEW", "HIGH");
        Order result = program.apply(original);

        assertThat(result).isNotSameAs(original);
        assertThat(result.status()).isEqualTo("REVIEW");
        assertThat(result.id()).isEqualTo("o-1");
        assertThat(result.amount()).isEqualByComparingTo("15000");
        // original must be untouched
        assertThat(original.status()).isEqualTo("NEW");
    }

    @Test
    void whereConditionNotMetLeavesRecordEqual() {
        OttlProgram<Order> program = engine.compile(
                "set(order.status, \"REVIEW\") where order.amount > 10000", Order.class);
        Order original = new Order("o-2", new BigDecimal("500"), "NEW", "LOW");
        Order result = program.apply(original);
        assertThat(result.status()).isEqualTo("NEW");
    }

    @Test
    void nestedRecordCopyBubblesThroughParents() {
        OttlProgram<Shipment> program = engine.compile(
                "set(shipment.address.city, \"Berlin\")", Shipment.class);
        Shipment original = new Shipment("s-1", new Shipment.Address("Munich", "80331"));
        Shipment result = program.apply(original);

        assertThat(result).isNotSameAs(original);
        assertThat(result.address().city()).isEqualTo("Berlin");
        assertThat(result.address().zip()).isEqualTo("80331");
        // original untouched
        assertThat(original.address().city()).isEqualTo("Munich");
    }
}
