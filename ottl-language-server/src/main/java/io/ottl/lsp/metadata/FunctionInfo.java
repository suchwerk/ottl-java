/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp.metadata;

import java.util.List;

/** Editor-facing metadata for an allowlisted OTTL function. */
public record FunctionInfo(
        String name,
        int minArgs,
        int maxArgs,
        List<ParamInfo> parameters,
        String documentation,
        String category) {

    /** A single function parameter. */
    public record ParamInfo(String name, String typeSimpleName) {
    }

    /** Render a human-readable signature, e.g. {@code set(target, value)}. */
    public String signatureLabel() {
        StringBuilder sb = new StringBuilder(name).append('(');
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            ParamInfo p = parameters.get(i);
            if (p.typeSimpleName() != null && !p.typeSimpleName().isBlank()) {
                sb.append(p.typeSimpleName()).append(' ');
            }
            sb.append(p.name());
        }
        return sb.append(')').toString();
    }
}
