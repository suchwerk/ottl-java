/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import io.ottl.ExecutionMode;
import io.ottl.OttlEvaluationException;
import io.ottl.function.Argument;
import io.ottl.function.ExecutionContext;
import io.ottl.function.FunctionRegistry;
import io.ottl.lang.ast.Expression;
import io.ottl.quarkus.OttlFunction;
import io.ottl.spi.ResolverRegistry;
import io.quarkus.gizmo.ClassOutput;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionDiscoveryTest {

    @Test
    void duplicateNamesFailWithBothLocations() {
        assertThatThrownBy(() -> discover(NormalizeA.class, NormalizeB.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Normalize")
                .hasMessageContaining(NormalizeA.class.getName())
                .hasMessageContaining(NormalizeB.class.getName());
    }

    @Test
    void builtinNameIsRejected() {
        assertThatThrownBy(() -> discover(BuiltinClash.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("set")
                .hasMessageContaining("built-in");
    }

    @Test
    void privateStaticGenericAndArrayMethodsAreRejected() {
        assertThatThrownBy(() -> discover(PrivateHost.class)).hasMessageContaining("public");
        assertThatThrownBy(() -> discover(StaticHost.class)).hasMessageContaining("instance method");
        assertThatThrownBy(() -> discover(GenericHost.class)).hasMessageContaining("type parameters");
        assertThatThrownBy(() -> discover(ArrayHost.class)).hasMessageContaining("array");
        assertThatThrownBy(() -> discover(FinalNormalScoped.class)).hasMessageContaining("final");
        assertThatThrownBy(() -> discover(BadName.class)).hasMessageContaining("not an identifier");
    }

    @Test
    void generatedInvokerCallsTheBeanDirectlyAndCoercesNumbers() throws Exception {
        FunctionDiscovery.DiscoveredFunction discovered = discover(SampleFunctions.class).get(0);
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        ClassOutput output = (name, data) -> bytes.put(name.replace('/', '.'), data);
        FunctionInvokerGenerator.generate(List.of(discovered), output);
        ClassLoader loader = new ClassLoader(FunctionDiscoveryTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] data = bytes.get(name);
                if (data == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, data, 0, data.length);
            }
        };
        Object invoker = loader.loadClass(discovered.invokerClassName()).getDeclaredConstructor().newInstance();
        Field host = invoker.getClass().getDeclaredField("host");
        host.setAccessible(true);
        host.set(invoker, new SampleFunctions());
        io.ottl.function.OttlFunction function = (io.ottl.function.OttlFunction) invoker;
        assertThat(function.name()).isEqualTo("DoubleIt");
        assertThat(function.minArgs()).isEqualTo(1);

        ExecutionContext ctx = new ExecutionContext(
                null, "root", ResolverRegistry.of(), FunctionRegistry.builder().build(), ExecutionMode.MUTATE);
        Object doubled = function.invoke(ctx, List.of(new Argument(new Expression.IntLiteral(21), ctx)));
        assertThat(doubled).isEqualTo(42);

        FunctionDiscovery.DiscoveredFunction failing = discover(FailingFunction.class).get(0);
        Map<String, byte[]> failBytes = new LinkedHashMap<>();
        FunctionInvokerGenerator.generate(List.of(failing), (name, data) -> failBytes.put(name.replace('/', '.'), data));
        ClassLoader failLoader = new ClassLoader(FunctionDiscoveryTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] data = failBytes.get(name);
                if (data == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, data, 0, data.length);
            }
        };
        Object failInvoker = failLoader.loadClass(failing.invokerClassName()).getDeclaredConstructor().newInstance();
        Field failHost = failInvoker.getClass().getDeclaredField("host");
        failHost.setAccessible(true);
        failHost.set(failInvoker, new FailingFunction());
        io.ottl.function.OttlFunction fail = (io.ottl.function.OttlFunction) failInvoker;
        assertThatThrownBy(() -> fail.invoke(ctx, List.of()))
                .isInstanceOf(OttlEvaluationException.class)
                .hasMessageContaining("Boom")
                .hasMessageContaining("Explode");
    }

    private static List<FunctionDiscovery.DiscoveredFunction> discover(Class<?>... types) throws Exception {
        Indexer indexer = new Indexer();
        for (Class<?> type : types) {
            String resource = type.getName().replace('.', '/') + ".class";
            try (InputStream in = type.getClassLoader().getResourceAsStream(resource)) {
                indexer.index(in);
            }
        }
        Index index = indexer.complete();
        return FunctionDiscovery.discover(index);
    }

    public static class SampleFunctions {
        @OttlFunction("DoubleIt")
        public int doubleIt(int value) {
            return value * 2;
        }
    }

    public static class FailingFunction {
        @OttlFunction("Explode")
        public void explode() {
            throw new IllegalStateException("Boom");
        }
    }

    public static class NormalizeA {
        @OttlFunction("Normalize")
        public String normalize(String value) {
            return value;
        }
    }

    public static class NormalizeB {
        @OttlFunction("Normalize")
        public String other(String value) {
            return value;
        }
    }

    public static class BuiltinClash {
        @OttlFunction("set")
        public String set(String value) {
            return value;
        }
    }

    public static class PrivateHost {
        @OttlFunction("Hidden")
        private String hidden(String value) {
            return value;
        }
    }

    public static class StaticHost {
        @OttlFunction("StaticOne")
        public static String staticOne(String value) {
            return value;
        }
    }

    public static class GenericHost {
        @OttlFunction("Id")
        public <T> T id(T value) {
            return value;
        }
    }

    public static class ArrayHost {
        @OttlFunction("Join")
        public String join(String[] parts) {
            return parts == null ? "" : String.join(",", parts);
        }
    }

    @ApplicationScoped
    public static class FinalNormalScoped {
        @OttlFunction("FinalOne")
        public final String finalOne(String value) {
            return value;
        }
    }

    public static class BadName {
        @OttlFunction("not-an-ident")
        public String bad(String value) {
            return value;
        }
    }
}
