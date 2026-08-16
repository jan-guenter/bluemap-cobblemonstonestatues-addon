/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StonePoseExportControllerReadinessTest {

    @Test
    void controlledPhysicalHelperCommandIsEnabled() {
        assertTrue(StonePoseExportController.HYBRID_EXPORT_READY);
    }
}
