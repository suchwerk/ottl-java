/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment.it;

import io.ottl.quarkus.OttlAccessible;

@OttlAccessible
public class AmbiguousCustomer {
    public void setAmount(long value) {
    }

    public void setAmount(String value) {
    }
}
