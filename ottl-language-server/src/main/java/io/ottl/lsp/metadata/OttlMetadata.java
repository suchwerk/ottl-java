/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp.metadata;

import io.ottl.OttlLimits;
import io.ottl.function.DescribedFunction;
import io.ottl.function.FunctionRegistry;
import io.ottl.function.OttlFunction;
import io.ottl.lang.OttlDialect;
import io.ottl.spi.EnumConstantRegistry;
import io.ottl.spi.PropertyAccessorRegistry;
import io.ottl.spi.TypeMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single source of truth the Language Server uses for completion, hover, signature help and
 * semantic diagnostics. It is built from exactly the same {@link PropertyAccessorRegistry} and
 * {@link FunctionRegistry} that the running engine uses — there is <strong>no duplicated, hardcoded
 * schema</strong>. Only approved (accessible) types and allowlisted functions are ever exposed, so
 * editor suggestions respect the same security restrictions as the runtime.
 */
public final class OttlMetadata {

    private final PropertyAccessorRegistry registry;
    private final FunctionRegistry functions;
    private final EnumConstantRegistry enums;
    private final OttlDialect dialect;
    private final int maxNesting;

    /** Curated documentation for the standard built-in editors (our own language, not a Java schema). */
    private static final Map<String, String> BUILTIN_DOCS = Map.of(
            "set", "Set a property (or map key) to a value. `set(target, value)`. "
                    + "Records are copied; POJOs and maps are mutated.",
            "delete_key", "Delete a key from a map-valued target. `delete_key(target, key)`.",
            "keep_keys", "Keep only the listed keys of a map-valued target. `keep_keys(target, keys...)`.");

    private static final Map<String, List<FunctionInfo.ParamInfo>> BUILTIN_PARAMS = Map.of(
            "set", List.of(new FunctionInfo.ParamInfo("target", "Path"),
                    new FunctionInfo.ParamInfo("value", "Value")),
            "delete_key", List.of(new FunctionInfo.ParamInfo("target", "Path"),
                    new FunctionInfo.ParamInfo("key", "Value")),
            "keep_keys", List.of(new FunctionInfo.ParamInfo("target", "Path"),
                    new FunctionInfo.ParamInfo("keys", "Value...")));

    public OttlMetadata(PropertyAccessorRegistry registry, FunctionRegistry functions) {
        this(registry, functions, EnumConstantRegistry.empty());
    }

    public OttlMetadata(PropertyAccessorRegistry registry, FunctionRegistry functions, EnumConstantRegistry enums) {
        this(registry, functions, enums, OttlDialect.OTTL, OttlLimits.defaults().maxCallDepth());
    }

    public OttlMetadata(PropertyAccessorRegistry registry, FunctionRegistry functions, EnumConstantRegistry enums,
                        OttlDialect dialect, int maxNesting) {
        this.registry = registry;
        this.functions = functions;
        this.enums = enums == null ? EnumConstantRegistry.empty() : enums;
        this.dialect = dialect == null ? OttlDialect.OTTL : dialect;
        this.maxNesting = maxNesting < 1 ? 1 : maxNesting;
    }

    /** A copy that parses with {@code dialect}. The engine metadata uses the engine's dialect. */
    public OttlMetadata withDialect(OttlDialect dialect) {
        return new OttlMetadata(registry, functions, enums, dialect, maxNesting);
    }

    public OttlDialect dialect() {
        return dialect;
    }

    public int maxNesting() {
        return maxNesting;
    }

    // ----- types / contexts -----

    /** All accessible context types, sorted by simple name. */
    public List<TypeInfo> contexts() {
        List<TypeInfo> result = new ArrayList<>();
        for (Class<?> type : registry.types()) {
            result.add(toTypeInfo(type));
        }
        result.sort((a, b) -> a.simpleName().compareToIgnoreCase(b.simpleName()));
        return result;
    }

    /** Resolve a context by simple name or fully-qualified name. */
    public Optional<TypeInfo> context(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (Class<?> type : registry.types()) {
            if (type.getSimpleName().equals(name) || type.getName().equals(name)) {
                return Optional.of(toTypeInfo(type));
            }
        }
        return Optional.empty();
    }

    /** Allowlisted enums. These are expression values, not editor contexts. */
    public List<EnumConstantRegistry.EnumType> enums() {
        return enums.types();
    }

    /** Resolve an enum by simple name or fully-qualified name. */
    public Optional<EnumConstantRegistry.EnumType> enumType(String name) {
        EnumConstantRegistry.EnumType type = enums.find(name);
        return Optional.ofNullable(type);
    }

    /**
     * Interpret a dotted path as an enum constant. Empty when the path does not name an allowlisted
     * enum. Same rules as {@link EnumConstantRegistry#match(List)}.
     */
    public Optional<EnumConstantRegistry.Match> enumReference(List<String> fieldNames) {
        return Optional.ofNullable(enums.match(fieldNames));
    }

    /** The default identifier used to refer to the root in rule source (engine convention). */
    public String contextVariable(TypeInfo type) {
        String s = type.simpleName();
        if (s.isEmpty()) {
            return "root";
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** Resolve the type a property navigates into (for nested completion), if it is itself accessible. */
    public Optional<TypeInfo> navigate(TypeInfo from, String property) {
        PropertyInfo p = from.property(property);
        if (p == null) {
            return Optional.empty();
        }
        Class<?> next = registry.typeByName(p.typeFqn());
        return next == null ? Optional.empty() : Optional.of(toTypeInfo(next));
    }

    public boolean isDynamicTypeName(String fqn) {
        return fqn == null
                || fqn.equals("java.lang.Object")
                || fqn.startsWith("java.util.")
                || fqn.startsWith("com.fasterxml.jackson.");
    }

    private TypeInfo toTypeInfo(Class<?> type) {
        TypeMetadata meta = registry.metadata(type);
        List<PropertyInfo> props = new ArrayList<>();
        if (meta != null) {
            List<String> names = new ArrayList<>(meta.properties());
            names.sort(String::compareTo);
            for (String name : names) {
                String fqn = meta.propertyTypeName(name);
                props.add(new PropertyInfo(name, fqn, simpleName(fqn),
                        meta.isReadable(name), meta.isWritable(name), type.getSimpleName()));
            }
        }
        return new TypeInfo(type.getSimpleName(), type.getName(), props);
    }

    // ----- functions -----

    public List<FunctionInfo> functions() {
        List<FunctionInfo> result = new ArrayList<>();
        for (Map.Entry<String, OttlFunction> e : sortedFunctions().entrySet()) {
            result.add(toFunctionInfo(e.getKey(), e.getValue()));
        }
        return result;
    }

    public Optional<FunctionInfo> function(String name) {
        if (name == null || !functions.contains(name)) {
            return Optional.empty();
        }
        return Optional.of(toFunctionInfo(name, functions.asMap().get(name)));
    }

    public boolean isFunction(String name) {
        return functions.contains(name);
    }

    private Map<String, OttlFunction> sortedFunctions() {
        Map<String, OttlFunction> sorted = new LinkedHashMap<>();
        functions.asMap().keySet().stream().sorted().forEach(k -> sorted.put(k, functions.asMap().get(k)));
        return sorted;
    }

    private FunctionInfo toFunctionInfo(String name, OttlFunction fn) {
        List<FunctionInfo.ParamInfo> params;
        String doc = BUILTIN_DOCS.getOrDefault(name, "");
        String category = BUILTIN_DOCS.containsKey(name) ? "editor" : "custom";

        if (fn instanceof DescribedFunction described && !described.parameterTypeNames().isEmpty()) {
            params = new ArrayList<>();
            List<String> pNames = described.parameterNames();
            List<String> pTypes = described.parameterTypeNames();
            for (int i = 0; i < pTypes.size(); i++) {
                String pName = i < pNames.size() ? pNames.get(i) : "arg" + i;
                params.add(new FunctionInfo.ParamInfo(pName, pTypes.get(i)));
            }
            if (!described.documentation().isBlank()) {
                doc = described.documentation();
            }
        } else if (BUILTIN_PARAMS.containsKey(name)) {
            params = BUILTIN_PARAMS.get(name);
        } else {
            params = genericParams(fn.minArgs(), fn.maxArgs());
        }
        return new FunctionInfo(name, fn.minArgs(), fn.maxArgs(), params, doc, category);
    }

    private static List<FunctionInfo.ParamInfo> genericParams(int min, int max) {
        int count = Math.max(min, Math.min(max == Integer.MAX_VALUE ? min : max, min == 0 ? max : min));
        if (count == Integer.MAX_VALUE || count < 0) {
            count = Math.max(min, 0);
        }
        List<FunctionInfo.ParamInfo> params = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            params.add(new FunctionInfo.ParamInfo("arg" + i, "Value"));
        }
        return params;
    }

    private static String simpleName(String fqn) {
        if (fqn == null) {
            return "Object";
        }
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }
}
