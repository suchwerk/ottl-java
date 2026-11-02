/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment.fixt;

/** Flat class whose binary name collides with {@code CollisionFoo$Bar} under underscore encoding. */
public class CollisionFoo_Bar {

    public String getId() {
        return "flat";
    }
}
