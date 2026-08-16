/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap522;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Process-bounded diagnostics that never include world, player, or NBT values. */
final class BoundedDiagnostics {

    private static final int MAX_MESSAGES = 16;
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final AtomicInteger emitted = new AtomicInteger();

    void report(String key) {
        if (seen.add(key) && emitted.incrementAndGet() <= MAX_MESSAGES) {
            System.err.println(
                    "BlueMap Cobblemon Stone Statues used empty fallback: " + key + "."
            );
        }
    }
}
