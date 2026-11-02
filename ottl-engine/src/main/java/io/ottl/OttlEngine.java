/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl;

import io.ottl.function.FunctionRegistry;
import io.ottl.function.OttlFunction;
import io.ottl.internal.CompiledProgram;
import io.ottl.internal.SemanticValidator;
import io.ottl.internal.Validator;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.parser.OttlParseException;
import io.ottl.lang.parser.Parser;
import io.ottl.spi.AccessorBackedResolver;
import io.ottl.spi.PropertyAccessorRegistry;
import io.ottl.spi.PropertyResolver;
import io.ottl.spi.ResolverRegistry;

import java.util.Collection;
import java.util.Map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The entry point for compiling OTTL source into {@link OttlProgram}s.
 *
 * <p>By default the engine discovers {@link PropertyResolver}s and {@link OttlFunction}s via the
 * {@link ServiceLoader} SPI, so the documented zero-wiring API works out of the box:
 *
 * <pre>{@code
 * OttlEngine engine = OttlEngine.builder().profile(OttlProfile.JAVA).build();
 * OttlProgram<Order> program = engine.compile("set(order.status, \"REVIEW\")", Order.class);
 * }</pre>
 *
 * <p>Engines are immutable and thread-safe; share one across your application.
 */
public final class OttlEngine {

    private final OttlProfile profile;
    private final ExecutionMode defaultMode;
    private final String contextNameOverride;
    private final ResolverRegistry resolvers;
    private final FunctionRegistry functions;
    private final OttlLimits limits;
    private final Validator validator;
    private final PropertyAccessorRegistry accessorRegistry;
    private final boolean strictTypes;
    private final SemanticValidator semanticValidator;

    private OttlEngine(Builder b) {
        this.profile = b.profile;
        this.defaultMode = b.defaultMode;
        this.contextNameOverride = b.contextName;
        this.limits = b.limits;
        this.accessorRegistry = b.accessorRegistry;
        this.strictTypes = b.strictTypes;

        List<PropertyResolver> allResolvers = new ArrayList<>(b.resolvers);
        if (accessorRegistry != null) {
            allResolvers.add(new AccessorBackedResolver(accessorRegistry));
        }
        if (b.loadDefaults) {
            ServiceLoader.load(PropertyResolver.class).forEach(allResolvers::add);
        }
        allResolvers.sort(Comparator.comparingInt(PropertyResolver::priority));
        this.resolvers = new ResolverRegistry(allResolvers);
        this.semanticValidator = accessorRegistry != null ? new SemanticValidator(accessorRegistry) : null;

        FunctionRegistry.Builder fnBuilder = FunctionRegistry.builder();
        if (b.loadDefaults) {
            ServiceLoader.load(OttlFunction.class).forEach(fnBuilder::add);
        }
        fnBuilder.addAll(b.functions);
        this.functions = fnBuilder.build();
        this.validator = new Validator(functions, limits);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Compile {@code source} for root type {@code type}. Records compile to COPY mode. */
    public <T> OttlProgram<T> compile(String source, Class<T> type) {
        return compile(source, type, defaultContextName(type));
    }

    /** Compile with an explicit context name (the identifier that refers to the root). */
    public <T> OttlProgram<T> compile(String source, Class<T> type, String contextName) {
        if (source != null && source.length() > limits.maxSourceLength()) {
            throw new OttlCompilationException("Source too long: " + source.length()
                    + " chars (limit " + limits.maxSourceLength() + ")");
        }
        OttlScript script;
        try {
            script = Parser.parse(source);
        } catch (OttlParseException e) {
            throw new OttlCompilationException("Parse error: " + e.getMessage(), e);
        }
        validator.validate(script);
        semanticValidate(type, contextName, script);

        ExecutionMode mode = type.isRecord() ? ExecutionMode.COPY : defaultMode;
        for (PropertyResolver r : resolvers.resolvers()) {
            r.prepare(type);
        }
        return new CompiledProgram<>(source, script, contextName, resolvers, functions, mode);
    }

    /** Create a {@link io.ottl.rules.RuleManager} for runtime-updatable rules of {@code type}. */
    public <T> io.ottl.rules.RuleManager<T> ruleManager(Class<T> type, io.ottl.rules.RuleRepository repository) {
        return new io.ottl.rules.DefaultRuleManager<>(this, type, repository);
    }

    /** Validate source without producing a program (used to gate rule activation). */
    public void validate(String source) {
        if (source != null && source.length() > limits.maxSourceLength()) {
            throw new OttlCompilationException("Source too long: " + source.length()
                    + " chars (limit " + limits.maxSourceLength() + ")");
        }
        try {
            validator.validate(Parser.parse(source));
        } catch (OttlParseException e) {
            throw new OttlCompilationException("Parse error: " + e.getMessage(), e);
        }
    }

    private void semanticValidate(Class<?> type, String contextName, OttlScript script) {
        if (!strictTypes || semanticValidator == null) {
            return;
        }
        if (accessorRegistry.contains(type)) {
            semanticValidator.validate(type, contextName, script);
        } else if (!isDynamicType(type)) {
            throw new OttlCompilationException(
                    "Type " + type.getName() + " is not registered as @OttlAccessible");
        }
    }

    private static boolean isDynamicType(Class<?> type) {
        return Map.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type)
                || type == Object.class
                || type.getName().startsWith("com.fasterxml.jackson.");
    }

    private String defaultContextName(Class<?> type) {
        if (contextNameOverride != null) {
            return contextNameOverride;
        }
        String simple = type.getSimpleName();
        if (simple.isEmpty()) {
            return "root";
        }
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    public OttlProfile profile() {
        return profile;
    }

    public FunctionRegistry functions() {
        return functions;
    }

    public ResolverRegistry resolvers() {
        return resolvers;
    }

    public OttlLimits limits() {
        return limits;
    }

    public PropertyAccessorRegistry accessorRegistry() {
        return accessorRegistry;
    }

    public boolean strictTypes() {
        return strictTypes;
    }

    /** Fluent builder for {@link OttlEngine}. */
    public static final class Builder {
        private OttlProfile profile = OttlProfile.STANDARD;
        private ExecutionMode defaultMode = ExecutionMode.MUTATE;
        private String contextName;
        private final List<PropertyResolver> resolvers = new ArrayList<>();
        private final List<OttlFunction> functions = new ArrayList<>();
        private OttlLimits limits = OttlLimits.defaults();
        private boolean loadDefaults = true;
        private PropertyAccessorRegistry accessorRegistry;
        private boolean strictTypes;

        public Builder profile(OttlProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder defaultMode(ExecutionMode mode) {
            this.defaultMode = mode;
            return this;
        }

        /** Force a context name for all compiles (otherwise derived from the type's simple name). */
        public Builder contextName(String contextName) {
            this.contextName = contextName;
            return this;
        }

        public Builder resolver(PropertyResolver resolver) {
            this.resolvers.add(resolver);
            return this;
        }

        public Builder resolvers(List<PropertyResolver> resolvers) {
            this.resolvers.addAll(resolvers);
            return this;
        }

        public Builder function(OttlFunction function) {
            this.functions.add(function);
            return this;
        }

        public Builder functions(List<OttlFunction> functions) {
            this.functions.addAll(functions);
            return this;
        }

        public Builder limits(OttlLimits limits) {
            this.limits = limits;
            return this;
        }

        /**
         * Whether to discover resolvers and functions via {@link ServiceLoader} (default {@code true}).
         * Disable for fully explicit wiring, e.g. in a Quarkus native build.
         */
        public Builder loadDefaults(boolean loadDefaults) {
            this.loadDefaults = loadDefaults;
            return this;
        }

        /**
         * Register build-time generated property accessors. When set, an {@link AccessorBackedResolver}
         * is added automatically (highest priority) so approved domain types resolve without reflection.
         */
        public Builder accessorRegistry(PropertyAccessorRegistry accessorRegistry) {
            this.accessorRegistry = accessorRegistry;
            return this;
        }

        /**
         * Enable strict type checking: rules are validated against the accessor registry at compile
         * time and rules referencing unknown types/properties are rejected. Intended for native mode.
         */
        public Builder strictTypes(boolean strictTypes) {
            this.strictTypes = strictTypes;
            return this;
        }

        public OttlEngine build() {
            return new OttlEngine(this);
        }
    }
}
