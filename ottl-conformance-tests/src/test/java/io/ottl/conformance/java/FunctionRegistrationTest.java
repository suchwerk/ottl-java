/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.java;

import io.ottl.OttlCompilationException;
import io.ottl.OttlEngine;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import io.ottl.conformance.model.Customer;
import io.ottl.function.Argument;
import io.ottl.function.ExecutionContext;
import io.ottl.function.OttlFunction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Custom function registration via the engine builder, and the allowlist enforced for unknown ones. */
class FunctionRegistrationTest {

    /** A tiny custom converter: {@code Upper("gold") -> "GOLD"}. */
    private static final class UpperFunction implements OttlFunction {
        @Override
        public String name() {
            return "Upper";
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
            Object v = args.get(0).value();
            return v == null ? null : v.toString().toUpperCase();
        }
    }

    @Test
    void customConverterIsInvokable() {
        OttlEngine engine = OttlEngine.builder()
                .profile(OttlProfile.JAVA)
                .function(new UpperFunction())
                .build();

        OttlProgram<Customer> program = engine.compile(
                "set(customer.tier, Upper(\"gold\"))", Customer.class);
        Customer c = new Customer("Ada", true, "SILVER");
        program.apply(c);
        assertThat(c.getTier()).isEqualTo("GOLD");
    }

    @Test
    void unknownFunctionIsRejectedAtCompileTime() {
        OttlEngine engine = OttlEngine.builder().profile(OttlProfile.JAVA).build();
        assertThatThrownBy(() -> engine.compile("set(customer.tier, Upper(\"gold\"))", Customer.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Upper");
    }
}
