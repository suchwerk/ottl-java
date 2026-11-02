/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.spi;

/**
 * Registry of {@link PropertyAccessor}s for the explicitly approved domain types. In Quarkus this is
 * populated at build time from {@code @OttlAccessible} types and contains only generated,
 * reflection-free accessors.
 */
public interface PropertyAccessorRegistry {

    boolean contains(Class<?> type);

    /** @return all registered accessible types (the approved editor contexts). */
    default java.util.Set<Class<?>> types() {
        return java.util.Set.of();
    }

    /** @return the accessor for {@code type}, or {@code null} if the type is not registered. */
    PropertyAccessor accessor(Class<?> type);

    /** @return parsed metadata for {@code type}, or {@code null} if not registered. */
    TypeMetadata metadata(Class<?> type);

    /** @return the registered type whose fully-qualified name equals {@code fqn}, or {@code null}. */
    Class<?> typeByName(String fqn);
}
