/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Quad;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Vec3;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Vertex;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.List;

/** Strict receiver for Stone's one expected entityCutout route. */
final class CaptureBuffer implements MultiBufferSource, VertexConsumer {

    private final RenderType expectedType;
    private final boolean allowSecondRequest;
    private final int expectedLight;
    private final int expectedOverlay;
    private final List<Vertex> vertices = new ArrayList<>();
    private final List<Vec3> normals = new ArrayList<>();
    private Pending pending;
    private int bufferRequests;

    CaptureBuffer(RenderType expectedType, int expectedLight, int expectedOverlay) {
        this(expectedType, expectedLight, expectedOverlay, false);
    }

    CaptureBuffer(
            RenderType expectedType,
            int expectedLight,
            int expectedOverlay,
            boolean allowSecondRequest
    ) {
        this.expectedType = expectedType;
        this.expectedLight = expectedLight;
        this.expectedOverlay = expectedOverlay;
        this.allowSecondRequest = allowSecondRequest;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        int requests = ++bufferRequests;
        if (!expectedType.equals(renderType)
                || requests != 1 && !(allowSecondRequest && requests == 2)) {
            throw new IllegalArgumentException("unexpected render route");
        }
        return this;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (pending != null) {
            throw new IllegalArgumentException("vertex stream began before previous completion");
        }
        pending = new Pending(new Vec3(x, y, z));
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        requirePending().color = alpha << 24 | red << 16 | green << 8 | blue;
        requirePending().colorSet = true;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        requirePending().u = u;
        requirePending().v = v;
        requirePending().uvSet = true;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        if ((u & 0xFFFF) != (expectedOverlay & 0xFFFF)
                || (v & 0xFFFF) != (expectedOverlay >>> 16 & 0xFFFF)) {
            throw new IllegalArgumentException("overlay sentinel changed");
        }
        requirePending().overlaySet = true;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        if ((u & 0xFFFF) != (expectedLight & 0xFFFF)
                || (v & 0xFFFF) != (expectedLight >>> 16 & 0xFFFF)) {
            throw new IllegalArgumentException("light sentinel changed");
        }
        requirePending().lightSet = true;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        Pending value = requirePending();
        if (!value.colorSet || !value.uvSet || !value.overlaySet || !value.lightSet) {
            throw new IllegalArgumentException("incomplete captured vertex");
        }
        vertices.add(new Vertex(value.position, value.u, value.v, value.color));
        normals.add(new Vec3(x, y, z));
        pending = null;
        return this;
    }

    CapturedMesh finish() {
        int expectedRequests = allowSecondRequest ? 2 : 1;
        if (bufferRequests != expectedRequests || pending != null || vertices.isEmpty()
                || vertices.size() % 4 != 0 || vertices.size() != normals.size()) {
            throw new IllegalArgumentException("captured stream is not complete quads");
        }
        int tint = vertices.getFirst().argb();
        List<Quad> quads = new ArrayList<>(vertices.size() / 4);
        int droppedDegenerateQuads = 0;
        for (int index = 0; index < vertices.size(); index += 4) {
            List<Vertex> quad = List.copyOf(vertices.subList(index, index + 4));
            Vec3 normal = normals.get(index);
            if (quad.stream().anyMatch(vertex -> vertex.argb() != tint)
                    || normals.subList(index, index + 4).stream().anyMatch(candidate ->
                    Float.floatToIntBits(candidate.x()) != Float.floatToIntBits(normal.x())
                            || Float.floatToIntBits(candidate.y())
                            != Float.floatToIntBits(normal.y())
                            || Float.floatToIntBits(candidate.z())
                            != Float.floatToIntBits(normal.z()))) {
                throw new IllegalArgumentException("captured pose tint/normal is nonuniform");
            }
            java.util.Optional<Quad> retained = Quad.retain(normal, quad);
            if (retained.isPresent()) {
                quads.add(retained.orElseThrow());
            } else {
                droppedDegenerateQuads++;
            }
        }
        if (quads.isEmpty()) {
            throw new IllegalArgumentException(
                    "captured stream has no visible quads: sourceQuads="
                            + vertices.size() / 4
                            + ", droppedDegenerateQuads=" + droppedDegenerateQuads
            );
        }
        return new CapturedMesh(quads, tint, droppedDegenerateQuads);
    }

    private Pending requirePending() {
        if (pending == null) {
            throw new IllegalArgumentException("vertex attribute without active vertex");
        }
        return pending;
    }

    private static final class Pending {
        private final Vec3 position;
        private int color;
        private float u;
        private float v;
        private boolean colorSet;
        private boolean uvSet;
        private boolean overlaySet;
        private boolean lightSet;

        Pending(Vec3 position) {
            this.position = position;
        }
    }
}
