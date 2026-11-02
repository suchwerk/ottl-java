/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java domain type (record, POJO/JavaBean) as accessible to OTTL rules.
 *
 * <p>At Quarkus build time the extension discovers these types, inspects their permitted properties
 * and generates native-image-safe {@link io.ottl.spi.PropertyAccessor}s for them — so OTTL rules can
 * read and write their properties <strong>without runtime reflection</strong>. Types that are not
 * annotated are not reachable from rules (rules referencing them are rejected at compile time in
 * strict mode).
 *
 * <pre>{@code
 * @OttlAccessible
 * public record Order(String id, BigDecimal amount, String status, String priority) {}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface OttlAccessible {
}
