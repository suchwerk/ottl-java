/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus;

import io.ottl.OttlEngine;
import io.ottl.OttlLimits;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.SmallRyeConfig;
import org.eclipse.microprofile.config.ConfigProvider;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Records construction of the shared {@link OttlEngine} at runtime init. Custom functions discovered
 * at build time are resolved from the Arc container here, so no classpath scanning occurs at runtime.
 */
@Recorder
public class OttlRecorder {

    private static final Map<String, Class<?>> PRIMITIVES = Map.of(
            "boolean", boolean.class, "byte", byte.class, "char", char.class, "short", short.class,
            "int", int.class, "long", long.class, "float", float.class, "double", double.class);

    public RuntimeValue<OttlEngine> createEngine(List<FunctionMetadata> metadata) {
        SmallRyeConfig smallRyeConfig = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        OttlConfig config = smallRyeConfig.getConfigMapping(OttlConfig.class);
        OttlEngine.Builder builder = OttlEngine.builder()
                .profile(config.profile())
                .limits(new OttlLimits(config.maxSourceLength(), config.maxStatements(), 16, 16));

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
