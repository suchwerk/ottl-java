/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.spi;

import io.ottl.OttlEvaluationException;

/**
 * A {@link PropertyResolver} that delegates to build-time generated {@link PropertyAccessor}s from a
 * {@link PropertyAccessorRegistry}. This is the native-image-safe path for approved domain types: it
 * performs no reflection. It has a very low priority so it wins over the reflective bean/record
 * resolvers for any registered type.
 */
public final class AccessorBackedResolver implements PropertyResolver {

    private final PropertyAccessorRegistry registry;

    public AccessorBackedResolver(PropertyAccessorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean canResolve(Object target) {
        return target != null && registry.contains(target.getClass());
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Object getField(Object target, String name) {
        return accessor(target).get(target, name);
    }

    @Override
    public Object setField(Object target, String name, Object value) {
        return accessor(target).set(target, name, value);
    }

    private PropertyAccessor accessor(Object target) {
        PropertyAccessor accessor = registry.accessor(target.getClass());
        if (accessor == null) {
            throw new OttlEvaluationException(
                    "No generated accessor for type " + target.getClass().getName());
        }
        return accessor;
    }
}
