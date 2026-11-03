/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import java.util.Locale;
import java.util.Set;

/**
 * {@code ConvertCase} modes. {@code snake} and {@code camel} follow
 * {@code github.com/iancoleman/strcase}, which is what the collector calls. {@code camel} is
 * PascalCase: {@code my_metric} becomes {@code MyMetric}.
 */
final class CaseStyles {

    static final Set<String> NAMES = Set.of("lower", "upper", "snake", "camel");

    private CaseStyles() {
    }

    static String convert(String value, String toCase) {
        if (value.isEmpty()) {
            return value;
        }
        return switch (toCase) {
            case "lower" -> value.toLowerCase(Locale.ROOT);
            case "upper" -> value.toUpperCase(Locale.ROOT);
            case "snake" -> toSnake(value);
            case "camel" -> toCamel(value);
            default -> throw new IllegalArgumentException(toCase);
        };
    }

    /** ASCII port of {@code strcase.ToDelimited}. Bytes outside ASCII letters pass through. */
    static String toSnake(String value) {
        String s = value.trim();
        StringBuilder out = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i++) {
            char original = s.charAt(i);
            boolean vIsCap = original >= 'A' && original <= 'Z';
            boolean vIsLow = original >= 'a' && original <= 'z';
            boolean vIsNum = original >= '0' && original <= '9';
            char v = original;
            if (vIsCap) {
                v = (char) (original - 'A' + 'a');
            }
            if (i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                boolean nextIsCap = next >= 'A' && next <= 'Z';
                boolean nextIsLow = next >= 'a' && next <= 'z';
                boolean nextIsNum = next >= '0' && next <= '9';
                if ((vIsCap && (nextIsLow || nextIsNum))
                        || (vIsLow && (nextIsCap || nextIsNum))
                        || (vIsNum && (nextIsCap || nextIsLow))) {
                    if (vIsCap && nextIsLow && i > 0) {
                        char prev = s.charAt(i - 1);
                        if (prev >= 'A' && prev <= 'Z') {
                            out.append('_');
                        }
                    }
                    out.append(v);
                    if (vIsLow || vIsNum || nextIsNum) {
                        out.append('_');
                    }
                    continue;
                }
            }
            if (original == ' ' || original == '_' || original == '-' || original == '.') {
                out.append('_');
            } else {
                out.append(v);
            }
        }
        return out.toString();
    }

    /** ASCII port of {@code strcase.ToCamel}. */
    static String toCamel(String value) {
        String s = value.trim();
        if (s.isEmpty()) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        boolean capNext = true;
        boolean prevIsCap = false;
        for (int i = 0; i < s.length(); i++) {
            char original = s.charAt(i);
            boolean vIsCap = original >= 'A' && original <= 'Z';
            boolean vIsLow = original >= 'a' && original <= 'z';
            char v = original;
            if (capNext) {
                if (vIsLow) {
                    v = (char) (original - 'a' + 'A');
                }
            } else if (i == 0) {
                if (vIsCap) {
                    v = (char) (original - 'A' + 'a');
                }
            } else if (prevIsCap && vIsCap) {
                v = (char) (original - 'A' + 'a');
            }
            prevIsCap = vIsCap;
            if (vIsCap || vIsLow) {
                out.append(v);
                capNext = false;
            } else if (original >= '0' && original <= '9') {
                out.append(original);
                capNext = true;
            } else {
                capNext = original == '_' || original == ' ' || original == '-' || original == '.';
            }
        }
        return out.toString();
    }
}
