/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.internal;

import io.ottl.ExecutionMode;
import io.ottl.OttlProgram;
import io.ottl.function.Argument;
import io.ottl.function.Evaluator;
import io.ottl.function.ExecutionContext;
import io.ottl.function.FunctionRegistry;
import io.ottl.function.OttlFunction;
import io.ottl.lang.ast.Block;
import io.ottl.lang.ast.Expression;
import io.ottl.lang.ast.IfStatement;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.ScriptItem;
import io.ottl.lang.ast.Statement;
import io.ottl.lang.ast.WhereBlock;
import io.ottl.spi.EnumConstantRegistry;
import io.ottl.spi.ResolverRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable, thread-safe implementation of {@link OttlProgram}. A fresh {@link ExecutionContext} is
 * created per {@link #apply(Object)} call; all shared state (script, registries, mode) is immutable.
 */
public final class CompiledProgram<T> implements OttlProgram<T> {

    private final String source;
    private final OttlScript script;
    private final String contextName;
    private final ResolverRegistry resolvers;
    private final FunctionRegistry functions;
    private final ExecutionMode mode;
    private final EnumConstantRegistry enums;

    public CompiledProgram(String source, OttlScript script, String contextName,
                           ResolverRegistry resolvers, FunctionRegistry functions, ExecutionMode mode,
                           EnumConstantRegistry enums) {
        this.source = source;
        this.script = script;
        this.contextName = contextName;
        this.resolvers = resolvers;
        this.functions = functions;
        this.mode = mode;
        this.enums = enums == null ? EnumConstantRegistry.empty() : enums;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T apply(T input) {
        ExecutionContext ctx = new ExecutionContext(input, contextName, resolvers, functions, mode, enums);
        executeItems(ctx, script.statements());
        return (T) ctx.root();
    }

    private void executeItems(ExecutionContext ctx, List<ScriptItem> items) {
        for (ScriptItem item : items) {
            executeItem(ctx, item);
        }
    }

    private void executeItem(ExecutionContext ctx, ScriptItem item) {
        switch (item) {
            case Statement stmt -> {
                if (stmt.condition().isEmpty()
                        || Evaluator.evalCondition(ctx, stmt.condition().get())) {
                    invokeEditor(ctx, stmt.editor());
                }
            }
            case WhereBlock block -> {
                if (Evaluator.evalCondition(ctx, block.condition())) {
                    executeItems(ctx, block.body());
                }
            }
            case Block block -> executeItems(ctx, block.statements());
            case IfStatement branch -> {
                if (Evaluator.evalCondition(ctx, branch.condition())) {
                    executeItems(ctx, branch.thenBlock().statements());
                } else {
                    branch.elseBranch().ifPresent(other -> executeItem(ctx, other));
                }
            }
        }
    }

    private void invokeEditor(ExecutionContext ctx, Expression.FunctionCall editor) {
        OttlFunction fn = functions.require(editor.name());
        List<Argument> args = new ArrayList<>(editor.arguments().size());
        for (Expression arg : editor.arguments()) {
            args.add(new Argument(arg, ctx));
        }
        fn.invoke(ctx, args);
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public ExecutionMode mode() {
        return mode;
    }

    @Override
    public String contextName() {
        return contextName;
    }
}
