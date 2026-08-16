// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Jan Guenter
/*
 * Clean-room provenance: SPEC.md; Microsoft Bedrock geometry 1.12 schema/visual docs;
 * sanitized fixture SHA-256 b287b9ff5b6bdd0aa9d7bf04865a1d9bb28f772e59b10333c224ea552fbc848f.
 */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dependency-free clean-room reference implementation of {@code SPEC.md}.
 *
 * <p>{@link #compileRuntimeProfile} selects every behavior resolved by the locked fixture set.
 * The lower-level {@link #compile} keeps explicit seams for bounded research.</p>
 */
public final class BedrockBoxReplay {
    private static final double MIN_NORMAL_LENGTH = 0.5D;
    private static final double MAX_NORMAL_LENGTH = 1.5D;

    private BedrockBoxReplay() {
    }

    public enum Face {
        NORTH,
        SOUTH,
        EAST,
        WEST,
        UP,
        DOWN
    }

    public record Vec2(float u, float v) {
        public Vec2 {
            requireFinite("u", u);
            requireFinite("v", v);
        }
    }

    public record Vec3(float x, float y, float z) {
        public Vec3 {
            requireFinite("x", x);
            requireFinite("y", y);
            requireFinite("z", z);
        }

        public Vec3 subtract(Vec3 other) {
            Objects.requireNonNull(other, "other");
            float resultX = x - other.x;
            float resultY = y - other.y;
            float resultZ = z - other.z;
            return new Vec3(resultX, resultY, resultZ);
        }

        public Vec3 cross(Vec3 other) {
            Objects.requireNonNull(other, "other");
            float resultX = y * other.z - z * other.y;
            float resultY = z * other.x - x * other.z;
            float resultZ = x * other.y - y * other.x;
            return new Vec3(resultX, resultY, resultZ);
        }

        public boolean isZero() {
            return x == 0.0f && y == 0.0f && z == 0.0f;
        }
    }

    /**
     * Exact-profile array box-UV cube input.
     *
     * <p>Object-form/per-face UV is deliberately not representable. The current profile also
     * requires an integral binary32 box-UV origin; fractional origins remain uncalibrated.</p>
     */
    public record Cube(
            Vec3 origin,
            Vec3 size,
            Vec3 deformation,
            boolean mirror,
            Vec2 boxUv,
            int textureWidth,
            int textureHeight) {
        public Cube {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(deformation, "deformation");
            Objects.requireNonNull(boxUv, "boxUv");
            if (boxUv.u != Math.rint((double) boxUv.u)
                    || boxUv.v != Math.rint((double) boxUv.v)) {
                throw new IllegalArgumentException(
                        "fractional box-UV origins are outside the calibrated array box-UV profile");
            }
            if (textureWidth <= 0 || textureHeight <= 0) {
                throw new IllegalArgumentException("texture dimensions must be positive");
            }
            // Signed/zero sizes, signed per-axis deformation, and negative UVs are valid.
        }

        public Cube(
                Vec3 origin,
                Vec3 size,
                float uniformDeformation,
                boolean mirror,
                Vec2 boxUv,
                int textureWidth,
                int textureHeight) {
            this(
                    origin,
                    size,
                    new Vec3(uniformDeformation, uniformDeformation, uniformDeformation),
                    mirror,
                    boxUv,
                    textureWidth,
                    textureHeight);
        }
    }

    public record AxisEndpoints(float a, float b) {
        public AxisEndpoints {
            requireFinite("endpoint a", a);
            requireFinite("endpoint b", b);
        }
    }

    @FunctionalInterface
    public interface EndpointDeformationConvention {
        /**
         * Receives the first endpoint and the already-rounded second endpoint origin+size.
         */
        AxisEndpoints deform(
                float origin, float secondEndpoint, float size, float deformation);
    }

    /** Black-box-backed for signed spans and positive/negative per-axis deformation. */
    public enum EndpointDeformation implements EndpointDeformationConvention {
        /** Keeps endpoint roles fixed: A=origin-deformation; B=(origin+size)+deformation. */
        RUNTIME_FIXED_ENDPOINTS {
            @Override
            public AxisEndpoints deform(
                    float origin, float secondEndpoint, float size, float deformation) {
                float first = origin - deformation;
                float second = secondEndpoint + deformation;
                return new AxisEndpoints(first, second);
            }
        }
    }

    @FunctionalInterface
    public interface BoxToMatrixSpace {
        Vec3 adapt(Vec3 boxPosition);

        /** Generic research/test adapter; it is not the locked runtime-profile boundary. */
        BoxToMatrixSpace IDENTITY = position -> position;

        /** Black-box-backed box-unit to captured-matrix boundary. */
        BoxToMatrixSpace RUNTIME_ONE_SIXTEENTH = position -> {
            float x = position.x * (1.0f / 16.0f);
            float y = position.y * (1.0f / 16.0f);
            float z = position.z * (1.0f / 16.0f);
            return new Vec3(x, y, z);
        };
    }
    @FunctionalInterface
    public interface AffinePositionTransform {
        /**
         * Applies a verified, finite, already-final affine position matrix once, with implicit
         * homogeneous w=1.
         */
        Vec3 transformPosition(Vec3 matrixSpacePosition);
    }

    public record FinalNodeFrame(
            BoxToMatrixSpace boxToMatrixSpace,
            AffinePositionTransform positionTransform,
            Map<Face, Vec3> finalNormals) {
        public FinalNodeFrame {
            Objects.requireNonNull(boxToMatrixSpace, "boxToMatrixSpace");
            Objects.requireNonNull(positionTransform, "positionTransform");
            Objects.requireNonNull(finalNormals, "finalNormals");

            EnumMap<Face, Vec3> copy = new EnumMap<>(Face.class);
            for (Face face : Face.values()) {
                copy.put(face, Objects.requireNonNull(
                        finalNormals.get(face), "missing final normal for " + face));
            }
            finalNormals = Collections.unmodifiableMap(copy);
        }
    }

    public record Vertex(Vec3 position, Vec2 uv) {
        public Vertex {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(uv, "uv");
        }
    }

    public record Quad(Face sourceFace, List<Vertex> vertices, Vec3 normal) {
        public Quad {
            Objects.requireNonNull(sourceFace, "sourceFace");
            vertices = List.copyOf(vertices);
            if (vertices.size() != 4) {
                throw new IllegalArgumentException("a quad must contain exactly four vertices");
            }
            Objects.requireNonNull(normal, "normal");
        }
    }

    /** UVs associated one-for-one with the compiler's four ordered local face vertices. */
    public record FaceUv(Vec2 first, Vec2 second, Vec2 third, Vec2 fourth) {
        public FaceUv {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            Objects.requireNonNull(third, "third");
            Objects.requireNonNull(fourth, "fourth");
        }

        public List<Vec2> ordered() {
            return List.of(first, second, third, fourth);
        }
    }

    @FunctionalInterface
    public interface BoxUvConvention {
        FaceUv uvFor(Cube cube, Face face);
    }

    /**
     * Box-net placement/orientation and mirror behavior observed in the provenance-locked runtime
     * fixture set. Signed fractional size spans are retained without flooring.
     */
    public static final class RuntimeBoxUv implements BoxUvConvention {
        @Override
        public FaceUv uvFor(Cube cube, Face face) {
            Objects.requireNonNull(cube, "cube");
            Objects.requireNonNull(face, "face");

            float u = cube.boxUv.u;
            float v = cube.boxUv.v;
            float width = cube.size.x;
            float height = cube.size.y;
            float depth = cube.size.z;

            Rect rect = switch (face) {
                case DOWN -> new Rect(add(u, depth), v, width, depth);
                case UP -> new Rect(add(add(u, depth), width), v, width, depth);
                case WEST -> cube.mirror
                        ? new Rect(
                                add(add(u, depth), width),
                                add(v, depth),
                                depth,
                                height)
                        : new Rect(u, add(v, depth), depth, height);
                case NORTH -> new Rect(add(u, depth), add(v, depth), width, height);
                case EAST -> cube.mirror
                        ? new Rect(u, add(v, depth), depth, height)
                        : new Rect(
                                add(add(u, depth), width),
                                add(v, depth),
                                depth,
                                height);
                case SOUTH -> new Rect(
                        add(add(add(u, depth), depth), width),
                        add(v, depth),
                        width,
                        height);
            };

            float right = add(rect.left, rect.width);
            float bottom = add(rect.top, rect.height);
            float mappedLeft = cube.mirror ? right : rect.left;
            float mappedRight = cube.mirror ? rect.left : right;
            if (face == Face.UP) {
                return new FaceUv(
                        normalize(cube, mappedLeft, bottom),
                        normalize(cube, mappedLeft, rect.top),
                        normalize(cube, mappedRight, rect.top),
                        normalize(cube, mappedRight, bottom));
            } else {
                return new FaceUv(
                        normalize(cube, mappedRight, bottom),
                        normalize(cube, mappedRight, rect.top),
                        normalize(cube, mappedLeft, rect.top),
                        normalize(cube, mappedLeft, bottom));
            }
        }

        private static Vec2 normalize(Cube cube, float u, float v) {
            float normalizedU = u / cube.textureWidth;
            float normalizedV = v / cube.textureHeight;
            return new Vec2(normalizedU, normalizedV);
        }

        private record Rect(float left, float top, float width, float height) {
            private Rect {
                requireFinite("rect left", left);
                requireFinite("rect top", top);
                requireFinite("rect width", width);
                requireFinite("rect height", height);
            }
        }
    }

    private static final RuntimeBoxUv RUNTIME_BOX_UV = new RuntimeBoxUv();

    /** Compiles with every seam resolved by the provenance-locked black-box fixture profile. */
    public static List<Quad> compileRuntimeProfile(
            Cube cube, AffinePositionTransform finalPositionMatrix, Map<Face, Vec3> finalNormals) {
        FinalNodeFrame frame = new FinalNodeFrame(
                BoxToMatrixSpace.RUNTIME_ONE_SIXTEENTH,
                finalPositionMatrix,
                finalNormals);
        return compile(
                cube,
                frame,
                EndpointDeformation.RUNTIME_FIXED_ENDPOINTS,
                RUNTIME_BOX_UV);
    }

    public static List<Quad> compile(
            Cube cube,
            FinalNodeFrame frame,
            EndpointDeformationConvention endpointConvention,
            BoxUvConvention uvConvention) {
        Objects.requireNonNull(cube, "cube");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(endpointConvention, "endpointConvention");
        Objects.requireNonNull(uvConvention, "uvConvention");

        AxisEndpoints x = endpoints(
                cube.origin.x, cube.size.x, cube.deformation.x, endpointConvention);
        AxisEndpoints y = endpoints(
                cube.origin.y, cube.size.y, cube.deformation.y, endpointConvention);
        AxisEndpoints z = endpoints(
                cube.origin.z, cube.size.z, cube.deformation.z, endpointConvention);

        List<Quad> result = new ArrayList<>(6);
        for (Face face : Face.values()) {
            Vec3 capturedNormal = frame.finalNormals.get(face);
            Vec3 finalNormal = new Vec3(
                    capturedNormal.x, capturedNormal.y, capturedNormal.z);
            validatedNormalLength(face, finalNormal);
            Vec3[] localPositions = localPositions(face, x, y, z);
            List<Vec2> uvs = uvConvention.uvFor(cube, face).ordered();
            List<Vertex> vertices = new ArrayList<>(4);
            for (int index = 0; index < 4; index++) {
                Vec3 matrixSpace = Objects.requireNonNull(
                        frame.boxToMatrixSpace.adapt(localPositions[index]),
                        "box-to-matrix adapter returned null");
                Vec3 transformed = Objects.requireNonNull(
                        frame.positionTransform.transformPosition(matrixSpace),
                        "position transform returned null");
                vertices.add(new Vertex(transformed, uvs.get(index)));
            }
            TriangleAreas areas = triangleAreas(vertices);
            if (!areas.isZeroArea()) {
                result.add(new Quad(face, vertices, finalNormal));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Applies the same normal and exact-degeneracy gates to captured candidates. This is useful
     * when comparing an unfiltered runtime stream with compiler output.
     */
    public static List<Quad> validateAndFilterCandidates(List<Quad> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        List<Quad> result = new ArrayList<>(candidates.size());
        for (Quad candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            Vec3 capturedNormal = candidate.normal;
            Vec3 finalNormal = new Vec3(
                    capturedNormal.x, capturedNormal.y, capturedNormal.z);
            validatedNormalLength(candidate.sourceFace, finalNormal);
            TriangleAreas areas = triangleAreas(candidate.vertices);
            if (!areas.isZeroArea()) {
                result.add(new Quad(candidate.sourceFace, candidate.vertices, finalNormal));
            }
        }
        return List.copyOf(result);
    }

    private static AxisEndpoints endpoints(
            float origin,
            float size,
            float deformation,
            EndpointDeformationConvention convention) {
        float secondEndpoint = origin + size;
        return Objects.requireNonNull(
                convention.deform(origin, secondEndpoint, size, deformation),
                "endpoint convention returned null");
    }

    private static Vec3[] localPositions(
            Face face, AxisEndpoints x, AxisEndpoints y, AxisEndpoints z) {
        return switch (face) {
            case NORTH -> new Vec3[] {
                point(x.b, y.b, z.a),
                point(x.b, y.a, z.a),
                point(x.a, y.a, z.a),
                point(x.a, y.b, z.a)
            };
            case SOUTH -> new Vec3[] {
                point(x.a, y.b, z.b),
                point(x.a, y.a, z.b),
                point(x.b, y.a, z.b),
                point(x.b, y.b, z.b)
            };
            case EAST -> new Vec3[] {
                point(x.b, y.b, z.b),
                point(x.b, y.a, z.b),
                point(x.b, y.a, z.a),
                point(x.b, y.b, z.a)
            };
            case WEST -> new Vec3[] {
                point(x.a, y.b, z.a),
                point(x.a, y.a, z.a),
                point(x.a, y.a, z.b),
                point(x.a, y.b, z.b)
            };
            case UP -> new Vec3[] {
                point(x.a, y.b, z.a),
                point(x.a, y.b, z.b),
                point(x.b, y.b, z.b),
                point(x.b, y.b, z.a)
            };
            case DOWN -> new Vec3[] {
                point(x.b, y.a, z.a),
                point(x.b, y.a, z.b),
                point(x.a, y.a, z.b),
                point(x.a, y.a, z.a)
            };
        };
    }

    private static Vec3 point(float x, float y, float z) {
        return new Vec3(x, y, z);
    }

    private static TriangleAreas triangleAreas(List<Vertex> vertices) {
        Vec3 p0 = vertices.get(0).position;
        Vec3 p1 = vertices.get(1).position;
        Vec3 p2 = vertices.get(2).position;
        Vec3 p3 = vertices.get(3).position;
        DoubleAreaVector firstArea = triangleCross(p0, p1, p2);
        // The externally fixed split/evaluation order is [0,1,2] and [2,3,0]. Do not rewrite
        // this algebraically: changing the subtraction origins can change double rounding for
        // finite raw-f32 coordinates at widely separated magnitudes.
        DoubleAreaVector secondArea = triangleCross(p2, p3, p0);
        return new TriangleAreas(firstArea, secondArea);
    }

    private static DoubleAreaVector triangleCross(Vec3 origin, Vec3 first, Vec3 second) {
        // Promote the final stored f32 coordinates before subtraction. This is deliberately the
        // one f64 verification calculation in an otherwise f32 replay pipeline.
        double firstX = (double) first.x - (double) origin.x;
        double firstY = (double) first.y - (double) origin.y;
        double firstZ = (double) first.z - (double) origin.z;
        double secondX = (double) second.x - (double) origin.x;
        double secondY = (double) second.y - (double) origin.y;
        double secondZ = (double) second.z - (double) origin.z;
        double crossX = firstY * secondZ - firstZ * secondY;
        double crossY = firstZ * secondX - firstX * secondZ;
        double crossZ = firstX * secondY - firstY * secondX;
        return new DoubleAreaVector(crossX, crossY, crossZ);
    }

    private static double validatedNormalLength(Face face, Vec3 normal) {
        double x = normal.x;
        double y = normal.y;
        double z = normal.z;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (!Double.isFinite(length)
                || length < MIN_NORMAL_LENGTH
                || length > MAX_NORMAL_LENGTH) {
            throw new IllegalArgumentException(
                    "final normal for " + face + " has invalid length " + length
                            + "; expected [" + MIN_NORMAL_LENGTH + ", "
                            + MAX_NORMAL_LENGTH + "]");
        }
        return length;
    }

    private record TriangleAreas(DoubleAreaVector first, DoubleAreaVector second) {
        private TriangleAreas {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
        }

        private boolean isZeroArea() {
            return first.isZero() && second.isZero();
        }
    }

    private record DoubleAreaVector(double x, double y, double z) {
        private DoubleAreaVector {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("non-finite transformed triangle cross");
            }
        }

        private boolean isZero() {
            return x == 0.0D && y == 0.0D && z == 0.0D;
        }

    }

    /**
     * Canonical raw-f32 quad encoding. Cyclic rotations are considered; reversed winding is not.
     * Source-face identity is intentionally omitted so callers can compare runtime quad multisets.
     */
    public static CanonicalQuad canonicalize(Quad quad) {
        Objects.requireNonNull(quad, "quad");
        byte[] best = null;
        for (int rotation = 0; rotation < 4; rotation++) {
            byte[] candidate = encodeRotation(quad, rotation);
            if (best == null || compareUnsigned(candidate, best) < 0) {
                best = candidate;
            }
        }
        return new CanonicalQuad(best);
    }

    public static List<CanonicalQuad> canonicalizeMultiset(List<Quad> quads) {
        Objects.requireNonNull(quads, "quads");
        List<CanonicalQuad> result = quads.stream()
                .map(BedrockBoxReplay::canonicalize)
                .sorted()
                .toList();
        return List.copyOf(result);
    }

    public static byte[] canonicalSha256(List<Quad> quads) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<CanonicalQuad> canonical = canonicalizeMultiset(quads);
            digest.update(ByteBuffer.allocate(Integer.BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(canonical.size())
                    .array());
            for (CanonicalQuad quad : canonical) {
                digest.update(quad.bytes);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 unavailable", impossible);
        }
    }

    public static final class CanonicalQuad implements Comparable<CanonicalQuad> {
        private final byte[] bytes;

        private CanonicalQuad(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public int compareTo(CanonicalQuad other) {
            return compareUnsigned(bytes, other.bytes);
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof CanonicalQuad other && Arrays.equals(bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return java.util.HexFormat.of().formatHex(bytes);
        }
    }

    private static byte[] encodeRotation(Quad quad, int rotation) {
        // 4 vertices * (3 position + 2 UV) words + 3 normal words.
        ByteBuffer encoded = ByteBuffer.allocate((4 * 5 + 3) * Float.BYTES)
                .order(ByteOrder.BIG_ENDIAN);
        for (int offset = 0; offset < 4; offset++) {
            Vertex vertex = quad.vertices.get((rotation + offset) & 3);
            putRawFloat(encoded, vertex.position.x);
            putRawFloat(encoded, vertex.position.y);
            putRawFloat(encoded, vertex.position.z);
            putRawFloat(encoded, vertex.uv.u);
            putRawFloat(encoded, vertex.uv.v);
        }
        putRawFloat(encoded, quad.normal.x);
        putRawFloat(encoded, quad.normal.y);
        putRawFloat(encoded, quad.normal.z);
        return encoded.array();
    }

    private static void putRawFloat(ByteBuffer destination, float value) {
        destination.putInt(Float.floatToRawIntBits(value));
    }

    private static int compareUnsigned(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(
                    Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static float add(float left, float right) {
        return left + right;
    }

    private static void requireFinite(String name, float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
