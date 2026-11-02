/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import io.ottl.quarkus.FunctionMetadata;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Build-time discovery and validation of {@code @OttlFunction} methods. Invalid signatures fail
 * augmentation; they are not deferred to the first rule execution.
 */
final class FunctionDiscovery {

    static final Set<String> BUILTIN_NAMES = Set.of("set", "delete_key", "keep_keys");

    private static final Set<DotName> NORMAL_SCOPES = Set.of(
            DotName.createSimple("jakarta.enterprise.context.ApplicationScoped"),
            DotName.createSimple("jakarta.enterprise.context.RequestScoped"),
            DotName.createSimple("jakarta.enterprise.context.SessionScoped"),
            DotName.createSimple("jakarta.enterprise.context.ConversationScoped"));

    private static final Map<String, Class<?>> PRIMITIVES = Map.of(
            "boolean", boolean.class, "byte", byte.class, "char", char.class, "short", short.class,
            "int", int.class, "long", long.class, "float", float.class, "double", double.class, "void", void.class);

    private FunctionDiscovery() {
    }

    record DiscoveredFunction(FunctionMetadata metadata, Method method, String invokerClassName) {
    }

    static List<DiscoveredFunction> discover(IndexView index) {
        List<DiscoveredFunction> found = new ArrayList<>();
        Map<String, String> owners = new LinkedHashMap<>();
        DotName annotation = DotName.createSimple(io.ottl.quarkus.OttlFunction.class.getName());
        for (AnnotationInstance ann : index.getAnnotations(annotation)) {
            if (ann.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }
            MethodInfo method = ann.target().asMethod();
            DiscoveredFunction function = validate(ann, method);
            String previous = owners.put(function.metadata().getFunctionName(), location(method));
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate OTTL function '" + function.metadata().getFunctionName()
                                + "'. Conflicting methods: " + previous + " and " + location(method)
                                + ". Function names are case-sensitive and must be unique.");
            }
            found.add(function);
        }
        return List.copyOf(found);
    }

    private static DiscoveredFunction validate(AnnotationInstance ann, MethodInfo method) {
        ClassInfo owner = method.declaringClass();
        String where = location(method);
        if (method.isBridge() || method.isSynthetic()) {
            throw new IllegalStateException(
                    "@OttlFunction is not supported on synthetic or bridge methods: " + where);
        }
        if (!Modifier.isPublic(method.flags())) {
            throw new IllegalStateException(
                    "@OttlFunction method " + where + " must be public so generated invokers and CDI proxies can call it.");
        }
        if (Modifier.isStatic(method.flags())) {
            throw new IllegalStateException(
                    "@OttlFunction method " + where + " must be an instance method on a CDI bean, not static.");
        }
        if (!method.typeParameters().isEmpty()) {
            throw new IllegalStateException(
                    "@OttlFunction method " + where + " must not declare type parameters. Generic methods are not supported.");
        }
        if (!Modifier.isPublic(owner.flags()) || owner.isInterface() || owner.isEnum() || owner.isAbstract()) {
            throw new IllegalStateException(
                    "@OttlFunction host " + owner.name()
                            + " must be a public concrete class. Interfaces, enums and abstract classes cannot host functions.");
        }
        if (Modifier.isFinal(method.flags()) && hasNormalScope(owner)) {
            throw new IllegalStateException(
                    "@OttlFunction method " + where
                            + " is final on a normal-scoped bean. CDI client proxies cannot delegate final methods;"
                            + " remove final or change the bean to @Singleton.");
        }
        for (Type param : method.parameterTypes()) {
            if (param.kind() == Type.Kind.ARRAY) {
                throw new IllegalStateException(
                        "@OttlFunction method " + where
                                + " has an array parameter. Arrays are not supported; pass a List or a scalar.");
            }
            if (param.kind() == Type.Kind.TYPE_VARIABLE
                    || param.kind() == Type.Kind.UNRESOLVED_TYPE_VARIABLE
                    || param.kind() == Type.Kind.TYPE_VARIABLE_REFERENCE
                    || param.kind() == Type.Kind.WILDCARD_TYPE) {
                throw new IllegalStateException(
                        "@OttlFunction method " + where
                                + " has an unsupported generic parameter " + param + ".");
            }
        }

        String functionName = functionName(ann, method);
        if (BUILTIN_NAMES.contains(functionName)) {
            throw new IllegalStateException(
                    "OTTL function '" + functionName + "' on " + where
                            + " collides with a built-in function (set, delete_key, keep_keys). Choose another name.");
        }
        if (!isOttlIdentifier(functionName)) {
            throw new IllegalStateException(
                    "OTTL function name '" + functionName + "' on " + where
                            + " is not an identifier. Use a letter or '_' followed by letters, digits or '_'.");
        }

        Class<?> declaring = loadClass(owner.name().toString());
        Method reflective = resolve(declaring, method);
        List<String> paramNames = new ArrayList<>();
        for (Class<?> param : reflective.getParameterTypes()) {
            paramNames.add(param.getName());
        }
        FunctionMetadata metadata = new FunctionMetadata(
                functionName, declaring.getName(), reflective.getName(), paramNames);
        String invoker = GeneratedNames.invokerClass(declaring.getName(), reflective.getName(), functionName);
        return new DiscoveredFunction(metadata, reflective, invoker);
    }

    private static boolean hasNormalScope(ClassInfo owner) {
        for (DotName scope : NORMAL_SCOPES) {
            if (!owner.annotations(scope).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String functionName(AnnotationInstance ann, MethodInfo method) {
        AnnotationValue value = ann.value();
        if (value == null || value.asString().isEmpty()) {
            return method.name();
        }
        return value.asString();
    }

    private static boolean isOttlIdentifier(String name) {
        if (name.isEmpty() || !(Character.isLetter(name.charAt(0)) || name.charAt(0) == '_')) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static Method resolve(Class<?> declaring, MethodInfo method) {
        Class<?>[] params = new Class<?>[method.parametersCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = loadType(method.parameterType(i));
        }
        try {
            Method reflective = declaring.getMethod(method.name(), params);
            if (!Modifier.isPublic(reflective.getModifiers())) {
                throw new IllegalStateException(
                        "@OttlFunction method " + location(method) + " must be public.");
            }
            return reflective;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Cannot resolve @OttlFunction method " + location(method), e);
        }
    }

    private static Class<?> loadType(Type type) {
        String name = type.name().toString();
        Class<?> primitive = PRIMITIVES.get(name);
        if (primitive != null) {
            return primitive;
        }
        return loadClass(name);
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load @OttlFunction host " + name, e);
        }
    }

    private static String location(MethodInfo method) {
        StringBuilder params = new StringBuilder();
        for (Type type : method.parameterTypes()) {
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append(type.name());
        }
        return method.declaringClass().name() + "#" + method.name() + "(" + params + ")";
    }
}
