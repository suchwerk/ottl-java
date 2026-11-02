/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus;

import io.ottl.OttlProfile;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the OTTL extension, bound under {@code quarkus.ottl.*}.
 *
 * <p>Strict property and function validation stays enabled. It is intentionally not a config key:
 * rule authors are less trusted than the application, and turning validation off would let rules
 * refer to types that were not prepared at build time.
 *
 * <p>Path depth and function-call nesting stay at the engine defaults ({@code 16} and {@code 16}).
 */
@ConfigMapping(prefix = "quarkus.ottl")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface OttlConfig {

    /** The engine profile: {@code STANDARD} or {@code JAVA}. */
    @WithDefault("JAVA")
    OttlProfile profile();

    /**
     * Maximum OTTL source length accepted by the engine. Must be between 1 and 1000000.
     * Values outside that range fail application startup.
     */
    @WithDefault("10000")
    int maxSourceLength();

    /**
     * Maximum number of statements in a single rule. Must be between 1 and 10000.
     * Values outside that range fail application startup.
     */
    @WithDefault("100")
    int maxStatements();
}
