/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus;

import io.ottl.OttlEngine;
import io.ottl.OttlLimits;
import io.ottl.context.jackson.JsonNodeResolver;
import io.ottl.context.map.MapResolver;
import io.ottl.functions.standard.DeleteKeyFunction;
import io.ottl.functions.standard.KeepKeysFunction;
import io.ottl.functions.standard.SetFunction;
import io.ottl.spi.PropertyAccessorRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.SmallRyeConfig;
import org.eclipse.microprofile.config.ConfigProvider;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Records construction of the shared {@link OttlEngine} at runtime init.
 *
 * <p>The engine is wired <strong>explicitly and without {@link java.util.ServiceLoader}</strong> so
 * it behaves identically in JVM and native mode:
 * <ul>
 *   <li>Approved domain types resolve through build-time generated accessors
 *       ({@link PropertyAccessorRegistry}, fetched from Arc) — no reflection.</li>
 *   <li>Maps and Jackson {@code JsonNode} trees use their (reflection-free) resolvers.</li>
 *   <li>Standard editors ({@code set}/{@code delete_key}/{@code keep_keys}) and discovered
 *       {@code @OttlFunction} methods are registered.</li>
 *   <li>Strict type checking rejects rules referencing unknown types/properties at compile time.</li>
 * </ul>
 */
@Recorder
public class OttlRecorder {

    private static final Map<String, Class<?>> PRIMITIVES = Map.of(
            "boolean", boolean.class, "byte", byte.class, "char", char.class, "short", short.class,
            "int", int.class, "long", long.class, "float", float.class, "double", double.class);

    public RuntimeValue<OttlEngine> createEngine(List<FunctionMetadata> metadata) {
        SmallRyeConfig smallRyeConfig = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        OttlConfig config = smallRyeConfig.getConfigMapping(OttlConfig.class);
        PropertyAccessorRegistry registry = Arc.container().instance(PropertyAccessorRegistry.class).get();

        OttlEngine.Builder builder = OttlEngine.builder()
                .profile(config.profile())
                .loadDefaults(false)
                .limits(new OttlLimits(config.maxSourceLength(), config.maxStatements(), 16, 16))
                .accessorRegistry(registry)
                .strictTypes(true)
                .resolver(new MapResolver())
                .resolver(new JsonNodeResolver())
                .function(new SetFunction())
                .function(new DeleteKeyFunction())
                .function(new KeepKeysFunction());

        for (FunctionMetadata meta : metadata) {
            Class<?> declaring = loadClass(meta.getDeclaringClass());
            Object bean = Arc.container().instance(declaring).get();
            if (bean == null) {
                throw new IllegalStateException(
                        "No CDI bean found for @OttlFunction host " + meta.getDeclaringClass());
            }
            Method method = resolveMethod(declaring, meta.getMethodName(), meta.getParameterTypes());
            builder.function(new MethodFunctionAdapter(meta.getFunctionName(), bean, method));
        }
        return new RuntimeValue<>(builder.build());
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load class " + name, e);
        }
    }

    private static Method resolveMethod(Class<?> declaring, String methodName, List<String> paramTypeNames) {
        Class<?>[] params = new Class<?>[paramTypeNames.size()];
        for (int i = 0; i < params.length; i++) {
            String n = paramTypeNames.get(i);
            params[i] = PRIMITIVES.containsKey(n) ? PRIMITIVES.get(n) : loadClass(n);
        }
        try {
            return declaring.getDeclaredMethod(methodName, params);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Cannot resolve @OttlFunction method " + declaring.getName() + "#" + methodName, e);
        }
    }
}
