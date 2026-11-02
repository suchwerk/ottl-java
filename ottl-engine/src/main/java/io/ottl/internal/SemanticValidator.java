/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.internal;

import io.ottl.OttlCompilationException;
import io.ottl.lang.ast.Condition;
import io.ottl.lang.ast.Expression;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.ScriptItem;
import io.ottl.lang.ast.Statement;
import io.ottl.lang.ast.WhereBlock;
import io.ottl.spi.PropertyAccessorRegistry;
import io.ottl.spi.TypeMetadata;

import java.util.List;

/**
 * Validates, at compile time, that every path in a script refers to approved properties of
 * {@code @OttlAccessible} types. Used in strict (native) mode so that rules referencing unknown
 * domain properties are rejected <em>before</em> activation, with no runtime reflection involved.
 */
public final class SemanticValidator {

    private final PropertyAccessorRegistry registry;

    public SemanticValidator(PropertyAccessorRegistry registry) {
        this.registry = registry;
    }

    public void validate(Class<?> rootType, String contextName, OttlScript script) {
        validateItems(script.statements(), rootType, contextName);
    }

    private void validateItems(List<ScriptItem> items, Class<?> rootType, String contextName) {
        for (ScriptItem item : items) {
            switch (item) {
                case Statement stmt -> {
                    List<Expression> args = stmt.editor().arguments();
                    for (int i = 0; i < args.size(); i++) {
                        // By convention the first argument of set/delete_key/keep_keys is the write target.
                        validateExpression(args.get(i), rootType, contextName, i == 0);
                    }
                    stmt.condition().ifPresent(c -> validateCondition(c, rootType, contextName));
                }
                case WhereBlock block -> {
                    validateCondition(block.condition(), rootType, contextName);
                    validateItems(block.body(), rootType, contextName);
                }
            }
        }
    }

    private void validateCondition(Condition condition, Class<?> rootType, String contextName) {
        switch (condition) {
            case Condition.And and -> {
                validateCondition(and.left(), rootType, contextName);
                validateCondition(and.right(), rootType, contextName);
            }
            case Condition.Or or -> {
                validateCondition(or.left(), rootType, contextName);
                validateCondition(or.right(), rootType, contextName);
            }
            case Condition.Not not -> validateCondition(not.condition(), rootType, contextName);
            case Condition.Comparison cmp -> {
                validateExpression(cmp.left(), rootType, contextName, false);
                validateExpression(cmp.right(), rootType, contextName, false);
            }
            case Condition.ValueCondition vc -> validateExpression(vc.expression(), rootType, contextName, false);
        }
    }

    private void validateExpression(Expression expr, Class<?> rootType, String contextName, boolean write) {
        switch (expr) {
            case Expression.Path path -> validatePath(path, rootType, contextName, write);
            case Expression.FunctionCall call -> {
                for (Expression arg : call.arguments()) {
                    validateExpression(arg, rootType, contextName, false);
                }
            }
            case Expression.ListLiteral list -> {
                for (Expression e : list.elements()) {
                    validateExpression(e, rootType, contextName, false);
                }
            }
            default -> {
                // literals need no validation
            }
        }
    }

    private void validatePath(Expression.Path path, Class<?> rootType, String contextName, boolean write) {
        List<Expression.PathSegment> segs = path.segments();
        int start = 0;
        if (!segs.isEmpty() && segs.get(0) instanceof Expression.FieldSegment f && f.name().equals(contextName)) {
            start = 1;
        }
        Class<?> curType = rootType;
        for (int i = start; i < segs.size(); i++) {
            Expression.PathSegment seg = segs.get(i);
            if (seg instanceof Expression.KeySegment) {
                return; // keyed/index access is dynamic; cannot be statically validated
            }
            String name = ((Expression.FieldSegment) seg).name();
            TypeMetadata meta = registry.metadata(curType);
            if (meta == null) {
                return; // current type is dynamic/leaf (e.g. Map); stop validating
            }
            if (!meta.knows(name)) {
                throw new OttlCompilationException(
                        "Unknown property '" + name + "' on type " + curType.getSimpleName());
            }
            boolean isLast = i == segs.size() - 1;
            if (isLast && write && !meta.isWritable(name)) {
                throw new OttlCompilationException(
                        "Property '" + name + "' on type " + curType.getSimpleName() + " is not writable");
            }
            if (isLast) {
                return;
            }
            String nextFqn = meta.propertyTypeName(name);
            Class<?> next = registry.typeByName(nextFqn);
            if (next == null) {
                if (isDynamicTypeName(nextFqn)) {
                    return; // navigating into a Map/JSON/Object is allowed dynamically
                }
                throw new OttlCompilationException(
                        "Cannot navigate into non-accessible type '" + nextFqn + "' via property '" + name + "'");
            }
            curType = next;
        }
    }

    private static boolean isDynamicTypeName(String fqn) {
        return fqn.equals("java.lang.Object")
                || fqn.startsWith("java.util.")
                || fqn.startsWith("com.fasterxml.jackson.");
    }
}
