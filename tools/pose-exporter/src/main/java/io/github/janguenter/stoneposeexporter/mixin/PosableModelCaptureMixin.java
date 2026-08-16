/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter.mixin;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.janguenter.stoneposeexporter.PoseCaptureHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes one armed bridge render and applies only the reversible exact-zero-scale sanitizer.
 */
@Mixin(value = PosableModel.class, remap = false)
public abstract class PosableModelCaptureMixin {

    @Inject(
            method = "render("
                    + "Lcom/cobblemon/mod/common/client/render/models/blockbench/repository/"
                    + "RenderContext;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"),
            remap = false,
            require = 1,
            allow = 1
    )
    private void atmonsStonePoseExporter$capture(
            RenderContext context,
            PoseStack stack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callback
    ) {
        PoseCaptureHook.observe(
                (PosableModel) (Object) this,
                context, stack, buffer, packedLight, packedOverlay, color
        );
    }
}
