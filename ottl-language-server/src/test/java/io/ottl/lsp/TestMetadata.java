/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp;

import io.ottl.function.DescribedFunction;
import io.ottl.function.ExecutionContext;
import io.ottl.function.FunctionRegistry;
import io.ottl.function.OttlFunction;
import io.ottl.lsp.metadata.OttlMetadata;
import io.ottl.spi.DefaultPropertyAccessorRegistry;
import io.ottl.spi.EnumConstantRegistry;
import io.ottl.spi.PropertyAccessor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Builds an {@link OttlMetadata} over the same registry types the engine uses, for LSP tests. The
 * accessors only need a correct {@code descriptor()} — the analyzer never calls get/set.
 */
final class TestMetadata {

    record Customer(String name, String tier) {
    }

    record Order(String id, BigDecimal amount, String status, String priority, Customer customer) {
    }

    public enum OrderStatus {
        PENDING,
        PROCESSING,
        COMPLETED
    }

    private TestMetadata() {
    }

    static OttlMetadata build() {
        DefaultPropertyAccessorRegistry registry = new DefaultPropertyAccessorRegistry();
        registry.register(Order.class, descriptor(
                "id:java.lang.String:r"
                        + ";amount:java.math.BigDecimal:r"
                        + ";status:java.lang.String:rw"
                        + ";priority:java.lang.String:rw"
                        + ";customer:" + Customer.class.getName() + ":r"));
        registry.register(Customer.class, descriptor(
                "name:java.lang.String:rw;tier:java.lang.String:rw"));

        FunctionRegistry functions = FunctionRegistry.builder()
                .add(editor("set", 2, 2))
                .add(editor("delete_key", 2, 2))
                .add(highValue())
                .build();

        return new OttlMetadata(registry, functions, EnumConstantRegistry.of(OrderStatus.class));
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

    private static OttlFunction editor(String name, int min, int max) {
        return new OttlFunction() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Object invoke(ExecutionContext ctx, List<io.ottl.function.Argument> args) {
                return null;
            }

            @Override
            public int minArgs() {
                return min;
            }

            @Override
            public int maxArgs() {
                return max;
            }
        };
    }

    private static OttlFunction highValue() {
        class HighValue implements OttlFunction, DescribedFunction {
            @Override
            public String name() {
                return "IsHighValue";
            }

            @Override
            public Object invoke(ExecutionContext ctx, List<io.ottl.function.Argument> args) {
                return Boolean.FALSE;
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
            public String documentation() {
                return "True if the amount exceeds the high-value threshold.";
            }

            @Override
            public List<String> parameterNames() {
                return List.of("amount");
            }

            @Override
            public List<String> parameterTypeNames() {
                return List.of("BigDecimal");
            }
        }
        return new HighValue();
    }
}
