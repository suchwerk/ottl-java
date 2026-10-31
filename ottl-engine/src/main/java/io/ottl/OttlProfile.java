/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl;

/**
 * Selects the behavioral profile of the engine.
 *
 * <ul>
 *   <li>{@link #STANDARD} — OTTL-compatible behavior intended to match the reference language.</li>
 *   <li>{@link #JAVA} — the STANDARD behavior plus Java-specific object access (POJOs, records,
 *       beans) and Java-only functions/annotations.</li>
 * </ul>
 */
public enum OttlProfile {
    STANDARD,
    JAVA
}
