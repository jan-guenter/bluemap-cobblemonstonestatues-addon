/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

/** Immutable one-sided posed mesh and canonical binary encoding. */
record CapturedMesh(List<Quad> quads, int tintArgb, int droppedDegenerateQuads) {

    private static final int MAGIC = 0x534D5441;
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 12;
    private static final int BYTES_PER_QUAD = 23 * Float.BYTES;
    private static final int MAX_QUADS = 50_000;

    CapturedMesh {
        quads = List.copyOf(quads);
        if (quads.isEmpty() || droppedDegenerateQuads < 0
                || Math.addExact(quads.size(), droppedDegenerateQuads) > MAX_QUADS) {
            throw new IllegalArgumentException("captured mesh outside quad budget");
        }
    }

    byte[] encode() {
        ByteBuffer output = ByteBuffer.allocate(Math.addExact(
                HEADER_BYTES, Math.multiplyExact(quads.size(), BYTES_PER_QUAD)
        )).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(MAGIC).putInt(VERSION).putInt(quads.size());
        for (Quad quad : quads) {
            put(output, quad.normal());
            for (Vertex vertex : quad.vertices()) {
                put(output, vertex.position());
                output.putFloat(vertex.u()).putFloat(vertex.v());
            }
        }
        return output.array();
    }

    boolean sameCapture(CapturedMesh other) {
        return other != null && tintArgb == other.tintArgb
                && droppedDegenerateQuads == other.droppedDegenerateQuads
                && quads.equals(other.quads);
    }

    Bounds bounds() {
        Vec3 minimum = null;
        Vec3 maximum = null;
        for (Quad quad : quads) {
            for (Vertex vertex : quad.vertices()) {
                Vec3 point = vertex.position();
                minimum = minimum == null ? point : new Vec3(
                        Math.min(minimum.x(), point.x()),
                        Math.min(minimum.y(), point.y()),
                        Math.min(minimum.z(), point.z())
                );
                maximum = maximum == null ? point : new Vec3(
                        Math.max(maximum.x(), point.x()),
                        Math.max(maximum.y(), point.y()),
                        Math.max(maximum.z(), point.z())
                );
            }
        }
        return new Bounds(minimum, maximum);
    }

    QuadMultisetDigest.Result multisetDigest() {
        Bounds capturedBounds = bounds();
        return QuadMultisetDigest.digest(quads.stream().map(quad ->
                new QuadMultisetDigest.Quad(
                        new QuadMultisetDigest.Vec3(
                                quad.normal().x(), quad.normal().y(), quad.normal().z()
                        ),
                        quad.vertices().stream().map(vertex ->
                                new QuadMultisetDigest.Vertex(
                                        new QuadMultisetDigest.Vec3(
                                                vertex.position().x(), vertex.position().y(),
                                                vertex.position().z()
                                        ), vertex.u(), vertex.v()
                                )).toList()
                )).toList(), tintArgb, droppedDegenerateQuads,
                new QuadMultisetDigest.Bounds(
                        new QuadMultisetDigest.Vec3(
                                capturedBounds.minimum().x(), capturedBounds.minimum().y(),
                                capturedBounds.minimum().z()
                        ),
                        new QuadMultisetDigest.Vec3(
                                capturedBounds.maximum().x(), capturedBounds.maximum().y(),
                                capturedBounds.maximum().z()
                        )
                ));
    }

    private static void put(ByteBuffer output, Vec3 value) {
        output.putFloat(value.x()).putFloat(value.y()).putFloat(value.z());
    }

    record Quad(Vec3 normal, List<Vertex> vertices) {
        Quad {
            vertices = List.copyOf(vertices);
            if (vertices.size() != 4) {
                throw new IllegalArgumentException("captured primitive is not a quad");
            }
            DoubleVec3 firstCross = triangleCross(vertices, 0, 1, 2);
            DoubleVec3 secondCross = triangleCross(vertices, 2, 3, 0);
            double normalLength = length(normal);
            if (isExactZero(firstCross) && isExactZero(secondCross)) {
                throw new IllegalArgumentException("retained captured quad is exactly degenerate");
            }
            if (normalLength < 0.5F || normalLength > 1.5F) {
                throw new IllegalArgumentException(
                        "captured quad normal length is invalid: normal=" + normal
                                + ", normalLength=" + normalLength
                );
            }
        }

        static Optional<Quad> retain(Vec3 normal, List<Vertex> vertices) {
            List<Vertex> immutable = List.copyOf(vertices);
            if (immutable.size() != 4) {
                throw new IllegalArgumentException("captured primitive is not a quad");
            }
            double normalLength = length(normal);
            if (normalLength < 0.5D || normalLength > 1.5D) {
                throw new IllegalArgumentException(
                        "captured quad normal length is invalid: normal=" + normal
                                + ", normalLength=" + normalLength
                );
            }
            DoubleVec3 firstCross = triangleCross(immutable, 0, 1, 2);
            DoubleVec3 secondCross = triangleCross(immutable, 2, 3, 0);
            if (isExactZero(firstCross) && isExactZero(secondCross)) {
                return Optional.empty();
            }
            return Optional.of(new Quad(normal, immutable));
        }

        private static double dot(Vec3 first, Vec3 second) {
            return (double) first.x() * second.x() + (double) first.y() * second.y()
                    + (double) first.z() * second.z();
        }

        private static double length(Vec3 value) {
            return Math.sqrt(dot(value, value));
        }

        private static DoubleVec3 triangleCross(
                List<Vertex> vertices, int first, int second, int third
        ) {
            Vec3 origin = vertices.get(first).position();
            Vec3 secondPoint = vertices.get(second).position();
            Vec3 thirdPoint = vertices.get(third).position();
            double firstX = (double) secondPoint.x() - origin.x();
            double firstY = (double) secondPoint.y() - origin.y();
            double firstZ = (double) secondPoint.z() - origin.z();
            double secondX = (double) thirdPoint.x() - origin.x();
            double secondY = (double) thirdPoint.y() - origin.y();
            double secondZ = (double) thirdPoint.z() - origin.z();
            return new DoubleVec3(
                    firstY * secondZ - firstZ * secondY,
                    firstZ * secondX - firstX * secondZ,
                    firstX * secondY - firstY * secondX
            );
        }

        private static boolean isExactZero(DoubleVec3 value) {
            return value.x() == 0D && value.y() == 0D && value.z() == 0D;
        }

        private record DoubleVec3(double x, double y, double z) {
        }
    }

    record Vertex(Vec3 position, float u, float v, int argb) {
        Vertex {
            if (!Float.isFinite(u) || !Float.isFinite(v)
                    || Math.abs(u) > 64F || Math.abs(v) > 64F) {
                throw new IllegalArgumentException("captured UV outside budget");
            }
        }
    }

    record Vec3(float x, float y, float z) {
        Vec3 {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || Math.abs(x) > 4_096F || Math.abs(y) > 4_096F
                    || Math.abs(z) > 4_096F) {
                throw new IllegalArgumentException("captured coordinate outside budget");
            }
        }

        Vec3 subtract(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }

        Vec3 cross(Vec3 other) {
            return new Vec3(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        float dot(Vec3 other) {
            return x * other.x + y * other.y + z * other.z;
        }

        float length() {
            return (float) Math.sqrt(dot(this));
        }

        Vec3 normalized() {
            float length = length();
            if (length <= 1.0E-12F) {
                throw new IllegalArgumentException("cannot normalize zero vector");
            }
            return new Vec3(x / length, y / length, z / length);
        }
    }

    record Bounds(Vec3 minimum, Vec3 maximum) {
    }
}
