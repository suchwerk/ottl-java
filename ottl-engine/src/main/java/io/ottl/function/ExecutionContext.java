/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.function;

import io.ottl.ExecutionMode;
import io.ottl.OttlEvaluationException;
import io.ottl.lang.ast.Expression;
import io.ottl.spi.EnumConstantRegistry;
import io.ottl.spi.PropertyResolver;
import io.ottl.spi.ResolverRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-execution state handed to functions. Holds the mutable "current root" and provides safe path
 * navigation with copy-on-write bubbling (see {@link PropertyResolver}).
 *
 * <p>Instances are created once per {@code apply(...)} call and are not shared between threads.
 */
public final class ExecutionContext {

    private Object root;
    private final String contextName;
    private final ResolverRegistry resolvers;
    private final FunctionRegistry functions;
    private final ExecutionMode mode;
    private final EnumConstantRegistry enums;
    private final ArrayDeque<Map<String, Object>> locals = new ArrayDeque<>();

    public ExecutionContext(Object root, String contextName, ResolverRegistry resolvers,
                            FunctionRegistry functions, ExecutionMode mode) {
        this(root, contextName, resolvers, functions, mode, EnumConstantRegistry.empty());
    }

    public ExecutionContext(Object root, String contextName, ResolverRegistry resolvers,
                            FunctionRegistry functions, ExecutionMode mode, EnumConstantRegistry enums) {
        this.root = root;
        this.contextName = contextName;
        this.resolvers = resolvers;
        this.functions = functions;
        this.mode = mode;
        this.enums = enums == null ? EnumConstantRegistry.empty() : enums;
    }

    public Object root() {
        return root;
    }

    public void setRoot(Object root) {
        this.root = root;
    }

    public ExecutionMode mode() {
        return mode;
    }

    public ResolverRegistry resolvers() {
        return resolvers;
    }

    public FunctionRegistry functions() {
        return functions;
    }

    /** Push read-only lambda bindings. The innermost scope is consulted first. */
    public void pushLocals(Map<String, Object> bindings) {
        locals.addFirst(bindings);
    }

    public void popLocals() {
        locals.removeFirst();
    }

    /** Evaluate an expression to a value. */
    public Object evaluate(Expression expression) {
        return Evaluator.eval(this, expression);
    }

    /**
     * Resolve {@code EnumName.CONSTANT} (or a fully-qualified type name). Empty when the path is not
     * an allowlisted enum reference. Unknown constants and property access on an enum fail here.
     */
    public Optional<Object> enumConstant(Expression.Path path) {
        List<String> names = fieldNames(path.segments());
        rejectBareEnum(names);
        EnumConstantRegistry.Match match = enums.match(names);
        if (match == null) {
            return Optional.empty();
        }
        if (match.propertyAccess()) {
            throw new OttlEvaluationException(
                    "Enum " + match.typeName() + " constants are values, not property bags");
        }
        if (match.value() == null) {
            throw new OttlEvaluationException(
                    "Unknown enum constant '" + match.constant() + "' on " + match.typeName());
        }
        return Optional.of(match.value());
    }

    /** Resolve a bare enum symbol, or fail when it is unknown or ambiguous. */
    public Object enumSymbol(String name) {
        EnumConstantRegistry.SymbolLookup lookup = enums.symbol(name);
        if (lookup == null) {
            throw new OttlEvaluationException("Unknown enum symbol '" + name + "'");
        }
        if (lookup.ambiguous()) {
            throw new OttlEvaluationException("Ambiguous enum symbol '" + name + "'");
        }
        return lookup.value();
    }

    /**
     * Strip a leading context-name segment, so both {@code order.status} and {@code status} navigate
     * to the same property when the context name is {@code order}.
     */
    public List<Expression.PathSegment> strip(Expression.Path path) {
        List<Expression.PathSegment> segs = path.segments();
        if (!segs.isEmpty() && segs.get(0) instanceof Expression.FieldSegment f
                && f.name().equals(contextName) && !isLocal(f.name())) {
            return segs.subList(1, segs.size());
        }
        return segs;
    }

    public boolean isLocalPath(Expression.Path path) {
        return !path.segments().isEmpty()
                && path.segments().get(0) instanceof Expression.FieldSegment field
                && isLocal(field.name());
    }

    public boolean isLocal(String name) {
        for (Map<String, Object> scope : locals) {
            if (scope.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    // ----- path reads -----

    public Object getPath(List<Expression.PathSegment> segments) {
        if (!segments.isEmpty() && segments.get(0) instanceof Expression.FieldSegment field && isLocal(field.name())) {
            return navigate(lookupLocal(field.name()), segments, 1);
        }
        return navigate(root, segments, 0);
    }

    private Object navigate(Object cur, List<Expression.PathSegment> segments, int start) {
        for (int i = start; i < segments.size(); i++) {
            if (cur == null) {
                return null;
            }
            cur = getSegment(cur, segments.get(i));
        }
        return cur;
    }

    private Object lookupLocal(String name) {
        for (Map<String, Object> scope : locals) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        throw new OttlEvaluationException("Unknown lambda parameter '" + name + "'");
    }

    private Object getSegment(Object target, Expression.PathSegment seg) {
        if (seg instanceof Expression.FieldSegment f) {
            return resolvers.forTarget(target).getField(target, f.name());
        }
        return index(target, segmentKey(seg));
    }

    private Object setSegment(Object target, Expression.PathSegment seg, Object value) {
        if (seg instanceof Expression.FieldSegment f) {
            return resolvers.forTarget(target).setField(target, f.name(), value);
        }
        return setIndex(target, segmentKey(seg), value);
    }

    /** Index a map, list, byte slice, or any other resolved target. A null target yields null. */
    public Object index(Object target, Object key) {
        if (target == null) {
            return null;
        }
        if (target instanceof List<?> list) {
            return list.get(asIndex(key, list.size()));
        }
        if (target instanceof byte[] bytes) {
            return Byte.toUnsignedInt(bytes[asIndex(key, bytes.length)]);
        }
        return resolvers.forTarget(target).getKey(target, key);
    }

    private Object setIndex(Object target, Object key, Object value) {
        if (target instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) list;
            raw.set(asIndex(key, raw.size()), value);
            return target;
        }
        if (target instanceof byte[]) {
            throw new OttlEvaluationException("Byte slices are not writable by index");
        }
        return resolvers.forTarget(target).setKey(target, key, value);
    }

    private Object segmentKey(Expression.PathSegment seg) {
        if (seg instanceof Expression.KeySegment key) {
            return key.key();
        }
        if (seg instanceof Expression.DynamicKeySegment dynamic) {
            return evaluate(dynamic.key());
        }
        throw new OttlEvaluationException("Expected an index but found a field");
    }

    private static int asIndex(Object key, int size) {
        long value;
        if (key instanceof Byte || key instanceof Short || key instanceof Integer || key instanceof Long) {
            value = ((Number) key).longValue();
        } else if (key instanceof java.math.BigInteger integer) {
            value = integer.longValueExact();
        } else {
            throw new OttlEvaluationException("List index must be an integer but was " + describeValue(key));
        }
        if (value < 0 || value >= size) {
            throw new OttlEvaluationException("List index " + value + " is out of range (size " + size + ")");
        }
        return (int) value;
    }

    private static String describeValue(Object value) {
        return value == null ? "nil" : value.getClass().getSimpleName();
    }

    // ----- path writes (updates root, with copy-on-write bubbling) -----

    public void setPath(List<Expression.PathSegment> segments, Object value) {
        List<String> names = fieldNames(segments);
        rejectBareEnum(names);
        if (enums.match(names) != null) {
            throw new OttlEvaluationException("Enum constants are values and cannot be assigned");
        }
        if (segments.isEmpty()) {
            this.root = value;
            return;
        }
        if (segments.get(0) instanceof Expression.FieldSegment field && isLocal(field.name())) {
            throw new OttlEvaluationException("Lambda parameter '" + field.name() + "' is read-only");
        }
        this.root = setRec(root, segments, 0, value);
    }

    private Object setRec(Object target, List<Expression.PathSegment> segs, int i, Object value) {
        Expression.PathSegment seg = segs.get(i);
        if (i == segs.size() - 1) {
            return setSegment(target, seg, value);
        }
        Object child = getSegment(target, seg);
        if (child == null) {
            throw new OttlEvaluationException(
                    "Cannot assign through a null intermediate value at path segment '"
                            + describe(seg) + "'");
        }
        Object newChild = setRec(child, segs, i + 1, value);
        if (newChild == child) {
            return target; // mutated in place
        }
        return setSegment(target, seg, newChild);
    }

    public void deleteKeyAtPath(List<Expression.PathSegment> segments, Object key) {
        Object map = getPath(segments);
        if (map == null) {
            return;
        }
        PropertyResolver r = resolvers.forTarget(map);
        Object newMap = r.deleteKey(map, key);
        bubble(segments, map, newMap);
    }

    public void keepKeysAtPath(List<Expression.PathSegment> segments, Collection<Object> keys) {
        Object map = getPath(segments);
        if (map == null) {
            return;
        }
        PropertyResolver r = resolvers.forTarget(map);
        Object newMap = r.keepKeys(map, keys);
        bubble(segments, map, newMap);
    }

    private void bubble(List<Expression.PathSegment> segments, Object oldValue, Object newValue) {
        if (newValue == oldValue) {
            return;
        }
        if (segments.isEmpty()) {
            this.root = newValue;
        } else {
            this.root = setRec(root, segments, 0, newValue);
        }
    }

    private void rejectBareEnum(List<String> names) {
        if (names == null || names.size() != 1) {
            return;
        }
        EnumConstantRegistry.EnumType type = enums.find(names.get(0));
        if (type == null || type.constants().isEmpty()) {
            return;
        }
        throw new OttlEvaluationException(
                "Enum '" + type.simpleName() + "' needs a constant, for example "
                        + type.simpleName() + "." + type.constants().get(0));
    }

    private static List<String> fieldNames(List<Expression.PathSegment> segments) {
        if (segments == null) {
            return null;
        }
        List<String> names = new ArrayList<>(segments.size());
        for (Expression.PathSegment seg : segments) {
            if (seg instanceof Expression.FieldSegment field) {
                names.add(field.name());
            } else {
                return null;
            }
        }
        return names;
    }

    private static String describe(Expression.PathSegment seg) {
        if (seg instanceof Expression.FieldSegment f) {
            return f.name();
        }
        if (seg instanceof Expression.DynamicKeySegment) {
            return "[...]";
        }
        return "[" + ((Expression.KeySegment) seg).key() + "]";
    }
}
