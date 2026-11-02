/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.spi;

/**
 * A native-image-safe accessor for the properties of one specific Java type. Implementations are
 * normally <strong>generated at Quarkus build time</strong> (one per {@code @OttlAccessible} type)
 * and contain no runtime reflection: {@link #get}/{@link #set} invoke the real getters/setters or
 * the record's canonical constructor directly.
 *
 * <p>{@link #set} returns the (possibly new) instance: mutable beans are mutated in place and return
 * the same identity; records return a new instance (COPY), which the engine's copy-on-write bubbling
 * uses to rebuild parent records.
 */
public interface PropertyAccessor {

    /** Read a property. Returns {@code null} for an unknown property or null value. */
    Object get(Object instance, String property);

    /** Write a property, returning the same (mutated) instance or a new (copied) instance. */
    Object set(Object instance, String property, Object value);

    /**
     * A compact descriptor of the accessible properties, used to build type metadata without
     * reflection. Format: {@code name:typeFqn:flags} entries joined by {@code ';'}, where flags are
     * {@code r}, {@code w} or {@code rw}. Example:
     * {@code "id:java.lang.String:rw;amount:java.math.BigDecimal:rw"}.
     */
    String descriptor();
}
