/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import kotlin.Pair;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Same-invocation observer and reversible exact-zero sanitizer for PosableModel.render HEAD. */
public final class PoseCaptureHook {

    private static final int INPUT_COLOR = -1;
    private static final SingleUseCaptureGate<Request, Callback, Observation> GATE =
            new SingleUseCaptureGate<>(PoseCaptureHook::captureCallback);
    private static ModelTreeCapture.NodeAccess<Bone> modelPartAccess(
            PoseStack stack, TemporarySkipDraw suppressions
    ) {
        return new ModelTreeCapture.NodeAccess<>() {
                @Override
                public Map<String, Bone> children(Bone node) {
                    requireModelPart(node);
                    return node.getChildren();
                }

                @Override
                public ModelTreeCapture.RenderFlags flags(Bone node) {
                    ModelPart part = requireModelPart(node);
                    return new ModelTreeCapture.RenderFlags(
                            part.visible, part.skipDraw, part.isEmpty()
                    );
                }

                @Override
                public boolean enter(Bone node) {
                    ModelPart part = requireModelPart(node);
                    stack.pushPose();
                    try {
                        part.translateAndRotate(stack);
                    } catch (RuntimeException exception) {
                        stack.popPose();
                        throw exception;
                    }
                    return part.xScale == 0F || part.yScale == 0F || part.zScale == 0F;
                }

                @Override
                public ModelTreeCapture.FrameDecision frame(
                        Bone node, boolean zeroScaleInPath
                ) {
                    requireModelPart(node);
                    return captureFrame(stack.last(), zeroScaleInPath);
                }

                @Override
                public void suppressDraw(Bone node) {
                    suppressions.suppress(requireModelPart(node));
                }

                @Override
                public void exit(Bone node) {
                    requireModelPart(node);
                    stack.popPose();
                }
            };
    }

    private PoseCaptureHook() {
    }

    static <E extends Exception> Observation capture(
            Request request,
            SingleUseCaptureGate.ThrowingAction<E> action
    ) throws E {
        return GATE.capture(request, action);
    }

    /** Called from the Mixin. An ordinary unarmed render is intentionally a no-op. */
    public static void observe(
            PosableModel model,
            RenderContext context,
            PoseStack stack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color
    ) {
        if (!GATE.isArmed()) {
            return;
        }
        GATE.observe(new Callback(
                model, context, stack, buffer, packedLight, packedOverlay, color
        ));
    }

    private static Observation captureCallback(Request request, Callback callback) {
        if (callback.model != request.model
                || callback.stack != request.stack
                || callback.buffer != request.buffer
                || callback.packedLight != request.packedLight
                || callback.packedOverlay != request.packedOverlay
                || callback.color != INPUT_COLOR) {
            throw new IllegalArgumentException("Stone render callback identity drift");
        }
        PosableModel model = callback.model;
        RenderContext context = callback.context;
        if (model.getContext() != context) {
            throw new IllegalArgumentException("Stone render context identity drift");
        }
        PosableState state = context.requires(RenderContext.Companion.getPOSABLE_STATE());
        if (model.getCurrentState() != state || state.getCurrentModel() != model
                || context.requires(RenderContext.Companion.getRENDER_STATE())
                != RenderContext.RenderState.BLOCK
                || !Boolean.FALSE.equals(
                context.requires(RenderContext.Companion.getDO_QUIRKS()))
                || !request.speciesId.equals(
                context.requires(RenderContext.Companion.getSPECIES()))
                || !request.aspects.equals(
                context.requires(RenderContext.Companion.getASPECTS()))
                || !request.dynamicTexture.equals(
                context.requires(RenderContext.Companion.getTEXTURE()))
                || Float.floatToRawIntBits(request.baseScale)
                != Float.floatToRawIntBits(
                context.requires(RenderContext.Companion.getSCALE()))) {
            throw new IllegalArgumentException("Stone render context value drift");
        }
        correlateCachedPoser(request, model);
        Bone wrapper = request.resolver.getModels().get(request.modelId);
        if (wrapper == null) {
            throw new IllegalArgumentException("effective baked model wrapper is absent");
        }
        PoseStack.Pose original = callback.stack.last();
        Matrix4f originalPosition = new Matrix4f(original.pose());
        Matrix3f originalNormal = new Matrix3f(original.normal());
        TemporarySkipDraw suppressions = new TemporarySkipDraw();
        GATE.deferCleanup(suppressions);
        ModelTreeCapture.CapturedTree tree;
        try {
            tree = ModelTreeCapture.capture(
                    wrapper, model.getRootPart(),
                    modelPartAccess(callback.stack, suppressions)
            );
        } finally {
            requireRestored(callback.stack, original, originalPosition, originalNormal);
        }
        String effectivePose = state.getCurrentPose();
        if (effectivePose != null && effectivePose.isBlank()) {
            throw new IllegalArgumentException("effective pose is blank at render HEAD");
        }
        return new Observation(tree, effectivePose, PoseTint.fromWhiteInput(
                model.getRed(), model.getGreen(), model.getBlue(), model.getAlpha()
        ));
    }

    private static void correlateCachedPoser(Request request, PosableModel model) {
        int matches = 0;
        for (Map.Entry<Pair<ResourceLocation, ResourceLocation>, PosableModel> entry
                : request.resolver.getPosers().entrySet()) {
            if (entry.getValue() != model) {
                continue;
            }
            matches++;
            Pair<ResourceLocation, ResourceLocation> key = entry.getKey();
            if (key == null || !request.poserId.equals(key.getFirst())
                    || !request.modelId.equals(key.getSecond())) {
                throw new IllegalArgumentException("effective cached poser key drift");
            }
        }
        if (matches != 1) {
            throw new IllegalArgumentException("effective cached poser identity is ambiguous");
        }
    }

    private static ModelPart requireModelPart(Bone node) {
        Object candidate = Objects.requireNonNull(node, "model bone");
        if (!(candidate instanceof ModelPart part)) {
            throw new IllegalArgumentException("custom non-ModelPart Bone route is unsupported");
        }
        return part;
    }

    private static Vector3f normal(
            PoseStack.Pose pose, float x, float y, float z
    ) {
        return pose.transformNormal(x, y, z, new Vector3f());
    }

    static PoseState.RuntimeFrame captureRuntimeFrame(PoseStack.Pose pose) {
        ModelTreeCapture.FrameDecision decision = captureFrame(pose, false);
        if (decision.suppressDraw()) {
            throw new IllegalStateException("ordinary runtime frame was unexpectedly suppressed");
        }
        return decision.runtimeFrame();
    }

    static ModelTreeCapture.FrameDecision captureFrame(
            PoseStack.Pose pose, boolean zeroScaleInPath
    ) {
        PoseState.PositionMatrix position = capturePosition(pose);
        Vector3f down = normal(pose, 0F, -1F, 0F);
        Vector3f up = normal(pose, 0F, 1F, 0F);
        Vector3f west = normal(pose, -1F, 0F, 0F);
        Vector3f north = normal(pose, 0F, 0F, -1F);
        Vector3f east = normal(pose, 1F, 0F, 0F);
        Vector3f south = normal(pose, 0F, 0F, 1F);
        Vector3f mirrorDown = normal(pose, -0.0F, -1F, 0F);
        Vector3f mirrorUp = normal(pose, -0.0F, 1F, 0F);
        Vector3f mirrorNorth = normal(pose, -0.0F, 0F, -1F);
        Vector3f mirrorSouth = normal(pose, -0.0F, 0F, 1F);
        if (nonFinite(
                down, up, west, north, east, south,
                mirrorDown, mirrorUp, mirrorNorth, mirrorSouth
        )) {
            if (zeroScaleInPath) {
                return ModelTreeCapture.FrameDecision.suppress();
            }
            throw new IllegalArgumentException(
                    "non-finite transformed normal without exact zero-scale ancestry"
            );
        }
        PoseState.CardinalNormals normals = new PoseState.CardinalNormals(
                down.x, down.y, down.z, up.x, up.y, up.z,
                west.x, west.y, west.z, north.x, north.y, north.z,
                east.x, east.y, east.z, south.x, south.y, south.z
        );
        PoseState.MirrorZeroXNormals mirrorZeroXNormals =
                new PoseState.MirrorZeroXNormals(
                        mirrorDown.x, mirrorDown.y, mirrorDown.z,
                        mirrorUp.x, mirrorUp.y, mirrorUp.z,
                        mirrorNorth.x, mirrorNorth.y, mirrorNorth.z,
                        mirrorSouth.x, mirrorSouth.y, mirrorSouth.z
                );
        return ModelTreeCapture.FrameDecision.captured(
                new PoseState.RuntimeFrame(position, normals, mirrorZeroXNormals)
        );
    }

    static PoseState.PositionMatrix capturePosition(PoseStack.Pose pose) {
        Matrix4f matrix = pose.pose();
        return new PoseState.PositionMatrix(
                matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03(),
                matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
                matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
                matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33()
        );
    }

    private static boolean nonFinite(Vector3f... values) {
        for (Vector3f value : values) {
            if (!Float.isFinite(value.x) || !Float.isFinite(value.y)
                    || !Float.isFinite(value.z)) {
                return true;
            }
        }
        return false;
    }

    private static void requireRestored(
            PoseStack stack,
            PoseStack.Pose original,
            Matrix4f position,
            Matrix3f normal
    ) {
        PoseStack.Pose current = stack.last();
        if (current != original || !sameRaw(position, current.pose())
                || !sameRaw(normal, current.normal())) {
            throw new IllegalStateException("pose-state capture did not restore Stone PoseStack");
        }
    }

    private static boolean sameRaw(Matrix4f first, Matrix4f second) {
        return sameRaw(first.m00(), second.m00())
                && sameRaw(first.m01(), second.m01())
                && sameRaw(first.m02(), second.m02())
                && sameRaw(first.m03(), second.m03())
                && sameRaw(first.m10(), second.m10())
                && sameRaw(first.m11(), second.m11())
                && sameRaw(first.m12(), second.m12())
                && sameRaw(first.m13(), second.m13())
                && sameRaw(first.m20(), second.m20())
                && sameRaw(first.m21(), second.m21())
                && sameRaw(first.m22(), second.m22())
                && sameRaw(first.m23(), second.m23())
                && sameRaw(first.m30(), second.m30())
                && sameRaw(first.m31(), second.m31())
                && sameRaw(first.m32(), second.m32())
                && sameRaw(first.m33(), second.m33());
    }

    private static boolean sameRaw(Matrix3f first, Matrix3f second) {
        return sameRaw(first.m00(), second.m00())
                && sameRaw(first.m01(), second.m01())
                && sameRaw(first.m02(), second.m02())
                && sameRaw(first.m10(), second.m10())
                && sameRaw(first.m11(), second.m11())
                && sameRaw(first.m12(), second.m12())
                && sameRaw(first.m20(), second.m20())
                && sameRaw(first.m21(), second.m21())
                && sameRaw(first.m22(), second.m22());
    }

    static final class TemporarySkipDraw implements Runnable {
        private final IdentityHashMap<ModelPart, Boolean> originals =
                new IdentityHashMap<>();

        void suppress(ModelPart part) {
            originals.putIfAbsent(part, part.skipDraw);
            part.skipDraw = true;
        }

        int size() {
            return originals.size();
        }

        @Override
        public void run() {
            originals.forEach((part, skipDraw) -> part.skipDraw = skipDraw);
            originals.clear();
        }
    }

    private static boolean sameRaw(float first, float second) {
        return Float.floatToRawIntBits(first) == Float.floatToRawIntBits(second);
    }

    record Request(
            PosableModel model,
            VaryingRenderableResolver resolver,
            ResourceLocation modelId,
            ResourceLocation poserId,
            ResourceLocation speciesId,
            Set<String> aspects,
            ResourceLocation dynamicTexture,
            float baseScale,
            PoseStack stack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay
    ) {
        Request {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(resolver, "resolver");
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(poserId, "poserId");
            Objects.requireNonNull(speciesId, "speciesId");
            aspects = Set.copyOf(aspects);
            Objects.requireNonNull(dynamicTexture, "dynamicTexture");
            Objects.requireNonNull(stack, "stack");
            Objects.requireNonNull(buffer, "buffer");
            if (!Float.isFinite(baseScale) || baseScale <= 0F || baseScale > 64F) {
                throw new IllegalArgumentException("invalid Stone form base scale");
            }
        }
    }

    record Observation(
            ModelTreeCapture.CapturedTree tree,
            String effectivePose,
            int derivedTintArgb
    ) {
        Observation {
            Objects.requireNonNull(tree, "tree");
        }

        PoseState finish(int authoritativeTintArgb) {
            return tree.withTint(PoseTint.requireAuthoritative(
                    derivedTintArgb, authoritativeTintArgb
            ));
        }
    }

    private record Callback(
            PosableModel model,
            RenderContext context,
            PoseStack stack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color
    ) {
        private Callback {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(stack, "stack");
            Objects.requireNonNull(buffer, "buffer");
        }
    }
}
