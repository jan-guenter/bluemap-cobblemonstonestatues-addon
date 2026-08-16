/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry;

import java.util.List;
import java.util.Objects;

/** Strict immutable subset of the Bedrock 1.12 box-model schema used by Cobblemon. */
public record BedrockGeometry(
        String formatVersion,
        String identifier,
        int textureWidth,
        int textureHeight,
        List<Bone> bones,
        int cubeCount,
        int locatorCount
) {

    public BedrockGeometry {
        Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(identifier, "identifier");
        bones = List.copyOf(bones);
        if (formatVersion.isBlank() || identifier.isBlank()
                || textureWidth < 1 || textureHeight < 1 || bones.isEmpty()
                || cubeCount < 0 || locatorCount < 0) {
            throw new IllegalArgumentException("invalid Bedrock geometry summary");
        }
    }

    public record Bone(
            String name,
            String parent,
            Vec3 pivot,
            Vec3 rotationDegrees,
            List<Cube> cubes,
            List<Locator> locators
    ) {

        public Bone {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(pivot, "pivot");
            cubes = List.copyOf(cubes);
            locators = List.copyOf(locators);
        }
    }

    public record Cube(
            Vec3 origin,
            Vec3 size,
            Vec3 pivot,
            Vec3 rotationDegrees,
            int u,
            int v,
            float inflate,
            boolean mirror
    ) {

        public Cube {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(size, "size");
        }
    }

    public record Locator(String name, Vec3 offset, Vec3 rotationDegrees) {

        public Locator {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(offset, "offset");
            Objects.requireNonNull(rotationDegrees, "rotationDegrees");
        }
    }

    public record Vec3(float x, float y, float z) {

        public Vec3 {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new IllegalArgumentException("non-finite Bedrock vector");
            }
        }
    }
}
