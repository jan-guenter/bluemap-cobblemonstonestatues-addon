/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.activation;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Objects;

/** Process-scoped, owner-bound atomic runtime snapshot for exact support. */
public final class StoneStatuesRuntime {

    public static final StoneStatuesRuntime INSTANCE = new StoneStatuesRuntime();

    private long nextAttempt;
    private volatile String permanentFailure;
    private volatile Snapshot snapshot = new Snapshot(
            null, State.INACTIVE, "not-installed", null
    );

    StoneStatuesRuntime() {
    }

    public synchronized Attempt beginAttempt(Object owner) {
        Attempt attempt = new Attempt(Objects.requireNonNull(owner, "owner"), ++nextAttempt);
        snapshot = permanentFailure == null
                ? new Snapshot(attempt, State.INACTIVE, "preflight-not-started", null)
                : new Snapshot(attempt, State.FAILED, permanentFailure, null);
        return attempt;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public Attempt attemptFor(Object owner) {
        if (permanentFailure != null) {
            return null;
        }
        Snapshot current = snapshot;
        Attempt attempt = current.attempt();
        return attempt != null && attempt.ownedBy(owner) ? attempt : null;
    }

    public boolean isCurrent(Attempt attempt) {
        return permanentFailure == null
                && attempt != null && snapshot.attempt() == attempt;
    }

    public synchronized boolean inactiveIfCurrent(Attempt attempt, String detail) {
        if (permanentFailure != null || attempt == null || snapshot.attempt() != attempt
                || snapshot.state() == State.FAILED) {
            return false;
        }
        snapshot = new Snapshot(attempt, State.INACTIVE, detail, null);
        return true;
    }

    public synchronized boolean activateIfCurrent(
            Attempt attempt, CompiledProfile profile
    ) {
        Objects.requireNonNull(profile, "profile");
        if (permanentFailure != null || attempt == null
                || snapshot.attempt() != attempt || profile.owner() != attempt
                || snapshot.state() != State.INACTIVE
                || !"pending-bake".equals(snapshot.detail())) {
            return false;
        }
        snapshot = new Snapshot(attempt, State.ACTIVE, "exact-format-2-profile", profile);
        return true;
    }

    public synchronized boolean runIfCurrentPending(Attempt attempt, Runnable action) {
        Objects.requireNonNull(action, "action");
        if (permanentFailure != null || attempt == null
                || snapshot.attempt() != attempt
                || snapshot.state() != State.INACTIVE
                || !"pending-bake".equals(snapshot.detail())) {
            return false;
        }
        action.run();
        return true;
    }

    public synchronized boolean disableIfCurrent(
            Snapshot expected, CompiledProfile profile, String detail
    ) {
        if (permanentFailure != null || expected == null
                || snapshot != expected || snapshot.profile() != profile) {
            return false;
        }
        snapshot = new Snapshot(expected.attempt(), State.FAILED, detail, null);
        return true;
    }

    public synchronized boolean disableIfCurrent(Attempt attempt, String detail) {
        if (permanentFailure != null || attempt == null || snapshot.attempt() != attempt
                || snapshot.state() == State.FAILED) {
            return false;
        }
        snapshot = new Snapshot(attempt, State.FAILED, detail, null);
        return true;
    }

    public synchronized void failPermanently(String detail) {
        permanentFailure = wire(detail);
        snapshot = new Snapshot(snapshot.attempt(), State.FAILED, permanentFailure, null);
    }

    public enum State {
        INACTIVE,
        ACTIVE,
        FAILED
    }

    public record Snapshot(
            Attempt attempt,
            State state,
            String detail,
            CompiledProfile profile
    ) {
        public Snapshot {
            Objects.requireNonNull(state, "state");
            detail = wire(detail);
            if ((state == State.ACTIVE) != (profile != null)
                    || profile != null && profile.owner() != attempt) {
                throw new IllegalArgumentException("runtime snapshot/profile invariant failed");
            }
        }

        public boolean activeFor(Attempt expected) {
            return expected != null && attempt == expected
                    && state == State.ACTIVE && profile != null;
        }
    }

    /** Opaque identity for one ResourcePack extension construction attempt. */
    public static final class Attempt {
        private final WeakReference<Object> owner;
        private final long ordinal;

        private Attempt(Object owner, long ordinal) {
            this.owner = new WeakReference<>(owner);
            this.ordinal = ordinal;
        }

        public long ordinal() {
            return ordinal;
        }

        boolean ownedBy(Object expected) {
            return expected != null && owner.get() == expected;
        }
    }

    private static String wire(String value) {
        Objects.requireNonNull(value, "detail");
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        if (!normalized.matches("[a-z0-9][a-z0-9._:-]*")) {
            throw new IllegalArgumentException("detail must be a lowercase wire value");
        }
        return normalized;
    }
}
