/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.spi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parsed, reflection-free metadata about an accessible type's properties, derived from a
 * {@link PropertyAccessor#descriptor()}. Used by the semantic validator to reject unknown or
 * unwritable properties before a rule is activated.
 */
public final class TypeMetadata {

    private final Set<String> readable = new HashSet<>();
    private final Set<String> writable = new HashSet<>();
    private final Map<String, String> propertyTypeName = new HashMap<>();

    private TypeMetadata() {
    }

    public static TypeMetadata parse(String descriptor) {
        TypeMetadata meta = new TypeMetadata();
        if (descriptor == null || descriptor.isBlank()) {
            return meta;
        }
        for (String entry : descriptor.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split(":");
            String name = parts[0];
            String typeFqn = parts.length > 1 ? parts[1] : "java.lang.Object";
            String flags = parts.length > 2 ? parts[2] : "rw";
            meta.propertyTypeName.put(name, typeFqn);
            if (flags.contains("r")) {
                meta.readable.add(name);
            }
            if (flags.contains("w")) {
                meta.writable.add(name);
            }
        }
        return meta;
    }

    public boolean isReadable(String property) {
        return readable.contains(property);
    }

    public boolean isWritable(String property) {
        return writable.contains(property);
    }

    public boolean knows(String property) {
        return propertyTypeName.containsKey(property);
    }

    public String propertyTypeName(String property) {
        return propertyTypeName.get(property);
    }

    public Set<String> properties() {
        return propertyTypeName.keySet();
    }
}
