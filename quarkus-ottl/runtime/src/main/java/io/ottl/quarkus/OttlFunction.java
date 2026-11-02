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
 * <p>Only annotated methods are exposed to rule authors. The method must be a public instance method
 * on a public concrete CDI bean. The name must be an OTTL identifier and must not collide with
 * another function or with the built-ins {@code set}, {@code delete_key} and {@code keep_keys}.
 * Static methods, generic methods, array parameters and final methods on normal-scoped beans are
 * rejected at build time. Host classes that declare no scope are registered as {@code @Singleton}.
 * The generated invoker calls the method directly; argument coercion matches
 * {@link FunctionArguments}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OttlFunction {

    /** The name used to call this function in OTTL source. Defaults to the method name. */
    String value() default "";
}
