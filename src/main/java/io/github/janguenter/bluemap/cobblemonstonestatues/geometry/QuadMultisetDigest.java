/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Order-insensitive retained-quad multiset identity with no winding reversal. */
public final class QuadMultisetDigest {

    public static final int VERSION = 1;
    private static final int MAX_QUADS = 50_000;
    private static final int FLOATS_PER_QUAD = 23;
    private static final int BYTES_PER_QUAD = FLOATS_PER_QUAD * Float.BYTES;
    private static final byte[] DOMAIN =
            "atmons-stone-quad-multiset-v1".getBytes(StandardCharsets.US_ASCII);

    private QuadMultisetDigest() {
    }

    public static Result digest(
            List<Quad> retainedSource,
            int tintArgb,
            int droppedDegenerateQuadCount,
            Bounds suppliedBounds
    ) {
        List<Quad> quads = List.copyOf(retainedSource);
        int sourceQuadCount = Math.addExact(quads.size(), droppedDegenerateQuadCount);
        if (quads.isEmpty() || droppedDegenerateQuadCount < 0
                || sourceQuadCount > MAX_QUADS) {
            throw new IllegalArgumentException("quad multiset count outside budget");
        }
        Bounds computedBounds = bounds(quads);
        if (!computedBounds.sameRaw(Objects.requireNonNull(
                suppliedBounds, "suppliedBounds"
        ))) {
            throw new IllegalArgumentException("quad multiset bounds mismatch");
        }
        List<byte[]> records = new ArrayList<>(quads.size());
        for (Quad quad : quads) {
            records.add(canonicalRotation(quad));
        }
        records.sort(QuadMultisetDigest::compareUnsigned);
        MessageDigest digest = sha256();
        digest.update(DOMAIN);
        digest.update(littleEndianInteger(VERSION));
        digest.update(littleEndianInteger(sourceQuadCount));
        digest.update(littleEndianInteger(records.size()));
        digest.update(littleEndianInteger(droppedDegenerateQuadCount));
        digest.update(littleEndianInteger(tintArgb));
        digest.update(computedBounds.encode());
        records.forEach(digest::update);
        return new Result(
                HexFormat.of().formatHex(digest.digest()), sourceQuadCount,
                records.size(), droppedDegenerateQuadCount, tintArgb, computedBounds
        );
    }

    private static Bounds bounds(List<Quad> quads) {
        Vec3 minimum = quads.getFirst().vertices().getFirst().position();
        Vec3 maximum = minimum;
        for (Quad quad : quads) {
            for (Vertex vertex : quad.vertices()) {
                Vec3 point = vertex.position();
                minimum = new Vec3(
                        Math.min(minimum.x(), point.x()),
                        Math.min(minimum.y(), point.y()),
                        Math.min(minimum.z(), point.z())
                );
                maximum = new Vec3(
                        Math.max(maximum.x(), point.x()),
                        Math.max(maximum.y(), point.y()),
                        Math.max(maximum.z(), point.z())
                );
            }
        }
        return new Bounds(minimum, maximum);
    }

    private static byte[] canonicalRotation(Quad quad) {
        byte[] best = null;
        for (int rotation = 0; rotation < 4; rotation++) {
            ByteBuffer output = ByteBuffer.allocate(BYTES_PER_QUAD)
                    .order(ByteOrder.LITTLE_ENDIAN);
            put(output, quad.storedNormal());
            for (int offset = 0; offset < 4; offset++) {
                Vertex vertex = quad.vertices().get((rotation + offset) & 3);
                put(output, vertex.position());
                output.putFloat(vertex.u()).putFloat(vertex.v());
            }
            byte[] candidate = output.array();
            if (best == null || compareUnsigned(candidate, best) < 0) {
                best = candidate;
            }
        }
        return Objects.requireNonNull(best, "canonical quad rotation");
    }

    private static int compareUnsigned(byte[] first, byte[] second) {
        for (int index = 0; index < first.length; index++) {
            int comparison = Integer.compare(first[index] & 0xff, second[index] & 0xff);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static void put(ByteBuffer output, Vec3 value) {
        output.putFloat(value.x()).putFloat(value.y()).putFloat(value.z());
    }

    private static byte[] littleEndianInteger(int value) {
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value).array();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Result(
            String sha256,
            int sourceQuadCount,
            int retainedQuadCount,
            int droppedDegenerateQuadCount,
            int tintArgb,
            Bounds bounds
    ) {
        public Result {
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")
                    || retainedQuadCount < 1 || droppedDegenerateQuadCount < 0
                    || Math.addExact(retainedQuadCount, droppedDegenerateQuadCount)
                    != sourceQuadCount || sourceQuadCount > MAX_QUADS) {
                throw new IllegalArgumentException("invalid quad multiset identity");
            }
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    public record Bounds(Vec3 minimum, Vec3 maximum) {
        public Bounds {
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            if (minimum.x() > maximum.x() || minimum.y() > maximum.y()
                    || minimum.z() > maximum.z()) {
                throw new IllegalArgumentException("invalid quad multiset bounds");
            }
        }

        private byte[] encode() {
            ByteBuffer output = ByteBuffer.allocate(6 * Float.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN);
            put(output, minimum);
            put(output, maximum);
            return output.array();
        }

        private boolean sameRaw(Bounds other) {
            return QuadMultisetDigest.sameRaw(minimum, other.minimum)
                    && QuadMultisetDigest.sameRaw(maximum, other.maximum);
        }
    }

    public record Quad(Vec3 storedNormal, List<Vertex> vertices) {
        public Quad {
            Objects.requireNonNull(storedNormal, "storedNormal");
            vertices = List.copyOf(vertices);
            if (vertices.size() != 4) {
                throw new IllegalArgumentException("quad multiset primitive is not a quad");
            }
        }
    }

    public record Vertex(Vec3 position, float u, float v) {
        public Vertex {
            Objects.requireNonNull(position, "position");
            requireFinite(u, "u");
            requireFinite(v, "v");
        }
    }

    public record Vec3(float x, float y, float z) {
        public Vec3 {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(z, "z");
        }
    }

    private static void requireFinite(float value, String label) {
        if (!Float.isFinite(value) || Math.abs(value) > 1_000_000F) {
            throw new IllegalArgumentException("invalid quad multiset " + label);
        }
    }

    private static boolean sameRaw(Vec3 first, Vec3 second) {
        return sameRaw(first.x(), second.x()) && sameRaw(first.y(), second.y())
                && sameRaw(first.z(), second.z());
    }

    private static boolean sameRaw(float first, float second) {
        return Float.floatToRawIntBits(first) == Float.floatToRawIntBits(second);
    }
}
