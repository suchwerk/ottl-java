/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.context.bean;

import io.ottl.OttlEvaluationException;
import io.ottl.spi.PropertyResolver;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link PropertyResolver} for Java records. Records are immutable, so writes use COPY semantics:
 * {@link #setField} constructs a new record via the canonical constructor with the target component
 * replaced. Combined with the engine's copy-on-write bubbling, this supports nested record updates.
 */
public final class RecordResolver implements PropertyResolver {

    private record RecordInfo(RecordComponent[] components, Map<String, Integer> indexByName,
                              Constructor<?> canonical) {
    }

    private final Map<Class<?>, RecordInfo> cache = new ConcurrentHashMap<>();

    @Override
    public boolean canResolve(Object target) {
        return target != null && target.getClass().isRecord();
    }

    @Override
    public int priority() {
        // More specific than the general BeanResolver, so it wins for records.
        return 30;
    }

    @Override
    public void prepare(Class<?> type) {
        if (type.isRecord()) {
            info(type);
        }
    }

    @Override
    public Object getField(Object target, String name) {
        RecordInfo info = info(target.getClass());
        Integer idx = info.indexByName().get(name);
        if (idx == null) {
            throw new OttlEvaluationException(
                    "No record component '" + name + "' on " + target.getClass().getName());
        }
        return accessor(info.components()[idx], target);
    }

    @Override
    public Object setField(Object target, String name, Object value) {
        RecordInfo info = info(target.getClass());
        Integer idx = info.indexByName().get(name);
        if (idx == null) {
            throw new OttlEvaluationException(
                    "No record component '" + name + "' on " + target.getClass().getName());
        }
        RecordComponent[] components = info.components();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            args[i] = (i == idx)
                    ? Coercions.coerce(value, components[i].getType())
                    : accessor(components[i], target);
        }
        try {
            return info.canonical().newInstance(args); // new record instance (copy)
        } catch (ReflectiveOperationException e) {
            throw new OttlEvaluationException(
                    "Failed to construct updated " + target.getClass().getSimpleName(), e);
        }
    }

    private RecordInfo info(Class<?> type) {
        return cache.computeIfAbsent(type, RecordResolver::introspect);
    }

    private static RecordInfo introspect(Class<?> type) {
        RecordComponent[] components = type.getRecordComponents();
        Map<String, Integer> indexByName = new LinkedHashMap<>();
        Class<?>[] paramTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            indexByName.put(components[i].getName(), i);
            paramTypes[i] = components[i].getType();
        }
        try {
            Constructor<?> canonical = type.getDeclaredConstructor(paramTypes);
            canonical.setAccessible(true);
            return new RecordInfo(components, Map.copyOf(indexByName), canonical);
        } catch (NoSuchMethodException e) {
            throw new OttlEvaluationException(
                    "No canonical constructor found for record " + type.getName(), e);
        }
    }

    private static Object accessor(RecordComponent component, Object target) {
        try {
            return component.getAccessor().invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new OttlEvaluationException(
                    "Failed to read record component '" + component.getName() + "'", e);
        }
    }
}
