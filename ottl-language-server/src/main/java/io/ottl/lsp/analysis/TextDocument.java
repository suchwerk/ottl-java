/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp.analysis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-URI document state cached by the server. Immutable snapshots are cheap to create on each
 * change so stale versions never leak into diagnostics for a newer version.
 */
public final class TextDocument {

    /** A rule file may declare its Java context with a comment directive: {@code # context: Order}. */
    private static final Pattern CONTEXT_DIRECTIVE =
            Pattern.compile("(?m)^\\s*#\\s*context\\s*:\\s*([A-Za-z_][A-Za-z0-9_.]*)\\s*$");

    private final String uri;
    private final String text;
    private final int version;

    public TextDocument(String uri, String text, int version) {
        this.uri = uri;
        this.text = text == null ? "" : text;
        this.version = version;
    }

    public String uri() {
        return uri;
    }

    public String text() {
        return text;
    }

    public int version() {
        return version;
    }

    /** The context type declared via a {@code # context: X} directive, or {@code null} if absent. */
    public String declaredContext() {
        Matcher m = CONTEXT_DIRECTIVE.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
