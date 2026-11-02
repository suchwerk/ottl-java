/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.SimpleBuildItem;

/** Indexed names of {@code @OttlAccessible} types that passed accessor validation. */
final class AccessibleTypesBuildItem extends SimpleBuildItem {

    private final List<DotName> types;

    AccessibleTypesBuildItem(List<DotName> types) {
        this.types = List.copyOf(types);
    }

    List<DotName> types() {
        return types;
    }
}
