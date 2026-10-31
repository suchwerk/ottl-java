/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.context.bean;

import io.ottl.OttlEvaluationException;
import io.ottl.spi.PropertyResolver;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link PropertyResolver} for JavaBeans/POJOs. Reads via {@code getX()}/{@code isX()} and writes via
 * {@code setX(...)} (mutating the object in place). Only declared, public bean accessors are used;
 * arbitrary methods are never invoked.
 *
 * <p>Accessor lookup is cached per class. {@link #prepare(Class)} can be called at build time
 * (e.g. by the Quarkus extension) to populate the cache ahead of execution.
 */
public final class BeanResolver implements PropertyResolver {

    private record BeanInfo(Map<String, Method> getters, Map<String, Method> setters) {
    }

    private final Map<Class<?>, BeanInfo> cache = new ConcurrentHashMap<>();

    @Override
    public boolean canResolve(Object target) {
        return target != null && !isLeaf(target.getClass());
    }

    @Override
    public int priority() {
        // General fallback: consulted after Map/record/JsonNode resolvers.
        return 100;
    }

    @Override
    public void prepare(Class<?> type) {
        if (!isLeaf(type) && !type.isRecord()) {
            info(type);
        }
    }

    @Override
    public Object getField(Object target, String name) {
        Method getter = info(target.getClass()).getters().get(name);
        if (getter == null) {
            throw new OttlEvaluationException(
                    "No readable property '" + name + "' on " + target.getClass().getName());
        }
        return invoke(getter, target);
    }

    @Override
    public Object setField(Object target, String name, Object value) {
        Method setter = info(target.getClass()).setters().get(name);
        if (setter == null) {
            throw new OttlEvaluationException(
                    "No writable property '" + name + "' on " + target.getClass().getName());
        }
        Object coerced = Coercions.coerce(value, setter.getParameterTypes()[0]);
        invoke(setter, target, coerced);
        return target; // mutated in place
    }

    private BeanInfo info(Class<?> type) {
        return cache.computeIfAbsent(type, BeanResolver::introspect);
    }

    private static BeanInfo introspect(Class<?> type) {
        Map<String, Method> getters = new HashMap<>();
        Map<String, Method> setters = new HashMap<>();
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
        return new BeanInfo(Map.copyOf(getters), Map.copyOf(setters));
    }

    private static String decap(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static Object invoke(Method m, Object target, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new OttlEvaluationException("Failed to access property via " + m.getName(), e);
        }
    }

    /** Value-like types that must never be treated as beans. */
    private static boolean isLeaf(Class<?> type) {
        return type.isPrimitive()
                || type.isArray()
                || type.isEnum()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || Map.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type)
                || UUID.class == type
                || type.getName().startsWith("java.time.");
    }
}
