/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp.metadata;

/**
 * Editor-facing metadata for a single property of an accessible type. Derived from the same
 * {@code TypeMetadata} the engine uses, so what the editor offers matches what the engine allows.
 */
public record PropertyInfo(
        String name,
        String typeFqn,
        String typeSimpleName,
        boolean readable,
        boolean writable,
        String declaringType) {
}
