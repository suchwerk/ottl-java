/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.function;

import java.util.List;

/**
 * Optional, tooling-facing self-description for an {@link OttlFunction}. Implementing it lets the
 * Language Server surface parameter types and documentation for completion, hover and signature
 * help. It carries <em>no</em> execution semantics and is ignored by the engine at runtime.
 */
public interface DescribedFunction {

    /** Short human-readable documentation shown in completion/hover. */
    default String documentation() {
        return "";
    }

    /** Parameter names, in order (may be empty if unknown). */
    default List<String> parameterNames() {
        return List.of();
    }

    /** Parameter type <em>simple</em> names, in order (may be empty if unknown). */
    default List<String> parameterTypeNames() {
        return List.of();
    }
}
