/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.function;

import io.ottl.OttlCompilationException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable, case-sensitive registry of allowlisted {@link OttlFunction}s.
 *
 * <p>Build a registry with {@link #builder()}; the resulting instance is safe to share across
 * threads and across compiled programs.
 */
public final class FunctionRegistry {

    private final Map<String, OttlFunction> functions;

    private FunctionRegistry(Map<String, OttlFunction> functions) {
        this.functions = Map.copyOf(functions);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean contains(String name) {
        return functions.containsKey(name);
    }

    /** @return the function, or throws {@link OttlCompilationException} if it is not allowlisted. */
    public OttlFunction require(String name) {
        OttlFunction fn = functions.get(name);
        if (fn == null) {
            throw new OttlCompilationException("Unknown or disallowed function: '" + name + "'");
        }
        return fn;
    }

    public Map<String, OttlFunction> asMap() {
        return functions;
    }

    public static final class Builder {
        private final Map<String, OttlFunction> functions = new LinkedHashMap<>();

        public Builder add(OttlFunction function) {
            functions.put(function.name(), function);
            return this;
        }

        public Builder addAll(Iterable<? extends OttlFunction> fns) {
            for (OttlFunction fn : fns) {
                add(fn);
            }
            return this;
        }

        public Builder addAll(Map<String, ? extends OttlFunction> fns) {
            functions.putAll(new HashMap<>(fns));
            return this;
        }

        public FunctionRegistry build() {
            return new FunctionRegistry(functions);
        }
    }
}
