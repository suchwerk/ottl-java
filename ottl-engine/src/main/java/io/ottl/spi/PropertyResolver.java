/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.spi;

import java.util.Collection;

/**
 * Pluggable property-access abstraction (an SPI). Each resolver knows how to read and write
 * properties of a particular family of objects (Maps, POJOs, records, {@code JsonNode}, ...).
 *
 * <p>Write operations return the (possibly new) target. In-place/mutable resolvers return the same
 * identity; functional/immutable resolvers (e.g. records) return a new object. The engine uses this
 * identity signal to decide whether it must "bubble" a rebuilt child up through its parents, which
 * is how COPY semantics are implemented generically for nested immutable structures.
 *
 * <p><strong>Security:</strong> resolvers must never invoke arbitrary user methods. They expose
 * only declared properties / map entries / record components. There is no mechanism to call an
 * arbitrary Java method through a path.
 */
public interface PropertyResolver {

    /** @return {@code true} if this resolver can handle the given (non-null) target object. */
    boolean canResolve(Object target);

    /**
     * Relative priority; lower values are consulted first. More specific resolvers (e.g. JsonNode)
     * should use a lower value than general fallbacks (e.g. arbitrary beans).
     */
    default int priority() {
        return 50;
    }

    /** Read a named field/property. Returns {@code null} if absent. */
    Object getField(Object target, String name);

    /**
     * Write a named field/property.
     *
     * @return the same target (mutated) or a new target (copy semantics)
     */
    Object setField(Object target, String name, Object value);

    /** Read a map/index key. */
    default Object getKey(Object target, Object key) {
        throw unsupported("keyed read");
    }

    /** Write a map/index key; returns the (possibly new) target. */
    default Object setKey(Object target, Object key, Object value) {
        throw unsupported("keyed write");
    }

    /** Remove a key; returns the (possibly new) target. */
    default Object deleteKey(Object target, Object key) {
        throw unsupported("delete_key");
    }

    /** Retain only the given keys; returns the (possibly new) target. */
    default Object keepKeys(Object target, Collection<Object> keys) {
        throw unsupported("keep_keys");
    }

    /**
     * Optional hook to prepare accessors ahead of time (e.g. for Quarkus native image) so that no
     * reflective discovery happens at execution time. No-op by default.
     */
    default void prepare(Class<?> type) {
    }

    private UnsupportedOperationException unsupported(String op) {
        return new UnsupportedOperationException(
                op + " is not supported by " + getClass().getSimpleName());
    }
}
