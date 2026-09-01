/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap523;

import de.bluecolored.bluemap.core.map.hires.ArrayTileModel;
import de.bluecolored.bluemap.core.map.hires.MaxCapacityReachedException;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.CompiledProfile;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.CompiledProfileTestAccess;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ProfilePreflight;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.StoneStatuesRuntime;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoneStatuesRendererTest {

    private static final int INITIAL_COLOR = 0xff123456;

    @TempDir
    Path temporary;

    @Test
    void staleRendererAndUnknownSelectionLeaveTheCurrentProfileActive() throws Exception {
        ActiveFixture first = activeFixture();
        RecordingEmitter emitter = new RecordingEmitter();
        StoneStatuesRenderer stale = renderer(first, emitter);
        ActiveFixture replacement = replacement(first.runtime(), first.preflight());
        TileFixture tile = tile();
        Color color = new Color().set(INITIAL_COLOR);

        stale.renderSelection(selection(), null, tile.view(), color);
        assertEquals(0, emitter.calls());
        assertSame(replacement.profile(), first.runtime().snapshot().profile());

        StoneStatuesRenderer current = renderer(replacement, emitter);
        current.renderSelection(new StatueSelection(
                "cobblemon:unknown", "Normal", "", "portrait",
                StatueSelection.Scale.NORMAL, Material.STONE, 0, 0L
        ), null, tile.view(), color);
        assertEquals(0, emitter.calls());
        assertSame(replacement.profile(), first.runtime().snapshot().profile());
        assertEquals(StoneStatuesRuntime.State.ACTIVE,
                first.runtime().snapshot().state());
    }

    @Test
    void replacementDuringEmissionRollsBackWithoutDisablingReplacement() throws Exception {
        ActiveFixture fixture = activeFixture();
        AtomicReference<ActiveFixture> replacement = new AtomicReference<>();
        RecordingEmitter emitter = new RecordingEmitter();
        emitter.duringEmit(() -> {
            try {
                replacement.set(replacement(fixture.runtime(), fixture.preflight()));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        StoneStatuesRenderer renderer = renderer(fixture, emitter);
        TileFixture tile = tile();
        Color color = new Color().set(INITIAL_COLOR);

        renderer.renderSelection(selection(), null, tile.view(), color);

        assertSame(replacement.get().profile(), fixture.runtime().snapshot().profile());
        assertEquals(StoneStatuesRuntime.State.ACTIVE,
                fixture.runtime().snapshot().state());
        assertRolledBack(tile, color);
    }

    @Test
    void ordinaryRenderFailureDisablesAndRollsBackTheCurrentProfile() throws Exception {
        ActiveFixture fixture = activeFixture();
        RecordingEmitter emitter = new RecordingEmitter();
        emitter.failure(new IllegalArgumentException("render failure"));
        StoneStatuesRenderer renderer = renderer(fixture, emitter);
        TileFixture tile = tile();
        Color color = new Color().set(INITIAL_COLOR);

        assertDoesNotThrow(() ->
                renderer.renderSelection(selection(), null, tile.view(), color));

        assertEquals(StoneStatuesRuntime.State.FAILED,
                fixture.runtime().snapshot().state());
        assertEquals("contained-render-failure", fixture.runtime().snapshot().detail());
        assertEquals(1, CompiledProfileTestAccess.cachedEntries(fixture.profile()));
        assertRolledBack(tile, color);
    }

    @Test
    void maxCapacityRollsBackAndRethrowsTheSameInstanceWithoutDisabling() throws Exception {
        ActiveFixture fixture = activeFixture();
        MaxCapacityReachedException capacity =
                new MaxCapacityReachedException("test capacity");
        RecordingEmitter emitter = new RecordingEmitter();
        emitter.failure(capacity);
        StoneStatuesRenderer renderer = renderer(fixture, emitter);
        TileFixture tile = tile();
        Color color = new Color().set(INITIAL_COLOR);

        MaxCapacityReachedException thrown = assertThrows(
                MaxCapacityReachedException.class,
                () -> renderer.renderSelection(selection(), null, tile.view(), color)
        );

        assertSame(capacity, thrown);
        assertEquals(StoneStatuesRuntime.State.ACTIVE,
                fixture.runtime().snapshot().state());
        assertSame(fixture.profile(), fixture.runtime().snapshot().profile());
        assertEquals(1, CompiledProfileTestAccess.cachedEntries(fixture.profile()));
        assertRolledBack(tile, color);
    }

    private ActiveFixture activeFixture() throws Exception {
        StoneStatuesRuntime runtime = StoneStatuesResourceExtensionTest.isolatedRuntime();
        ProfilePreflight.Result preflight =
                StoneStatuesResourceExtensionTest.preflight(temporary);
        return replacement(runtime, preflight);
    }

    private static ActiveFixture replacement(
            StoneStatuesRuntime runtime, ProfilePreflight.Result preflight
    ) {
        Object owner = new Object();
        StoneStatuesRuntime.Attempt attempt = runtime.beginAttempt(owner);
        var pose = preflight.catalog().fallbacks().values().iterator().next();
        Key texture = Key.parse(
                "bluemap_cobblemonstonestatues:generated/stone/renderer-test"
        );
        CompiledProfile profile = CompiledProfileTestAccess.cachedProfile(
                attempt, preflight, Map.of(
                        MaterialTexturePlan.cacheKey(
                                Material.STONE,
                                StoneStatuesResourceExtensionTest.TEXTURE_ID,
                                StoneStatuesResourceExtensionTest.TINT
                        ), texture
                ), pose, geometry()
        );
        assertTrue(runtime.inactiveIfCurrent(attempt, "pending-bake"));
        assertTrue(runtime.activateIfCurrent(attempt, profile));
        return new ActiveFixture(runtime, attempt, profile, preflight, owner);
    }

    private static StoneStatuesRenderer renderer(
            ActiveFixture fixture, RecordingEmitter emitter
    ) {
        return new StoneStatuesRenderer(
                fixture.runtime(), fixture.attempt(), emitter, CompiledProfile::modelFor
        );
    }

    private static StatueSelection selection() {
        return new StatueSelection(
                "cobblemon:test", "Normal", "", "portrait",
                StatueSelection.Scale.NORMAL, Material.STONE, 0, 0L
        );
    }

    private static ProfilePreflight.Geometry geometry() {
        StatueModel.Vec3 minimum = new StatueModel.Vec3(0D, 0D, 0D);
        StatueModel.Vec3 maximum = new StatueModel.Vec3(1D, 1D, 0D);
        StatueModel.Quad quad = new StatueModel.Quad(
                vertex(0D, 0D), vertex(1D, 0D),
                vertex(1D, 1D), vertex(0D, 1D),
                new StatueModel.Vec3(0D, 0D, 1D)
        );
        return new ProfilePreflight.Geometry(
                List.of(quad), new StatueModel.Bounds(minimum, maximum)
        );
    }

    private static StatueModel.Vertex vertex(double x, double y) {
        return new StatueModel.Vertex(new StatueModel.Vec3(x, y, 0D), 0F, 0F);
    }

    private static TileFixture tile() {
        ArrayTileModel model = new ArrayTileModel(4);
        model.add(1);
        return new TileFixture(model, new TileModelView(model));
    }

    private static void assertRolledBack(TileFixture tile, Color color) {
        assertEquals(1, tile.model().size());
        assertEquals(1, tile.view().getStart());
        assertEquals(0, tile.view().getSize());
        assertEquals(INITIAL_COLOR, color.getInt());
    }

    private record ActiveFixture(
            StoneStatuesRuntime runtime,
            StoneStatuesRuntime.Attempt attempt,
            CompiledProfile profile,
            ProfilePreflight.Result preflight,
            Object owner
    ) {
    }

    private record TileFixture(ArrayTileModel model, TileModelView view) {
    }

    private static final class RecordingEmitter implements StoneStatuesRenderer.MeshEmitter {
        private final AtomicInteger calls = new AtomicInteger();
        private Runnable duringEmit = () -> { };
        private RuntimeException failure;

        @Override
        public void beginVariantColor() {
        }

        @Override
        public boolean emit(
                StatueModel model,
                StatueSelection selection,
                BlockNeighborhood block,
                TileModelView target,
                Color mapColor
        ) {
            calls.incrementAndGet();
            target.add(2);
            mapColor.set(0xffabcdef);
            duringEmit.run();
            if (failure != null) {
                throw failure;
            }
            return true;
        }

        @Override
        public void finishVariantColor(Color mapColor) {
            mapColor.set(0xfffedcba);
        }

        void duringEmit(Runnable action) {
            duringEmit = action;
        }

        void failure(RuntimeException value) {
            failure = value;
        }

        int calls() {
            return calls.get();
        }
    }
}
