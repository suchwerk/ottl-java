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
 * Marks a CDI bean method as an OTTL converter function, callable from rule source by the given
 * name:
 *
 * <pre>{@code
 * @ApplicationScoped
 * public class CustomerFunctions {
 *     @OttlFunction("IsPremiumCustomer")
 *     public boolean isPremium(Customer customer) {
 *         return customer.isPremium();
 *     }
 * }
 * }</pre>
 *
 * <p>Only annotated methods are exposed to rule authors; there is no way to invoke an arbitrary Java
 * method from OTTL source. The Quarkus extension discovers these methods at build time and registers
 * them (and the reflection metadata they need) with the engine.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OttlFunction {

    /** The name used to call this function in OTTL source. Defaults to the method name. */
    String value() default "";
}
