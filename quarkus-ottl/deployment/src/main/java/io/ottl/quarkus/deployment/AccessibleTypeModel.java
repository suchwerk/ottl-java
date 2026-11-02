/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import java.beans.Introspector;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Discovers and validates the properties of an {@code @OttlAccessible} type before any bytecode is
 * generated. The generator consumes the model returned here and does not re-scan the class.
 */
final class AccessibleTypeModel {

    private AccessibleTypeModel() {
    }

    /** One JavaBean property whose getter and setter types already agree. */
    record BeanProperty(String name, Class<?> type, Method getter, Method setter) {
        String flags() {
            return (getter != null ? "r" : "") + (setter != null ? "w" : "");
        }
    }

    /** One record component, in canonical-constructor order. */
    record RecordProperty(String name, Class<?> type, Method accessor) {
    }

    record BeanModel(Class<?> type, String accessorClassName, List<BeanProperty> properties) {
    }

    record RecordModel(Class<?> type, String accessorClassName, List<RecordProperty> components) {
    }

    static void validateType(Class<?> type) {
        if (type.isInterface() || type.isAnnotation()) {
            throw new AccessorGenerationException(
                    "@OttlAccessible does not support interfaces: " + type.getName()
                            + ". Annotate a concrete class or record.");
        }
        if (type.isEnum()) {
            throw new AccessorGenerationException(
                    "@OttlAccessible does not support enums: " + type.getName()
                            + ". Enum constants are values, not property bags.");
        }
        if (type.isArray() || type.isPrimitive()) {
            throw new AccessorGenerationException(
                    "@OttlAccessible does not support " + type.getName());
        }
        if (type.isAnonymousClass() || type.isLocalClass()) {
            throw new AccessorGenerationException(
                    "@OttlAccessible does not support local or anonymous classes: " + type.getName());
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new AccessorGenerationException(
                    "@OttlAccessible does not support abstract classes: " + type.getName()
                            + ". Annotate the concrete class that rules will read and write.");
        }
        if (!Modifier.isPublic(type.getModifiers())) {
            throw new AccessorGenerationException(
                    "@OttlAccessible type " + type.getName()
                            + " is not public. Generated accessors live in another package and can only call public types.");
        }
        for (Class<?> enclosing = type.getEnclosingClass(); enclosing != null; enclosing = enclosing.getEnclosingClass()) {
            if (!Modifier.isPublic(enclosing.getModifiers())) {
                throw new AccessorGenerationException(
                        "@OttlAccessible type " + type.getName()
                                + " is not accessible because enclosing type " + enclosing.getName()
                                + " is not public.");
            }
        }
    }

    static RecordModel discoverRecord(Class<?> type) {
        validateType(type);
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        List<RecordProperty> properties = new ArrayList<>(components.length);
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            Class<?> componentType = component.getType();
            rejectDescriptorType(type, component.getName(), componentType);
            paramTypes[i] = componentType;
            Method accessor = component.getAccessor();
            if (accessor == null || !Modifier.isPublic(accessor.getModifiers()) || Modifier.isStatic(accessor.getModifiers())) {
                throw new AccessorGenerationException(
                        "Record component '" + component.getName() + "' on " + type.getName()
                                + " has no public instance accessor.");
            }
            properties.add(new RecordProperty(component.getName(), componentType, accessor));
        }
        try {
            var ctor = type.getDeclaredConstructor(paramTypes);
            if (!Modifier.isPublic(ctor.getModifiers())) {
                throw new AccessorGenerationException(
                        "Record " + type.getName() + " canonical constructor is not public.");
            }
        } catch (NoSuchMethodException e) {
            throw new AccessorGenerationException(
                    "Record " + type.getName() + " has no canonical constructor.", e);
        }
        return new RecordModel(type, GeneratedNames.accessorClass(type.getName()), List.copyOf(properties));
    }

    static BeanModel discoverBean(Class<?> type) {
        validateType(type);
        Map<String, List<Method>> getters = new LinkedHashMap<>();
        Map<String, List<Method>> setters = new LinkedHashMap<>();
        for (Method method : type.getMethods()) {
            if (skip(method)) {
                continue;
            }
            String methodName = method.getName();
            if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                if (methodName.startsWith("get") && methodName.length() > 3 && !methodName.equals("getClass")) {
                    getters.computeIfAbsent(propertyName(methodName.substring(3)), k -> new ArrayList<>()).add(method);
                } else if (methodName.startsWith("is") && methodName.length() > 2 && isBoolean(method.getReturnType())) {
                    getters.computeIfAbsent(propertyName(methodName.substring(2)), k -> new ArrayList<>()).add(method);
                }
            } else if (methodName.startsWith("set") && methodName.length() > 3 && method.getParameterCount() == 1) {
                // void and fluent setters (returning this) are both writers; the return value is ignored
                setters.computeIfAbsent(propertyName(methodName.substring(3)), k -> new ArrayList<>()).add(method);
            }
        }

        TreeSet<String> names = new TreeSet<>();
        names.addAll(getters.keySet());
        names.addAll(setters.keySet());
        List<BeanProperty> properties = new ArrayList<>();
        for (String name : names) {
            Method getter = selectGetter(type, name, getters.getOrDefault(name, List.of()));
            Method setter = selectSetter(type, name, setters.getOrDefault(name, List.of()));
            if (getter == null && setter == null) {
                continue;
            }
            if (getter != null && setter != null && getter.getReturnType() != setter.getParameterTypes()[0]) {
                throw new AccessorGenerationException(
                        "Incompatible getter and setter types for property '" + name + "' on " + type.getName()
                                + ": " + signature(getter) + " versus " + signature(setter)
                                + ". The getter return type and the setter parameter type must be the same.");
            }
            Class<?> propertyType = getter != null ? getter.getReturnType() : setter.getParameterTypes()[0];
            rejectDescriptorType(type, name, propertyType);
            properties.add(new BeanProperty(name, propertyType, getter, setter));
        }
        return new BeanModel(type, GeneratedNames.accessorClass(type.getName()), List.copyOf(properties));
    }

    private static boolean skip(Method method) {
        return method.getDeclaringClass() == Object.class
                || method.isSynthetic()
                || method.isBridge()
                || Modifier.isStatic(method.getModifiers())
                || !Modifier.isPublic(method.getModifiers());
    }

    private static Method selectGetter(Class<?> type, String property, List<Method> methods) {
        if (methods.isEmpty()) {
            return null;
        }
        if (distinctTypes(methods, method -> method.getReturnType()).size() > 1) {
            throw new AccessorGenerationException(
                    "Ambiguous getter for property '" + property + "' on " + type.getName()
                            + ". Conflicting signatures: " + signatures(methods)
                            + ". getX() and isX() must agree, and overloads are not supported.");
        }
        List<Method> preferred = methods.stream().filter(m -> m.getName().startsWith("is")).toList();
        List<Method> candidates = preferred.isEmpty() ? methods : preferred;
        return mostSpecific(type, property, "getter", candidates);
    }

    private static Method selectSetter(Class<?> type, String property, List<Method> methods) {
        if (methods.isEmpty()) {
            return null;
        }
        if (distinctTypes(methods, m -> m.getParameterTypes()[0]).size() > 1) {
            throw new AccessorGenerationException(
                    "Ambiguous setter for property '" + property + "' on " + type.getName()
                            + ". Conflicting signatures: " + signatures(methods)
                            + ". Declare a single setter; overloaded setters are rejected.");
        }
        return mostSpecific(type, property, "setter", methods);
    }

    /**
     * Distinct property types. A loop, because Quarkus dev mode compiles this module with ECJ:
     * {@code stream().map(Method::getReturnType).toList()} is inferred as
     * {@code List<Class<capture of ?>>}, which ECJ will not assign to {@code List<Class<?>>}.
     */
    private static List<Class<?>> distinctTypes(List<Method> methods, java.util.function.Function<Method, Class<?>> typeOf) {
        List<Class<?>> types = new ArrayList<>();
        for (Method method : methods) {
            Class<?> type = typeOf.apply(method);
            if (!types.contains(type)) {
                types.add(type);
            }
        }
        return types;
    }

    private static Method mostSpecific(Class<?> type, String property, String kind, List<Method> methods) {
        List<Method> ordered = new ArrayList<>(methods);
        ordered.sort(Comparator.comparing(Method::toString));
        Method best = ordered.get(0);
        for (int i = 1; i < ordered.size(); i++) {
            Method other = ordered.get(i);
            Class<?> bestType = best.getDeclaringClass();
            Class<?> otherType = other.getDeclaringClass();
            if (bestType.equals(otherType)) {
                if (!sameSignature(best, other)) {
                    throw ambiguous(type, property, kind, ordered);
                }
                continue;
            }
            if (bestType.isAssignableFrom(otherType)) {
                best = other;
            } else if (!otherType.isAssignableFrom(bestType)) {
                throw ambiguous(type, property, kind, ordered);
            }
        }
        return best;
    }

    private static boolean sameSignature(Method left, Method right) {
        return left.getName().equals(right.getName())
                && left.getReturnType() == right.getReturnType()
                && Arrays.equals(left.getParameterTypes(), right.getParameterTypes());
    }

    private static AccessorGenerationException ambiguous(Class<?> type, String property, String kind, List<Method> methods) {
        return new AccessorGenerationException(
                "Ambiguous " + kind + " for property '" + property + "' on " + type.getName()
                        + ". Conflicting signatures: " + signatures(methods));
    }

    private static void rejectDescriptorType(Class<?> owner, String property, Class<?> propertyType) {
        String name = propertyType.getName();
        if (propertyType.isArray() || name.indexOf(';') >= 0 || name.indexOf(':') >= 0) {
            throw new AccessorGenerationException(
                    "Property '" + property + "' on " + owner.getName() + " has unsupported type " + name
                            + ". Arrays and types whose binary names contain ';' or ':' cannot be recorded in accessor metadata.");
        }
    }

    private static boolean isBoolean(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    /** JavaBeans decapitalization: {@code getURL} stays {@code URL}; {@code getName} becomes {@code name}. */
    private static String propertyName(String suffix) {
        return Introspector.decapitalize(suffix);
    }

    private static String signatures(List<Method> methods) {
        return methods.stream()
                .map(AccessibleTypeModel::signature)
                .sorted()
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    static String signature(Method method) {
        String params = Arrays.stream(method.getParameterTypes()).map(Class::getName).reduce((a, b) -> a + ", " + b).orElse("");
        return method.getReturnType().getName() + " " + method.getDeclaringClass().getName() + "#" + method.getName()
                + "(" + params + ")";
    }
}
