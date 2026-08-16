/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.stoneposeexporter.CapturedMesh.Quad;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Vec3;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Vertex;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapturedMeshTest {

    private static final int WHITE = 0xffffffff;

    @Test
    void exactModelPartUnitCubeFaceOrderIsRetained() {
        Vec3 v000 = point(0F, 0F, 0F);
        Vec3 v100 = point(1F, 0F, 0F);
        Vec3 v110 = point(1F, 1F, 0F);
        Vec3 v010 = point(0F, 1F, 0F);
        Vec3 v001 = point(0F, 0F, 1F);
        Vec3 v101 = point(1F, 0F, 1F);
        Vec3 v111 = point(1F, 1F, 1F);
        Vec3 v011 = point(0F, 1F, 1F);

        List<Face> faces = List.of(
                new Face(point(0F, -1F, 0F), List.of(v101, v001, v000, v100)),
                new Face(point(0F, 1F, 0F), List.of(v110, v010, v011, v111)),
                new Face(point(-1F, 0F, 0F), List.of(v000, v001, v011, v010)),
                new Face(point(0F, 0F, -1F), List.of(v100, v000, v010, v110)),
                new Face(point(1F, 0F, 0F), List.of(v101, v100, v110, v111)),
                new Face(point(0F, 0F, 1F), List.of(v001, v101, v111, v011))
        );

        for (Face face : faces) {
            Optional<Quad> retained = Quad.retain(face.normal(), vertices(face.points()));
            assertTrue(retained.isPresent());
            assertEquals(face.points(), retained.orElseThrow().vertices().stream()
                    .map(Vertex::position).toList());
        }
    }

    @Test
    void flatModelPartCubeDropsOnlyItsFourExactZeroAreaEdgeFaces() {
        Vec3 v000 = point(0F, 0F, 0F);
        Vec3 v100 = point(1F, 0F, 0F);
        Vec3 v110 = point(1F, 1F, 0F);
        Vec3 v010 = point(0F, 1F, 0F);
        List<Face> faces = List.of(
                new Face(point(0F, -1F, 0F), List.of(v100, v000, v000, v100)),
                new Face(point(0F, 1F, 0F), List.of(v110, v010, v010, v110)),
                new Face(point(-1F, 0F, 0F), List.of(v000, v000, v010, v010)),
                new Face(point(0F, 0F, -1F), List.of(v100, v000, v010, v110)),
                new Face(point(1F, 0F, 0F), List.of(v100, v100, v110, v110)),
                new Face(point(0F, 0F, 1F), List.of(v000, v100, v110, v010))
        );

        List<Quad> retained = new ArrayList<>();
        int dropped = 0;
        for (Face face : faces) {
            Optional<Quad> candidate = Quad.retain(face.normal(), vertices(face.points()));
            if (candidate.isPresent()) {
                retained.add(candidate.orElseThrow());
            } else {
                dropped++;
            }
        }

        CapturedMesh mesh = new CapturedMesh(retained, WHITE, dropped);
        assertEquals(2, mesh.quads().size());
        assertEquals(4, mesh.droppedDegenerateQuads());
        assertEquals(point(0F, 0F, 0F), mesh.bounds().minimum());
        assertEquals(point(1F, 1F, 0F), mesh.bounds().maximum());
        assertEquals(2, ByteBuffer.wrap(mesh.encode()).order(ByteOrder.LITTLE_ENDIAN)
                .getInt(8));
    }

    @Test
    void reflectedTinyAndPartiallyCollapsedVisibleQuadsStayInSourceOrder() {
        List<Vertex> square = vertices(List.of(
                point(0F, 0F, 0F), point(1F, 0F, 0F),
                point(1F, 1F, 0F), point(0F, 1F, 0F)
        ));
        Quad reflected = Quad.retain(point(0F, 0F, -1F), square).orElseThrow();
        assertEquals(square, reflected.vertices());

        List<Vertex> oneTriangle = vertices(List.of(
                point(0F, 0F, 0F), point(1F, 0F, 0F),
                point(1F, 0F, 0F), point(0F, 1F, 0F)
        ));
        assertTrue(Quad.retain(point(0F, 0F, 1F), oneTriangle).isPresent());

        float tiny = 1.0E-4F;
        List<Vertex> tinySquare = vertices(List.of(
                point(0F, 0F, 0F), point(tiny, 0F, 0F),
                point(tiny, tiny, 0F), point(0F, tiny, 0F)
        ));
        assertTrue(Quad.retain(point(0F, 0F, 1F), tinySquare).isPresent());

        float floatCrossUnderflow = 1.0E-30F;
        List<Vertex> underflowSquare = vertices(List.of(
                point(0F, 0F, 0F), point(floatCrossUnderflow, 0F, 0F),
                point(floatCrossUnderflow, floatCrossUnderflow, 0F),
                point(0F, floatCrossUnderflow, 0F)
        ));
        assertTrue(Quad.retain(point(0F, 0F, 1F), underflowSquare).isPresent());
    }

    @Test
    void retainsQuantizationLimitedMegaMalamarLightPlane() {
        Vec3 normal = point(-0.69943154F, 0.5726601F, -0.4276163F);
        List<Vertex> vertices = vertices(List.of(
                rawPoint(0x401654f7, 0xc0452292, 0xbe35cc2e),
                rawPoint(0x40165159, 0xc0452602, 0xbe35b730),
                rawPoint(0x4016515b, 0xc0452608, 0xbe35b7c2),
                rawPoint(0x401654f9, 0xc0452298, 0xbe35ccc0)
        ));

        Quad retained = Quad.retain(normal, vertices).orElseThrow();
        assertEquals(normal, retained.normal());
        assertEquals(vertices, retained.vertices());
    }

    @Test
    void invalidNormalLengthsAndAnAllDegeneratePoseStillFailClosed() {
        List<Vertex> visible = vertices(List.of(
                point(0F, 0F, 0F), point(1F, 0F, 0F),
                point(1F, 1F, 0F), point(0F, 1F, 0F)
        ));
        assertThrows(IllegalArgumentException.class, () ->
                Quad.retain(point(0F, 2F, 0F), visible));
        assertTrue(Quad.retain(point(0F, 1F, 0F), visible).isPresent());

        List<Vertex> collapsed = vertices(List.of(
                point(0F, 0F, 0F), point(1F, 0F, 0F),
                point(2F, 0F, 0F), point(3F, 0F, 0F)
        ));
        assertFalse(Quad.retain(point(0F, 1F, 0F), collapsed).isPresent());
        List<Vertex> collapsedOnActualQuadDiagonal = vertices(List.of(
                point(0F, 0F, 0F), point(1F, 0F, 0F),
                point(0F, 0F, 0F), point(0F, 1F, 0F)
        ));
        assertFalse(Quad.retain(
                point(0F, 0F, 1F), collapsedOnActualQuadDiagonal
        ).isPresent());
        assertThrows(IllegalArgumentException.class, () ->
                new CapturedMesh(List.of(), WHITE, 1));
    }

    @Test
    void rawQuadBudgetIncludesDroppedDegenerates() {
        Quad visible = Quad.retain(point(0F, 0F, 1F), vertices(List.of(
                point(0F, 0F, 0F), point(1F, 0F, 0F),
                point(1F, 1F, 0F), point(0F, 1F, 0F)
        ))).orElseThrow();

        assertThrows(IllegalArgumentException.class, () ->
                new CapturedMesh(List.of(visible), WHITE, 50_000));

        CapturedMesh first = new CapturedMesh(List.of(visible), WHITE, 1);
        CapturedMesh same = new CapturedMesh(List.of(visible), WHITE, 1);
        CapturedMesh differentDroppedCount = new CapturedMesh(
                List.of(visible), WHITE, 2
        );
        assertTrue(first.sameCapture(same));
        assertFalse(first.sameCapture(differentDroppedCount));
    }

    private static List<Vertex> vertices(List<Vec3> points) {
        return points.stream().map(point -> new Vertex(point, 0F, 0F, WHITE)).toList();
    }

    private static Vec3 point(float x, float y, float z) {
        return new Vec3(x, y, z);
    }

    private static Vec3 rawPoint(int x, int y, int z) {
        return point(
                Float.intBitsToFloat(x), Float.intBitsToFloat(y), Float.intBitsToFloat(z)
        );
    }

    private record Face(Vec3 normal, List<Vec3> points) {
    }
}
