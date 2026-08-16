/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import com.mojang.brigadier.Command;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Command/reload/tick lifecycle for one all-or-nothing client export. */
final class StonePoseExportController {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final boolean HYBRID_EXPORT_READY = true;
    private final ModelGenerationTracker generation = new ModelGenerationTracker();
    private StonePoseExportEngine active;
    private long expectedGeneration;
    private boolean failed;
    private int lastProgressBucket = -1;

    @SuppressWarnings("this-escape")
    StonePoseExportController(IEventBus modEventBus) {
        modEventBus.addListener(this::registerReloadListener);
        modEventBus.addListener(ModelEvent.BakingCompleted.class, this::onModelBakingCompleted);
        NeoForge.EVENT_BUS.addListener(this::registerCommand);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onLogout);
    }

    private void registerCommand(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("atmons_stone_export")
                .then(Commands.literal("create").executes(context -> {
                    start(context.getSource()::sendFailure,
                            message -> context.getSource().sendSuccess(
                                    () -> Component.literal(message), false
                            ));
                    return Command.SINGLE_SUCCESS;
                })));
    }

    private void onModelBakingCompleted(ModelEvent.BakingCompleted event) {
        generation.modelsBaked();
    }

    private void start(
            java.util.function.Consumer<Component> failure,
            java.util.function.Consumer<String> success
    ) {
        if (!HYBRID_EXPORT_READY) {
            failure.accept(Component.literal(
                    "Hybrid pose-state export is not wired; this development build is inactive."
            ));
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (active != null) {
            failure.accept(Component.literal("Stone pose export is already running."));
            return;
        }
        if (!generation.matches(generation.current())) {
            failure.accept(Component.literal("Resources are reloading; wait and retry."));
            return;
        }
        try {
            expectedGeneration = generation.current();
            active = new StonePoseExportEngine(minecraft, expectedGeneration);
            failed = false;
            lastProgressBucket = -1;
            success.accept("Stone pose export started on the render thread.");
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Stone pose export refused during startup", exception);
            failed = true;
            failure.accept(Component.literal(
                    "Stone pose export refused to start: " + safeFailure(exception)
            ));
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (active == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!generation.matches(expectedGeneration)) {
            cancel("resource generation changed during export");
            return;
        }
        try {
            StonePoseExportEngine.Progress progress = active.advance();
            int bucket = progress.total() == 0 ? 0
                    : progress.completed() * 20 / progress.total();
            if (bucket != lastProgressBucket && minecraft.player != null) {
                lastProgressBucket = bucket;
                minecraft.player.displayClientMessage(Component.literal(
                        "Stone pose export: " + progress.completed() + '/'
                                + progress.total()
                ), false);
            }
            if (!progress.complete()) {
                return;
            }
            if (!generation.matches(expectedGeneration)) {
                cancel("resource generation changed before publication");
                return;
            }
            active.requirePublicationReady();
            DeterministicBundleWriter.Result result = DeterministicBundleWriter.write(
                    minecraft.gameDirectory.toPath(), progress.catalog(), () -> {
                        if (!generation.matches(expectedGeneration)) {
                            throw new IllegalStateException(
                                    "resource generation changed during publication"
                            );
                        }
                        active.requirePublicationReady();
                    }
            );
            active = null;
            failed = false;
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(
                        "Stone pose export complete: " + result.path()
                                + " (" + result.size() + " bytes, SHA-256 "
                                + result.sha256() + ')'
                ), false);
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Stone pose export failed after startup", exception);
            cancel("fatal export failure: " + safeFailure(exception));
        }
    }

    private void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        cancel("client disconnected");
    }

    private void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new GenerationReloadListener(generation));
    }

    private void cancel(String reason) {
        if (active == null) {
            return;
        }
        active.cancel();
        active = null;
        failed = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(
                    "Stone pose export aborted without output: " + reason
            ), false);
        }
    }

    private static String safeFailure(Throwable exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    @SuppressWarnings("unused")
    boolean failed() {
        return failed;
    }

    private static final class GenerationReloadListener implements PreparableReloadListener {
        private final ModelGenerationTracker generation;

        GenerationReloadListener(ModelGenerationTracker generation) {
            this.generation = generation;
        }

        @Override
        public CompletableFuture<Void> reload(
                PreparationBarrier barrier,
                ResourceManager resourceManager,
                ProfilerFiller preparationProfiler,
                ProfilerFiller reloadProfiler,
                Executor preparationExecutor,
                Executor reloadExecutor
        ) {
            generation.reloadStarted();
            return barrier.wait(Boolean.TRUE)
                    .thenRunAsync(generation::reloadCompleted, reloadExecutor);
        }

        @Override
        public String getName() {
            return "ATMons Stone pose-export generation guard";
        }
    }
}
