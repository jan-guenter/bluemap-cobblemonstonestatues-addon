/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.model;

import de.bluecolored.bluemap.core.util.Key;

import java.util.List;
import java.util.Objects;

/** Immutable operator-local geometry compiled from an active exact GEO file. */
public record StatueModel(Key texture, List<Quad> quads, Bounds bounds) {

    public StatueModel {
        Objects.requireNonNull(texture, "texture");
        quads = List.copyOf(quads);
        Objects.requireNonNull(bounds, "bounds");
        if (quads.isEmpty()) {
            throw new IllegalArgumentException("empty statue mesh");
        }
    }

    public record Quad(
            Vertex first, Vertex second, Vertex third, Vertex fourth, Vec3 storedNormal
    ) {
        public Quad {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            Objects.requireNonNull(third, "third");
            Objects.requireNonNull(fourth, "fourth");
            Objects.requireNonNull(storedNormal, "storedNormal");
            double length = Math.sqrt(
                    storedNormal.x * storedNormal.x
                            + storedNormal.y * storedNormal.y
                            + storedNormal.z * storedNormal.z
            );
            Vec3 firstCross = second.position().subtract(first.position())
                    .cross(third.position().subtract(first.position()));
            Vec3 secondCross = fourth.position().subtract(third.position())
                    .cross(first.position().subtract(third.position()));
            if (isExactZero(firstCross) && isExactZero(secondCross)) {
                throw new IllegalArgumentException("degenerate statue quad");
            }
            if (length < 0.5D || length > 1.5D) {
                throw new IllegalArgumentException("stored mesh normal is invalid");
            }
        }

        private static boolean isExactZero(Vec3 value) {
            return value.x == 0D && value.y == 0D && value.z == 0D;
        }

        public Vec3 normal() {
            return storedNormal.normalizedOr(new Vec3(0D, 1D, 0D));
        }

    }

    public record Vertex(Vec3 position, float u, float v) {
        public Vertex {
            Objects.requireNonNull(position, "position");
            if (!Float.isFinite(u) || !Float.isFinite(v)
                    || Math.abs(u) > 64F || Math.abs(v) > 64F
                    || Math.abs(position.x()) > 4_096D
                    || Math.abs(position.y()) > 4_096D
                    || Math.abs(position.z()) > 4_096D) {
                throw new IllegalArgumentException("statue vertex outside safety budget");
            }
        }
    }

    public record Vec3(double x, double y, double z) {
        public Vec3 {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("non-finite statue coordinate");
            }
        }

        public Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        public Vec3 subtract(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }

        public Vec3 scale(double factor) {
            return new Vec3(x * factor, y * factor, z * factor);
        }

        public Vec3 multiply(Vec3 other) {
            return new Vec3(x * other.x, y * other.y, z * other.z);
        }

        public Vec3 cross(Vec3 other) {
            return new Vec3(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        public double lengthSquared() {
            return x * x + y * y + z * z;
        }

        public Vec3 normalizedOr(Vec3 fallback) {
            double length = Math.sqrt(x * x + y * y + z * z);
            return length <= 1.0E-12D ? fallback : scale(1D / length);
        }

        public Vec3 rotateX(double radians) {
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            return new Vec3(x, y * cosine - z * sine, y * sine + z * cosine);
        }

        public Vec3 rotateY(double radians) {
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            return new Vec3(x * cosine + z * sine, y, -x * sine + z * cosine);
        }

        public Vec3 rotateZ(double radians) {
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            return new Vec3(x * cosine - y * sine, x * sine + y * cosine, z);
        }

        /** Matches Quaternionf.rotationZYX(z, y, x) on a column vector. */
        public Vec3 rotateZYX(Vec3 radians) {
            return rotateX(radians.x).rotateY(radians.y).rotateZ(radians.z);
        }

        public Vec3 rotateWorldY(double radians) {
            return rotateY(radians);
        }
    }

    public record Bounds(Vec3 minimum, Vec3 maximum) {
        public Bounds {
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            if (minimum.x > maximum.x || minimum.y > maximum.y
                    || minimum.z > maximum.z) {
                throw new IllegalArgumentException("invalid statue bounds");
            }
        }
    }
}
