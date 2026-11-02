/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.java;

import io.ottl.OttlCompilationException;
import io.ottl.OttlEngine;
import io.ottl.OttlLimits;
import io.ottl.OttlProfile;
import io.ottl.conformance.model.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Compile-time validation: parse errors, arity, unknown functions and complexity limits. */
class CompilationErrorTest {

    private final OttlEngine engine = OttlEngine.builder().profile(OttlProfile.JAVA).build();

    @Test
    void parseErrorsSurfaceAsCompilationExceptionsWithPosition() {
        assertThatThrownBy(() -> engine.compile("set(order.status \"X\")", Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Parse error");
    }

    @Test
    void wrongArityIsRejected() {
        assertThatThrownBy(() -> engine.compile("set(order.status)", Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("expects between");
    }

    @Test
    void unknownFunctionIsRejected() {
        assertThatThrownBy(() -> engine.compile("frobnicate(order.status, 1)", Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Unknown or disallowed function");
    }

    @Test
    void statementLimitIsEnforced() {
        OttlEngine limited = OttlEngine.builder()
                .profile(OttlProfile.JAVA)
                .limits(new OttlLimits(10_000, 2, 16, 16))
                .build();
        String src = """
                set(order.status, "A")
                set(order.status, "B")
                set(order.status, "C")
                """;
        assertThatThrownBy(() -> limited.compile(src, Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Too many statements");
    }

    @Test
    void statementsInsideWhereBlockCountTowardLimit() {
        OttlEngine limited = OttlEngine.builder()
                .profile(OttlProfile.JAVA)
                .limits(new OttlLimits(10_000, 2, 16, 16))
                .build();
        String src = """
                where order.amount > 0 {
                    set(order.status, "A")
                    set(order.status, "B")
                    set(order.status, "C")
                }
                """;
        assertThatThrownBy(() -> limited.compile(src, Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Too many statements");
    }

    @Test
    void whereBlockNestingIsBounded() {
        OttlEngine limited = OttlEngine.builder()
                .profile(OttlProfile.JAVA)
                .limits(new OttlLimits(10_000, 100, 16, 1))
                .build();
        String src = """
                where order.amount > 0 {
                    where order.amount > 1 {
                        set(order.status, "A")
                    }
                }
                """;
        assertThatThrownBy(() -> limited.compile(src, Order.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Nesting too deep");
    }
}
