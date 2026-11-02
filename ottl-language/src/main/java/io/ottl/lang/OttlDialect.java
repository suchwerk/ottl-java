/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang;

/**
 * Selects which language syntax a parser or engine accepts.
 *
 * <p>This is independent of {@code OttlProfile}. A profile chooses data shapes (maps versus Java
 * objects). A dialect chooses syntax. The default is {@link #OTTL} so existing programs keep their
 * current behavior.
 *
 * <ul>
 *   <li>{@link #OTTL} — supported OTTL statements, including a trailing {@code where} condition and
 *       the existing ottl-java {@code where condition { ... }} block. Bare blocks and
 *       {@code if}/{@code else} are rejected.</li>
 *   <li>{@link #EXTENDED} — everything in {@link #OTTL}, plus statement blocks and
 *       {@code if}/{@code else if}/{@code else}. This is an ottl-java language extension. It is not
 *       OpenTelemetry OTTL.</li>
 * </ul>
 *
 * <p>{@code where} blocks stay valid in {@link #OTTL} because they already shipped. Moving them
 * behind {@link #EXTENDED} would reject programs that parse today. They have the same execution
 * shape as an {@code if} without {@code else}: the condition runs once, then the body runs against
 * the same root. They remain a distinct AST node so existing {@code WhereBlock} consumers stay
 * compatible.
 */
public enum OttlDialect {
    OTTL,
    EXTENDED
}
