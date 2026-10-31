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
 */
@ConfigMapping(prefix = "quarkus.ottl")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface OttlConfig {

    /** The engine profile: {@code STANDARD} or {@code JAVA}. */
    @WithDefault("JAVA")
    OttlProfile profile();

    /** Maximum OTTL source length accepted by the engine. */
    @WithDefault("10000")
    int maxSourceLength();

    /** Maximum number of statements in a single rule. */
    @WithDefault("100")
    int maxStatements();
}
