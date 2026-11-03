/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import io.ottl.OttlEvaluationException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Conversions shared by the standard converters. String and JSON formatting follow the collector's
 * {@code StringLikeGetter} and {@code encoding/json} closely enough for the documented cases.
 * Number text uses {@link Double#toString(double)}, so a few values differ in the last digit from
 * Go's {@code strconv}.
 */
final class StandardValues {

    private static final int MAX_JSON_DEPTH = 64;

    private StandardValues() {
    }

    static String requireString(Object value, String what) {
        if (value instanceof String s) {
            return s;
        }
        String got = value == null ? "nil" : value.getClass().getSimpleName();
        throw new OttlEvaluationException(what + " must be a string but got " + got);
    }

    static boolean requireBool(Object value, String name) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw new OttlEvaluationException(name + " must be a bool");
    }

    /** An OTTL int: a Java integral value, not a float. */
    static long requireLong(Object value, String name) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger bi) {
            try {
                return bi.longValueExact();
            } catch (ArithmeticException e) {
                throw new OttlEvaluationException(name + " is out of int64 range", e);
            }
        }
        throw new OttlEvaluationException(name + " must be an int");
    }

    static List<?> requireList(Object value, String function) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new OttlEvaluationException(function + ": expected a list");
    }

    static long length(Object value) {
        if (value instanceof String s) {
            return utf8Length(s);
        }
        if (value instanceof byte[] bytes) {
            return bytes.length;
        }
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        if (value != null && value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value);
        }
        throw new OttlEvaluationException("Len: unsupported type " + typeName(value)
                + ". target must be a string, list, map, or byte slice");
    }

    static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Collector {@code StringLikeGetter}: strings pass through, byte slices are lowercase hex,
     * and everything else is JSON. Nil is not a string.
     */
    static String stringLike(Object value) {
        if (value == null) {
            throw new OttlEvaluationException("cannot convert nil to a string");
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof byte[] bytes) {
            return HexFormat.of().formatHex(bytes);
        }
        return json(value);
    }

    /** Text {@code IsMatch} compares. Byte slices are standard Base64, unlike {@link #stringLike}. */
    static String matchText(Object value) {
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Boolean b) {
            return Boolean.toString(b);
        }
        if (value instanceof BigInteger bi) {
            return bi.toString();
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Float || value instanceof Double) {
            return formatPlainDouble(((Number) value).doubleValue());
        }
        if (value instanceof Number n) {
            return Long.toString(n.longValue());
        }
        if (value instanceof Map<?, ?> || value instanceof List<?> || isArray(value)) {
            return json(value);
        }
        throw new OttlEvaluationException("IsMatch: unsupported type " + typeName(value));
    }

    static boolean matches(String text, String pattern) {
        try {
            return Pattern.compile(pattern).matcher(text).find();
        } catch (PatternSyntaxException e) {
            throw new OttlEvaluationException("IsMatch: invalid pattern", e);
        }
    }

    static List<String> split(String target, String delimiter) {
        if (delimiter.isEmpty()) {
            if (target.isEmpty()) {
                return List.of();
            }
            List<String> parts = new ArrayList<>();
            target.codePoints().forEach(cp -> parts.add(Character.toString(cp)));
            return List.copyOf(parts);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        int found;
        while ((found = target.indexOf(delimiter, start)) >= 0) {
            parts.add(target.substring(start, found));
            start = found + delimiter.length();
        }
        parts.add(target.substring(start));
        return List.copyOf(parts);
    }

    /** {@code strings.Trim}: {@code cutset} is a set of characters, not a prefix. */
    static String trim(String target, String cutset) {
        int start = 0;
        int end = target.length();
        while (start < end && cutsetContains(cutset, target.codePointAt(start))) {
            start += Character.charCount(target.codePointAt(start));
        }
        while (end > start) {
            int cp = target.codePointBefore(end);
            if (!cutsetContains(cutset, cp)) {
                break;
            }
            end -= Character.charCount(cp);
        }
        return target.substring(start, end);
    }

    /**
     * Byte window into the UTF-8 encoding of {@code target}. When {@code utf8Safe} is set, a
     * mid-character start moves forward to the next character and a mid-character end moves back
     * to the previous one, matching {@code utf8.RuneStart}.
     */
    static String substring(String target, long start, long length, boolean utf8Safe) {
        if (start < 0) {
            throw new OttlEvaluationException(
                    "invalid start for substring function, " + start + " cannot be negative");
        }
        if (length <= 0) {
            throw new OttlEvaluationException(
                    "invalid length for substring function, " + length + " cannot be negative or zero");
        }
        byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
        if (start > bytes.length || length > bytes.length - start) {
            throw new OttlEvaluationException(
                    "invalid range for substring function, start(" + start + ")+length(" + length
                            + ") cannot be greater than the length of target string(" + bytes.length + ")");
        }
        int byteStart = (int) start;
        int byteEnd = (int) (start + length);
        if (utf8Safe) {
            while (byteStart < bytes.length && !isRuneStart(bytes[byteStart])) {
                byteStart++;
            }
            while (byteStart < byteEnd && byteEnd < bytes.length && !isRuneStart(bytes[byteEnd])) {
                byteEnd--;
            }
            if (byteEnd < byteStart) {
                byteEnd = byteStart;
            }
        }
        return new String(bytes, byteStart, byteEnd - byteStart, StandardCharsets.UTF_8);
    }

    static Long toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b ? 1L : 0L;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger bi) {
            try {
                return bi.longValueExact();
            } catch (ArithmeticException e) {
                throw new OttlEvaluationException("Int: value is out of int64 range", e);
            }
        }
        if (value instanceof Float || value instanceof Double) {
            double d = ((Number) value).doubleValue();
            if (!Double.isFinite(d) || d >= 9223372036854775808.0d || d < -9223372036854775808.0d) {
                throw new OttlEvaluationException("Int: value is out of int64 range");
            }
            return (long) d;
        }
        if (value instanceof BigDecimal bd) {
            try {
                return bd.setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            } catch (ArithmeticException e) {
                throw new OttlEvaluationException("Int: value is out of int64 range", e);
            }
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                throw new OttlEvaluationException("Int: cannot parse \"" + s + "\" as an int", e);
            }
        }
        throw new OttlEvaluationException("Int: unsupported type " + value.getClass().getSimpleName());
    }

    static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Float || value instanceof Double) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Boolean b) {
            return b ? 1.0d : 0.0d;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof BigInteger bi) {
            double d = bi.doubleValue();
            if (!Double.isFinite(d)) {
                throw new OttlEvaluationException("Double: value is out of float64 range");
            }
            return d;
        }
        if (value instanceof BigDecimal bd) {
            double d = bd.doubleValue();
            if (!Double.isFinite(d)) {
                throw new OttlEvaluationException("Double: value is out of float64 range");
            }
            return d;
        }
        if (value instanceof String s) {
            try {
                return parseFloat(s);
            } catch (NumberFormatException e) {
                throw new OttlEvaluationException("Double: cannot parse \"" + s + "\" as a float", e);
            }
        }
        throw new OttlEvaluationException("Double: unsupported type " + value.getClass().getSimpleName());
    }

    static String json(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, value, new IdentityHashMap<>(), 0);
        return sb.toString();
    }

    private static void writeJson(StringBuilder sb, Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String s) {
            writeJsonString(sb, s);
            return;
        }
        if (value instanceof Boolean b) {
            sb.append(b);
            return;
        }
        if (value instanceof byte[] bytes) {
            writeJsonString(sb, Base64.getEncoder().encodeToString(bytes));
            return;
        }
        if (value instanceof Float || value instanceof Double) {
            sb.append(formatJsonDouble(((Number) value).doubleValue()));
            return;
        }
        if (value instanceof BigDecimal bd) {
            sb.append(bd.stripTrailingZeros().toPlainString());
            return;
        }
        if (value instanceof BigInteger bi) {
            sb.append(bi.toString());
            return;
        }
        if (value instanceof Number n) {
            sb.append(n.longValue());
            return;
        }
        if (depth > MAX_JSON_DEPTH) {
            throw new OttlEvaluationException("String: value is nested too deeply");
        }
        if (value instanceof Map<?, ?> map) {
            if (seen.put(map, Boolean.TRUE) != null) {
                throw new OttlEvaluationException("String: cyclic value");
            }
            writeMap(sb, map, seen, depth + 1);
            seen.remove(map);
            return;
        }
        if (value instanceof List<?> list) {
            if (seen.put(list, Boolean.TRUE) != null) {
                throw new OttlEvaluationException("String: cyclic value");
            }
            writeArray(sb, list.size(), i -> list.get(i), seen, depth + 1);
            seen.remove(list);
            return;
        }
        if (isArray(value)) {
            if (seen.put(value, Boolean.TRUE) != null) {
                throw new OttlEvaluationException("String: cyclic value");
            }
            int length = java.lang.reflect.Array.getLength(value);
            writeArray(sb, length, i -> java.lang.reflect.Array.get(value, i), seen, depth + 1);
            seen.remove(value);
            return;
        }
        throw new OttlEvaluationException("String: cannot marshal " + value.getClass().getName());
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map, IdentityHashMap<Object, Boolean> seen, int depth) {
        List<String> keys = new ArrayList<>(map.size());
        for (Object key : map.keySet()) {
            if (!(key instanceof String s)) {
                throw new OttlEvaluationException("String: map key must be a string");
            }
            keys.add(s);
        }
        keys.sort((a, b) -> Arrays.compare(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)));
        sb.append('{');
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String key = keys.get(i);
            writeJsonString(sb, key);
            sb.append(':');
            writeJson(sb, map.get(key), seen, depth);
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, int length, java.util.function.IntFunction<Object> element,
                                   IdentityHashMap<Object, Boolean> seen, int depth) {
        sb.append('[');
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeJson(sb, element.apply(i), seen, depth);
        }
        sb.append(']');
    }

    private static void writeJsonString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            switch (cp) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '&' -> sb.append("\\u0026");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case 0x2028, 0x2029 -> sb.append(String.format(Locale.ROOT, "\\u%04x", cp));
                default -> {
                    if (cp < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", cp));
                    } else {
                        sb.appendCodePoint(cp);
                    }
                }
            }
            i += Character.charCount(cp);
        }
        sb.append('"');
    }

    /**
     * {@code encoding/json} number form: plain decimal inside {@code [1e-6, 1e21)}, scientific
     * outside it. The exponent matches Go's cleanup of {@code e-09} to {@code e-9}.
     */
    static String formatJsonDouble(double d) {
        if (!Double.isFinite(d)) {
            throw new OttlEvaluationException("String: cannot marshal a non-finite number");
        }
        BigDecimal exact = new BigDecimal(Double.toString(d)).stripTrailingZeros();
        double abs = Math.abs(d);
        if (d != 0.0d && (abs < 1e-6d || abs >= 1e21d)) {
            return scientific(exact);
        }
        return exact.toPlainString();
    }

    /** {@code strconv.FormatFloat(f, 'f', -1, 64)}, including {@code +Inf}, {@code -Inf}, and {@code NaN}. */
    static String formatPlainDouble(double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (d == Double.POSITIVE_INFINITY) {
            return "+Inf";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
    }

    private static String scientific(BigDecimal exact) {
        int exponent = exact.precision() - exact.scale() - 1;
        String digits = exact.unscaledValue().abs().toString();
        String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        if (exact.signum() < 0) {
            mantissa = "-" + mantissa;
        }
        String expDigits = Integer.toString(Math.abs(exponent));
        if (expDigits.length() < 2) {
            expDigits = "0" + expDigits;
        }
        if (exponent < 0 && expDigits.charAt(0) == '0') {
            expDigits = expDigits.substring(1);
        }
        return mantissa + "e" + (exponent < 0 ? "-" : "+") + expDigits;
    }

    private static double parseFloat(String value) {
        if (value.equalsIgnoreCase("inf") || value.equalsIgnoreCase("+inf")
                || value.equalsIgnoreCase("infinity") || value.equalsIgnoreCase("+infinity")) {
            return Double.POSITIVE_INFINITY;
        }
        if (value.equalsIgnoreCase("-inf") || value.equalsIgnoreCase("-infinity")) {
            return Double.NEGATIVE_INFINITY;
        }
        if (value.equalsIgnoreCase("nan") || value.equalsIgnoreCase("+nan") || value.equalsIgnoreCase("-nan")) {
            return Double.NaN;
        }
        return Double.parseDouble(value);
    }

    private static boolean cutsetContains(String cutset, int codePoint) {
        return cutset.codePoints().anyMatch(cp -> cp == codePoint);
    }

    private static boolean isRuneStart(byte b) {
        return (b & 0xC0) != 0x80;
    }

    private static boolean isArray(Object value) {
        return value != null && value.getClass().isArray();
    }

    private static String typeName(Object value) {
        return value == null ? "nil" : value.getClass().getSimpleName();
    }
}
