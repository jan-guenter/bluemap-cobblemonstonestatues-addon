/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Dependency-free ordered winner registry used by the exact client resource observer. */
final class OrderedModelBindingRegistry<K, P, N> {

    private final int maximumBindings;
    private final long maximumBytes;
    private final Map<K, Binding<P, N>> winners = new LinkedHashMap<>();
    private boolean collecting;
    private boolean complete;
    private long generation;
    private int invocationOrdinal;
    private long observedBytes;

    OrderedModelBindingRegistry(int maximumBindings, long maximumBytes) {
        if (maximumBindings < 1 || maximumBytes < 1) {
            throw new IllegalArgumentException("invalid model-binding budget");
        }
        this.maximumBindings = maximumBindings;
        this.maximumBytes = maximumBytes;
    }

    synchronized void begin() {
        if (collecting) {
            throw new IllegalStateException("model-binding collection is already active");
        }
        start();
    }

    synchronized void restart() {
        start();
    }

    private void start() {
        winners.clear();
        collecting = true;
        complete = false;
        generation = Math.incrementExact(generation);
        invocationOrdinal = 0;
        observedBytes = 0L;
    }

    synchronized void record(
            K logicalId,
            P physicalId,
            String packId,
            long size,
            String sha256,
            N node
    ) {
        if (!collecting || complete) {
            throw new IllegalStateException("model binding observed outside registration");
        }
        Objects.requireNonNull(logicalId, "logicalId");
        Objects.requireNonNull(physicalId, "physicalId");
        Objects.requireNonNull(node, "node");
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("model binding lacks source pack");
        }
        invocationOrdinal = Math.incrementExact(invocationOrdinal);
        int ordinal = invocationOrdinal;
        observedBytes = Math.addExact(observedBytes, size);
        if (ordinal > maximumBindings || observedBytes > maximumBytes || size < 1
                || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("model binding registry exceeds budget");
        }
        winners.put(logicalId, new Binding<>(
                physicalId, packId, size, sha256, node, ordinal
        ));
    }

    synchronized void complete() {
        if (!collecting || complete || winners.isEmpty()) {
            throw new IllegalStateException("model-binding collection is incomplete");
        }
        collecting = false;
        complete = true;
    }

    synchronized void invalidate() {
        winners.clear();
        collecting = false;
        complete = false;
    }

    synchronized Resolved<P> resolve(K logicalId, N expectedNode) {
        if (!complete || collecting) {
            throw new IllegalStateException("model-binding registry is not complete");
        }
        Binding<P, N> winner = winners.get(logicalId);
        if (winner == null || winner.node != expectedNode) {
            throw new IllegalArgumentException(
                    "effective model does not match the observed built-in GEO winner"
            );
        }
        return new Resolved<>(
                winner.physicalId, winner.packId, winner.size,
                winner.sha256, winner.ordinal
        );
    }

    synchronized long generation() {
        if (!complete || collecting || generation < 1) {
            throw new IllegalStateException("model-binding registry is not complete");
        }
        return generation;
    }

    private record Binding<P, N>(
            P physicalId,
            String packId,
            long size,
            String sha256,
            N node,
            int ordinal
    ) {
    }

    record Resolved<P>(
            P physicalId,
            String packId,
            long size,
            String sha256,
            int registrationOrdinal
    ) {
        Resolved {
            Objects.requireNonNull(physicalId, "physicalId");
            Objects.requireNonNull(packId, "packId");
            if (packId.isBlank() || size < 1 || sha256 == null
                    || !sha256.matches("[0-9a-f]{64}") || registrationOrdinal < 1) {
                throw new IllegalArgumentException("invalid resolved model binding");
            }
        }
    }
}
