/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry;

import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BakedGeometryTopology.BoundGeometry;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BakedGeometryTopology.BoundNode;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.JomlAdapters;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapts a topology-bound runtime pose to the independently specified clean-room box compiler.
 *
 * <p>The runtime matrices already contain Stone's form base scale. This adapter applies each
 * matrix exactly once and never reconstructs a client pose stack.</p>
 */
public final class CleanRoomGeometryCompiler {

    private static final int FACES_PER_BOX = 6;
    private static final int MAX_SOURCE_QUADS = 50_000;

    private CleanRoomGeometryCompiler() {
    }

    public static Compilation compile(BoundGeometry geometry, int tintArgb) {
        Objects.requireNonNull(geometry, "geometry");
        List<QuadMultisetDigest.Quad> retained = new ArrayList<>();
        int sourceQuadCount = 0;
        for (BoundNode node : geometry.nodes()) {
            PoseState.RuntimeFrame runtimeFrame = node.pose().runtimeFrame();
            if (runtimeFrame == null) {
                continue;
            }
            if (node.geometry().boxes().isEmpty()) {
                throw new IllegalArgumentException("runtime frame has no bound boxes");
            }
            Matrix4f matrix = matrix(runtimeFrame.positionTransform());
            for (BakedGeometryTopology.Box box : node.geometry().boxes()) {
                sourceQuadCount = Math.addExact(sourceQuadCount, FACES_PER_BOX);
                if (sourceQuadCount > MAX_SOURCE_QUADS) {
                    throw new IllegalArgumentException("clean-room source quad budget exceeded");
                }
                BedrockBoxReplay.Cube cube = new BedrockBoxReplay.Cube(
                        vector(box.origin()), vector(box.size()), box.inflate(), box.mirror(),
                        new BedrockBoxReplay.Vec2(box.u(), box.v()),
                        geometry.textureWidth(), geometry.textureHeight()
                );
                List<BedrockBoxReplay.Quad> compiled =
                        BedrockBoxReplay.compileRuntimeProfile(
                                cube, JomlAdapters.finalPositionMatrix(matrix),
                                normals(runtimeFrame, box.mirror())
                        );
                compiled.stream().map(CleanRoomGeometryCompiler::quad).forEach(retained::add);
            }
        }
        int dropped = Math.subtractExact(sourceQuadCount, retained.size());
        QuadMultisetDigest.Bounds bounds = bounds(retained);
        QuadMultisetDigest.Result identity = QuadMultisetDigest.digest(
                retained, tintArgb, dropped, bounds
        );
        return new Compilation(retained, identity);
    }

    private static Matrix4f matrix(PoseState.PositionMatrix value) {
        return new Matrix4f(
                value.m00(), value.m01(), value.m02(), value.m03(),
                value.m10(), value.m11(), value.m12(), value.m13(),
                value.m20(), value.m21(), value.m22(), value.m23(),
                value.m30(), value.m31(), value.m32(), value.m33()
        );
    }

    private static Map<BedrockBoxReplay.Face, BedrockBoxReplay.Vec3> normals(
            PoseState.RuntimeFrame frame, boolean mirrored
    ) {
        PoseState.CardinalNormals value = frame.cardinalNormals();
        EnumMap<BedrockBoxReplay.Face, BedrockBoxReplay.Vec3> result =
                new EnumMap<>(BedrockBoxReplay.Face.class);
        result.put(BedrockBoxReplay.Face.DOWN,
                vector(value.downX(), value.downY(), value.downZ()));
        result.put(BedrockBoxReplay.Face.UP,
                vector(value.upX(), value.upY(), value.upZ()));
        result.put(BedrockBoxReplay.Face.WEST,
                vector(value.westX(), value.westY(), value.westZ()));
        result.put(BedrockBoxReplay.Face.NORTH,
                vector(value.northX(), value.northY(), value.northZ()));
        result.put(BedrockBoxReplay.Face.EAST,
                vector(value.eastX(), value.eastY(), value.eastZ()));
        result.put(BedrockBoxReplay.Face.SOUTH,
                vector(value.southX(), value.southY(), value.southZ()));
        if (mirrored) {
            PoseState.MirrorZeroXNormals mirror = frame.mirrorZeroXNormals();
            result.put(BedrockBoxReplay.Face.DOWN,
                    vector(mirror.downX(), mirror.downY(), mirror.downZ()));
            result.put(BedrockBoxReplay.Face.UP,
                    vector(mirror.upX(), mirror.upY(), mirror.upZ()));
            result.put(BedrockBoxReplay.Face.NORTH,
                    vector(mirror.northX(), mirror.northY(), mirror.northZ()));
            result.put(BedrockBoxReplay.Face.SOUTH,
                    vector(mirror.southX(), mirror.southY(), mirror.southZ()));
        }
        return result;
    }

    private static QuadMultisetDigest.Quad quad(BedrockBoxReplay.Quad value) {
        return new QuadMultisetDigest.Quad(
                vector(value.normal()),
                value.vertices().stream().map(vertex -> new QuadMultisetDigest.Vertex(
                        vector(vertex.position()), vertex.uv().u(), vertex.uv().v()
                )).toList()
        );
    }

    private static QuadMultisetDigest.Bounds bounds(List<QuadMultisetDigest.Quad> quads) {
        if (quads.isEmpty()) {
            throw new IllegalArgumentException("clean-room replay retained no quads");
        }
        QuadMultisetDigest.Vec3 minimum = quads.getFirst().vertices().getFirst().position();
        QuadMultisetDigest.Vec3 maximum = minimum;
        for (QuadMultisetDigest.Quad quad : quads) {
            for (QuadMultisetDigest.Vertex vertex : quad.vertices()) {
                QuadMultisetDigest.Vec3 point = vertex.position();
                minimum = new QuadMultisetDigest.Vec3(
                        Math.min(minimum.x(), point.x()),
                        Math.min(minimum.y(), point.y()),
                        Math.min(minimum.z(), point.z())
                );
                maximum = new QuadMultisetDigest.Vec3(
                        Math.max(maximum.x(), point.x()),
                        Math.max(maximum.y(), point.y()),
                        Math.max(maximum.z(), point.z())
                );
            }
        }
        return new QuadMultisetDigest.Bounds(minimum, maximum);
    }

    private static BedrockBoxReplay.Vec3 vector(BedrockGeometry.Vec3 value) {
        return vector(value.x(), value.y(), value.z());
    }

    private static BedrockBoxReplay.Vec3 vector(float x, float y, float z) {
        return new BedrockBoxReplay.Vec3(x, y, z);
    }

    private static QuadMultisetDigest.Vec3 vector(BedrockBoxReplay.Vec3 value) {
        return new QuadMultisetDigest.Vec3(value.x(), value.y(), value.z());
    }

    /** Retained replay quads plus their versioned order-insensitive identity. */
    public record Compilation(
            List<QuadMultisetDigest.Quad> quads,
            QuadMultisetDigest.Result identity
    ) {
        public Compilation {
            quads = List.copyOf(quads);
            Objects.requireNonNull(identity, "identity");
            if (quads.size() != identity.retainedQuadCount()) {
                throw new IllegalArgumentException("clean-room retained count mismatch");
            }
        }
    }
}
