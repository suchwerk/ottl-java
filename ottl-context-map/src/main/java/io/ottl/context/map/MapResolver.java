/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.context.map;

import io.ottl.spi.PropertyResolver;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link PropertyResolver} for {@link Map}s. Field access and keyed access are equivalent (both map
 * lookups), and maps are mutated in place.
 */
public final class MapResolver implements PropertyResolver {

    @Override
    public boolean canResolve(Object target) {
        return target instanceof Map;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public Object getField(Object target, String name) {
        return asMap(target).get(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object setField(Object target, String name, Object value) {
        ((Map<Object, Object>) target).put(name, value);
        return target;
    }

    @Override
    public Object getKey(Object target, Object key) {
        return asMap(target).get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object setKey(Object target, Object key, Object value) {
        ((Map<Object, Object>) target).put(key, value);
        return target;
    }

    @Override
    public Object deleteKey(Object target, Object key) {
        asMap(target).remove(key);
        return target;
    }

    @Override
    public Object keepKeys(Object target, Collection<Object> keys) {
        Set<Object> keep = new HashSet<>(keys);
        asMap(target).keySet().removeIf(k -> !keep.contains(k));
        return target;
    }

    private static Map<?, ?> asMap(Object target) {
        return (Map<?, ?>) target;
    }
}
