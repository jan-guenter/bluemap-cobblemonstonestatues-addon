/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Dependency-free, noninterfering lifecycle for observing one synchronous model factory.
 * Observation methods deliberately never throw into the runtime being observed.
 */
final class ModelBindingObserverCore<K, P, N> {

    private final int maximumFactoryCalls;
    private final int maximumResourceBytes;
    private final long maximumAggregateBytes;
    private final Predicate<String> packIdValidator;
    private final BiPredicate<P, K> logicalIdValidator;
    private final OrderedModelBindingRegistry<K, P, N> registry;
    private Thread owner;
    private Pending<P> pending;
    private boolean invalid = true;
    private int observedFactoryCalls;
    private long observedFactoryBytes;

    ModelBindingObserverCore(
            int maximumFactoryCalls,
            int maximumResourceBytes,
            long maximumAggregateBytes,
            Predicate<String> packIdValidator,
            BiPredicate<P, K> logicalIdValidator
    ) {
        if (maximumFactoryCalls < 1 || maximumResourceBytes < 1
                || maximumAggregateBytes < 1) {
            throw new IllegalArgumentException("invalid model observer budget");
        }
        this.maximumFactoryCalls = maximumFactoryCalls;
        this.maximumResourceBytes = maximumResourceBytes;
        this.maximumAggregateBytes = maximumAggregateBytes;
        this.packIdValidator = Objects.requireNonNull(packIdValidator, "packIdValidator");
        this.logicalIdValidator = Objects.requireNonNull(
                logicalIdValidator, "logicalIdValidator"
        );
        registry = new OrderedModelBindingRegistry<>(
                maximumFactoryCalls, maximumAggregateBytes
        );
    }

    synchronized void beginReload() {
        owner = Thread.currentThread();
        pending = null;
        invalid = false;
        observedFactoryCalls = 0;
        observedFactoryBytes = 0L;
        try {
            registry.restart();
        } catch (RuntimeException exception) {
            invalidateInternal();
        }
    }

    synchronized void beginFactory(P physicalId, String packId) {
        if (!validOwner() || pending != null) {
            invalidateInternal();
            return;
        }
        try {
            observedFactoryCalls = Math.incrementExact(observedFactoryCalls);
            if (observedFactoryCalls > maximumFactoryCalls
                    || physicalId == null || !packIdValidator.test(packId)) {
                throw new IllegalArgumentException("invalid built-in GEO factory metadata");
            }
            pending = new Pending<>(physicalId, packId, null);
        } catch (RuntimeException exception) {
            invalidateInternal();
        }
    }

    synchronized void observeFactoryBytes(byte[] raw) {
        if (!validOwner() || pending == null || pending.digest != null) {
            invalidateInternal();
            return;
        }
        try {
            if (raw == null || raw.length < 2 || raw.length > maximumResourceBytes) {
                throw new IllegalArgumentException("built-in GEO bytes outside budget");
            }
            observedFactoryBytes = Math.addExact(observedFactoryBytes, raw.length);
            if (observedFactoryBytes > maximumAggregateBytes) {
                throw new IllegalArgumentException("built-in GEO aggregate exceeds budget");
            }
            pending = new Pending<>(
                    pending.physicalId, pending.packId,
                    new Hashing.DigestResult(raw.length, Hashing.sha256(raw))
            );
        } catch (RuntimeException exception) {
            invalidateInternal();
        }
    }

    synchronized void finishFactory(K logicalId, N node) {
        Pending<P> completed = pending;
        pending = null;
        if (!validOwner() || completed == null || completed.digest == null) {
            invalidateInternal();
            return;
        }
        if (logicalId == null && node == null) {
            return;
        }
        try {
            if (logicalId == null || node == null
                    || !logicalIdValidator.test(completed.physicalId, logicalId)) {
                throw new IllegalArgumentException("built-in GEO logical identity drift");
            }
            registry.record(
                    logicalId, completed.physicalId, completed.packId,
                    completed.digest.size(), completed.digest.sha256(), node
            );
        } catch (RuntimeException exception) {
            invalidateInternal();
        }
    }

    synchronized void completeReload() {
        if (!validOwner() || pending != null) {
            invalidateInternal();
        } else {
            try {
                registry.complete();
            } catch (RuntimeException exception) {
                invalidateInternal();
            }
        }
        owner = null;
        pending = null;
    }

    synchronized void invalidate() {
        invalidateInternal();
    }

    synchronized OrderedModelBindingRegistry.Resolved<P> resolve(
            K logicalId, N expectedNode
    ) {
        requireAvailable();
        return registry.resolve(logicalId, expectedNode);
    }

    synchronized long generation() {
        requireAvailable();
        return registry.generation();
    }

    private void requireAvailable() {
        if (invalid || owner != null) {
            throw new IllegalStateException("model-resource binding epoch is unavailable");
        }
    }

    private boolean validOwner() {
        return !invalid && owner == Thread.currentThread();
    }

    private void invalidateInternal() {
        invalid = true;
        pending = null;
        registry.invalidate();
    }

    private record Pending<P>(
            P physicalId,
            String packId,
            Hashing.DigestResult digest
    ) {
    }
}
