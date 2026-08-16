/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseStateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoseStateStoreTest {

    @Test
    void storesCanonicalStatesByContentAndDeduplicatesWithoutCloningGeometry() {
        PoseState state = state(-0.0F);
        byte[] raw = PoseStateCodec.encode(state);
        PoseStateStore store = new PoseStateStore(raw.length);

        String first = store.retain(state);
        assertEquals(first, store.retain(state));
        assertEquals(raw.length, store.retainedBytes());
        assertEquals(state, PoseStateCodec.decode(store.copy(first)));
        assertThrows(IllegalArgumentException.class, () -> store.retain(state(0.0F)));
        assertEquals(raw.length, store.retainedBytes());
        assertThrows(IllegalArgumentException.class, () -> store.copy("0".repeat(64)));
    }

    private static PoseState state(float x) {
        return new PoseState("/root", 0xFFFFFFFF, List.of(new PoseState.Node(
                "/root", -1, 0, new PoseState.RuntimeFrame(
                new PoseState.PositionMatrix(
                        1F, 0F, 0F, 0F,
                        0F, 1F, 0F, 0F,
                        0F, 0F, 1F, 0F,
                        x, 0F, 0F, 1F
                ),
                new PoseState.CardinalNormals(
                        0F, -1F, 0F, 0F, 1F, 0F,
                        -1F, 0F, 0F, 0F, 0F, -1F,
                        1F, 0F, 0F, 0F, 0F, 1F
                ),
                new PoseState.MirrorZeroXNormals(
                        -0.0F, -1F, 0F, -0.0F, 1F, 0F,
                        -0.0F, 0F, -1F, -0.0F, 0F, 1F
                )), true, false
        )));
    }
}
