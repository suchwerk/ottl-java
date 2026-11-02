/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import io.ottl.OttlEvaluationException;
import io.ottl.function.Argument;
import io.ottl.function.ExecutionContext;
import io.ottl.quarkus.FunctionArguments;
import io.quarkus.gizmo.CatchBlockCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.gizmo.TryBlock;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Emits a CDI {@code @Singleton} invoker per {@code @OttlFunction} method. The invoker calls the
 * bean method directly. Arc injects the host, including its client proxy when the host is
 * normal-scoped, so {@code Method.invoke} is not used on the execution path.
 */
final class FunctionInvokerGenerator {

    private static final MethodDescriptor COERCE = MethodDescriptor.ofMethod(
            FunctionArguments.class, "coerce", Object.class, Object.class, Class.class);
    private static final MethodDescriptor ARGUMENT_VALUE =
            MethodDescriptor.ofMethod(Argument.class, "value", Object.class);
    private static final MethodDescriptor LIST_GET =
            MethodDescriptor.ofMethod(List.class, "get", Object.class, int.class);

    private FunctionInvokerGenerator() {
    }

    static void generate(List<FunctionDiscovery.DiscoveredFunction> functions, ClassOutput output) {
        for (FunctionDiscovery.DiscoveredFunction function : functions) {
            generateInvoker(function, output);
        }
    }

    private static void generateInvoker(FunctionDiscovery.DiscoveredFunction function, ClassOutput output) {
        Method method = function.method();
        String functionName = function.metadata().getFunctionName();
        Class<?> host = method.getDeclaringClass();
        try (ClassCreator cc = ClassCreator.builder().classOutput(output)
                .className(function.invokerClassName())
                .interfaces(io.ottl.function.OttlFunction.class)
                .build()) {
            cc.addAnnotation(Singleton.class);
            FieldCreator hostField = cc.getFieldCreator("host", host);
            // Package-private so Arc's generated subclass, which lives in the same package, can inject it.
            hostField.setModifiers(0);
            hostField.addAnnotation(Inject.class);
            FieldDescriptor hostDescriptor = hostField.getFieldDescriptor();

            MethodCreator ctor = cc.getMethodCreator("<init>", void.class);
            ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(Object.class), ctor.getThis());
            ctor.returnValue(null);

            MethodCreator name = cc.getMethodCreator("name", String.class);
            name.returnValue(name.load(functionName));
            MethodCreator min = cc.getMethodCreator("minArgs", int.class);
            min.returnValue(min.load(method.getParameterCount()));
            MethodCreator max = cc.getMethodCreator("maxArgs", int.class);
            max.returnValue(max.load(method.getParameterCount()));

            MethodCreator invoke = cc.getMethodCreator(
                    "invoke", Object.class, ExecutionContext.class, List.class);
            TryBlock tb = invoke.tryBlock();
            ResultHandle receiver = tb.readInstanceField(hostDescriptor, tb.getThis());
            ResultHandle receiverTyped = tb.checkCast(receiver, host);
            ResultHandle args = tb.getMethodParam(1);
            ResultHandle[] params = new ResultHandle[method.getParameterCount()];
            for (int i = 0; i < params.length; i++) {
                Class<?> paramType = method.getParameterTypes()[i];
                ResultHandle element = tb.invokeInterfaceMethod(LIST_GET, args, tb.load(i));
                ResultHandle argument = tb.checkCast(element, Argument.class);
                ResultHandle value = tb.invokeVirtualMethod(ARGUMENT_VALUE, argument);
                ResultHandle coerced = tb.invokeStaticMethod(
                        COERCE, value, BytecodeSupport.loadClassConstant(tb, paramType));
                params[i] = BytecodeSupport.convertArg(tb, coerced, paramType);
            }
            if (method.getReturnType() == void.class) {
                tb.invokeVirtualMethod(MethodDescriptor.ofMethod(method), receiverTyped, params);
                tb.returnValue(tb.loadNull());
            } else {
                ResultHandle result = tb.invokeVirtualMethod(MethodDescriptor.ofMethod(method), receiverTyped, params);
                tb.returnValue(BytecodeSupport.box(tb, result, method.getReturnType()));
            }
            CatchBlockCreator caught = tb.addCatch(Exception.class);
            ResultHandle exception = caught.getCaughtException();
            ResultHandle detail = caught.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(Throwable.class, "getMessage", String.class), exception);
            ResultHandle detailText = caught.invokeStaticMethod(
                    MethodDescriptor.ofMethod(String.class, "valueOf", String.class, Object.class), detail);
            ResultHandle message = caught.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(String.class, "concat", String.class, String.class),
                    caught.load("Function '" + functionName + "' failed: "),
                    detailText);
            ResultHandle wrapped = caught.newInstance(
                    MethodDescriptor.ofConstructor(OttlEvaluationException.class, String.class, Throwable.class),
                    message, exception);
            caught.throwException(wrapped);
        }
    }
}
