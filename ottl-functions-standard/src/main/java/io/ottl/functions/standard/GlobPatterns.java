/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.OttlCompilationException;
import io.ottl.OttlEvaluationException;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Glob syntax used by {@code replace_match} and {@code replace_all_matches}.
 *
 * <p>The collector compiles these patterns with {@code github.com/gobwas/glob} and no separators,
 * so {@code *} and {@code ?} match {@code /}. The README still mentions {@code filepath.Match},
 * whose {@code *} does not cross {@code /}. This class follows the compiler.
 */
final class GlobPatterns {

    private GlobPatterns() {
    }

    static Pattern compile(String pattern, boolean atCompileTime) {
        try {
            return Pattern.compile(toRegex(pattern));
        } catch (OttlEvaluationException | PatternSyntaxException e) {
            String message = "invalid glob pattern '" + pattern + "'";
            if (atCompileTime) {
                throw new OttlCompilationException(message, e);
            }
            throw new OttlEvaluationException(message, e);
        }
    }

    static boolean matches(Pattern pattern, String text) {
        return pattern.matcher(text).matches();
    }

    private static String toRegex(String pattern) {
        StringBuilder out = new StringBuilder(pattern.length() + 8);
        out.append("(?s)^");
        translate(pattern, 0, pattern.length(), out);
        out.append('$');
        return out.toString();
    }

    private static void translate(String pattern, int start, int end, StringBuilder out) {
        int i = start;
        while (i < end) {
            char c = pattern.charAt(i);
            switch (c) {
                case '\\' -> {
                    if (i + 1 >= end) {
                        throw bad("trailing escape");
                    }
                    out.append(Pattern.quote(String.valueOf(pattern.charAt(i + 1))));
                    i += 2;
                }
                case '*' -> {
                    while (i < end && pattern.charAt(i) == '*') {
                        i++;
                    }
                    out.append(".*");
                }
                case '?' -> {
                    out.append('.');
                    i++;
                }
                case '[' -> i = translateClass(pattern, i, end, out);
                case '{' -> i = translateAlt(pattern, i, end, out);
                default -> {
                    out.append(Pattern.quote(String.valueOf(c)));
                    i++;
                }
            }
        }
    }

    private static int translateClass(String pattern, int open, int end, StringBuilder out) {
        int i = open + 1;
        if (i >= end) {
            throw bad("unclosed '['");
        }
        boolean negate = pattern.charAt(i) == '!';
        if (negate) {
            i++;
        }
        StringBuilder body = new StringBuilder();
        if (i < end && pattern.charAt(i) == ']') {
            body.append(']');
            i++;
        }
        boolean closed = false;
        while (i < end) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                if (i + 1 >= end) {
                    throw bad("trailing escape");
                }
                appendClassChar(body, pattern.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == ']') {
                closed = true;
                i++;
                break;
            }
            appendClassChar(body, c);
            i++;
        }
        if (!closed || body.isEmpty()) {
            throw bad("unclosed '['");
        }
        out.append('[');
        if (negate) {
            out.append('^');
        }
        out.append(body).append(']');
        return i;
    }

    private static void appendClassChar(StringBuilder body, char c) {
        if (c == '\\' || c == '&') {
            body.append('\\');
        }
        body.append(c);
    }

    private static int translateAlt(String pattern, int open, int end, StringBuilder out) {
        int i = open + 1;
        out.append("(?:");
        boolean first = true;
        while (i < end) {
            int altStart = i;
            int braces = 0;
            boolean inClass = false;
            while (i < end) {
                char c = pattern.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '[') {
                    inClass = true;
                    i++;
                    continue;
                }
                if (c == ']' && inClass) {
                    inClass = false;
                    i++;
                    continue;
                }
                if (!inClass && c == '{') {
                    braces++;
                    i++;
                    continue;
                }
                if (!inClass && c == '}' && braces > 0) {
                    braces--;
                    i++;
                    continue;
                }
                if (!inClass && braces == 0 && (c == ',' || c == '}')) {
                    break;
                }
                i++;
            }
            if (i >= end) {
                throw bad("unclosed '{'");
            }
            if (!first) {
                out.append('|');
            }
            translate(pattern, altStart, i, out);
            first = false;
            if (pattern.charAt(i) == '}') {
                out.append(')');
                return i + 1;
            }
            i++;
        }
        throw bad("unclosed '{'");
    }

    private static OttlEvaluationException bad(String reason) {
        return new OttlEvaluationException(reason);
    }
}
