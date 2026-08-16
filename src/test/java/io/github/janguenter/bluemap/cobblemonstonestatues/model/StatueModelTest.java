/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.model;

import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel.Quad;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel.Vec3;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel.Vertex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatueModelTest {

    @Test
    void acceptsExactClientOrderWithOppositeSignedStoredNormal() {
        assertDoesNotThrow(() -> new Quad(
                vertex(0D, 0D, 0D), vertex(1D, 0D, 0D),
                vertex(1D, 1D, 0D), vertex(0D, 1D, 0D),
                new Vec3(0D, 0D, -1D)
        ));
    }

    @Test
    void acceptsOneCollapsedActualTriangleAndRawStoredNormalsButRejectsInvisibleQuads() {
        assertDoesNotThrow(() -> new Quad(
                vertex(0D, 0D, 0D), vertex(1D, 0D, 0D),
                vertex(1D, 0D, 0D), vertex(0D, 1D, 0D),
                new Vec3(0D, 0D, 1D)
        ));
        assertThrows(IllegalArgumentException.class, () -> new Quad(
                vertex(0D, 0D, 0D), vertex(1D, 0D, 0D),
                vertex(0D, 0D, 0D), vertex(0D, 1D, 0D),
                new Vec3(0D, 0D, 1D)
        ));
        assertDoesNotThrow(() -> new Quad(
                vertex(0D, 0D, 0D), vertex(1D, 0D, 0D),
                vertex(1D, 1D, 0D), vertex(0D, 1D, 0D),
                new Vec3(0D, 1D, 0D)
        ));
    }

    @Test
    void acceptsFloatCoordinateFaceBelowTheFormerAreaEpsilon() {
        double tiny = (double) 1.0E-30F;
        assertDoesNotThrow(() -> new Quad(
                vertex(0D, 0D, 0D), vertex(tiny, 0D, 0D),
                vertex(tiny, tiny, 0D), vertex(0D, tiny, 0D),
                new Vec3(0D, 0D, 1D)
        ));
    }

    @Test
    void acceptsQuantizationLimitedMegaMalamarLightPlaneAfterDigestVerification() {
        assertDoesNotThrow(() -> new Quad(
                rawVertex(0x401654f7, 0xc0452292, 0xbe35cc2e),
                rawVertex(0x40165159, 0xc0452602, 0xbe35b730),
                rawVertex(0x4016515b, 0xc0452608, 0xbe35b7c2),
                rawVertex(0x401654f9, 0xc0452298, 0xbe35ccc0),
                new Vec3(-0.69943154D, 0.5726601D, -0.4276163D)
        ));
    }

    private static Vertex vertex(double x, double y, double z) {
        return new Vertex(new Vec3(x, y, z), 0F, 0F);
    }

    private static Vertex rawVertex(int x, int y, int z) {
        return vertex(
                Float.intBitsToFloat(x), Float.intBitsToFloat(y), Float.intBitsToFloat(z)
        );
    }
}
