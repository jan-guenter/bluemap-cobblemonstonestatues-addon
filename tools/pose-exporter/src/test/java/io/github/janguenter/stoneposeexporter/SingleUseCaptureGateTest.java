/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleUseCaptureGateTest {

    @Test
    void ignoresUnarmedCallbacksAndRequiresExactlyOneArmedCallback() {
        SingleUseCaptureGate<String, String, String> gate = gate();
        gate.observe("ordinary render");
        assertEquals("expected", gate.capture("expected", () -> gate.observe("expected")));
        assertThrows(IllegalStateException.class,
                () -> gate.capture("missing", () -> { }));
        assertThrows(IllegalStateException.class, () -> gate.capture("twice", () -> {
            gate.observe("twice");
            gate.observe("twice");
        }));
        assertThrows(IllegalArgumentException.class,
                () -> gate.capture("expected", () -> gate.observe("wrong")));
    }

    @Test
    void rejectsNestedCaptureAndAlwaysClearsAfterFailures() {
        SingleUseCaptureGate<String, String, String> gate = gate();
        assertThrows(IllegalStateException.class, () -> gate.capture("outer", () ->
                gate.capture("inner", () -> gate.observe("inner"))));
        assertThrows(IOException.class, () -> gate.capture("throw", () -> {
            throw new IOException("bridge failure");
        }));
        assertEquals("next", gate.capture("next", () -> gate.observe("next")));
    }

    @Test
    void activeSessionDoesNotPropagateToAChildThread() throws Exception {
        SingleUseCaptureGate<String, String, String> gate = gate();
        AtomicReference<Throwable> childFailure = new AtomicReference<>();
        assertEquals("parent", gate.capture("parent", () -> {
            Thread child = new Thread(() -> {
                try {
                    gate.observe("parent");
                } catch (Throwable throwable) {
                    childFailure.set(throwable);
                }
            });
            child.start();
            child.join();
            gate.observe("parent");
        }));
        assertNull(childFailure.get());
    }

    @Test
    void deferredCleanupRunsAfterActionAndEveryFailurePath() {
        SingleUseCaptureGate<String, String, String> gate = gate();
        int[] value = {0};

        assertEquals("success", gate.capture("success", () -> {
            value[0] = 1;
            gate.deferCleanup(() -> value[0] = 0);
            gate.observe("success");
            assertEquals(1, value[0]);
        }));
        assertEquals(0, value[0]);

        assertThrows(IOException.class, () -> gate.capture("action-failure", () -> {
            value[0] = 2;
            gate.deferCleanup(() -> value[0] = 0);
            throw new IOException("bridge failure");
        }));
        assertEquals(0, value[0]);

        assertThrows(IllegalArgumentException.class, () -> gate.capture("observer-failure", () -> {
            value[0] = 3;
            gate.deferCleanup(() -> value[0] = 0);
            gate.observe("wrong");
        }));
        assertEquals(0, value[0]);
        assertThrows(IllegalStateException.class, () -> gate.deferCleanup(() -> { }));
    }

    @Test
    void temporarySkipDrawIsVisibleDuringBridgeAndRestoredAfterSuccessAndFailure() {
        SingleUseCaptureGate<String, String, String> gate = gate();
        ModelPart ordinary = new ModelPart(List.of(), Map.of());

        assertEquals("success", gate.capture("success", () -> {
            PoseCaptureHook.TemporarySkipDraw suppressions =
                    new PoseCaptureHook.TemporarySkipDraw();
            gate.deferCleanup(suppressions);
            suppressions.suppress(ordinary);
            assertTrue(ordinary.skipDraw);
            gate.observe("success");
            assertTrue(ordinary.skipDraw);
        }));
        assertFalse(ordinary.skipDraw);

        assertThrows(IOException.class, () -> gate.capture("failure", () -> {
            PoseCaptureHook.TemporarySkipDraw suppressions =
                    new PoseCaptureHook.TemporarySkipDraw();
            gate.deferCleanup(suppressions);
            suppressions.suppress(ordinary);
            assertTrue(ordinary.skipDraw);
            throw new IOException("bridge failed after observer mutation");
        }));
        assertFalse(ordinary.skipDraw);

        ModelPart alreadySkipped = new ModelPart(List.of(), Map.of());
        alreadySkipped.skipDraw = true;
        PoseCaptureHook.TemporarySkipDraw suppressions =
                new PoseCaptureHook.TemporarySkipDraw();
        suppressions.suppress(alreadySkipped);
        suppressions.run();
        assertTrue(alreadySkipped.skipDraw);
    }

    @Test
    void observerFailureAfterMutationStillRestoresSkipDraw() {
        AtomicReference<SingleUseCaptureGate<ModelPart, ModelPart, String>> reference =
                new AtomicReference<>();
        SingleUseCaptureGate<ModelPart, ModelPart, String> gate =
                new SingleUseCaptureGate<>((request, callback) -> {
                    PoseCaptureHook.TemporarySkipDraw suppressions =
                            new PoseCaptureHook.TemporarySkipDraw();
                    reference.get().deferCleanup(suppressions);
                    suppressions.suppress(callback);
                    throw new IllegalArgumentException("observer failed after suppression");
                });
        reference.set(gate);
        ModelPart part = new ModelPart(List.of(), Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> gate.capture(part, () -> gate.observe(part)));
        assertFalse(part.skipDraw);
    }

    private static SingleUseCaptureGate<String, String, String> gate() {
        return new SingleUseCaptureGate<>((request, callback) -> {
            if (!request.equals(callback)) {
                throw new IllegalArgumentException("callback identity mismatch");
            }
            return callback;
        });
    }
}
