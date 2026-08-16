/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseStateCodec;
import io.github.janguenter.stoneposeexporter.ExportRecords.PoseStateBlob;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded content-addressed compact pose-state store; no final geometry enters it. */
final class PoseStateStore {

    static final long MAX_UNIQUE_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_STATES = 50_000;

    private final long maximumBytes;
    private final Map<String, byte[]> entries = new LinkedHashMap<>();
    private long retainedBytes;

    PoseStateStore() {
        this(MAX_UNIQUE_BYTES);
    }

    PoseStateStore(long maximumBytes) {
        if (maximumBytes < 1 || maximumBytes > MAX_UNIQUE_BYTES) {
            throw new IllegalArgumentException("invalid pose-state store byte budget");
        }
        this.maximumBytes = maximumBytes;
    }

    String retain(PoseState state) {
        byte[] raw = PoseStateCodec.encode(state);
        String sha256 = PoseStateCodec.sha256(raw);
        byte[] existing = entries.get(sha256);
        if (existing != null) {
            if (!Arrays.equals(existing, raw)) {
                throw new IllegalArgumentException("pose-state SHA-256 collision");
            }
            return sha256;
        }
        if (entries.size() >= MAX_STATES
                || retainedBytes > maximumBytes - raw.length) {
            throw new IllegalArgumentException("pose-state store exceeds aggregate budget");
        }
        entries.put(sha256, raw);
        retainedBytes += raw.length;
        return sha256;
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    long retainedBytes() {
        return retainedBytes;
    }

    byte[] copy(String sha256) {
        byte[] raw = entries.get(sha256);
        if (raw == null) {
            throw new IllegalArgumentException("unknown pose-state identity");
        }
        return raw.clone();
    }

    Map<String, PoseStateBlob> snapshot() {
        Map<String, PoseStateBlob> result = new LinkedHashMap<>();
        entries.forEach((sha256, raw) ->
                result.put(sha256, PoseStateBlob.from(sha256, raw)));
        return java.util.Collections.unmodifiableMap(result);
    }
}
