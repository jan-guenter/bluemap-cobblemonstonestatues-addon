/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.activation;

import de.bluecolored.bluemap.core.util.Key;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ProfilePreflight.Geometry;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ProfilePreflight.ReplayKey;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ProfilePreflight.VerifiedModel;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseEntry;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseStateIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Immutable exact-profile closure with a texture-neutral weighted single-flight cache. */
public final class CompiledProfile {

    public static final int MAX_CACHE_ENTRIES = 128;
    public static final int MAX_CACHE_QUADS = 32_768;

    private final StoneStatuesRuntime.Attempt owner;
    private final PoseCatalog catalog;
    private final Map<String, VerifiedModel> models;
    private final Map<ReplayKey, QuadMultisetDigest.Result> expectations;
    private final Map<String, Key> materialTextures;
    private final GeometryCache cache;

    public CompiledProfile(
            StoneStatuesRuntime.Attempt owner,
            ProfilePreflight.Result preflight,
            Map<String, Key> materialTextures
    ) {
        this(owner, preflight, materialTextures,
                new GeometryCache(MAX_CACHE_ENTRIES, MAX_CACHE_QUADS));
    }

    CompiledProfile(
            StoneStatuesRuntime.Attempt owner,
            ProfilePreflight.Result preflight,
            Map<String, Key> materialTextures,
            GeometryCache cache
    ) {
        this.owner = Objects.requireNonNull(owner, "owner");
        ProfilePreflight.Result verified = Objects.requireNonNull(preflight, "preflight");
        this.catalog = verified.catalog();
        this.models = verified.models();
        this.expectations = verified.expectations();
        this.materialTextures = Collections.unmodifiableMap(
                new LinkedHashMap<>(materialTextures)
        );
        this.cache = Objects.requireNonNull(cache, "cache");
        if (materialTextures.isEmpty()) {
            throw new IllegalArgumentException("generated texture roster is empty");
        }
    }

    public StoneStatuesRuntime.Attempt owner() {
        return owner;
    }

    public PoseCatalog catalog() {
        return catalog;
    }

    public Map<String, Key> materialTextures() {
        return materialTextures;
    }

    public StatueModel modelFor(
            PoseEntry pose, Key texture, Runnable beforeFailurePublication
    ) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(texture, "texture");
        ReplayKey replay = ReplayKey.from(pose);
        VerifiedModel model = models.get(pose.modelId());
        PoseStateIdentity state = catalog.poseStates().get(pose.poseStateSha256());
        QuadMultisetDigest.Result expected = expectations.get(replay);
        CacheKey key = model == null ? new CacheKey(
                pose.modelId(), "missing", pose.poseStateSha256()
        ) : new CacheKey(
                pose.modelId(), model.identity().sha256(), pose.poseStateSha256()
        );
        Geometry geometry = cache.get(key, () -> {
            if (model == null || state == null || expected == null
                    || !expected.equals(pose.verification())) {
                throw new IllegalArgumentException("runtime replay closure is incomplete");
            }
            return ProfilePreflight.compile(model, state, expected);
        }, beforeFailurePublication);
        return geometry.withTexture(texture);
    }

    int cachedEntries() {
        return cache.readyEntries();
    }

    int cachedQuads() {
        return cache.readyWeight();
    }

    record CacheKey(String modelId, String modelSha256, String poseStateSha256) {
    }

    static final class GeometryCache {
        private final int maximumEntries;
        private final int maximumWeight;
        private final LinkedHashMap<CacheKey, Geometry> ready =
                new LinkedHashMap<>(16, 0.75F, true);
        private final Map<CacheKey, CompletableFuture<Geometry>> inFlight = new LinkedHashMap<>();
        private final ThreadLocal<Set<CacheKey>> compiling =
                ThreadLocal.withInitial(HashSet::new);
        private final Consumer<CacheKey> beforeSuccessPublication;
        private final Consumer<CacheKey> beforeFollowerAwait;
        private int readyWeight;

        GeometryCache(int maximumEntries, int maximumWeight) {
            this(maximumEntries, maximumWeight, ignored -> { }, ignored -> { });
        }

        GeometryCache(
                int maximumEntries,
                int maximumWeight,
                Consumer<CacheKey> beforeSuccessPublication
        ) {
            this(maximumEntries, maximumWeight, beforeSuccessPublication, ignored -> { });
        }

        GeometryCache(
                int maximumEntries,
                int maximumWeight,
                Consumer<CacheKey> beforeSuccessPublication,
                Consumer<CacheKey> beforeFollowerAwait
        ) {
            if (maximumEntries < 1 || maximumEntries > MAX_CACHE_ENTRIES
                    || maximumWeight < 1 || maximumWeight > MAX_CACHE_QUADS) {
                throw new IllegalArgumentException("invalid geometry cache budget");
            }
            this.maximumEntries = maximumEntries;
            this.maximumWeight = maximumWeight;
            this.beforeSuccessPublication = Objects.requireNonNull(
                    beforeSuccessPublication, "beforeSuccessPublication"
            );
            this.beforeFollowerAwait = Objects.requireNonNull(
                    beforeFollowerAwait, "beforeFollowerAwait"
            );
        }

        Geometry get(
                CacheKey key,
                Supplier<Geometry> loader,
                Runnable beforeFailurePublication
        ) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(loader, "loader");
            Objects.requireNonNull(beforeFailurePublication, "beforeFailurePublication");
            CompletableFuture<Geometry> future;
            boolean leader;
            synchronized (this) {
                Geometry cached = ready.get(key);
                if (cached != null) {
                    return cached;
                }
                future = inFlight.get(key);
                leader = future == null;
                if (leader) {
                    future = new CompletableFuture<>();
                    inFlight.put(key, future);
                }
            }
            if (!leader) {
                if (compiling.get().contains(key)) {
                    throw new IllegalStateException("recursive same-key geometry compilation");
                }
                beforeFollowerAwait.accept(key);
                return await(future);
            }
            Set<CacheKey> active = compiling.get();
            if (!active.add(key)) {
                fail(future, key, new IllegalStateException(
                        "recursive same-key geometry compilation"
                ), beforeFailurePublication);
                return await(future);
            }
            try {
                Geometry loaded = Objects.requireNonNull(loader.get(), "loaded geometry");
                if (loaded.weight() > maximumWeight) {
                    throw new IllegalArgumentException("geometry exceeds cache weight budget");
                }
                synchronized (this) {
                    ready.put(key, loaded);
                    readyWeight = Math.addExact(readyWeight, loaded.weight());
                    evict();
                }
                beforeSuccessPublication.accept(key);
                future.complete(loaded);
                synchronized (this) {
                    inFlight.remove(key, future);
                }
                return loaded;
            } catch (Throwable failure) {
                fail(future, key, failure, beforeFailurePublication);
                return await(future);
            } finally {
                active.remove(key);
                if (active.isEmpty()) {
                    compiling.remove();
                }
            }
        }

        private void fail(
                CompletableFuture<Geometry> future,
                CacheKey key,
                Throwable failure,
                Runnable beforeFailurePublication
        ) {
            Throwable callbackFailure = null;
            try {
                beforeFailurePublication.run();
            } catch (Throwable caught) {
                callbackFailure = caught;
            }
            Throwable published = failure;
            if (!(failure instanceof Error) && callbackFailure instanceof Error) {
                published = callbackFailure;
                suppressDistinct(published, failure);
            } else {
                suppressDistinct(published, callbackFailure);
            }
            future.completeExceptionally(published);
            synchronized (this) {
                inFlight.remove(key, future);
            }
        }

        private static void suppressDistinct(Throwable primary, Throwable secondary) {
            if (secondary != null && secondary != primary) {
                primary.addSuppressed(secondary);
            }
        }

        private void evict() {
            while (ready.size() > maximumEntries || readyWeight > maximumWeight) {
                Map.Entry<CacheKey, Geometry> eldest = ready.entrySet().iterator().next();
                readyWeight = Math.subtractExact(readyWeight, eldest.getValue().weight());
                ready.remove(eldest.getKey());
            }
        }

        private static Geometry await(CompletableFuture<Geometry> future) {
            try {
                return future.join();
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("geometry compilation failed", cause);
            }
        }

        synchronized int readyEntries() {
            return ready.size();
        }

        synchronized int readyWeight() {
            return readyWeight;
        }
    }
}
