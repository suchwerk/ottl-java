/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus;

import java.util.List;

/**
 * Build-time-discovered description of an {@link OttlFunction}-annotated method. The deployment
 * module validates the method and generates a direct invoker; this bean is not consulted at runtime.
 *
 * <p>Modeled as a mutable JavaBean (no-arg constructor + getters/setters) so Quarkus can record it
 * into bytecode without requiring {@code -parameters} metadata.
 */
public final class FunctionMetadata {

    private String functionName;
    private String declaringClass;
    private String methodName;
    private List<String> parameterTypes;

    public FunctionMetadata() {
    }

    public FunctionMetadata(String functionName, String declaringClass, String methodName,
                            List<String> parameterTypes) {
        this.functionName = functionName;
        this.declaringClass = declaringClass;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

    public void setDeclaringClass(String declaringClass) {
        this.declaringClass = declaringClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(List<String> parameterTypes) {
        this.parameterTypes = parameterTypes;
    }
}
