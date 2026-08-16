/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuadMultisetDigestTest {

    @Test
    void ignoresQuadOrderAndCyclicStartButPreservesWindingAndMultiplicity() {
        QuadMultisetDigest.Quad first = quad(0F);
        QuadMultisetDigest.Quad second = quad(2F);
        QuadMultisetDigest.Bounds bounds = bounds(0F, 3F);
        String ordinary = digest(List.of(first, second), 0xffffffff, 0, bounds);

        assertEquals(ordinary, digest(
                List.of(second, rotate(first, 2)), 0xffffffff, 0, bounds
        ));
        assertNotEquals(ordinary, digest(
                List.of(reverse(first), second), 0xffffffff, 0, bounds
        ));
        assertNotEquals(ordinary, digest(
                List.of(first, second, second), 0xffffffff, 0, bounds
        ));
    }

    @Test
    void bindsCountsTintBoundsAndRawFloatBits() {
        QuadMultisetDigest.Quad ordinary = quad(0F);
        QuadMultisetDigest.Bounds bounds = bounds(0F, 1F);
        QuadMultisetDigest.Result identity = QuadMultisetDigest.digest(
                List.of(ordinary), 0xff112233, 4, bounds
        );

        assertEquals(5, identity.sourceQuadCount());
        assertEquals(1, identity.retainedQuadCount());
        assertEquals(4, identity.droppedDegenerateQuadCount());
        assertEquals(0xff112233, identity.tintArgb());
        assertEquals(bounds, identity.bounds());
        assertNotEquals(identity.sha256(), digest(
                List.of(ordinary), 0xff112234, 4, bounds
        ));
        assertNotEquals(identity.sha256(), digest(
                List.of(ordinary), 0xff112233, 5, bounds
        ));
        assertThrows(IllegalArgumentException.class, () -> QuadMultisetDigest.digest(
                List.of(ordinary), 0xff112233, 4, bounds(0F, 2F)
        ));

        QuadMultisetDigest.Quad negativeZero = quad(-0.0F);
        QuadMultisetDigest.Bounds negativeBounds = bounds(-0.0F, 1F);
        assertNotEquals(
                digest(List.of(ordinary), -1, 0, bounds),
                digest(List.of(negativeZero), -1, 0, negativeBounds)
        );

        QuadMultisetDigest.Quad changedNormal = new QuadMultisetDigest.Quad(
                new QuadMultisetDigest.Vec3(0F, 1F, 0F), ordinary.vertices()
        );
        assertNotEquals(
                digest(List.of(ordinary), -1, 0, bounds),
                digest(List.of(changedNormal), -1, 0, bounds)
        );
    }

    private static String digest(
            List<QuadMultisetDigest.Quad> quads,
            int tint,
            int dropped,
            QuadMultisetDigest.Bounds bounds
    ) {
        return QuadMultisetDigest.digest(quads, tint, dropped, bounds).sha256();
    }

    private static QuadMultisetDigest.Quad quad(float firstX) {
        return new QuadMultisetDigest.Quad(
                new QuadMultisetDigest.Vec3(0F, 0F, 1F),
                List.of(
                        vertex(firstX, 0F, 0F, 0F, 0F),
                        vertex(firstX + 1F, 0F, 0F, 1F, 0F),
                        vertex(firstX + 1F, 1F, 0F, 1F, 1F),
                        vertex(firstX, 1F, 0F, 0F, 1F)
                )
        );
    }

    private static QuadMultisetDigest.Quad rotate(
            QuadMultisetDigest.Quad source, int amount
    ) {
        return new QuadMultisetDigest.Quad(source.storedNormal(), List.of(
                source.vertices().get(amount & 3),
                source.vertices().get((amount + 1) & 3),
                source.vertices().get((amount + 2) & 3),
                source.vertices().get((amount + 3) & 3)
        ));
    }

    private static QuadMultisetDigest.Quad reverse(QuadMultisetDigest.Quad source) {
        return new QuadMultisetDigest.Quad(source.storedNormal(), List.of(
                source.vertices().get(0), source.vertices().get(3),
                source.vertices().get(2), source.vertices().get(1)
        ));
    }

    private static QuadMultisetDigest.Vertex vertex(
            float x, float y, float z, float u, float v
    ) {
        return new QuadMultisetDigest.Vertex(
                new QuadMultisetDigest.Vec3(x, y, z), u, v
        );
    }

    private static QuadMultisetDigest.Bounds bounds(float minimum, float maximum) {
        return new QuadMultisetDigest.Bounds(
                new QuadMultisetDigest.Vec3(minimum, 0F, 0F),
                new QuadMultisetDigest.Vec3(maximum, 1F, 0F)
        );
    }
}
