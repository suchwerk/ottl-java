/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus;

import io.ottl.OttlEvaluationException;
import io.ottl.function.Argument;
import io.ottl.function.ExecutionContext;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Adapts a {@link OttlFunction}-annotated bean method to the engine's
 * {@link io.ottl.function.OttlFunction} SPI. Arguments are evaluated and lightly coerced to the
 * declared parameter types; only this one method is ever invoked.
 */
public final class MethodFunctionAdapter implements io.ottl.function.OttlFunction {

    private final String name;
    private final Object bean;
    private final Method method;
    private final Class<?>[] parameterTypes;

    public MethodFunctionAdapter(String name, Object bean, Method method) {
        this.name = name;
        this.bean = bean;
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
        this.method.setAccessible(true);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int minArgs() {
        return parameterTypes.length;
    }

    @Override
    public int maxArgs() {
        return parameterTypes.length;
    }

    @Override
    public Object invoke(ExecutionContext ctx, List<Argument> args) {
        Object[] params = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            params[i] = coerce(args.get(i).value(), parameterTypes[i]);
        }
        try {
            return method.invoke(bean, params);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new OttlEvaluationException("Function '" + name + "' failed: " + cause.getMessage(), cause);
        }
    }

    private static Object coerce(Object value, Class<?> target) {
        if (value == null || target.isInstance(value) || target == Object.class) {
            return value;
        }
        if (value instanceof Number n) {
            if (target == int.class || target == Integer.class) return n.intValue();
            if (target == long.class || target == Long.class) return n.longValue();
            if (target == double.class || target == Double.class) return n.doubleValue();
            if (target == float.class || target == Float.class) return n.floatValue();
        }
        if ((target == boolean.class || target == Boolean.class) && value instanceof Boolean b) {
            return b;
        }
        if (target == String.class) {
            return value.toString();
        }
        return value;
    }
}
