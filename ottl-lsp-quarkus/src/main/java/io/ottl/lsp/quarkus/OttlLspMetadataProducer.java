/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp.quarkus;

import io.ottl.OttlEngine;
import io.ottl.lsp.metadata.OttlMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Builds the shared {@link OttlMetadata} from the running {@link OttlEngine}. The Language Server and
 * the engine therefore use the <strong>same</strong> accessor and function registries — editor
 * suggestions can never drift from what the engine accepts, and only approved types/functions are
 * exposed.
 */
@ApplicationScoped
public class OttlLspMetadataProducer {

    @Produces
    @Singleton
    public OttlMetadata ottlMetadata(OttlEngine engine) {
        return new OttlMetadata(engine.accessorRegistry(), engine.functions(), engine.enumRegistry());
    }
}
