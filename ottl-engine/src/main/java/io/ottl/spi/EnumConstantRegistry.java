/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allowlist of enum constants that rules may name in expressions, as {@code EnumName.CONSTANT}.
 *
 * <p>An enum registered here is a set of values. It is not a context type and has no properties.
 * Quarkus fills this registry from {@code @OttlAccessible} enums with generated {@code getstatic}
 * references. {@link #of(Class[])} is for tests and plain Java, and reads the constants once while
 * the registry is built.
 */
public class EnumConstantRegistry {

    private static final EnumConstantRegistry EMPTY = new EnumConstantRegistry();

    private final Map<String, EnumType> byName = new LinkedHashMap<>();
    private final List<EnumType> types = new ArrayList<>();

    public static EnumConstantRegistry empty() {
        return EMPTY;
    }

    /** Register every constant of each enum. Simple names must be unique. */
    public static EnumConstantRegistry of(Class<?>... enumTypes) {
        EnumConstantRegistry registry = new EnumConstantRegistry();
        for (Class<?> type : enumTypes) {
            if (!type.isEnum()) {
                throw new IllegalArgumentException(type.getName() + " is not an enum");
            }
            registry.declare(type.getName(), type.getSimpleName());
            for (Object constant : type.getEnumConstants()) {
                registry.register(type.getName(), ((Enum<?>) constant).name(), constant);
            }
        }
        return registry;
    }

    /**
     * Start an enum type. {@code simpleName} is what expressions use ({@code OrderStatus.PENDING}).
     * Two enums with the same simple name are rejected.
     */
    public void declare(String fqn, String simpleName) {
        checkMutable();
        EnumType existingSimple = byName.get(simpleName);
        if (existingSimple != null && !existingSimple.fqn.equals(fqn)) {
            throw new IllegalArgumentException(
                    "Two enums share the simple name '" + simpleName + "': " + existingSimple.fqn
                            + " and " + fqn
                            + ". Expressions refer to constants as EnumName.CONSTANT, so each simple name must be unique.");
        }
        if (byName.containsKey(fqn)) {
            return;
        }
        EnumType type = new EnumType(simpleName, fqn);
        types.add(type);
        byName.put(fqn, type);
        if (!fqn.equals(simpleName)) {
            byName.put(simpleName, type);
        }
    }

    /** Add one constant. {@link #declare(String, String)} must already have recorded the type. */
    public void register(String fqn, String constant, Object value) {
        checkMutable();
        EnumType type = byName.get(fqn);
        if (type == null) {
            throw new IllegalStateException("No enum declared for " + fqn);
        }
        type.constants.put(constant, value);
    }

    public List<EnumType> types() {
        return List.copyOf(types);
    }

    private void checkMutable() {
        if (this == EMPTY) {
            throw new UnsupportedOperationException("The empty enum registry cannot be modified");
        }
    }

    public EnumType find(String name) {
        return name == null ? null : byName.get(name);
    }

    /**
     * Interpret a dotted path as an enum constant. Returns {@code null} when the path does not name
     * a registered enum. {@link Match#propertyAccess()} is set when segments follow the constant.
     * {@link Match#value()} is {@code null} when the constant name is not one of the enum's constants.
     */
    public Match match(List<String> fieldNames) {
        if (fieldNames == null || fieldNames.size() < 2) {
            return null;
        }
        for (int i = fieldNames.size() - 1; i >= 1; i--) {
            EnumType type = byName.get(String.join(".", fieldNames.subList(0, i)));
            if (type == null) {
                continue;
            }
            if (i != fieldNames.size() - 1) {
                return new Match(type.simpleName, null, null, true);
            }
            String constant = fieldNames.get(i);
            return new Match(type.simpleName, constant, type.constants.get(constant), false);
        }
        return null;
    }

    /**
     * Resolve a bare enum symbol such as {@code SPAN_KIND_SERVER}.
     *
     * @return {@code null} when no registered constant has that name, or a lookup whose
     *     {@link SymbolLookup#ambiguous()} is true when more than one enum defines it
     */
    public SymbolLookup symbol(String name) {
        if (name == null) {
            return null;
        }
        Object found = null;
        int matches = 0;
        for (EnumType type : types) {
            if (!type.constants.containsKey(name)) {
                continue;
            }
            matches++;
            found = type.constants.get(name);
        }
        if (matches == 0) {
            return null;
        }
        if (matches > 1) {
            return new SymbolLookup(null, true);
        }
        return new SymbolLookup(found, false);
    }

    /** Result of {@link #symbol(String)}. */
    public record SymbolLookup(Object value, boolean ambiguous) {
    }

    /** One allowlisted enum and its constant names, in declaration order. */
    public static final class EnumType {
        private final String simpleName;
        private final String fqn;
        private final Map<String, Object> constants = new LinkedHashMap<>();

        private EnumType(String simpleName, String fqn) {
            this.simpleName = simpleName;
            this.fqn = fqn;
        }

        public String simpleName() {
            return simpleName;
        }

        public String fqn() {
            return fqn;
        }

        public List<String> constants() {
            return List.copyOf(constants.keySet());
        }

        public boolean has(String constant) {
            return constants.containsKey(constant);
        }
    }

    /**
     * @param typeName       simple name of the enum
     * @param constant       constant name, or {@code null} when {@code propertyAccess} is true
     * @param value          the constant, or {@code null} when it is unknown or this is a property access
     * @param propertyAccess segments follow the enum type other than a single constant name
     */
    public record Match(String typeName, String constant, Object value, boolean propertyAccess) {
    }
}
