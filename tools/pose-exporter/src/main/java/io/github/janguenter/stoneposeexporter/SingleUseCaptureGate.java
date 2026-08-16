/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Non-inheritable, exactly-once callback gate for one synchronous render invocation. */
final class SingleUseCaptureGate<R, C, O> {

    private final ThreadLocal<Session<R, O>> active = new ThreadLocal<>();
    private final Observer<R, C, O> observer;

    SingleUseCaptureGate(Observer<R, C, O> observer) {
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    boolean isArmed() {
        return active.get() != null;
    }

    <E extends Exception> O capture(R request, ThrowingAction<E> action) throws E {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(action, "action");
        if (active.get() != null) {
            throw new IllegalStateException("nested pose capture is forbidden");
        }
        Session<R, O> session = new Session<>(request);
        active.set(session);
        try {
            action.run();
            if (session.fireCount != 1 || session.observation == null) {
                throw new IllegalStateException("pose capture observer did not fire exactly once");
            }
            return session.observation;
        } finally {
            try {
                session.cleanup();
            } finally {
                active.remove();
            }
        }
    }

    void deferCleanup(Runnable cleanup) {
        Session<R, O> session = active.get();
        if (session == null) {
            throw new IllegalStateException("pose capture cleanup requires an active session");
        }
        session.cleanups.addFirst(Objects.requireNonNull(cleanup, "cleanup"));
    }

    void observe(C callback) {
        Session<R, O> session = active.get();
        if (session == null) {
            return;
        }
        session.fireCount = Math.addExact(session.fireCount, 1);
        if (session.fireCount != 1) {
            throw new IllegalStateException("pose capture observer fired more than once");
        }
        session.observation = Objects.requireNonNull(
                observer.observe(session.request, callback), "observation"
        );
    }

    @FunctionalInterface
    interface Observer<R, C, O> {
        O observe(R request, C callback);
    }

    @FunctionalInterface
    interface ThrowingAction<E extends Exception> {
        void run() throws E;
    }

    private static final class Session<R, O> {
        private final R request;
        private final Deque<Runnable> cleanups = new ArrayDeque<>();
        private int fireCount;
        private O observation;

        private Session(R request) {
            this.request = request;
        }

        private void cleanup() {
            while (!cleanups.isEmpty()) {
                cleanups.removeFirst().run();
            }
        }
    }
}
