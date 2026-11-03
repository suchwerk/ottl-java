/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.functions.standard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Shared key filtering for {@code delete_matching_keys} and {@code keep_matching_keys}. */
final class KeyFilters {

    private KeyFilters() {
    }

    /** When {@code keepMatches} is true, drop keys that do not match. Otherwise drop keys that do. */
    static void retain(Map<String, Object> map, Pattern pattern, boolean keepMatches) {
        List<String> drop = new ArrayList<>();
        for (String key : map.keySet()) {
            boolean matches = pattern.matcher(key).find();
            if (matches != keepMatches) {
                drop.add(key);
            }
        }
        drop.forEach(map::remove);
    }
}
