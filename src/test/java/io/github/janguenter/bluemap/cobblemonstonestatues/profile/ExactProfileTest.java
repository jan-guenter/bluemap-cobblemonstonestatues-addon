/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactProfileTest {

    @Test
    void auditedFormatTwoRuntimeIsEnabledForControlledStaging() {
        assertTrue(ExactProfile.FORMAT_2_READY);
    }

    @Test
    void productionPinsTheControlledCommandEnabledHelper() {
        assertEquals(284_840L, ExactProfile.EXPORTER_SIZE);
        assertEquals(
                "f35727ba2ce5c96abe085df36d5d02534b6a19e1f710eb4c145c0d7243c75b98",
                ExactProfile.EXPORTER_SHA256
        );
    }
}
