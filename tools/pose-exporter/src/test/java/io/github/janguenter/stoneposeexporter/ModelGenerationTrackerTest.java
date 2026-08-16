/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelGenerationTrackerTest {

    @Test
    void invalidatesAtReloadStartAndStabilizesOnlyAfterApplyCompletion() {
        ModelGenerationTracker tracker = new ModelGenerationTracker();
        long initial = tracker.current();
        assertTrue(tracker.matches(initial));

        tracker.reloadStarted();
        long preparing = tracker.current();
        assertFalse(tracker.matches(initial));
        assertFalse(tracker.matches(preparing));

        tracker.modelsBaked();
        long baked = tracker.current();
        assertFalse(tracker.matches(preparing));
        assertFalse(tracker.matches(baked));

        tracker.reloadCompleted();
        assertFalse(tracker.matches(baked));
        assertTrue(tracker.matches(tracker.current()));
    }
}
