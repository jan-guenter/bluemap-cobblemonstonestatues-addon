/*
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * Adapted from the first-party BlueMap Botany Pots adapter scaffold at
 * v0.1.0-alpha.1 / f40eed6c1f7f30356bcdfabbc3e2a6455fec7884.
 * Modified in 2026 for the Cobblemon Stone Statues integration.
 */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap523;

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.MaxCapacityReachedException;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.CompiledProfile;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.StoneStatuesRuntime;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection;
import io.github.janguenter.bluemap.cobblemonstonestatues.profile.ExactProfile;

/** Owner-bound lazy format-2 renderer; post-resolution faults disable the current profile. */
final class StoneStatuesRenderer implements BlockRenderer {

    private final StoneStatuesRuntime runtime;
    private final StoneStatuesRuntime.Attempt attempt;
    private final MeshEmitter emitter;
    private final ModelResolver modelResolver;
    private final BoundedDiagnostics diagnostics = new BoundedDiagnostics();

    StoneStatuesRenderer(
            ResourcePack resourcePack,
            TextureGallery textureGallery,
            RenderSettings renderSettings,
            StoneStatuesRuntime runtime
    ) {
        this(
                runtime, runtime.attemptFor(resourcePack),
                new StatueMeshEmitter(resourcePack, textureGallery, renderSettings),
                CompiledProfile::modelFor
        );
    }

    StoneStatuesRenderer(
            StoneStatuesRuntime runtime,
            StoneStatuesRuntime.Attempt attempt,
            MeshEmitter emitter,
            ModelResolver modelResolver
    ) {
        this.runtime = runtime;
        this.attempt = attempt;
        this.emitter = emitter;
        this.modelResolver = modelResolver;
    }

    @Override
    public void render(
            BlockNeighborhood block,
            Variant ignoredDispatch,
            TileModelView target,
            Color mapColor
    ) {
        StoneStatuesRuntime.Snapshot snapshot = runtime.snapshot();
        if (!snapshot.activeFor(attempt)
                || !ExactProfile.BLOCK.equals(block.getBlockState().getId())) {
            return;
        }
        StatueBlockEntityData data = block.getBlockEntity()
                instanceof StatueBlockEntityData found ? found : null;
        if (data == null) {
            diagnostics.report("selection-data-missing");
            return;
        }
        StatueSelection selection;
        try {
            selection = data.selection();
        } catch (RuntimeException exception) {
            diagnostics.report("selection-data-invalid");
            return;
        }
        renderSelection(snapshot, selection, block, target, mapColor);
    }

    void renderSelection(
            StatueSelection selection,
            BlockNeighborhood block,
            TileModelView target,
            Color mapColor
    ) {
        StoneStatuesRuntime.Snapshot snapshot = runtime.snapshot();
        if (!snapshot.activeFor(attempt)) {
            return;
        }
        renderSelection(snapshot, selection, block, target, mapColor);
    }

    private void renderSelection(
            StoneStatuesRuntime.Snapshot snapshot,
            StatueSelection selection,
            BlockNeighborhood block,
            TileModelView target,
            Color mapColor
    ) {
        CompiledProfile compiled = snapshot.profile();
        PoseCatalog.PoseEntry pose = compiled.catalog().resolve(selection);
        if (pose == null) {
            diagnostics.report("selection-not-exported");
            return;
        }

        int start = target.getStart();
        Color initialMapColor = new Color().set(mapColor);
        try {
            String textureCache = MaterialTexturePlan.cacheKey(
                    selection.material(), pose.textureResourceId(), pose.tintArgb()
            );
            Key texture = compiled.materialTextures().get(textureCache);
            if (texture == null) {
                throw new IllegalArgumentException("generated texture is absent");
            }
            StatueModel model = modelResolver.resolve(compiled, pose, texture, () ->
                    runtime.disableIfCurrent(snapshot, compiled, "runtime-replay-failed")
            );
            if (runtime.snapshot() != snapshot || !snapshot.activeFor(attempt)
                    || snapshot.profile() != compiled) {
                return;
            }
            emitter.beginVariantColor();
            if (!emitter.emit(model, selection, block, target, mapColor)) {
                throw new IllegalArgumentException("material texture unavailable");
            }
            emitter.finishVariantColor(mapColor);
            if (runtime.snapshot() != snapshot || !snapshot.activeFor(attempt)
                    || snapshot.profile() != compiled) {
                resetPartialGeometry(target, start, mapColor, initialMapColor);
            }
        } catch (MaxCapacityReachedException exception) {
            resetPartialGeometry(target, start, mapColor, initialMapColor);
            throw exception;
        } catch (RuntimeException exception) {
            runtime.disableIfCurrent(snapshot, compiled, "contained-render-failure");
            diagnostics.report("contained-render-failure");
            resetPartialGeometry(target, start, mapColor, initialMapColor);
        }
    }

    static void resetPartialGeometry(
            TileModelView target,
            int start,
            Color mapColor,
            Color initialMapColor
    ) {
        target.getTileModel().reset(start);
        target.initialize(start);
        mapColor.set(initialMapColor);
    }

    interface MeshEmitter {
        void beginVariantColor();

        boolean emit(
                StatueModel model,
                StatueSelection selection,
                BlockNeighborhood block,
                TileModelView target,
                Color mapColor
        );

        void finishVariantColor(Color mapColor);
    }

    @FunctionalInterface
    interface ModelResolver {
        StatueModel resolve(
                CompiledProfile profile,
                PoseCatalog.PoseEntry pose,
                Key texture,
                Runnable beforeFailurePublication
        );
    }
}
