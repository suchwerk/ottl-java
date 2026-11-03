/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.parser;

/** The lexical token categories of OTTL. */
public enum TokenType {
    IDENT,
    STRING,
    INT,
    FLOAT,
    LPAREN,
    RPAREN,
    LBRACKET,
    RBRACKET,
    LBRACE,
    RBRACE,
    COMMA,
    DOT,
    COLON,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    ASSIGN,
    ARROW,
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE,
    BYTES,
    EOF
}
