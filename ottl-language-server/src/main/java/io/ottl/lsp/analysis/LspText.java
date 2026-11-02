/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp.analysis;

import io.ottl.lang.parser.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/** Conversions between OTTL token positions (1-based line/col) and LSP positions (0-based). */
public final class LspText {

    private LspText() {
    }

    /** Convert an LSP {@link Position} to a 0-based character offset in {@code text}. */
    public static int offsetAt(String text, Position pos) {
        int line = 0;
        int offset = 0;
        int len = text.length();
        while (offset < len && line < pos.getLine()) {
            if (text.charAt(offset) == '\n') {
                line++;
            }
            offset++;
        }
        return Math.min(offset + pos.getCharacter(), len);
    }

    /** Convert a 0-based character offset to an LSP {@link Position}. */
    public static Position positionAt(String text, int offset) {
        int line = 0;
        int col = 0;
        int bound = Math.min(offset, text.length());
        for (int i = 0; i < bound; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        return new Position(line, col);
    }

    /** The source range covered by {@code token} (identifiers/symbols are exact; strings add quotes). */
    public static Range tokenRange(Token token) {
        int line = Math.max(0, token.line() - 1);
        int startChar = Math.max(0, token.column() - 1);
        int length = switch (token.type()) {
            case STRING -> token.text().length() + 2; // approximate: include surrounding quotes
            case EOF -> 1;
            default -> Math.max(1, token.text().length());
        };
        return new Range(new Position(line, startChar), new Position(line, startChar + length));
    }

    /** A single-character range at a 1-based line/column (used for lexer/parser errors). */
    public static Range pointRange(int line1, int col1, int length) {
        int line = Math.max(0, line1 - 1);
        int startChar = Math.max(0, col1 - 1);
        return new Range(new Position(line, startChar), new Position(line, startChar + Math.max(1, length)));
    }
}
