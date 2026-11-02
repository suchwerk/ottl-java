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
 * Marks a Java domain type (record, POJO/JavaBean, or enum) as accessible to OTTL rules.
 *
 * <p>At Quarkus build time the extension discovers these types and generates native-image-safe
 * accessors for classes and records. A public enum is not a context and gets no accessor or rule
 * manager: its constants become values a rule can name, as in {@code OrderStatus.PENDING}, and the
 * editor can complete them. Nested types are accessible only when every enclosing type is public.
 * Interfaces, abstract classes and non-public types fail the build. Properties follow JavaBeans
 * ({@code get}/{@code is}/{@code set}, with {@link java.beans.Introspector#decapitalize(String)}) and
 * record components. Overloaded setters and getter/setter type mismatches fail the build. Static
 * methods are not properties. Non-public accessors are ignored.
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
