/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.internal;

import io.ottl.OttlCompilationException;
import io.ottl.function.CallBinder;
import io.ottl.function.FunctionRegistry;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates, at compile time, that every path in a script refers to approved properties of
 * {@code @OttlAccessible} types. Used in strict (native) mode so that rules referencing unknown
 * domain properties are rejected <em>before</em> activation, with no runtime reflection involved.
 */
public final class SemanticValidator {

    private final PropertyAccessorRegistry registry;
    private final EnumConstantRegistry enums;
    private final FunctionRegistry functions;

    public SemanticValidator(PropertyAccessorRegistry registry, EnumConstantRegistry enums,
                             FunctionRegistry functions) {
        this.registry = registry;
        this.enums = enums == null ? EnumConstantRegistry.empty() : enums;
        this.functions = functions;
    }

    public void validate(Class<?> rootType, String contextName, OttlScript script) {
        validateItems(script.statements(), rootType, contextName, new ArrayDeque<>());
    }

    private void validateItems(List<ScriptItem> items, Class<?> rootType, String contextName,
                               Deque<Set<String>> scopes) {
        for (ScriptItem item : items) {
            validateItem(item, rootType, contextName, scopes);
        }
    }

    private void validateItem(ScriptItem item, Class<?> rootType, String contextName,
                              Deque<Set<String>> scopes) {
        switch (item) {
            case Statement stmt -> {
                List<Expression> args = CallBinder.expressions(functions.require(stmt.editor().name()), stmt.editor());
                for (int i = 0; i < args.size(); i++) {
                    if (args.get(i) == null) {
                        continue;
                    }
                    // By convention the first argument of an editor is the write target.
                    validateExpression(args.get(i), rootType, contextName, i == 0, scopes);
                }
                stmt.condition().ifPresent(c -> validateCondition(c, rootType, contextName, scopes));
            }
            case WhereBlock block -> {
                validateCondition(block.condition(), rootType, contextName, scopes);
                validateItems(block.body(), rootType, contextName, scopes);
            }
            case Block block -> validateItems(block.statements(), rootType, contextName, scopes);
            case IfStatement branch -> {
                validateCondition(branch.condition(), rootType, contextName, scopes);
                validateItems(branch.thenBlock().statements(), rootType, contextName, scopes);
                branch.elseBranch().ifPresent(other -> {
                    if (other instanceof Block elseBlock) {
                        validateItems(elseBlock.statements(), rootType, contextName, scopes);
                    } else {
                        validateItem(other, rootType, contextName, scopes);
                    }
                });
            }
        }
    }

    private void validateCondition(Condition condition, Class<?> rootType, String contextName,
                                   Deque<Set<String>> scopes) {
        switch (condition) {
            case Condition.And and -> {
                validateCondition(and.left(), rootType, contextName, scopes);
                validateCondition(and.right(), rootType, contextName, scopes);
            }
            case Condition.Or or -> {
                validateCondition(or.left(), rootType, contextName, scopes);
                validateCondition(or.right(), rootType, contextName, scopes);
            }
            case Condition.Not not -> validateCondition(not.condition(), rootType, contextName, scopes);
            case Condition.Comparison cmp -> {
                validateExpression(cmp.left(), rootType, contextName, false, scopes);
                validateExpression(cmp.right(), rootType, contextName, false, scopes);
            }
            case Condition.ValueCondition vc ->
                    validateExpression(vc.expression(), rootType, contextName, false, scopes);
        }
    }

    private void validateExpression(Expression expr, Class<?> rootType, String contextName, boolean write,
                                    Deque<Set<String>> scopes) {
        switch (expr) {
            case Expression.Path path -> validatePath(path, rootType, contextName, write, scopes);
            case Expression.FunctionCall call -> {
                for (Expression arg : call.arguments()) {
                    validateExpression(arg, rootType, contextName, false, scopes);
                }
                for (Expression key : call.keys()) {
                    validateExpression(key, rootType, contextName, false, scopes);
                }
            }
            case Expression.ListLiteral list -> {
                for (Expression e : list.elements()) {
                    validateExpression(e, rootType, contextName, false, scopes);
                }
            }
            case Expression.MapLiteral map -> {
                for (Expression.MapEntry entry : map.entries()) {
                    validateExpression(entry.key(), rootType, contextName, false, scopes);
                    validateExpression(entry.value(), rootType, contextName, false, scopes);
                }
            }
            case Expression.Math math -> {
                validateExpression(math.left(), rootType, contextName, false, scopes);
                validateExpression(math.right(), rootType, contextName, false, scopes);
            }
            case Expression.Unary unary -> validateExpression(unary.operand(), rootType, contextName, false, scopes);
            case Expression.Lambda lambda -> validateLambda(lambda, rootType, contextName, scopes);
            default -> {
                // literals and enum symbols need no property check
            }
        }
    }

    private void validateLambda(Expression.Lambda lambda, Class<?> rootType, String contextName,
                                Deque<Set<String>> scopes) {
        Set<String> scope = new LinkedHashSet<>();
        for (String parameter : lambda.parameters()) {
            if (!parameter.equals("_")) {
                scope.add(parameter);
            }
        }
        scopes.addFirst(scope);
        try {
            switch (lambda.body()) {
                case Expression.LambdaValue value ->
                        validateExpression(value.expression(), rootType, contextName, false, scopes);
                case Expression.LambdaCondition condition ->
                        validateCondition(condition.condition(), rootType, contextName, scopes);
            }
        } finally {
            scopes.removeFirst();
        }
    }

    private void validatePath(Expression.Path path, Class<?> rootType, String contextName, boolean write,
                              Deque<Set<String>> scopes) {
        List<Expression.PathSegment> segs = path.segments();
        if (!segs.isEmpty() && segs.get(0) instanceof Expression.FieldSegment first && isLocal(scopes, first.name())) {
            if (write) {
                throw new OttlCompilationException("Lambda parameter '" + first.name() + "' is read-only");
            }
            validateDynamicKeys(segs, rootType, contextName, scopes);
            return;
        }
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
            if (seg instanceof Expression.DynamicKeySegment dynamic) {
                validateExpression(dynamic.key(), rootType, contextName, false, scopes);
                return;
            }
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
            if (segs.get(i + 1) instanceof Expression.DynamicKeySegment dynamic) {
                validateExpression(dynamic.key(), rootType, contextName, false, scopes);
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

    private void validateDynamicKeys(List<Expression.PathSegment> segs, Class<?> rootType, String contextName,
                                     Deque<Set<String>> scopes) {
        for (Expression.PathSegment seg : segs) {
            if (seg instanceof Expression.DynamicKeySegment dynamic) {
                validateExpression(dynamic.key(), rootType, contextName, false, scopes);
            }
        }
    }

    private static boolean isLocal(Deque<Set<String>> scopes, String name) {
        for (Set<String> scope : scopes) {
            if (scope.contains(name)) {
                return true;
            }
        }
        return false;
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
