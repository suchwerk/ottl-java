/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.rules;

import java.util.Optional;

/**
 * Manages compiled rules for a single root type {@code T} and supports replacing them at runtime
 * without restarting the application.
 *
 * <p>Implementations must guarantee: validation before activation, atomic replacement of the active
 * program, retention of the previous version when compilation fails, and thread-safe concurrent
 * reads during updates.
 *
 * @param <T> the root/context type the managed rules operate on
 */
public interface RuleManager<T> {

    /**
     * Validate, compile and atomically activate {@code source} as the active program for {@code id}.
     *
     * @throws io.ottl.OttlCompilationException if the source is invalid; the previously active
     *     version (if any) is retained unchanged.
     */
    void activate(String id, String source);

    /** Apply the active rule for {@code id} to {@code input}. */
    T apply(String id, T input);

    /** @return the active definition (id, source, version) for {@code id}, if any. */
    Optional<RuleDefinition> active(String id);
}
