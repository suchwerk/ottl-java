/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.java;

import io.ottl.ExecutionMode;
import io.ottl.OttlEngine;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import io.ottl.conformance.model.Customer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** JAVA-profile: mutable POJOs transformed with MUTATE semantics. */
class PojoMutationTest {

    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.JAVA).build();

    @Test
    void pojoIsMutatedInPlaceWhenConditionHolds() {
        OttlProgram<Customer> program = engine.compile(
                "set(customer.tier, \"GOLD\") where customer.premium == true", Customer.class);
        assertThat(program.mode()).isEqualTo(ExecutionMode.MUTATE);

        Customer c = new Customer("Ada", true, "SILVER");
        Customer result = program.apply(c);

        assertThat(result).isSameAs(c); // mutated in place
        assertThat(c.getTier()).isEqualTo("GOLD");
    }

    @Test
    void pojoUnchangedWhenConditionFails() {
        OttlProgram<Customer> program = engine.compile(
                "set(customer.tier, \"GOLD\") where customer.premium == true", Customer.class);
        Customer c = new Customer("Bob", false, "SILVER");
        program.apply(c);
        assertThat(c.getTier()).isEqualTo("SILVER");
    }
}
