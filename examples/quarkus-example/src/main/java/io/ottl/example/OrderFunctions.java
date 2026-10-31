/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.quarkus.OttlFunction;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;

/** Java-specific OTTL functions contributed by the application via {@link OttlFunction}. */
@ApplicationScoped
public class OrderFunctions {

    private static final BigDecimal THRESHOLD = new BigDecimal("10000");

    @OttlFunction("IsHighValue")
    public boolean isHighValue(BigDecimal amount) {
        return amount != null && amount.compareTo(THRESHOLD) > 0;
    }
}
