/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter.mixin;

import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import io.github.janguenter.stoneposeexporter.ModelResourceBindings;
import kotlin.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.io.InputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes exact physical winners produced by Cobblemon's pinned built-in GEO factory. */
@Mixin(value = VaryingModelRepository.class, remap = false)
public abstract class VaryingModelRepositoryResourceMixin {

    @Inject(
            method = "registerModels("
                    + "Lnet/minecraft/server/packs/resources/ResourceManager;)V",
            at = @At("HEAD"),
            remap = false,
            require = 1,
            allow = 1
    )
    private void atmonsStonePoseExporter$beginModelRegistration(
            ResourceManager manager, CallbackInfo callback
    ) {
        ModelResourceBindings.beginReload();
    }

    @Inject(
            method = "registerModels("
                    + "Lnet/minecraft/server/packs/resources/ResourceManager;)V",
            at = @At("RETURN"),
            remap = false,
            require = 1,
            allow = 1
    )
    private void atmonsStonePoseExporter$completeModelRegistration(
            ResourceManager manager, CallbackInfo callback
    ) {
        ModelResourceBindings.completeReload();
    }

    @Inject(
            method = "MODEL_FACTORIES$lambda$0$0("
                    + "Lnet/minecraft/resources/ResourceLocation;"
                    + "Lnet/minecraft/server/packs/resources/Resource;)Lkotlin/Pair;",
            at = @At("HEAD"),
            remap = false,
            require = 1,
            allow = 1
    )
    private static void atmonsStonePoseExporter$beginBuiltInGeo(
            ResourceLocation physicalId,
            Resource resource,
            CallbackInfoReturnable<Pair<ResourceLocation, Bone>> callback
    ) {
        ModelResourceBindings.beginBuiltInFactory(physicalId, resource);
    }

    @Redirect(
            method = "MODEL_FACTORIES$lambda$0$0("
                    + "Lnet/minecraft/resources/ResourceLocation;"
                    + "Lnet/minecraft/server/packs/resources/Resource;)Lkotlin/Pair;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/io/InputStream;readAllBytes()[B"
            ),
            remap = false,
            require = 1,
            allow = 1
    )
    private static byte[] atmonsStonePoseExporter$captureBuiltInGeoBytes(
            InputStream input
    ) throws IOException {
        byte[] raw = input.readAllBytes();
        ModelResourceBindings.observeFactoryBytes(raw);
        return raw;
    }

    @Inject(
            method = "MODEL_FACTORIES$lambda$0$0("
                    + "Lnet/minecraft/resources/ResourceLocation;"
                    + "Lnet/minecraft/server/packs/resources/Resource;)Lkotlin/Pair;",
            at = @At("RETURN"),
            remap = false,
            require = 2,
            allow = 2
    )
    private static void atmonsStonePoseExporter$observeBuiltInGeo(
            ResourceLocation physicalId,
            Resource resource,
            CallbackInfoReturnable<Pair<ResourceLocation, Bone>> callback
    ) {
        ModelResourceBindings.finishBuiltInFactory(callback.getReturnValue());
    }
}
