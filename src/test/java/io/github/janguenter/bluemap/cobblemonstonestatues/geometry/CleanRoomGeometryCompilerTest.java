/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry;

import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CleanRoomGeometryCompilerTest {

    @Test
    void adaptsBoundBoxesAndUsesTheCapturedAbsoluteMatrixExactlyOnce() {
        BakedGeometryTopology.BoundGeometry unit = bound(
                new BakedGeometryTopology.Box(
                        vector(0F, 0F, 0F), vector(16F, 16F, 16F),
                        0, 0, 0F, false
                ), frame(2F, -3F, 0.5F, 0.5F)
        );

        CleanRoomGeometryCompiler.Compilation result =
                CleanRoomGeometryCompiler.compile(unit, 0xFF89ABCD);

        assertEquals(6, result.identity().sourceQuadCount());
        assertEquals(6, result.identity().retainedQuadCount());
        assertEquals(0, result.identity().droppedDegenerateQuadCount());
        assertEquals(0xFF89ABCD, result.identity().tintArgb());
        assertEquals(new QuadMultisetDigest.Bounds(
                digestVector(2F, -3F, 0.5F), digestVector(2.5F, -2.5F, 1F)
        ), result.identity().bounds());
    }

    @Test
    void retainsOnlyTheTwoVisiblePlanesOfAZeroThicknessBox() {
        BakedGeometryTopology.BoundGeometry flat = bound(
                new BakedGeometryTopology.Box(
                        vector(0F, 0F, 0F), vector(0F, 16F, 16F),
                        -4, 3, 0F, true
                ), frame(0F, 0F, 0F, 1F)
        );

        CleanRoomGeometryCompiler.Compilation result =
                CleanRoomGeometryCompiler.compile(flat, 0xFFFFFFFF);

        assertEquals(6, result.identity().sourceQuadCount());
        assertEquals(2, result.identity().retainedQuadCount());
        assertEquals(4, result.identity().droppedDegenerateQuadCount());
        assertEquals(new QuadMultisetDigest.Bounds(
                digestVector(0F, 0F, 0F), digestVector(0F, 1F, 1F)
        ), result.identity().bounds());
    }

    @Test
    void preservesAsymmetricRawMatrixElementPlacement() {
        PoseState.RuntimeFrame rotated = new PoseState.RuntimeFrame(
                new PoseState.PositionMatrix(
                        0F, 1F, 0F, 0F,
                        -1F, 0F, 0F, 0F,
                        0F, 0F, 1F, 0F,
                        2F, 3F, 4F, 1F
                ),
                new PoseState.CardinalNormals(
                        1F, 0F, 0F, -1F, 0F, 0F,
                        0F, -1F, 0F, 0F, 0F, -1F,
                        0F, 1F, 0F, 0F, 0F, 1F
                ),
                new PoseState.MirrorZeroXNormals(
                        1F, 0F, 0F, -1F, 0F, 0F,
                        0F, 0F, -1F, 0F, 0F, 1F
                )
        );
        BakedGeometryTopology.BoundGeometry geometry = bound(
                new BakedGeometryTopology.Box(
                        vector(0F, 0F, 0F), vector(16F, 16F, 16F),
                        0, 0, 0F, false
                ), rotated
        );

        CleanRoomGeometryCompiler.Compilation result =
                CleanRoomGeometryCompiler.compile(geometry, 0xFFFFFFFF);

        assertEquals(new QuadMultisetDigest.Bounds(
                digestVector(1F, 3F, 4F), digestVector(2F, 4F, 5F)
        ), result.identity().bounds());
    }

    @Test
    void selectsCapturedMirrorZeroXNormalWithItsRawSignBit() {
        BakedGeometryTopology.BoundGeometry geometry = bound(
                new BakedGeometryTopology.Box(
                        vector(0F, 0F, 0F), vector(16F, 16F, 16F),
                        0, 0, 0F, true
                ), frame(0F, 0F, 0F, 1F)
        );

        CleanRoomGeometryCompiler.Compilation result =
                CleanRoomGeometryCompiler.compile(geometry, 0xFFFFFFFF);
        QuadMultisetDigest.Vec3 north = result.quads().stream()
                .map(QuadMultisetDigest.Quad::storedNormal)
                .filter(normal -> normal.z() == -1F)
                .findFirst()
                .orElseThrow();

        assertEquals(
                Float.floatToRawIntBits(-0.0F),
                Float.floatToRawIntBits(north.x())
        );
    }

    @Test
    void doesNotLeakMirrorZeroXNormalsIntoAnOrdinaryBox() {
        BakedGeometryTopology.BoundGeometry geometry = bound(
                new BakedGeometryTopology.Box(
                        vector(0F, 0F, 0F), vector(16F, 16F, 16F),
                        0, 0, 0F, false
                ), frame(0F, 0F, 0F, 1F)
        );

        CleanRoomGeometryCompiler.Compilation result =
                CleanRoomGeometryCompiler.compile(geometry, 0xFFFFFFFF);
        QuadMultisetDigest.Vec3 north = result.quads().stream()
                .map(QuadMultisetDigest.Quad::storedNormal)
                .filter(normal -> normal.z() == -1F)
                .findFirst()
                .orElseThrow();

        assertEquals(
                Float.floatToRawIntBits(0.0F),
                Float.floatToRawIntBits(north.x())
        );
    }

    @Test
    void rejectsAFrameThatIsNotBackedByAnyBoxes() {
        BakedGeometryTopology.Node empty = new BakedGeometryTopology.Node(
                "body", "/body", null, defaultTransform(), List.of()
        );
        PoseState.Node pose = new PoseState.Node(
                "/body", -1, 0, frame(0F, 0F, 0F, 1F), true, false
        );
        BakedGeometryTopology.BoundGeometry invalid = new BakedGeometryTopology.BoundGeometry(
                "geometry.test", 64, 64,
                List.of(new BakedGeometryTopology.BoundNode(empty, pose))
        );

        assertThrows(IllegalArgumentException.class,
                () -> CleanRoomGeometryCompiler.compile(invalid, 0xFFFFFFFF));
    }

    private static BakedGeometryTopology.BoundGeometry bound(
            BakedGeometryTopology.Box box,
            PoseState.RuntimeFrame frame
    ) {
        BakedGeometryTopology.Node geometry = new BakedGeometryTopology.Node(
                "body", "/body", null, defaultTransform(), List.of(box)
        );
        PoseState.Node pose = new PoseState.Node(
                "/body", -1, 0, frame, true, false
        );
        return new BakedGeometryTopology.BoundGeometry(
                "geometry.test", 64, 64,
                List.of(new BakedGeometryTopology.BoundNode(geometry, pose))
        );
    }

    private static PoseState.RuntimeFrame frame(
            float translateX,
            float translateY,
            float translateZ,
            float scale
    ) {
        return new PoseState.RuntimeFrame(
                new PoseState.PositionMatrix(
                        scale, 0F, 0F, 0F,
                        0F, scale, 0F, 0F,
                        0F, 0F, scale, 0F,
                        translateX, translateY, translateZ, 1F
                ),
                new PoseState.CardinalNormals(
                        0F, -1F, 0F, 0F, 1F, 0F,
                        -1F, 0F, 0F, 0F, 0F, -1F,
                        1F, 0F, 0F, 0F, 0F, 1F
                ),
                mirrorIdentityNormals()
        );
    }

    private static PoseState.MirrorZeroXNormals mirrorIdentityNormals() {
        return new PoseState.MirrorZeroXNormals(
                -0.0F, -1F, 0F, -0.0F, 1F, 0F,
                -0.0F, 0F, -1F, -0.0F, 0F, 1F
        );
    }

    private static BakedGeometryTopology.Transform defaultTransform() {
        return new BakedGeometryTopology.Transform(
                vector(0F, 0F, 0F), vector(0F, 0F, 0F), vector(1F, 1F, 1F)
        );
    }

    private static BedrockGeometry.Vec3 vector(float x, float y, float z) {
        return new BedrockGeometry.Vec3(x, y, z);
    }

    private static QuadMultisetDigest.Vec3 digestVector(float x, float y, float z) {
        return new QuadMultisetDigest.Vec3(x, y, z);
    }
}
