// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Jan Guenter
/*
 * Clean-room provenance: SPEC.md; Microsoft Bedrock geometry 1.12 schema/visual docs;
 * sanitized fixture SHA-256 b287b9ff5b6bdd0aa9d7bf04865a1d9bb28f772e59b10333c224ea552fbc848f.
 */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom;

import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Optional JOML 1.10.5 adapter for the neutral clean-room compiler API. */
public final class JomlAdapters {
    private JomlAdapters() {
    }

    /**
     * Snapshots the already-final matrix and applies JOML's f32 {@code transformPosition} exactly
     * once for every compiler vertex.
     */
    public static BedrockBoxReplay.AffinePositionTransform finalPositionMatrix(Matrix4fc matrix) {
        Matrix4f snapshot = new Matrix4f(Objects.requireNonNull(matrix, "matrix"));
        for (float element : snapshot.get(new float[16])) {
            if (!Float.isFinite(element)) {
                throw new IllegalArgumentException("final position matrix must be finite");
            }
        }
        if (snapshot.m03() != 0.0f
                || snapshot.m13() != 0.0f
                || snapshot.m23() != 0.0f
                || snapshot.m33() != 1.0f) {
            throw new IllegalArgumentException("final position matrix must be affine");
        }
        return position -> {
            Vector3f destination = snapshot.transformPosition(
                    position.x(), position.y(), position.z(), new Vector3f());
            return new BedrockBoxReplay.Vec3(
                    destination.x, destination.y, destination.z);
        };
    }
}
