/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All-occurrence replacement with Go's {@code regexp.Expand} rules: {@code $$} is a literal
 * {@code $}, {@code $1} and {@code ${name}} are groups, and a missing group expands to empty.
 */
final class RegexReplace {

    private RegexReplace() {
    }

    static String replaceAll(Pattern pattern, String input, String replacement) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder out = new StringBuilder();
        int last = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            out.append(input, last, matcher.start());
            out.append(expand(replacement, matcher));
            last = matcher.end();
        }
        if (!found) {
            return input;
        }
        out.append(input, last, input.length());
        return out.toString();
    }

    private static String expand(String template, Matcher matcher) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c != '$' || i + 1 >= template.length()) {
                out.append(c);
                continue;
            }
            if (template.charAt(i + 1) == '$') {
                out.append('$');
                i++;
                continue;
            }
            int consumed = readGroup(template, i + 1, matcher, out);
            if (consumed == 0) {
                out.append('$');
            } else {
                i += consumed;
            }
        }
        return out.toString();
    }

    /** @return how many characters of {@code template}, starting at {@code from}, belonged to the group */
    private static int readGroup(String template, int from, Matcher matcher, StringBuilder out) {
        if (template.charAt(from) == '{') {
            int close = template.indexOf('}', from + 1);
            if (close < 0) {
                return 0;
            }
            appendGroup(out, matcher, template.substring(from + 1, close));
            return close - from + 1;
        }
        int i = from;
        if (isDigit(template.charAt(i))) {
            while (i < template.length() && isDigit(template.charAt(i))) {
                i++;
            }
            appendGroup(out, matcher, template.substring(from, i));
            return i - from;
        }
        if (isNameStart(template.charAt(i))) {
            while (i < template.length() && isNamePart(template.charAt(i))) {
                i++;
            }
            appendGroup(out, matcher, template.substring(from, i));
            return i - from;
        }
        return 0;
    }

    private static void appendGroup(StringBuilder out, Matcher matcher, String name) {
        try {
            String value = isDigits(name) ? matcher.group(Integer.parseInt(name)) : matcher.group(name);
            if (value != null) {
                out.append(value);
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {
            // A missing group expands to nothing, matching regexp.Expand.
        }
    }

    private static boolean isDigits(String name) {
        for (int i = 0; i < name.length(); i++) {
            if (!isDigit(name.charAt(i))) {
                return false;
            }
        }
        return !name.isEmpty();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isNameStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    private static boolean isNamePart(char c) {
        return isNameStart(c) || isDigit(c);
    }
}
