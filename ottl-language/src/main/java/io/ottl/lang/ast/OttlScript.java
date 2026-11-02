/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

import java.util.List;

/**
 * A parsed OTTL script: an ordered list of {@link ScriptItem}s (editor statements and
 * {@code where} blocks) executed sequentially.
 */
public record OttlScript(List<ScriptItem> statements) {
    public OttlScript {
        statements = List.copyOf(statements);
    }
}
