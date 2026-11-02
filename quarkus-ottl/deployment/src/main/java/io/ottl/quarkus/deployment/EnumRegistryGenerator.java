/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import io.ottl.spi.EnumConstantRegistry;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates a {@link EnumConstantRegistry} bean whose constructor loads each allowlisted constant
 * with {@code getstatic}. Rules can then name {@code EnumName.CONSTANT} without reflection.
 */
final class EnumRegistryGenerator {

    /** Fully-qualified name of the generated registry bean. */
    static final String REGISTRY_CLASS = "io.ottl.quarkus.enums.GeneratedEnumRegistry";

    private static final MethodDescriptor SUPER =
            MethodDescriptor.ofConstructor(EnumConstantRegistry.class);
    private static final MethodDescriptor DECLARE = MethodDescriptor.ofMethod(
            EnumConstantRegistry.class, "declare", void.class, String.class, String.class);
    private static final MethodDescriptor REGISTER = MethodDescriptor.ofMethod(
            EnumConstantRegistry.class, "register", void.class, String.class, String.class, Object.class);

    private EnumRegistryGenerator() {
    }

    static void generate(List<Class<?>> enumTypes, ClassOutput output) {
        try (ClassCreator cc = ClassCreator.builder().classOutput(output)
                .className(REGISTRY_CLASS)
                .superClass(EnumConstantRegistry.class)
                .build()) {
            cc.addAnnotation(Singleton.class);
            MethodCreator ctor = cc.getMethodCreator("<init>", void.class);
            ctor.invokeSpecialMethod(SUPER, ctor.getThis());
            for (Class<?> type : enumTypes) {
                ctor.invokeVirtualMethod(DECLARE, ctor.getThis(),
                        ctor.load(type.getName()), ctor.load(type.getSimpleName()));
                for (Object constant : type.getEnumConstants()) {
                    String name = ((Enum<?>) constant).name();
                    ResultHandle value = ctor.readStaticField(FieldDescriptor.of(type, name, type));
                    ctor.invokeVirtualMethod(REGISTER, ctor.getThis(), ctor.load(type.getName()), ctor.load(name), value);
                }
            }
            ctor.returnValue(null);
        }
    }
}
