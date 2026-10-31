/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import io.ottl.OttlEngine;
import io.ottl.function.OttlFunction;
import io.ottl.quarkus.DefaultRuleRepositoryProducer;
import io.ottl.quarkus.FunctionMetadata;
import io.ottl.quarkus.OttlRecorder;
import io.ottl.spi.PropertyResolver;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import jakarta.inject.Singleton;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Build-time augmentation for the OTTL extension.
 */
public class OttlProcessor {

    private static final String FEATURE = "ottl";
    private static final DotName OTTL_FUNCTION = DotName.createSimple(io.ottl.quarkus.OttlFunction.class.getName());

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

    /** Register the SPI service files so ServiceLoader discovery works in native image. */
    @BuildStep
    void nativeServices(BuildProducer<ServiceProviderBuildItem> services) {
        services.produce(ServiceProviderBuildItem.allProvidersFromClassPath(PropertyResolver.class.getName()));
        services.produce(ServiceProviderBuildItem.allProvidersFromClassPath(OttlFunction.class.getName()));
    }

    /**
     * Discover {@code @OttlFunction} methods, register their hosts as (reflective) beans, and
     * synthesize the shared {@link OttlEngine} bean wired with those functions.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    SyntheticBeanBuildItem engine(OttlRecorder recorder,
                                  CombinedIndexBuildItem combinedIndex,
                                  BuildProducer<AdditionalBeanBuildItem> additionalBeans,
                                  BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        List<FunctionMetadata> metadata = new ArrayList<>();
        Set<String> hostClasses = new LinkedHashSet<>();

        for (AnnotationInstance ann : combinedIndex.getIndex().getAnnotations(OTTL_FUNCTION)) {
            if (ann.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }
            MethodInfo method = ann.target().asMethod();
            AnnotationValue nameValue = ann.value();
            String functionName = (nameValue == null || nameValue.asString().isEmpty())
                    ? method.name()
                    : nameValue.asString();
            String declaring = method.declaringClass().name().toString();
            List<String> params = new ArrayList<>();
            for (Type t : method.parameterTypes()) {
                params.add(t.name().toString());
            }
            metadata.add(new FunctionMetadata(functionName, declaring, method.name(), params));
            hostClasses.add(declaring);
        }

        if (!hostClasses.isEmpty()) {
            String[] classes = hostClasses.toArray(new String[0]);
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(classes)
                    .setUnremovable()
                    .build());
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(classes)
                    .constructors().methods().build());
        }

        return SyntheticBeanBuildItem.configure(OttlEngine.class)
                .scope(Singleton.class)
                .unremovable()
                .setRuntimeInit()
                .runtimeValue(recorder.createEngine(metadata))
                .done();
    }
}
