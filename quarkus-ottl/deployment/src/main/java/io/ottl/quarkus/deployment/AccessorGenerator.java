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
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import jakarta.inject.Singleton;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates, at build time, a reflection-free {@link PropertyAccessor} implementation for one
 * {@code @OttlAccessible} type using Gizmo. The generated class invokes the real
 * getters/setters/record constructor directly, so there is zero runtime reflection on the hot path.
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
     * Generate a {@code @Singleton} registry bean (a subclass of
     * {@link DefaultPropertyAccessorRegistry}) whose no-arg constructor instantiates every generated
     * accessor via {@code new} and registers it. No runtime reflection is involved.
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
        String accessorName = "io.ottl.quarkus.accessors."
                + type.getName().replace('.', '_').replace('$', '_') + "_Accessor";

        if (type.isRecord()) {
            generateRecordAccessor(type, accessorName, output);
        } else {
            generateBeanAccessor(type, accessorName, output);
        }
        return new Generated(type.getName(), accessorName);
    }

    // ----- records (COPY via canonical constructor) -----

    private static void generateRecordAccessor(Class<?> type, String accessorName, ClassOutput output) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
        }
        try {
            type.getDeclaredConstructor(paramTypes); // validate the canonical constructor exists
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No canonical constructor for record " + type.getName(), e);
        }
        MethodDescriptor canonicalCtor = MethodDescriptor.ofConstructor(type, paramTypes);

        StringBuilder descriptor = new StringBuilder();
        for (RecordComponent c : components) {
            append(descriptor, c.getName(), c.getType(), "rw");
        }

        try (ClassCreator cc = ClassCreator.builder().classOutput(output)
                .className(accessorName).interfaces(PropertyAccessor.class).build()) {

            // get
            MethodCreator get = cc.getMethodCreator("get", Object.class, Object.class, String.class);
            ResultHandle typed = get.checkCast(get.getMethodParam(0), type);
            ResultHandle prop = get.getMethodParam(1);
            for (RecordComponent c : components) {
                BytecodeCreator tb = get.ifTrue(get.invokeVirtualMethod(STRING_EQUALS, prop, get.load(c.getName())))
                        .trueBranch();
                ResultHandle raw = tb.invokeVirtualMethod(MethodDescriptor.ofMethod(c.getAccessor()), typed);
                tb.returnValue(box(tb, raw, c.getType()));
            }
            get.returnValue(get.loadNull());

            // set (returns a NEW record instance)
            MethodCreator set = cc.getMethodCreator("set", Object.class, Object.class, String.class, Object.class);
            ResultHandle inst = set.checkCast(set.getMethodParam(0), type);
            ResultHandle setProp = set.getMethodParam(1);
            ResultHandle value = set.getMethodParam(2);
            for (int idx = 0; idx < components.length; idx++) {
                BytecodeCreator tb = set.ifTrue(
                        set.invokeVirtualMethod(STRING_EQUALS, setProp, set.load(components[idx].getName())))
                        .trueBranch();
                ResultHandle[] args = new ResultHandle[components.length];
                for (int i = 0; i < components.length; i++) {
                    Class<?> ct = components[i].getType();
                    if (i == idx) {
                        ResultHandle coerced = tb.invokeStaticMethod(COERCE, value, loadClassConstant(tb, ct));
                        args[i] = convertArg(tb, coerced, ct);
                    } else {
                        args[i] = tb.invokeVirtualMethod(
                                MethodDescriptor.ofMethod(components[i].getAccessor()), inst);
                    }
                }
                tb.returnValue(tb.newInstance(canonicalCtor, args));
            }
            set.returnValue(inst);

            descriptor(cc, descriptor.toString());
        }
    }

    // ----- beans/POJOs (mutate via setters) -----

    private static void generateBeanAccessor(Class<?> type, String accessorName, ClassOutput output) {
        Map<String, Method> getters = new LinkedHashMap<>();
        Map<String, Method> setters = new LinkedHashMap<>();
        for (Method m : type.getMethods()) {
            if (m.getDeclaringClass() == Object.class) {
                continue;
            }
            String n = m.getName();
            if (m.getParameterCount() == 0 && m.getReturnType() != void.class) {
                if (n.startsWith("get") && n.length() > 3 && !n.equals("getClass")) {
                    getters.put(decap(n.substring(3)), m);
                } else if (n.startsWith("is") && n.length() > 2
                        && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                    getters.put(decap(n.substring(2)), m);
                }
            } else if (n.startsWith("set") && n.length() > 3 && m.getParameterCount() == 1) {
                setters.put(decap(n.substring(3)), m);
            }
        }

        List<String> names = new ArrayList<>();
        names.addAll(getters.keySet());
        setters.keySet().forEach(k -> {
            if (!names.contains(k)) {
                names.add(k);
            }
        });

        StringBuilder descriptor = new StringBuilder();
        for (String name : names) {
            Class<?> t = getters.containsKey(name) ? getters.get(name).getReturnType()
                    : setters.get(name).getParameterTypes()[0];
            String flags = (getters.containsKey(name) ? "r" : "") + (setters.containsKey(name) ? "w" : "");
            append(descriptor, name, t, flags);
        }

        try (ClassCreator cc = ClassCreator.builder().classOutput(output)
                .className(accessorName).interfaces(PropertyAccessor.class).build()) {

            MethodCreator get = cc.getMethodCreator("get", Object.class, Object.class, String.class);
            ResultHandle typed = get.checkCast(get.getMethodParam(0), type);
            ResultHandle prop = get.getMethodParam(1);
            for (Map.Entry<String, Method> e : getters.entrySet()) {
                BytecodeCreator tb = get.ifTrue(get.invokeVirtualMethod(STRING_EQUALS, prop, get.load(e.getKey())))
                        .trueBranch();
                ResultHandle raw = tb.invokeVirtualMethod(MethodDescriptor.ofMethod(e.getValue()), typed);
                tb.returnValue(box(tb, raw, e.getValue().getReturnType()));
            }
            get.returnValue(get.loadNull());

            MethodCreator set = cc.getMethodCreator("set", Object.class, Object.class, String.class, Object.class);
            ResultHandle inst = set.checkCast(set.getMethodParam(0), type);
            ResultHandle setProp = set.getMethodParam(1);
            ResultHandle value = set.getMethodParam(2);
            for (Map.Entry<String, Method> e : setters.entrySet()) {
                Class<?> pt = e.getValue().getParameterTypes()[0];
                BytecodeCreator tb = set.ifTrue(set.invokeVirtualMethod(STRING_EQUALS, setProp, set.load(e.getKey())))
                        .trueBranch();
                ResultHandle coerced = tb.invokeStaticMethod(COERCE, value, loadClassConstant(tb, pt));
                tb.invokeVirtualMethod(MethodDescriptor.ofMethod(e.getValue()), inst, convertArg(tb, coerced, pt));
                tb.returnValue(inst);
            }
            set.returnValue(set.getMethodParam(0));

            descriptor(cc, descriptor.toString());
        }
    }

    // ----- helpers -----

    private static void descriptor(ClassCreator cc, String value) {
        MethodCreator d = cc.getMethodCreator("descriptor", String.class);
        d.returnValue(d.load(value));
    }

    private static void append(StringBuilder sb, String name, Class<?> type, String flags) {
        if (sb.length() > 0) {
            sb.append(';');
        }
        sb.append(name).append(':').append(type.getName()).append(':').append(flags);
    }

    /** Box a primitive result handle to its wrapper; reference values pass through. */
    private static ResultHandle box(BytecodeCreator bc, ResultHandle value, Class<?> type) {
        if (!type.isPrimitive()) {
            return value;
        }
        Class<?> wrapper = wrapper(type);
        return bc.invokeStaticMethod(
                MethodDescriptor.ofMethod(wrapper, "valueOf", wrapper, type), value);
    }

    /** Convert a coerced (boxed/Object) value into the target parameter type (unbox or cast). */
    private static ResultHandle convertArg(BytecodeCreator bc, ResultHandle coerced, Class<?> type) {
        if (!type.isPrimitive()) {
            return bc.checkCast(coerced, type);
        }
        Class<?> wrapper = wrapper(type);
        ResultHandle boxed = bc.checkCast(coerced, wrapper);
        return bc.invokeVirtualMethod(
                MethodDescriptor.ofMethod(wrapper, type.getName() + "Value", type), boxed);
    }

    private static ResultHandle loadClassConstant(BytecodeCreator bc, Class<?> type) {
        if (!type.isPrimitive()) {
            return bc.loadClass(type);
        }
        return bc.readStaticField(FieldDescriptor.of(wrapper(type), "TYPE", Class.class));
    }

    private static Class<?> wrapper(Class<?> primitive) {
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == double.class) return Double.class;
        if (primitive == float.class) return Float.class;
        if (primitive == short.class) return Short.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == char.class) return Character.class;
        throw new IllegalArgumentException("Not a primitive: " + primitive);
    }

    private static String decap(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
