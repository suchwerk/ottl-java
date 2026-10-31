/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.ast;

import java.util.List;

/**
 * A parsed OTTL script: an ordered list of {@link Statement}s executed sequentially.
 */
public record OttlScript(List<Statement> statements) {
    public OttlScript {
        statements = List.copyOf(statements);
    }
}
