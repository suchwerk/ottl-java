/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.internal;

import io.ottl.OttlCompilationException;
import io.ottl.lang.ast.Block;
import io.ottl.lang.ast.Condition;
import io.ottl.lang.ast.Expression;
import io.ottl.lang.ast.IfStatement;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.ScriptItem;
import io.ottl.lang.ast.Statement;
import io.ottl.lang.ast.WhereBlock;
import io.ottl.spi.EnumConstantRegistry;
import io.ottl.spi.PropertyAccessorRegistry;
import io.ottl.spi.TypeMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates, at compile time, that every path in a script refers to approved properties of
 * {@code @OttlAccessible} types. Used in strict (native) mode so that rules referencing unknown
 * domain properties are rejected <em>before</em> activation, with no runtime reflection involved.
 */
public final class SemanticValidator {

    private final PropertyAccessorRegistry registry;
    private final EnumConstantRegistry enums;

    public SemanticValidator(PropertyAccessorRegistry registry, EnumConstantRegistry enums) {
        this.registry = registry;
        this.enums = enums == null ? EnumConstantRegistry.empty() : enums;
    }

    public void validate(Class<?> rootType, String contextName, OttlScript script) {
        validateItems(script.statements(), rootType, contextName);
    }

    private void validateItems(List<ScriptItem> items, Class<?> rootType, String contextName) {
        for (ScriptItem item : items) {
            validateItem(item, rootType, contextName);
        }
    }

    private void validateItem(ScriptItem item, Class<?> rootType, String contextName) {
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
            case Block block -> validateItems(block.statements(), rootType, contextName);
            case IfStatement branch -> {
                validateCondition(branch.condition(), rootType, contextName);
                validateItems(branch.thenBlock().statements(), rootType, contextName);
                branch.elseBranch().ifPresent(other -> {
                    if (other instanceof Block elseBlock) {
                        validateItems(elseBlock.statements(), rootType, contextName);
                    } else {
                        validateItem(other, rootType, contextName);
                    }
                });
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
        if (rejectEnum(segs, write)) {
            return;
        }
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

    /** @return true when the path is an enum constant reference and has been checked */
    private boolean rejectEnum(List<Expression.PathSegment> segs, boolean write) {
        List<String> names = new ArrayList<>();
        for (Expression.PathSegment seg : segs) {
            if (seg instanceof Expression.FieldSegment field) {
                names.add(field.name());
            } else {
                return false;
            }
        }
        if (names.size() == 1) {
            EnumConstantRegistry.EnumType type = enums.find(names.get(0));
            if (type != null && !type.constants().isEmpty()) {
                throw new OttlCompilationException(
                        "Enum '" + type.simpleName() + "' needs a constant, for example "
                                + type.simpleName() + "." + type.constants().get(0));
            }
        }
        EnumConstantRegistry.Match match = enums.match(names);
        if (match == null) {
            return false;
        }
        if (match.propertyAccess()) {
            throw new OttlCompilationException(
                    "Enum " + match.typeName() + " constants are values, not property bags");
        }
        if (match.value() == null) {
            throw new OttlCompilationException(
                    "Unknown enum constant '" + match.constant() + "' on " + match.typeName());
        }
        if (write) {
            throw new OttlCompilationException(
                    "Enum constant '" + match.typeName() + "." + match.constant() + "' is not writable");
        }
        return true;
    }

    private static boolean isDynamicTypeName(String fqn) {
        return fqn.equals("java.lang.Object")
                || fqn.startsWith("java.util.")
                || fqn.startsWith("com.fasterxml.jackson.");
    }
}
