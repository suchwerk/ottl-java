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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    /**
     * Strip a leading context-name segment, so both {@code order.status} and {@code status} navigate
     * to the same property when the context name is {@code order}.
     */
    public List<Expression.PathSegment> strip(Expression.Path path) {
        List<Expression.PathSegment> segs = path.segments();
        if (!segs.isEmpty() && segs.get(0) instanceof Expression.FieldSegment f
                && f.name().equals(contextName)) {
            return segs.subList(1, segs.size());
        }
        return segs;
    }

    // ----- path reads -----

    public Object getPath(List<Expression.PathSegment> segments) {
        Object cur = root;
        for (Expression.PathSegment seg : segments) {
            if (cur == null) {
                return null;
            }
            cur = getSegment(cur, seg);
        }
        return cur;
    }

    private Object getSegment(Object target, Expression.PathSegment seg) {
        PropertyResolver r = resolvers.forTarget(target);
        if (seg instanceof Expression.FieldSegment f) {
            return r.getField(target, f.name());
        }
        return r.getKey(target, ((Expression.KeySegment) seg).key());
    }

    private Object setSegment(Object target, Expression.PathSegment seg, Object value) {
        PropertyResolver r = resolvers.forTarget(target);
        if (seg instanceof Expression.FieldSegment f) {
            return r.setField(target, f.name(), value);
        }
        return r.setKey(target, ((Expression.KeySegment) seg).key(), value);
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
        return "[" + ((Expression.KeySegment) seg).key() + "]";
    }
}
