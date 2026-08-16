/*
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * Adapted from the first-party BlueMap Botany Pots adapter scaffold at
 * v0.1.0-alpha.1 / f40eed6c1f7f30356bcdfabbc3e2a6455fec7884.
 * Modified in 2026 for the Cobblemon Stone Statues integration.
 */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap522;

import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueMap522AdapterTest {

    @Test
    void installsBeforeBlueNbtSnapshotAndRetainsEverySelectionField() {
        assertTrue(BlueMap522Adapter.install());
        assertTrue(BlueMap522Adapter.probeBlockEntityRetention());
        assertTrue(BlueMap522Adapter.install());
    }

    @Test
    void absentSpeciesUsesStoneBlockEntityDefault() {
        StatueSelection selection = new StatueBlockEntityData().selection();

        assertEquals("cobblemon:bulbasaur", selection.speciesId());
        assertEquals("Normal", selection.formId());
        assertEquals("", selection.aspectsCsv());
        assertEquals("portrait", selection.animationName());
        assertEquals(StatueSelection.Scale.NORMAL, selection.scale());
        assertEquals(StatueSelection.Material.STONE, selection.material());
        assertEquals(0, selection.rotation45());
        assertEquals(0L, selection.stoneSeed());
    }

    @Test
    void partialRegistrationInvokesPermanentFailureAndStops() {
        AtomicBoolean permanentFailure = new AtomicBoolean();
        AtomicInteger registrations = new AtomicInteger();

        assertFalse(BlueMap522Adapter.registerAll(
                () -> permanentFailure.set(true),
                () -> {
                    registrations.incrementAndGet();
                    return true;
                },
                () -> {
                    registrations.incrementAndGet();
                    return false;
                },
                () -> {
                    registrations.incrementAndGet();
                    return true;
                }
        ));
        assertTrue(permanentFailure.get());
        assertEquals(2, registrations.get());
    }
}
