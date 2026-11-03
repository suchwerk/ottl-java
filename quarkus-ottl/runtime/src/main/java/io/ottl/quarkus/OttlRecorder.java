/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus;

import java.util.function.Function;

import io.ottl.OttlEngine;
import io.ottl.OttlLimits;
import io.ottl.context.jackson.JsonNodeResolver;
import io.ottl.context.map.MapResolver;
import io.ottl.functions.standard.StandardFunctions;
import io.ottl.rules.RuleManager;
import io.ottl.rules.RuleRepository;
import io.ottl.spi.EnumConstantRegistry;
import io.ottl.spi.PropertyAccessorRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.SmallRyeConfig;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Records construction of the shared {@link OttlEngine} at runtime init.
 *
 * <p>The engine is wired explicitly and does not call {@link java.util.ServiceLoader}. Domain
 * properties go through the generated {@link PropertyAccessorRegistry}. Standard editors and
 * converters come from {@link StandardFunctions#all()}. {@code @OttlFunction} methods are invoked
 * by generated CDI beans, not by {@code Method.invoke}. Strict type checking stays on.
 */
@Recorder
public class OttlRecorder {

    static final int MAX_CONFIGURED_SOURCE_LENGTH = 1_000_000;
    static final int MAX_CONFIGURED_STATEMENTS = 10_000;

    public RuntimeValue<OttlEngine> createEngine() {
        SmallRyeConfig smallRyeConfig = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        OttlConfig config = smallRyeConfig.getConfigMapping(OttlConfig.class);
        PropertyAccessorRegistry registry = Arc.container().instance(PropertyAccessorRegistry.class).get();
        EnumConstantRegistry enums = Arc.container().instance(EnumConstantRegistry.class).get();

        OttlEngine.Builder builder = OttlEngine.builder()
                .profile(config.profile())
                .dialect(config.dialect())
                .loadDefaults(false)
                .limits(limits(config))
                .accessorRegistry(registry)
                .enumRegistry(enums)
                .strictTypes(true)
                .resolver(new MapResolver())
                .resolver(new JsonNodeResolver());
        for (io.ottl.function.OttlFunction function : StandardFunctions.all()) {
            builder.function(function);
        }

        for (InstanceHandle<io.ottl.function.OttlFunction> handle : Arc.container()
                .listAll(io.ottl.function.OttlFunction.class)) {
            String beanClass = handle.getBean().getBeanClass().getName();
            if (beanClass.startsWith("io.ottl.quarkus.functions.F_")) {
                builder.function(handle.get());
            }
        }
        return new RuntimeValue<>(builder.build());
    }

    /**
     * Supplies the default {@code RuleManager<T>} for one approved type. The injection points make
     * Arc create the engine and the selected {@link RuleRepository} first.
     */
    public Function<SyntheticCreationalContext<RuleManager<?>>, RuleManager<?>> createRuleManager(String className) {
        return ctx -> {
            OttlEngine engine = ctx.getInjectedReference(OttlEngine.class);
            RuleRepository repository = ctx.getInjectedReference(RuleRepository.class);
            Class<?> type = loadClass(className);
            return engine.ruleManager(type, repository);
        };
    }

    static OttlLimits limits(OttlConfig config) {
        int maxSourceLength = config.maxSourceLength();
        int maxStatements = config.maxStatements();
        if (maxSourceLength < 1 || maxSourceLength > MAX_CONFIGURED_SOURCE_LENGTH) {
            throw new IllegalArgumentException(
                    "quarkus.ottl.max-source-length must be between 1 and " + MAX_CONFIGURED_SOURCE_LENGTH
                            + " (was " + maxSourceLength + ")");
        }
        if (maxStatements < 1 || maxStatements > MAX_CONFIGURED_STATEMENTS) {
            throw new IllegalArgumentException(
                    "quarkus.ottl.max-statements must be between 1 and " + MAX_CONFIGURED_STATEMENTS
                            + " (was " + maxStatements + ")");
        }
        OttlLimits defaults = OttlLimits.defaults();
        return new OttlLimits(maxSourceLength, maxStatements, defaults.maxPathDepth(), defaults.maxCallDepth());
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load @OttlAccessible type " + name, e);
        }
    }
}
