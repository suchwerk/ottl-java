/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import io.ottl.spi.DefaultPropertyAccessorRegistry;
import io.ottl.spi.PropertyAccessor;
import io.ottl.spi.ValueCoercion;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import jakarta.inject.Singleton;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Generates, at build time, a reflection-free {@link PropertyAccessor} for one
 * {@code @OttlAccessible} type. Property discovery and validation live in {@link AccessibleTypeModel};
 * this class only emits Gizmo bytecode from that model.
 */
final class AccessorGenerator {

    /** Result of generating an accessor for a type. */
    record Generated(String typeName, String accessorClassName) {
    }

    /** Fully-qualified name of the generated {@link io.ottl.spi.PropertyAccessorRegistry} bean. */
    static final String REGISTRY_CLASS = "io.ottl.quarkus.accessors.GeneratedAccessorRegistry";

    private static final MethodDescriptor STRING_EQUALS =
            MethodDescriptor.ofMethod(String.class, "equals", boolean.class, Object.class);
    private static final MethodDescriptor COERCE =
            MethodDescriptor.ofMethod(ValueCoercion.class, "coerce", Object.class, Object.class, Class.class);
    private static final MethodDescriptor REGISTRY_SUPER_CTOR =
            MethodDescriptor.ofConstructor(DefaultPropertyAccessorRegistry.class);
    private static final MethodDescriptor REGISTRY_REGISTER = MethodDescriptor.ofMethod(
            DefaultPropertyAccessorRegistry.class, "register", void.class, Class.class, PropertyAccessor.class);

    private AccessorGenerator() {
    }

    /**
     * Generate a {@code @Singleton} registry bean whose constructor instantiates every generated
     * accessor with {@code new}. No runtime reflection is involved.
     */
    static void generateRegistry(List<Generated> accessors, ClassOutput output) {
        try (ClassCreator cc = ClassCreator.builder().classOutput(output)
                .className(REGISTRY_CLASS)
                .superClass(DefaultPropertyAccessorRegistry.class)
                .build()) {
            cc.addAnnotation(Singleton.class);
            MethodCreator ctor = cc.getMethodCreator("<init>", void.class);
            ctor.invokeSpecialMethod(REGISTRY_SUPER_CTOR, ctor.getThis());
            for (Generated g : accessors) {
                ResultHandle accessor = ctor.newInstance(MethodDescriptor.ofConstructor(g.accessorClassName()));
                ResultHandle typeClass = ctor.loadClass(g.typeName());
                ctor.invokeVirtualMethod(REGISTRY_REGISTER, ctor.getThis(), typeClass, accessor);
            }
            ctor.returnValue(null);
        }
    }

    static Generated generate(Class<?> type, ClassOutput output) {
        if (type.isRecord()) {
            AccessibleTypeModel.RecordModel model = AccessibleTypeModel.discoverRecord(type);
            generateRecordAccessor(model, output);
            return new Generated(type.getName(), model.accessorClassName());
        }
        AccessibleTypeModel.BeanModel model = AccessibleTypeModel.discoverBean(type);
        generateBeanAccessor(model, output);
        return new Generated(type.getName(), model.accessorClassName());
    }

    private static void generateRecordAccessor(AccessibleTypeModel.RecordModel model, ClassOutput output) {
        List<AccessibleTypeModel.RecordProperty> components = model.components();
        Class<?>[] paramTypes = components.stream().map(AccessibleTypeModel.RecordProperty::type).toArray(Class<?>[]::new);
        MethodDescriptor canonicalCtor = MethodDescriptor.ofConstructor(model.type(), paramTypes);

        StringBuilder descriptor = new StringBuilder();
        for (AccessibleTypeModel.RecordProperty component : components) {
            append(descriptor, component.name(), component.type(), "rw");
        }

        try (ClassCreator cc = ClassCreator.builder().classOutput(output)
                .className(model.accessorClassName()).interfaces(PropertyAccessor.class).build()) {

            MethodCreator get = cc.getMethodCreator("get", Object.class, Object.class, String.class);
            ResultHandle typed = get.checkCast(get.getMethodParam(0), model.type());
            ResultHandle prop = get.getMethodParam(1);
            for (AccessibleTypeModel.RecordProperty component : components) {
                BytecodeCreator tb = get.ifTrue(get.invokeVirtualMethod(STRING_EQUALS, prop, get.load(component.name())))
                        .trueBranch();
                ResultHandle raw = tb.invokeVirtualMethod(MethodDescriptor.ofMethod(component.accessor()), typed);
                tb.returnValue(BytecodeSupport.box(tb, raw, component.type()));
            }
            get.returnValue(get.loadNull());

            MethodCreator set = cc.getMethodCreator("set", Object.class, Object.class, String.class, Object.class);
            ResultHandle inst = set.checkCast(set.getMethodParam(0), model.type());
            ResultHandle setProp = set.getMethodParam(1);
            ResultHandle value = set.getMethodParam(2);
            for (int idx = 0; idx < components.size(); idx++) {
                AccessibleTypeModel.RecordProperty changed = components.get(idx);
                BytecodeCreator tb = set.ifTrue(
                        set.invokeVirtualMethod(STRING_EQUALS, setProp, set.load(changed.name()))).trueBranch();
                ResultHandle[] args = new ResultHandle[components.size()];
                for (int i = 0; i < components.size(); i++) {
                    AccessibleTypeModel.RecordProperty component = components.get(i);
                    if (i == idx) {
                        ResultHandle coerced = tb.invokeStaticMethod(
                                COERCE, value, BytecodeSupport.loadClassConstant(tb, component.type()));
                        args[i] = BytecodeSupport.convertArg(tb, coerced, component.type());
                    } else {
                        args[i] = tb.invokeVirtualMethod(MethodDescriptor.ofMethod(component.accessor()), inst);
                    }
                }
                tb.returnValue(tb.newInstance(canonicalCtor, args));
            }
            set.returnValue(inst);
            descriptor(cc, descriptor.toString());
        }
    }

    private static void generateBeanAccessor(AccessibleTypeModel.BeanModel model, ClassOutput output) {
        StringBuilder descriptor = new StringBuilder();
        for (AccessibleTypeModel.BeanProperty property : model.properties()) {
            append(descriptor, property.name(), property.type(), property.flags());
        }

        try (ClassCreator cc = ClassCreator.builder().classOutput(output)
                .className(model.accessorClassName()).interfaces(PropertyAccessor.class).build()) {

            MethodCreator get = cc.getMethodCreator("get", Object.class, Object.class, String.class);
            ResultHandle typed = get.checkCast(get.getMethodParam(0), model.type());
            ResultHandle prop = get.getMethodParam(1);
            for (AccessibleTypeModel.BeanProperty property : model.properties()) {
                Method getter = property.getter();
                if (getter == null) {
                    continue;
                }
                BytecodeCreator tb = get.ifTrue(get.invokeVirtualMethod(STRING_EQUALS, prop, get.load(property.name())))
                        .trueBranch();
                ResultHandle raw = tb.invokeVirtualMethod(MethodDescriptor.ofMethod(getter), typed);
                tb.returnValue(BytecodeSupport.box(tb, raw, getter.getReturnType()));
            }
            get.returnValue(get.loadNull());

            MethodCreator set = cc.getMethodCreator("set", Object.class, Object.class, String.class, Object.class);
            ResultHandle inst = set.checkCast(set.getMethodParam(0), model.type());
            ResultHandle setProp = set.getMethodParam(1);
            ResultHandle value = set.getMethodParam(2);
            for (AccessibleTypeModel.BeanProperty property : model.properties()) {
                Method setter = property.setter();
                if (setter == null) {
                    continue;
                }
                Class<?> param = setter.getParameterTypes()[0];
                BytecodeCreator tb = set.ifTrue(set.invokeVirtualMethod(STRING_EQUALS, setProp, set.load(property.name())))
                        .trueBranch();
                ResultHandle coerced = tb.invokeStaticMethod(COERCE, value, BytecodeSupport.loadClassConstant(tb, param));
                tb.invokeVirtualMethod(MethodDescriptor.ofMethod(setter), inst, BytecodeSupport.convertArg(tb, coerced, param));
                tb.returnValue(inst);
            }
            set.returnValue(set.getMethodParam(0));
            descriptor(cc, descriptor.toString());
        }
    }

    private static void descriptor(ClassCreator cc, String value) {
        MethodCreator method = cc.getMethodCreator("descriptor", String.class);
        method.returnValue(method.load(value));
    }

    private static void append(StringBuilder sb, String name, Class<?> type, String flags) {
        if (sb.length() > 0) {
            sb.append(';');
        }
        sb.append(name).append(':').append(type.getName()).append(':').append(flags);
    }
}
