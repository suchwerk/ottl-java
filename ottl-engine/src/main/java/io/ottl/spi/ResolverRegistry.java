/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.spi;

import io.ottl.OttlEvaluationException;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable, ordered collection of {@link PropertyResolver}s. The first resolver whose
 * {@link PropertyResolver#canResolve(Object)} returns {@code true} wins.
 */
public final class ResolverRegistry {

    private final List<PropertyResolver> resolvers;

    public ResolverRegistry(List<PropertyResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    public static ResolverRegistry of(PropertyResolver... resolvers) {
        return new ResolverRegistry(List.of(resolvers));
    }

    /** @return a new registry with the given resolvers appended (lower priority). */
    public ResolverRegistry withAppended(List<PropertyResolver> extra) {
        List<PropertyResolver> merged = new ArrayList<>(resolvers);
        merged.addAll(extra);
        return new ResolverRegistry(merged);
    }

    public PropertyResolver forTarget(Object target) {
        if (target == null) {
            throw new OttlEvaluationException("Cannot resolve a property on a null value");
        }
        for (PropertyResolver r : resolvers) {
            if (r.canResolve(target)) {
                return r;
            }
        }
        throw new OttlEvaluationException(
                "No PropertyResolver registered for type " + target.getClass().getName());
    }

    public List<PropertyResolver> resolvers() {
        return resolvers;
    }
}
