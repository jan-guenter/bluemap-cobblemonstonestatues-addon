/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelBindingObserverCoreTest {

    private static final String PHYSICAL = "models/joltik.geo.json";

    @Test
    void associatesExactBytesAndDiscardsNullFactoryResult() {
        ModelBindingObserverCore<String, String, Object> observer = observer(3, 32, 64L);
        Object winner = new Object();
        byte[] first = "first-geo".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ignored = "invalid-geo".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] last = "last-geo".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        observer.beginReload();
        observe(observer, PHYSICAL, first, "joltik.geo", new Object());
        observe(observer, "models/invalid.geo.json", ignored, null, null);
        observe(observer, PHYSICAL, last, "joltik.geo", winner);
        observer.completeReload();

        OrderedModelBindingRegistry.Resolved<String> resolved = observer.resolve(
                "joltik.geo", winner
        );
        assertEquals(PHYSICAL, resolved.physicalId());
        assertEquals(last.length, resolved.size());
        assertEquals(Hashing.sha256(last), resolved.sha256());
        assertEquals(2, resolved.registrationOrdinal());
        assertEquals(1L, observer.generation());
    }

    @Test
    void laterHeadRecoversFromAbandonedAndInvalidEpochs() {
        ModelBindingObserverCore<String, String, Object> observer = observer(2, 32, 64L);
        observer.beginReload();
        observer.beginFactory(PHYSICAL, "mod/cobblemon");
        assertThrows(IllegalStateException.class, observer::generation);

        Object recovered = new Object();
        observer.beginReload();
        observe(observer, PHYSICAL, bytes("recovered"), "joltik.geo", recovered);
        observer.completeReload();
        assertEquals(2L, observer.generation());
        assertEquals(PHYSICAL, observer.resolve("joltik.geo", recovered).physicalId());

        observer.beginReload();
        assertDoesNotThrow(() -> observer.finishFactory("orphan", new Object()));
        assertDoesNotThrow(observer::completeReload);
        assertThrows(IllegalStateException.class, observer::generation);

        Object secondRecovery = new Object();
        observer.beginReload();
        observe(observer, PHYSICAL, bytes("again"), "joltik.geo", secondRecovery);
        observer.completeReload();
        assertEquals(4L, observer.generation());
    }

    @Test
    void crossThreadCallbackInvalidatesWithoutThrowingIntoCaller() throws Exception {
        ModelBindingObserverCore<String, String, Object> observer = observer(2, 32, 64L);
        observer.beginReload();
        observer.beginFactory(PHYSICAL, "mod/cobblemon");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                observer.observeFactoryBytes(bytes("cross-thread"));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        other.start();
        other.join();
        assertNull(failure.get());
        assertDoesNotThrow(() -> observer.finishFactory("joltik.geo", new Object()));
        assertDoesNotThrow(observer::completeReload);
        assertThrows(IllegalStateException.class, observer::generation);
    }

    @Test
    void nullAndSuccessfulResultsBothConsumeEventAndByteBudgets() {
        ModelBindingObserverCore<String, String, Object> calls = observer(2, 16, 32L);
        calls.beginReload();
        observe(calls, "models/null.geo.json", bytes("null"), null, null);
        observe(calls, PHYSICAL, bytes("ok"), "joltik.geo", new Object());
        assertDoesNotThrow(() -> calls.beginFactory("models/third.geo.json", "pack"));
        assertDoesNotThrow(calls::completeReload);
        assertThrows(IllegalStateException.class, calls::generation);

        ModelBindingObserverCore<String, String, Object> bytes = observer(3, 16, 6L);
        bytes.beginReload();
        observe(bytes, "models/null.geo.json", bytes("four"), null, null);
        assertDoesNotThrow(() -> bytes.beginFactory(PHYSICAL, "pack"));
        assertDoesNotThrow(() -> bytes.observeFactoryBytes(bytes("tri")));
        assertDoesNotThrow(() -> bytes.finishFactory("joltik.geo", new Object()));
        assertDoesNotThrow(bytes::completeReload);
        assertThrows(IllegalStateException.class, bytes::generation);
    }

    @Test
    void generationIsUnavailableDuringCollectionAndDriftsAcrossReloads() {
        ModelBindingObserverCore<String, String, Object> observer = observer(1, 16, 16L);
        Object first = new Object();
        observer.beginReload();
        assertThrows(IllegalStateException.class, observer::generation);
        observe(observer, PHYSICAL, bytes("first"), "joltik.geo", first);
        observer.completeReload();
        assertEquals(1L, observer.generation());

        Object second = new Object();
        observer.beginReload();
        assertThrows(IllegalStateException.class, observer::generation);
        observe(observer, PHYSICAL, bytes("second"), "joltik.geo", second);
        observer.completeReload();
        assertEquals(2L, observer.generation());
        assertThrows(IllegalArgumentException.class, () ->
                observer.resolve("joltik.geo", first));
    }

    private static ModelBindingObserverCore<String, String, Object> observer(
            int calls, int perResource, long aggregate
    ) {
        return new ModelBindingObserverCore<>(
                calls, perResource, aggregate,
                pack -> pack != null && !pack.isBlank(),
                (physical, logical) -> physical.endsWith('/' + logical + ".json")
        );
    }

    private static void observe(
            ModelBindingObserverCore<String, String, Object> observer,
            String physical,
            byte[] raw,
            String logical,
            Object node
    ) {
        observer.beginFactory(physical, "mod/cobblemon");
        observer.observeFactoryBytes(raw);
        observer.finishFactory(logical, node);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
