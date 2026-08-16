/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.activation;

import io.github.janguenter.bluemap.cobblemonstonestatues.activation.CompiledProfile.CacheKey;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.CompiledProfile.GeometryCache;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ProfilePreflight.Geometry;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledProfileTest {

    @Test
    void productionCacheBudgetsAreLiteralConstructorCeilings() {
        assertEquals(128, CompiledProfile.MAX_CACHE_ENTRIES);
        assertEquals(32_768, CompiledProfile.MAX_CACHE_QUADS);
        new GeometryCache(
                CompiledProfile.MAX_CACHE_ENTRIES,
                CompiledProfile.MAX_CACHE_QUADS
        );
        assertThrows(IllegalArgumentException.class, () -> new GeometryCache(
                CompiledProfile.MAX_CACHE_ENTRIES + 1,
                CompiledProfile.MAX_CACHE_QUADS
        ));
        assertThrows(IllegalArgumentException.class, () -> new GeometryCache(
                CompiledProfile.MAX_CACHE_ENTRIES,
                CompiledProfile.MAX_CACHE_QUADS + 1
        ));
    }

    @Test
    void completedGeometryStaysSingleFlightThroughImmediateEviction() throws Exception {
        CacheKey firstKey = new CacheKey("model:first", "a".repeat(64), "b".repeat(64));
        CacheKey secondKey = new CacheKey("model:second", "c".repeat(64), "d".repeat(64));
        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch duplicateLoader = new CountDownLatch(1);
        GeometryCache cache = new GeometryCache(1, 4, key -> {
            if (key.equals(firstKey)) {
                firstReady.countDown();
                await(releaseFirst);
            }
        });
        Geometry first = geometry(0D);
        Geometry second = geometry(2D);
        AtomicInteger firstLoads = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var leader = executor.submit(() -> cache.get(
                    firstKey,
                    () -> {
                        firstLoads.incrementAndGet();
                        return first;
                    },
                    () -> { }
            ));
            assertTrue(firstReady.await(5, TimeUnit.SECONDS));

            assertSame(second, cache.get(secondKey, () -> second, () -> { }));
            var waiter = executor.submit(() -> cache.get(
                    firstKey,
                    () -> {
                        firstLoads.incrementAndGet();
                        duplicateLoader.countDown();
                        return first;
                    },
                    () -> { }
            ));

            assertFalse(duplicateLoader.await(200, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            assertSame(first, leader.get(5, TimeUnit.SECONDS));
            assertSame(first, waiter.get(5, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
        }
        assertEquals(1, firstLoads.get());
    }

    @Test
    void sameKeySuccessAndFailureFanOutToOneLoader() throws Exception {
        assertFanOut(false);
        assertFanOut(true);
    }

    @Test
    void callbackErrorIsTheSharedPublishedFailure() throws Exception {
        CacheKey key = key("callback-error");
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch followerAttached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger();
        IllegalArgumentException loaderFailure =
                new IllegalArgumentException("loader failure");
        AssertionError callbackError = new AssertionError("disable callback failed");
        GeometryCache cache = new GeometryCache(
                2, 4, ignored -> { }, ignored -> followerAttached.countDown()
        );
        Runnable callback = () -> {
            callbackCount.incrementAndGet();
            throw callbackError;
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var leader = executor.submit(() -> cache.get(key, () -> {
                loaderStarted.countDown();
                await(release);
                throw loaderFailure;
            }, callback));
            assertTrue(loaderStarted.await(5, TimeUnit.SECONDS));
            var follower = executor.submit(() -> cache.get(
                    key, () -> geometry(4D), callback
            ));
            assertTrue(followerAttached.await(5, TimeUnit.SECONDS));
            release.countDown();

            assertSame(callbackError, executionCause(leader));
            assertSame(callbackError, executionCause(follower));
        } finally {
            release.countDown();
        }
        assertEquals(1, callbackCount.get());
        assertEquals(1, callbackError.getSuppressed().length);
        assertSame(loaderFailure, callbackError.getSuppressed()[0]);
    }

    @Test
    void differentKeysCompileOutsideTheCacheMonitor() throws Exception {
        GeometryCache cache = new GeometryCache(4, 8);
        CountDownLatch bothLoaders = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CacheKey firstKey = key("first");
        CacheKey secondKey = key("second");

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cache.get(firstKey, () -> {
                bothLoaders.countDown();
                await(release);
                return geometry(0D);
            }, () -> { }));
            var second = executor.submit(() -> cache.get(secondKey, () -> {
                bothLoaders.countDown();
                await(release);
                return geometry(2D);
            }, () -> { }));

            assertTrue(bothLoaders.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertSame(first.get(5, TimeUnit.SECONDS), cache.get(
                    firstKey, () -> geometry(8D), () -> { }
            ));
            assertSame(second.get(5, TimeUnit.SECONDS), cache.get(
                    secondKey, () -> geometry(10D), () -> { }
            ));
        } finally {
            release.countDown();
        }
    }

    @Test
    void failuresCanRetryButRecursiveSameKeyFailsClosed() {
        GeometryCache cache = new GeometryCache(2, 4);
        CacheKey retry = key("retry");
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> cache.get(retry, () -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("first failure");
        }, () -> { }));
        Geometry recovered = geometry(0D);
        assertSame(recovered, cache.get(retry, () -> {
            attempts.incrementAndGet();
            return recovered;
        }, () -> { }));
        assertEquals(2, attempts.get());

        CacheKey recursive = key("recursive");
        assertThrows(IllegalStateException.class, () -> cache.get(
                recursive,
                () -> cache.get(recursive, () -> geometry(4D), () -> { }),
                () -> { }
        ));
    }

    @Test
    void countAndWeightEvictionUseAccessOrder() {
        GeometryCache countCache = new GeometryCache(2, 8);
        CacheKey countFirst = key("count-first");
        CacheKey countSecond = key("count-second");
        CacheKey countThird = key("count-third");
        Geometry first = geometry(0D);
        Geometry second = geometry(2D);
        countCache.get(countFirst, () -> first, () -> { });
        countCache.get(countSecond, () -> second, () -> { });
        assertSame(first, countCache.get(countFirst, () -> geometry(4D), () -> { }));
        countCache.get(countThird, () -> geometry(6D), () -> { });
        AtomicInteger countReload = new AtomicInteger();
        assertSame(first, countCache.get(countFirst, () -> geometry(8D), () -> { }));
        countCache.get(countSecond, () -> {
            countReload.incrementAndGet();
            return second;
        }, () -> { });
        assertEquals(1, countReload.get());

        GeometryCache weightCache = new GeometryCache(8, 4);
        CacheKey weightFirst = key("weight-first");
        CacheKey weightSecond = key("weight-second");
        CacheKey weightThird = key("weight-third");
        Geometry heavy = geometry(10D, 2);
        Geometry light = geometry(14D);
        weightCache.get(weightFirst, () -> heavy, () -> { });
        weightCache.get(weightSecond, () -> light, () -> { });
        weightCache.get(weightFirst, () -> geometry(18D), () -> { });
        weightCache.get(weightThird, () -> geometry(20D, 2), () -> { });
        AtomicInteger weightReload = new AtomicInteger();
        assertSame(heavy, weightCache.get(
                weightFirst, () -> geometry(24D), () -> { }
        ));
        weightCache.get(weightSecond, () -> {
            weightReload.incrementAndGet();
            return light;
        }, () -> { });
        assertEquals(1, weightReload.get());
    }

    private static void assertFanOut(boolean fail) throws Exception {
        CacheKey key = key(fail ? "failure" : "success");
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch followerAttached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger failureCallbacks = new AtomicInteger();
        AtomicBoolean failurePublished = new AtomicBoolean();
        AtomicInteger failureObservers = new AtomicInteger();
        IllegalArgumentException failure = new IllegalArgumentException("shared failure");
        Geometry geometry = geometry(0D);
        GeometryCache cache = new GeometryCache(
                2, 4, ignored -> { }, ignored -> followerAttached.countDown()
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            Runnable publishFailure = () -> {
                failureCallbacks.incrementAndGet();
                failurePublished.set(true);
            };
            var leader = executor.submit(() -> observeFailurePublication(
                    () -> cache.get(key, () -> {
                        loads.incrementAndGet();
                        loaderStarted.countDown();
                        await(release);
                        if (fail) {
                            throw failure;
                        }
                        return geometry;
                    }, publishFailure), fail, failurePublished, failureObservers
            ));
            assertTrue(loaderStarted.await(5, TimeUnit.SECONDS));
            var follower = executor.submit(() -> observeFailurePublication(
                    () -> cache.get(key, () -> {
                        loads.incrementAndGet();
                        return geometry;
                    }, publishFailure), fail, failurePublished, failureObservers
            ));
            assertTrue(followerAttached.await(5, TimeUnit.SECONDS));
            release.countDown();
            if (fail) {
                assertSame(failure, executionCause(leader));
                assertSame(failure, executionCause(follower));
                assertEquals(1, failureCallbacks.get());
                assertEquals(2, failureObservers.get());
            } else {
                assertSame(geometry, leader.get(5, TimeUnit.SECONDS));
                assertSame(geometry, follower.get(5, TimeUnit.SECONDS));
                assertEquals(0, failureCallbacks.get());
            }
        } finally {
            release.countDown();
        }
        assertEquals(1, loads.get());
    }

    private static Geometry observeFailurePublication(
            java.util.concurrent.Callable<Geometry> action,
            boolean expectedFailure,
            AtomicBoolean failurePublished,
            AtomicInteger failureObservers
    ) throws Exception {
        try {
            return action.call();
        } catch (RuntimeException exception) {
            if (!expectedFailure || !failurePublished.get()) {
                throw new AssertionError(
                        "same-flight exception escaped before failure publication",
                        exception
                );
            }
            failureObservers.incrementAndGet();
            throw exception;
        }
    }

    private static Throwable executionCause(java.util.concurrent.Future<?> future)
            throws Exception {
        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS)
        );
        return failure.getCause();
    }

    private static CacheKey key(String value) {
        return new CacheKey("model:" + value, "a".repeat(64), value);
    }

    private static Geometry geometry(double offset) {
        return geometry(offset, 1);
    }

    private static Geometry geometry(double offset, int quadCount) {
        java.util.ArrayList<StatueModel.Quad> quads = new java.util.ArrayList<>();
        for (int index = 0; index < quadCount; index++) {
            double shifted = offset + index * 2D;
            StatueModel.Vec3 first = new StatueModel.Vec3(shifted, 0D, 0D);
            StatueModel.Vec3 second = new StatueModel.Vec3(shifted + 1D, 0D, 0D);
            StatueModel.Vec3 third = new StatueModel.Vec3(shifted + 1D, 1D, 0D);
            StatueModel.Vec3 fourth = new StatueModel.Vec3(shifted, 1D, 0D);
            quads.add(new StatueModel.Quad(
                    vertex(first), vertex(second), vertex(third), vertex(fourth),
                    new StatueModel.Vec3(0D, 0D, 1D)
            ));
        }
        StatueModel.Vec3 first = new StatueModel.Vec3(offset, 0D, 0D);
        StatueModel.Vec3 maximum = new StatueModel.Vec3(
                offset + (quadCount - 1) * 2D + 1D, 1D, 0D
        );
        return new Geometry(List.copyOf(quads), new StatueModel.Bounds(first, maximum));
    }

    private static StatueModel.Vertex vertex(StatueModel.Vec3 position) {
        return new StatueModel.Vertex(position, 0F, 0F);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for cache test barrier");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("cache test interrupted", exception);
        }
    }
}
