/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.ottl.OttlEngine;
import io.ottl.quarkus.DefaultRuleRepositoryProducer;
import io.ottl.quarkus.OttlRecorder;
import io.ottl.rules.RuleManager;
import io.ottl.rules.RuleRepository;
import io.ottl.spi.PropertyAccessorRegistry;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

/**
 * Build-time augmentation for the OTTL extension.
 *
 * <p>Resolvers and functions are registered explicitly by {@link OttlRecorder}. ServiceLoader
 * providers are not copied into the native image, so reflective bean and record resolvers cannot
 * appear beside the generated accessors.
 */
public class OttlProcessor {

    private static final String FEATURE = "ottl";
    private static final DotName OTTL_ACCESSIBLE = DotName.createSimple(io.ottl.quarkus.OttlAccessible.class.getName());
    private static final DotName SINGLETON = DotName.createSimple(Singleton.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem defaultBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(DefaultRuleRepositoryProducer.class)
                .setUnremovable()
                .build();
    }

    /**
     * Discover {@code @OttlAccessible} types and generate a reflection-free accessor for each.
     * Jackson still needs reflective constructors, methods and fields for REST (de)serialization;
     * property access itself does not.
     */
    @BuildStep
    AccessibleTypesBuildItem accessibleTypes(CombinedIndexBuildItem combinedIndex,
                                              BuildProducer<GeneratedClassBuildItem> generatedClasses,
                                              BuildProducer<GeneratedBeanBuildItem> generatedBeans,
                                              BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
                                              BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {

        GeneratedClassGizmoAdaptor classAdaptor = new GeneratedClassGizmoAdaptor(generatedClasses, true);
        List<AccessorGenerator.Generated> generated = new ArrayList<>();
        List<DotName> accessibleTypes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (AnnotationInstance ann : combinedIndex.getIndex().getAnnotations(OTTL_ACCESSIBLE)) {
            if (ann.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            ClassInfo classInfo = ann.target().asClass();
            String typeName = classInfo.name().toString();
            if (!seen.add(typeName)) {
                continue;
            }
            Class<?> type = loadClass(typeName);
            generated.add(AccessorGenerator.generate(type, classAdaptor));
            accessibleTypes.add(classInfo.name());
        }

        if (!accessibleTypes.isEmpty()) {
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                    accessibleTypes.stream().map(DotName::toString).toArray(String[]::new))
                    .constructors().methods().fields().build());
        }

        AccessorGenerator.generateRegistry(generated, new GeneratedBeanGizmoAdaptor(generatedBeans));
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(PropertyAccessorRegistry.class));
        return new AccessibleTypesBuildItem(accessibleTypes);
    }

    /**
     * Discover {@code @OttlFunction} methods, generate direct invokers, and synthesize the engine.
     * Host classes without a scope become {@code @Singleton} so a dependent instance is not captured
     * for the life of the process. Classes that already declare a scope keep it.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    SyntheticBeanBuildItem engine(OttlRecorder recorder,
                                  CombinedIndexBuildItem combinedIndex,
                                  BuildProducer<AdditionalBeanBuildItem> additionalBeans,
                                  BuildProducer<GeneratedBeanBuildItem> generatedBeans,
                                  BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        List<FunctionDiscovery.DiscoveredFunction> functions = FunctionDiscovery.discover(combinedIndex.getIndex());
        if (!functions.isEmpty()) {
            Set<String> hostClasses = new LinkedHashSet<>();
            for (FunctionDiscovery.DiscoveredFunction function : functions) {
                hostClasses.add(function.metadata().getDeclaringClass());
            }
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(hostClasses)
                    .setUnremovable()
                    .setDefaultScope(SINGLETON)
                    .build());
            FunctionInvokerGenerator.generate(functions, new GeneratedBeanGizmoAdaptor(generatedBeans));
            String[] invokers = functions.stream()
                    .map(FunctionDiscovery.DiscoveredFunction::invokerClassName)
                    .toArray(String[]::new);
            unremovableBeans.produce(UnremovableBeanBuildItem.beanClassNames(invokers));
        }

        return SyntheticBeanBuildItem.configure(OttlEngine.class)
                .scope(Singleton.class)
                .unremovable()
                .setRuntimeInit()
                .runtimeValue(recorder.createEngine())
                .done();
    }

    /**
     * Default {@code RuleManager<T>} bean for every approved type. An application {@code @Produces}
     * method of the same parameterized type is selected instead, because these beans are
     * {@code @DefaultBean}s. The raw {@code RuleManager} type is not registered, so managers for
     * different domain types do not collide.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void ruleManagers(OttlRecorder recorder,
                      AccessibleTypesBuildItem accessibleTypes,
                      BuildProducer<SyntheticBeanBuildItem> beans) {
        for (DotName typeName : accessibleTypes.types()) {
            Type typeArgument = Type.create(typeName, Type.Kind.CLASS);
            Type parameterized = ParameterizedType.create(RuleManager.class, typeArgument);
            Function<SyntheticCreationalContext<RuleManager<?>>, RuleManager<?>> creator =
                    recorder.createRuleManager(typeName.toString());
            beans.produce(SyntheticBeanBuildItem.create(RuleManager.class)
                    .addType(parameterized)
                    .scope(Singleton.class)
                    .defaultBean()
                    .setRuntimeInit()
                    .addInjectionPoint(Type.create(OttlEngine.class))
                    .addInjectionPoint(Type.create(RuleRepository.class))
                    .createWith(creator)
                    .done());
        }
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load @OttlAccessible type " + name, e);
        }
    }
}
