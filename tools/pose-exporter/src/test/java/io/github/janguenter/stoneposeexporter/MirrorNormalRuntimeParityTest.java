/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BakedGeometryTopology;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BedrockGeometry;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.CleanRoomGeometryCompiler;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Black-box parity against the exact pinned Minecraft ModelPart public API. */
class MirrorNormalRuntimeParityTest {

    private static final int TEXTURE_SIZE = 64;

    @Test
    void matchesMirroredRuntimeUnderIdentityTransform() {
        assertRuntimeParity(PartPose.ZERO, false);
    }

    @Test
    void preservesMirroredNorthNegativeZeroUnderRotatedUntrustedNormalTransform() {
        PoseState.RuntimeFrame frame = assertRuntimeParity(
                PartPose.offsetAndRotation(
                        1.25F, -2.5F, 3.75F, 0F, 0F,
                        (float) Math.toRadians(-143.04137D)
                ),
                true
        );
        assertEquals(
                Float.floatToRawIntBits(-0.0F),
                Float.floatToRawIntBits(frame.mirrorZeroXNormals().northX())
        );
        assertEquals(
                Float.floatToRawIntBits(0.0F),
                Float.floatToRawIntBits(frame.cardinalNormals().northX())
        );
    }

    @Test
    void archaludonLikeZeroYAxisKeepsFinitePositionButHasInvalidRuntimeNormals() {
        PoseStack stack = new PoseStack();
        stack.translate(0.25F, -0.5F, 0.75F);
        stack.mulPose(new org.joml.Quaternionf().rotationZYX(0.17F, -0.31F, 0.23F));
        stack.scale(1F, 0F, 1F);
        stack.mulPose(new org.joml.Quaternionf().rotationZYX(-0.19F, 0.29F, -0.37F));

        PoseState.PositionMatrix position = PoseCaptureHook.capturePosition(stack.last());

        assertEquals(Float.floatToRawIntBits(1F),
                Float.floatToRawIntBits(position.m33()));
        assertTrue(PoseCaptureHook.captureFrame(stack.last(), true).suppressDraw());
        assertThrows(IllegalArgumentException.class,
                () -> PoseCaptureHook.captureRuntimeFrame(stack.last()));
    }

    @Test
    void inheritedZeroZAxisAfterChildRotationUsesTheNarrowSuppressionDecision() {
        PoseStack stack = new PoseStack();
        stack.scale(1F, 1F, 0F);
        stack.mulPose(new org.joml.Quaternionf().rotationZYX(0.41F, -0.27F, 0.13F));

        ModelTreeCapture.FrameDecision decision =
                PoseCaptureHook.captureFrame(stack.last(), true);

        assertTrue(decision.suppressDraw());
    }

    @Test
    void uniformZeroScaleKeepsFiniteNormalsAndIsNotSuppressed() {
        PoseStack stack = new PoseStack();
        stack.scale(0F, 0F, 0F);

        ModelTreeCapture.FrameDecision decision =
                PoseCaptureHook.captureFrame(stack.last(), true);

        assertFalse(decision.suppressDraw());
        assertEquals(0F, decision.runtimeFrame().positionTransform().m00());
        assertEquals(1F, decision.runtimeFrame().cardinalNormals().upY());
    }

    @Test
    void nonFiniteNormalFromNonzeroScaleRemainsFatal() {
        PoseStack stack = new PoseStack();
        stack.scale(1F, Float.MIN_VALUE, 1F);

        assertThrows(IllegalArgumentException.class,
                () -> PoseCaptureHook.captureFrame(stack.last(), false));
    }

    @Test
    void finiteInvalidNormalsRemainFatalEvenWithExactZeroScaleAncestry() {
        PoseStack stack = new PoseStack();
        stack.last().normal().zero();

        assertThrows(IllegalArgumentException.class,
                () -> PoseCaptureHook.captureFrame(stack.last(), true));
    }

    private static PoseState.RuntimeFrame assertRuntimeParity(
            PartPose partPose, boolean outerTransform
    ) {
        CubeListBuilder cubes = CubeListBuilder.create()
                .texOffs(9, 11)
                .mirror(true)
                .addBox(-2.75F, 1.125F, -3.375F, 2F, 3F, 5F);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("fixture", cubes, partPose);
        ModelPart part = LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE)
                .bakeRoot()
                .getChild("fixture");
        PoseStack stack = new PoseStack();
        if (outerTransform) {
            stack.translate(2.5F, -1.25F, 0.625F);
            stack.scale(-1.25F, 0.75F, 2F);
        }

        PoseState.RuntimeFrame frame = captureFrame(stack, part);
        RawConsumer runtime = new RawConsumer();
        part.render(stack, runtime, 0, 0, -1);
        List<BedrockBoxReplay.CanonicalQuad> runtimeTokens = canonical(runtime.finish());
        CleanRoomGeometryCompiler.Compilation replay = CleanRoomGeometryCompiler.compile(
                bound(frame), -1
        );
        List<BedrockBoxReplay.CanonicalQuad> replayTokens = canonicalReplay(replay.quads());

        assertEquals(runtimeTokens, replayTokens);
        return frame;
    }

    private static PoseState.RuntimeFrame captureFrame(PoseStack stack, ModelPart part) {
        stack.pushPose();
        try {
            part.translateAndRotate(stack);
            PoseStack.Pose pose = stack.last();
            PoseState.RuntimeFrame frame = PoseCaptureHook.captureRuntimeFrame(pose);
            assertMirroredXFacesUseOppositeOrdinaryCardinals(
                    pose, frame.cardinalNormals()
            );
            return frame;
        } finally {
            stack.popPose();
        }
    }

    private static Vector3f normal(PoseStack.Pose pose, float x, float y, float z) {
        return pose.transformNormal(x, y, z, new Vector3f());
    }

    private static void assertMirroredXFacesUseOppositeOrdinaryCardinals(
            PoseStack.Pose pose, PoseState.CardinalNormals ordinary
    ) {
        assertRawVectorEquals(
                normal(pose, 1F, 0F, 0F),
                ordinary.eastX(), ordinary.eastY(), ordinary.eastZ()
        );
        assertRawVectorEquals(
                normal(pose, -1F, 0F, 0F),
                ordinary.westX(), ordinary.westY(), ordinary.westZ()
        );
    }

    private static void assertRawVectorEquals(
            Vector3f actual, float expectedX, float expectedY, float expectedZ
    ) {
        assertEquals(Float.floatToRawIntBits(expectedX), Float.floatToRawIntBits(actual.x));
        assertEquals(Float.floatToRawIntBits(expectedY), Float.floatToRawIntBits(actual.y));
        assertEquals(Float.floatToRawIntBits(expectedZ), Float.floatToRawIntBits(actual.z));
    }

    private static BakedGeometryTopology.BoundGeometry bound(
            PoseState.RuntimeFrame frame
    ) {
        BakedGeometryTopology.Box box = new BakedGeometryTopology.Box(
                vector(-2.75F, 1.125F, -3.375F), vector(2F, 3F, 5F),
                9, 11, 0F, true
        );
        BakedGeometryTopology.Node geometry = new BakedGeometryTopology.Node(
                "fixture", "/fixture", null,
                new BakedGeometryTopology.Transform(
                        vector(0F, 0F, 0F), vector(0F, 0F, 0F), vector(1F, 1F, 1F)
                ),
                List.of(box)
        );
        PoseState.Node pose = new PoseState.Node(
                "/fixture", -1, 0, frame, true, false
        );
        return new BakedGeometryTopology.BoundGeometry(
                "geometry.mirror_fixture", TEXTURE_SIZE, TEXTURE_SIZE,
                List.of(new BakedGeometryTopology.BoundNode(geometry, pose))
        );
    }

    private static List<BedrockBoxReplay.CanonicalQuad> canonical(
            List<RuntimeVertex> vertices
    ) {
        List<BedrockBoxReplay.Quad> candidates = new ArrayList<>();
        for (int offset = 0; offset < vertices.size(); offset += 4) {
            RuntimeVertex first = vertices.get(offset);
            List<BedrockBoxReplay.Vertex> quad = new ArrayList<>(4);
            for (int index = 0; index < 4; index++) {
                RuntimeVertex vertex = vertices.get(offset + index);
                quad.add(new BedrockBoxReplay.Vertex(
                        replayVector(vertex.x, vertex.y, vertex.z),
                        new BedrockBoxReplay.Vec2(vertex.u, vertex.v)
                ));
            }
            candidates.add(new BedrockBoxReplay.Quad(
                    BedrockBoxReplay.Face.NORTH, quad,
                    replayVector(first.normalX, first.normalY, first.normalZ)
            ));
        }
        return BedrockBoxReplay.canonicalizeMultiset(
                BedrockBoxReplay.validateAndFilterCandidates(candidates)
        );
    }

    private static List<BedrockBoxReplay.CanonicalQuad> canonicalReplay(
            List<QuadMultisetDigest.Quad> quads
    ) {
        return BedrockBoxReplay.canonicalizeMultiset(quads.stream().map(quad ->
                new BedrockBoxReplay.Quad(
                        BedrockBoxReplay.Face.NORTH,
                        quad.vertices().stream().map(vertex ->
                                new BedrockBoxReplay.Vertex(
                                        replayVector(
                                                vertex.position().x(),
                                                vertex.position().y(),
                                                vertex.position().z()
                                        ),
                                        new BedrockBoxReplay.Vec2(vertex.u(), vertex.v())
                                )
                        ).toList(),
                        replayVector(
                                quad.storedNormal().x(),
                                quad.storedNormal().y(),
                                quad.storedNormal().z()
                        )
                )
        ).toList());
    }

    private static BedrockGeometry.Vec3 vector(float x, float y, float z) {
        return new BedrockGeometry.Vec3(x, y, z);
    }

    private static BedrockBoxReplay.Vec3 replayVector(float x, float y, float z) {
        return new BedrockBoxReplay.Vec3(x, y, z);
    }

    private record RuntimeVertex(
            float x, float y, float z,
            float u, float v,
            float normalX, float normalY, float normalZ
    ) {
    }

    private static final class RawConsumer implements VertexConsumer {
        private final List<RuntimeVertex> vertices = new ArrayList<>();
        private Pending pending;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (pending != null) {
                throw new IllegalStateException("overlapping runtime vertex");
            }
            pending = new Pending(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            requirePending().u = u;
            requirePending().v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            Pending value = requirePending();
            vertices.add(new RuntimeVertex(
                    value.x, value.y, value.z, value.u, value.v, x, y, z
            ));
            pending = null;
            return this;
        }

        private Pending requirePending() {
            if (pending == null) {
                throw new IllegalStateException("runtime vertex attribute without vertex");
            }
            return pending;
        }

        private List<RuntimeVertex> finish() {
            if (pending != null || vertices.size() != 24) {
                throw new IllegalStateException("incomplete runtime quad stream");
            }
            return List.copyOf(vertices);
        }
    }

    private static final class Pending {
        private final float x;
        private final float y;
        private final float z;
        private float u;
        private float v;

        private Pending(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
