/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment.fixt;

/** Nested type whose binary name shares an underscore form with {@link CollisionFoo_Bar}. */
public class CollisionFoo {

    public static class Bar {
        public String getId() {
            return "nested";
        }
    }
}
