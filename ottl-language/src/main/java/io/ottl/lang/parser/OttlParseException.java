/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.parser;

/**
 * Thrown when OTTL source cannot be tokenized or parsed. Carries a 1-based line/column so
 * callers can produce clear, position-aware diagnostics.
 */
public class OttlParseException extends RuntimeException {

    private final int line;
    private final int column;

    public OttlParseException(String message, int line, int column) {
        super("[" + line + ":" + column + "] " + message);
        this.line = line;
        this.column = column;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}
