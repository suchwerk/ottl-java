/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.java;

import io.ottl.OttlCompilationException;
import io.ottl.OttlEngine;
import io.ottl.OttlEvaluationException;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import io.ottl.spi.DefaultPropertyAccessorRegistry;
import io.ottl.spi.EnumConstantRegistry;
import io.ottl.spi.PropertyAccessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Enum constants are expression values. The enum itself is not a context type. */
class EnumConstantTest {

    public enum Color {
        RED,
        BLUE
    }

    public record Paint(String id, Color color) {
    }

    private final OttlEngine engine = OttlEngine.builder()
            .profile(OttlProfile.JAVA)
            .enumRegistry(EnumConstantRegistry.of(Color.class))
            .build();

    @Test
    void constantCanBeComparedAndAssigned() {
        OttlProgram<Paint> program = engine.compile("""
                set(paint.color, Color.BLUE) where paint.color == Color.RED
                """, Paint.class);
        Paint red = new Paint("p-1", Color.RED);
        assertThat(program.apply(red)).isEqualTo(new Paint("p-1", Color.BLUE));
        assertThat(red.color()).isEqualTo(Color.RED);

        Paint blue = new Paint("p-2", Color.BLUE);
        assertThat(program.apply(blue)).isEqualTo(blue);
    }

    @Test
    void unknownConstantFailsWhenTheExpressionRuns() {
        OttlProgram<Paint> program = engine.compile("set(paint.color, Color.GREEN)", Paint.class);
        assertThatThrownBy(() -> program.apply(new Paint("p", Color.RED)))
                .isInstanceOf(OttlEvaluationException.class)
                .hasMessageContaining("Unknown enum constant 'GREEN'");
    }

    @Test
    void strictModeRejectsAnUnknownConstantBeforeActivation() {
        DefaultPropertyAccessorRegistry accessors = new DefaultPropertyAccessorRegistry();
        accessors.register(Paint.class, descriptor(
                "id:java.lang.String:r;color:" + Color.class.getName() + ":rw"));
        OttlEngine strict = OttlEngine.builder()
                .profile(OttlProfile.JAVA)
                .loadDefaults(true)
                .accessorRegistry(accessors)
                .enumRegistry(EnumConstantRegistry.of(Color.class))
                .strictTypes(true)
                .build();
        assertThatThrownBy(() -> strict.compile("set(paint.color, Color.GREEN)", Paint.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("Unknown enum constant 'GREEN'");
        assertThatThrownBy(() -> strict.compile("set(Color.RED, Color.BLUE)", Paint.class))
                .isInstanceOf(OttlCompilationException.class)
                .hasMessageContaining("not writable");
        strict.compile("set(paint.color, Color.BLUE) where paint.color == Color.RED", Paint.class);
    }

    private static PropertyAccessor descriptor(String descriptor) {
        return new PropertyAccessor() {
            @Override
            public Object get(Object instance, String property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object set(Object instance, String property, Object value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String descriptor() {
                return descriptor;
            }
        };
    }
}
