/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Resource generation token that invalidates an export across any reload window. */
final class ModelGenerationTracker {

    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean stable = new AtomicBoolean(true);

    long current() {
        return generation.get();
    }

    boolean matches(long expected) {
        return stable.get() && generation.get() == expected;
    }

    void reloadStarted() {
        stable.set(false);
        generation.incrementAndGet();
    }

    void modelsBaked() {
        generation.incrementAndGet();
    }

    void reloadCompleted() {
        generation.incrementAndGet();
        stable.set(true);
    }
}
