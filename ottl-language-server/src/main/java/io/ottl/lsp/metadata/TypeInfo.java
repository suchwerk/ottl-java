/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp.metadata;

import java.util.List;

/** Editor-facing metadata for an accessible context type (e.g. {@code Order}). */
public record TypeInfo(String simpleName, String fqn, List<PropertyInfo> properties) {

    public PropertyInfo property(String name) {
        for (PropertyInfo p : properties) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        return null;
    }
}
