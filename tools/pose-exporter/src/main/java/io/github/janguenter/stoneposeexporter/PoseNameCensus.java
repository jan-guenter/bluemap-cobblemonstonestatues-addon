/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Dependency-neutral validation for one Stone choice's ordered named-pose census. */
final class PoseNameCensus {

    private PoseNameCensus() {
    }

    static void requireValid(
            List<String> poses,
            int maximum,
            Set<String> reserved,
            String message
    ) {
        if (maximum < 0 || poses.size() > maximum
                || new LinkedHashSet<>(poses).size() != poses.size()
                || poses.stream().anyMatch(String::isBlank)
                || poses.stream().anyMatch(reserved::contains)) {
            throw new IllegalArgumentException(message);
        }
    }
}
