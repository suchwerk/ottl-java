/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.parser;

/**
 * A lexical token with its source position.
 *
 * @param type   the token category
 * @param text   the raw source text (for STRING, the decoded value)
 * @param line   1-based line
 * @param column 1-based column
 */
public record Token(TokenType type, String text, int line, int column) {
}
