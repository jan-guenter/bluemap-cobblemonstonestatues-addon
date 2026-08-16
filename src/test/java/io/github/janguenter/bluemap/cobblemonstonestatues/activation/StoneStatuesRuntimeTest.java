/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.activation;

import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoneStatuesRuntimeTest {

    @Test
    void permanentFailureCannotBeRecoveredByAResourcePackAttempt() {
        StoneStatuesRuntime runtime = new StoneStatuesRuntime();
        StoneStatuesRuntime.Attempt first = runtime.beginAttempt(new Object());
        assertTrue(runtime.inactiveIfCurrent(first, "ordinary-preflight-failure"));

        runtime.failPermanently("registry-collision");
        Object laterOwner = new Object();
        StoneStatuesRuntime.Attempt later = runtime.beginAttempt(laterOwner);

        assertFalse(runtime.isCurrent(later));
        assertNull(runtime.attemptFor(laterOwner));
        assertEquals(StoneStatuesRuntime.State.FAILED, runtime.snapshot().state());
        assertEquals("registry-collision", runtime.snapshot().detail());
        assertFalse(runtime.inactiveIfCurrent(later, "preflight-not-started"));
        assertFalse(runtime.disableIfCurrent(later, "ordinary-runtime-failure"));
        assertEquals("registry-collision", runtime.snapshot().detail());
    }

    @Test
    void ordinaryAttemptFailureRemainsRecoverableOnTheNextAttempt() {
        StoneStatuesRuntime runtime = new StoneStatuesRuntime();
        StoneStatuesRuntime.Attempt first = runtime.beginAttempt(new Object());
        assertTrue(runtime.disableIfCurrent(first, "format-2-preflight-failed"));
        assertFalse(runtime.inactiveIfCurrent(first, "preflight-running"));
        assertEquals(StoneStatuesRuntime.State.FAILED, runtime.snapshot().state());

        StoneStatuesRuntime.Attempt later = runtime.beginAttempt(new Object());

        assertEquals(StoneStatuesRuntime.State.INACTIVE, runtime.snapshot().state());
        assertTrue(runtime.inactiveIfCurrent(later, "pending-bake"));
    }

    @Test
    void attemptUsesOnlyAWeakOwnerIdentity() {
        StoneStatuesRuntime runtime = new StoneStatuesRuntime();
        Object owner = new Object();
        StoneStatuesRuntime.Attempt attempt = runtime.beginAttempt(owner);

        assertTrue(attempt.ownedBy(owner));
        assertFalse(attempt.ownedBy(null));
        assertTrue(Arrays.stream(StoneStatuesRuntime.Attempt.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == WeakReference.class));
        assertFalse(Arrays.stream(StoneStatuesRuntime.Attempt.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == Object.class));
    }

    @Test
    void stalePendingPublicationNeverInvokesItsCallback() {
        StoneStatuesRuntime runtime = new StoneStatuesRuntime();
        StoneStatuesRuntime.Attempt stale = runtime.beginAttempt(new Object());
        assertTrue(runtime.inactiveIfCurrent(stale, "pending-bake"));
        StoneStatuesRuntime.Attempt current = runtime.beginAttempt(new Object());
        AtomicBoolean invoked = new AtomicBoolean();

        assertFalse(runtime.runIfCurrentPending(stale, () -> invoked.set(true)));
        assertFalse(invoked.get());
        assertTrue(runtime.isCurrent(current));
    }
}
