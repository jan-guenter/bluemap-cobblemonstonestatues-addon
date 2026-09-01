/*
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * Adapted from the first-party BlueMap Botany Pots adapter scaffold at
 * v0.1.0-alpha.1 / f40eed6c1f7f30356bcdfabbc3e2a6455fec7884.
 * Modified in 2026 for the Cobblemon Stone Statues integration.
 */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap523;

import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtension;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.BlockProperties;
import de.bluecolored.bluemap.core.world.BlockState;
import io.github.janguenter.bluemap.addon.adapter.api.bluemap523.SyntheticDispatch;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.CompiledProfile;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ProfilePreflight;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.StoneStatuesRuntime;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalogLoader;
import io.github.janguenter.bluemap.cobblemonstonestatues.profile.ExactModArtifactDetector;
import io.github.janguenter.bluemap.cobblemonstonestatues.profile.ExactProfile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Owner-bound format-2 preflight that activates only after BlueMap's bake phase. */
final class StoneStatuesResourceExtension implements ResourcePackExtension {

    private static final Key SYNTHETIC =
            Key.parse("bluemap_cobblemonstonestatues:exact_shape");

    private final ResourcePack resourcePack;
    private final StoneStatuesRuntime runtime;
    private final StoneStatuesRuntime.Attempt attempt;
    private final BooleanSupplier blockEntityRetentionProbe;
    private volatile CompiledProfile pending;
    private Map<Key, Texture> publishedTextures = Map.of();

    StoneStatuesResourceExtension(ResourcePack resourcePack, StoneStatuesRuntime runtime) {
        this(resourcePack, runtime, BlueMap523Adapter::probeBlockEntityRetention);
    }

    StoneStatuesResourceExtension(
            ResourcePack resourcePack,
            StoneStatuesRuntime runtime,
            BooleanSupplier blockEntityRetentionProbe
    ) {
        this.resourcePack = resourcePack;
        this.runtime = runtime;
        this.attempt = runtime.beginAttempt(resourcePack);
        this.blockEntityRetentionProbe = Objects.requireNonNull(
                blockEntityRetentionProbe, "blockEntityRetentionProbe"
        );
    }

    @Override
    public void loadResources(Iterable<Path> roots) throws IOException, InterruptedException {
        boolean reset = runtime.inactiveIfCurrent(attempt, "preflight-running");
        pending = null;
        rollbackPublishedTextures();
        if (!reset) {
            return;
        }
        if (!ExactProfile.FORMAT_2_READY) {
            runtime.inactiveIfCurrent(attempt, "format-2-runtime-not-ready");
            return;
        }
        boolean exactArtifacts;
        try {
            exactArtifacts = ExactModArtifactDetector.matchesRequiredPair(roots);
        } catch (RuntimeException exception) {
            runtime.inactiveIfCurrent(attempt, "exact-artifact-pair-read-failed");
            return;
        }
        if (!exactArtifacts) {
            runtime.inactiveIfCurrent(attempt, "exact-artifact-pair-missing");
            return;
        }
        PoseCatalog catalog;
        try {
            catalog = PoseCatalogLoader.loadConfigured();
        } catch (IOException | RuntimeException exception) {
            runtime.inactiveIfCurrent(attempt, "pose-bundle-invalid");
            return;
        }
        MaterialTexturePlan materialPlan;
        try {
            materialPlan = MaterialTexturePlan.create(catalog);
        } catch (RuntimeException exception) {
            runtime.inactiveIfCurrent(attempt, "material-texture-plan-invalid");
            return;
        }
        ActiveResourceLoader.Result active = loadActiveResources(() ->
                ActiveResourceLoader.load(
                        resourcePack, roots, catalog.models(), catalog.textures()
                ));
        if (!active.valid()) {
            runtime.inactiveIfCurrent(attempt, active.reason());
            return;
        }
        ProfilePreflight.Result preflight;
        try {
            preflight = ProfilePreflight.verify(
                    catalog, active.models(), active.textures()
            );
        } catch (RuntimeException exception) {
            runtime.inactiveIfCurrent(attempt, "format-2-preflight-failed");
            return;
        }
        MaterialTexturePlan.Generated generated;
        try {
            generated = materialPlan.generate(
                    preflight.textures(), resourcePack.getTextures()::containsKey
            );
        } catch (IOException | RuntimeException exception) {
            runtime.inactiveIfCurrent(attempt, "material-texture-generation-failed");
            return;
        }
        de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.BlockState dispatch;
        CompiledProfile candidate;
        try {
            dispatch = resourcePack.getBlockStates().get(SYNTHETIC);
            if (!validDispatch(dispatch)) {
                runtime.inactiveIfCurrent(attempt, "synthetic-dispatch-invalid");
                return;
            }
            candidate = new CompiledProfile(attempt, preflight, generated.keys());
        } catch (RuntimeException exception) {
            runtime.inactiveIfCurrent(attempt, "activation-candidate-invalid");
            return;
        }
        stageForBake(candidate, generated.textures());
    }

    @Override
    public Set<Key> collectUsedTextureKeys() {
        CompiledProfile candidate = boundProfile();
        return candidate == null ? Set.of()
                : Set.copyOf(candidate.materialTextures().values());
    }

    @Override
    public void bake() {
        CompiledProfile candidate = pending;
        if (candidate == null) {
            if (runtime.snapshot().activeFor(attempt)) {
                return;
            }
            rollbackPublishedTextures();
            return;
        }
        if (!runtime.isCurrent(attempt)) {
            pending = null;
            rollbackPublishedTextures();
            return;
        }
        if (candidate.materialTextures().values().stream()
                .anyMatch(key -> !publishedTextures.containsKey(key)
                        || resourcePack.getTextures().get(key)
                        != publishedTextures.get(key))) {
            failPending("generated-texture-retention-failed");
            return;
        }
        if (!blockEntityRetentionProbe.getAsBoolean()) {
            failPending("bluenbt-snapshot-requires-jvm-restart");
            return;
        }
        if (runtime.activateIfCurrent(attempt, candidate)) {
            pending = null;
        } else {
            pending = null;
            rollbackPublishedTextures();
        }
    }

    @Override
    public Key getBlockStateKey(Key key) {
        return active() && ExactProfile.BLOCK.equals(key) ? SYNTHETIC : key;
    }

    @Override
    public void getBlockProperties(BlockState state, BlockProperties.Builder builder) {
        if (active() && ExactProfile.BLOCK.equals(state.getId())) {
            builder.culling(false).occluding(false).cullingIdentical(false);
        }
    }

    static ActiveResourceLoader.Result loadActiveResources(
            ActiveResourceCapture capture
    ) throws InterruptedException {
        try {
            return Objects.requireNonNull(capture, "capture").load();
        } catch (IOException | RuntimeException exception) {
            return ActiveResourceLoader.Result.invalid("active-resource-read-failed");
        }
    }

    boolean stageForBake(CompiledProfile candidate, Map<Key, Texture> generated) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(generated, "generated");
        if (candidate.owner() != attempt
                || !runtime.inactiveIfCurrent(attempt, "pending-bake")) {
            return false;
        }
        Set<Key> expectedKeys = new HashSet<>(candidate.materialTextures().values());
        if (!generated.keySet().equals(expectedKeys)
                || generated.values().stream().anyMatch(Objects::isNull)) {
            runtime.disableIfCurrent(attempt, "generated-texture-closure-invalid");
            return false;
        }
        try {
            boolean published = runtime.runIfCurrentPending(attempt, () -> {
                publishTextures(generated);
                pending = candidate;
            });
            if (!published) {
                rollbackPublishedTextures();
            }
            return published;
        } catch (RuntimeException failure) {
            pending = null;
            runtime.disableIfCurrent(attempt, "generated-texture-publication-failed");
            return false;
        } catch (Error failure) {
            pending = null;
            runtime.disableIfCurrent(attempt, "generated-texture-publication-failed");
            throw failure;
        }
    }

    StoneStatuesRuntime.Attempt attempt() {
        return attempt;
    }

    private CompiledProfile boundProfile() {
        if (!runtime.isCurrent(attempt)) {
            return null;
        }
        CompiledProfile candidate = pending;
        if (candidate != null && candidate.owner() == attempt) {
            return candidate;
        }
        StoneStatuesRuntime.Snapshot snapshot = runtime.snapshot();
        return snapshot.activeFor(attempt) ? snapshot.profile() : null;
    }

    private boolean active() {
        return runtime.snapshot().activeFor(attempt);
    }

    private void publishTextures(Map<Key, Texture> generated) {
        java.util.ArrayList<Key> inserted = new java.util.ArrayList<>(generated.size());
        try {
            for (Map.Entry<Key, Texture> entry : generated.entrySet()) {
                if (resourcePack.getTextures().containsKey(entry.getKey())) {
                    throw new IllegalArgumentException("generated texture key collision");
                }
                resourcePack.getTextures().put(entry.getKey(), entry.getValue());
                inserted.add(entry.getKey());
            }
            publishedTextures = Map.copyOf(generated);
        } catch (RuntimeException | Error failure) {
            inserted.forEach(key -> {
                Texture expected = generated.get(key);
                if (resourcePack.getTextures().get(key) == expected) {
                    resourcePack.getTextures().remove(key);
                }
            });
            throw failure;
        }
    }

    private void failPending(String detail) {
        pending = null;
        rollbackPublishedTextures();
        runtime.disableIfCurrent(attempt, detail);
    }

    private void rollbackPublishedTextures() {
        for (Map.Entry<Key, Texture> entry : publishedTextures.entrySet()) {
            if (resourcePack.getTextures().get(entry.getKey()) == entry.getValue()) {
                resourcePack.getTextures().remove(entry.getKey());
            }
        }
        publishedTextures = Map.of();
    }

    private static boolean validDispatch(
            de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.BlockState state
    ) {
        return SyntheticDispatch.matches(state, BlueMap523Adapter.rendererType());
    }

    @FunctionalInterface
    interface ActiveResourceCapture {
        ActiveResourceLoader.Result load() throws IOException, InterruptedException;
    }
}
