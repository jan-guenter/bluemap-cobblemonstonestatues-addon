/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StonePoseExportEngineCensusTest {

    @Test
    void emptyNamedPoseListIsValidBecauseEveryChoiceStillGetsItsSentinelFallback() {
        assertDoesNotThrow(() -> PoseNameCensus.requireValid(
                List.of(), 256, Set.of(
                        "__atmons_impossible_pose__",
                        "__bridge_fallback_output_ambiguous__"
                ), "invalid"
        ));
    }

    @Test
    void duplicateBlankAndReservedPoseNamesRemainInvalid() {
        for (List<String> poses : List.of(
                List.of("standing", "standing"),
                List.of(""),
                List.of("__atmons_impossible_pose__"),
                List.of("__bridge_fallback_output_ambiguous__")
        )) {
            assertThrows(IllegalArgumentException.class,
                    () -> PoseNameCensus.requireValid(
                            poses, 256, Set.of(
                                    "__atmons_impossible_pose__",
                                    "__bridge_fallback_output_ambiguous__"
                            ), "invalid"
                    ));
        }
    }
}
