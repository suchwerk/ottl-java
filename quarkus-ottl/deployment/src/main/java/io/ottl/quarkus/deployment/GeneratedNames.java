/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

/**
 * Injective class-name encoding for generated accessors and function invokers.
 *
 * <p>{@code Class.getName()} is unique, but replacing {@code '.'} and {@code '$'} with {@code '_'}
 * is not: {@code a.b.Foo_Bar} and {@code a.b.Foo$Bar} would collide. {@link #encode(String)} escapes
 * {@code '_'}, {@code '.'} and {@code '$'} so distinct binary names stay distinct.
 */
final class GeneratedNames {

    static final String INSTALLER_INTERNAL = "io.ottl.quarkus.functions";

    private GeneratedNames() {
    }

    static String accessorClass(String binaryName) {
        return "io.ottl.quarkus.accessors.A_" + encode(binaryName);
    }

    static String invokerClass(String declaringBinaryName, String methodName, String functionName) {
        return INSTALLER_INTERNAL + ".F_" + encode(declaringBinaryName + "#" + methodName + "#" + functionName);
    }

    static String encode(String value) {
        StringBuilder sb = new StringBuilder(value.length() * 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '.' -> sb.append("_D");
                case '$' -> sb.append("_N");
                case '_' -> sb.append("__");
                default -> {
                    if (c <= 0x7f && Character.isJavaIdentifierPart(c)) {
                        sb.append(c);
                    } else {
                        sb.append("_U");
                        sb.append(String.format("%04x", (int) c));
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Inverse of {@link #encode(String)}, used by tests to prove the encoding is injective. */
    static String decode(String encoded) {
        StringBuilder sb = new StringBuilder(encoded.length());
        for (int i = 0; i < encoded.length();) {
            char c = encoded.charAt(i);
            if (c != '_') {
                sb.append(c);
                i++;
                continue;
            }
            if (i + 1 >= encoded.length()) {
                throw new IllegalArgumentException("Truncated encoding: " + encoded);
            }
            switch (encoded.charAt(i + 1)) {
                case '_' -> {
                    sb.append('_');
                    i += 2;
                }
                case 'D' -> {
                    sb.append('.');
                    i += 2;
                }
                case 'N' -> {
                    sb.append('$');
                    i += 2;
                }
                case 'U' -> {
                    if (i + 6 > encoded.length()) {
                        throw new IllegalArgumentException("Truncated code point in " + encoded);
                    }
                    sb.append((char) Integer.parseInt(encoded.substring(i + 2, i + 6), 16));
                    i += 6;
                }
                default -> throw new IllegalArgumentException("Bad escape in " + encoded);
            }
        }
        return sb.toString();
    }
}
