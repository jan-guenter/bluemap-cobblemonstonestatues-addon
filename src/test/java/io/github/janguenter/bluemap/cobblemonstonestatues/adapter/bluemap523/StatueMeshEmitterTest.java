/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap523;

import de.bluecolored.bluemap.core.map.hires.ArrayTileModel;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.VoidTileModel;
import de.bluecolored.bluemap.core.util.Direction;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel.Quad;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel.Vec3;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel.Vertex;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatueMeshEmitterTest {

    @Test
    void reservesOneContiguousRangeForArrayAndVoidTargets() {
        ArrayTileModel array = new ArrayTileModel(1);
        array.add(1);
        TileModelView arrayView = new TileModelView(array);

        assertEquals(1, StatueMeshEmitter.reserveTriangles(arrayView, 2));
        assertEquals(5, array.size());
        assertEquals(1, arrayView.getStart());
        assertEquals(4, arrayView.getSize());

        TileModelView voidView = new TileModelView(VoidTileModel.INSTANCE);
        assertEquals(0, StatueMeshEmitter.reserveTriangles(voidView, 2));
        assertEquals(0, voidView.getStart());
        assertEquals(0, voidView.getSize());
    }

    @Test
    void appliesStockOuterTransformWithoutRepeatingCapturedBaseScale() {
        Quad source = upwardUnitQuad();
        StatueSelection selection = new StatueSelection(
                "cobblemon:pikachu", "Normal", "", "shoulder_left",
                StatueSelection.Scale.DOUBLE, StatueSelection.Material.GOLD_BLOCK,
                2, 0L
        );

        Quad transformed = StatueMeshEmitter.transform(source, selection);

        assertVec(new Vec3(0.5D, 0D, 0.5D), transformed.first().position());
        assertVec(new Vec3(-1.5D, 0D, 0.5D), transformed.second().position());
        assertVec(new Vec3(-1.5D, 0D, -1.5D), transformed.third().position());
        assertVec(new Vec3(0.5D, 0D, -1.5D), transformed.fourth().position());
        assertVec(new Vec3(0D, -1D, 0D), transformed.normal());
    }

    @Test
    void emitsOneOutwardWindingAndUsesFaceNormalForPolicies() {
        assertEquals(2, StatueMeshEmitter.emittedTriangleCount(1));
        assertArrayEquals(new int[]{0, 1, 2}, StatueMeshEmitter.triangleOrder(0));
        assertArrayEquals(new int[]{0, 2, 3}, StatueMeshEmitter.triangleOrder(1));
        assertEquals(Direction.UP, StatueMeshEmitter.nearestDirection(new Vec3(0.1D, 0.8D, 0.2D)));
        assertEquals(Direction.WEST,
                StatueMeshEmitter.nearestDirection(new Vec3(-0.9D, 0.1D, 0.2D)));
        assertTrue(StatueMeshEmitter.hiddenByCave(
                true, false, new FaceLighting.Sample(0, 12)
        ));
        assertFalse(StatueMeshEmitter.hiddenByCave(
                true, true, new FaceLighting.Sample(0, 12)
        ));
    }

    @Test
    void skipsOnlyQuadsThatBecomeExactRasterNoOpsAfterBlockTransform() {
        StatueSelection selection = new StatueSelection(
                "cobblemon:test", "Normal", "", "portrait",
                StatueSelection.Scale.NORMAL, StatueSelection.Material.STONE,
                0, 0L
        );
        double tiny = (double) 1.0E-30F;
        Quad tinySource = new Quad(
                vertex(0D, 0D, 0D, 0F, 0F),
                vertex(tiny, 0D, 0D, 1F, 0F),
                vertex(tiny, tiny, 0D, 1F, 1F),
                vertex(0D, tiny, 0D, 0F, 1F),
                new Vec3(0D, 0D, 1D)
        );

        assertTrue(StatueMeshEmitter.transformVisible(
                upwardUnitQuad(), selection
        ).isPresent());
        assertTrue(StatueMeshEmitter.transformVisible(tinySource, selection).isEmpty());
    }

    private static Quad upwardUnitQuad() {
        return new Quad(
                vertex(0D, 0D, 0D, 0F, 0F),
                vertex(0D, 0D, 1D, 0F, 1F),
                vertex(1D, 0D, 1D, 1F, 1F),
                vertex(1D, 0D, 0D, 1F, 0F),
                new Vec3(0D, 1D, 0D)
        );
    }

    private static Vertex vertex(double x, double y, double z, float u, float v) {
        return new Vertex(new Vec3(x, y, z), u, v);
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-9D);
        assertEquals(expected.y(), actual.y(), 1.0E-9D);
        assertEquals(expected.z(), actual.z(), 1.0E-9D);
    }
}
