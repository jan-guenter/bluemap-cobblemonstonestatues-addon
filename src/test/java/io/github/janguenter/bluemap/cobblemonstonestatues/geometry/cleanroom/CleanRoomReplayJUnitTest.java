/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom;

import org.junit.jupiter.api.Test;

import java.io.IOException;

/** JUnit entrypoints for the mechanically integrated clean-room reference tests. */
class CleanRoomReplayJUnitTest {

    @Test
    void runsTheDependencyNeutralCleanRoomContract() {
        BedrockBoxReplayTest.main(new String[0]);
    }

    @Test
    void matchesTheProvenanceLockedRuntimeFixture() throws IOException {
        String fixture = System.getProperty("stoneRuntimeFixture");
        if (fixture == null) {
            throw new IllegalStateException("sanitized runtime fixture property is required");
        }
        RuntimeFixtureParityTest.main(new String[] {fixture});
    }
}
