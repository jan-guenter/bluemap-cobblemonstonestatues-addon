/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap523;

import de.bluecolored.bluemap.core.resources.pack.PackVersion;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Key;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.CompiledProfile;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ProfilePreflight;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ResourceBlob;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.StoneStatuesRuntime;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.Attestation;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.CodeSourceIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ModelIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseEntry;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseStateIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ResolutionKind;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ResourcePackIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.TextureIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BakedGeometryTopology;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BedrockGeometry;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection.Material;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseStateCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoneStatuesResourceExtensionTest {

    static final String MODEL_ID = "cobblemon:test.geo";
    static final String TEXTURE_ID = "cobblemon:textures/pokemon/test.png";
    static final int TINT = 0xfffefdfc;

    @TempDir
    Path temporary;

    @Test
    void resourceReadFailuresRemainContainedToTheCurrentAttempt() throws Exception {
        ActiveResourceLoader.Result ioFailure =
                StoneStatuesResourceExtension.loadActiveResources(() -> {
                    throw new IOException("unreadable active root");
                });
        ActiveResourceLoader.Result runtimeFailure =
                StoneStatuesResourceExtension.loadActiveResources(() -> {
                    throw new IllegalArgumentException("invalid active root");
                });

        assertFalse(ioFailure.valid());
        assertEquals("active-resource-read-failed", ioFailure.reason());
        assertFalse(runtimeFailure.valid());
        assertEquals("active-resource-read-failed", runtimeFailure.reason());
    }

    @Test
    void resourceReadInterruptionStillPropagates() {
        assertThrows(InterruptedException.class, () ->
                StoneStatuesResourceExtension.loadActiveResources(() -> {
                    throw new InterruptedException("cancelled");
                }));
    }

    @Test
    void stagingPublishesKeysAndBakeActivatesTheProfile() throws Exception {
        ResourcePack pack = pack();
        StoneStatuesRuntime runtime = isolatedRuntime();
        StoneStatuesResourceExtension extension = new StoneStatuesResourceExtension(
                pack, runtime, () -> true
        );
        PendingFixture fixture = fixture(extension, 0xff112233);

        assertTrue(extension.stageForBake(fixture.profile(), fixture.generated()));
        assertEquals(StoneStatuesRuntime.State.INACTIVE, runtime.snapshot().state());
        assertEquals(Set.of(fixture.key()), extension.collectUsedTextureKeys());
        assertSame(fixture.texture(), pack.getTextures().get(fixture.key()));

        extension.bake();
        assertEquals(StoneStatuesRuntime.State.ACTIVE, runtime.snapshot().state());
        assertSame(fixture.profile(), runtime.snapshot().profile());
        extension.bake();
        assertEquals(StoneStatuesRuntime.State.ACTIVE, runtime.snapshot().state());
        assertSame(fixture.texture(), pack.getTextures().get(fixture.key()));
    }

    @Test
    void malformedGeneratedClosureNeverStagesOrPublishes() throws Exception {
        ResourcePack missingPack = pack();
        StoneStatuesRuntime missingRuntime = isolatedRuntime();
        StoneStatuesResourceExtension missing = new StoneStatuesResourceExtension(
                missingPack, missingRuntime, () -> true
        );
        PendingFixture missingFixture = fixture(missing, 0xff223344);

        assertFalse(missing.stageForBake(missingFixture.profile(), Map.of()));
        assertEquals(StoneStatuesRuntime.State.FAILED, missingRuntime.snapshot().state());
        assertFalse(missingPack.getTextures().containsKey(missingFixture.key()));

        ResourcePack extraPack = pack();
        StoneStatuesRuntime extraRuntime = isolatedRuntime();
        StoneStatuesResourceExtension extra = new StoneStatuesResourceExtension(
                extraPack, extraRuntime, () -> true
        );
        PendingFixture extraFixture = fixture(extra, 0xff334466);
        Key extraKey = Key.parse("bluemap_cobblemonstonestatues:generated/stone/extra");
        Texture extraTexture = texture(extraKey, 0xff556677);

        assertFalse(extra.stageForBake(extraFixture.profile(), Map.of(
                extraFixture.key(), extraFixture.texture(), extraKey, extraTexture
        )));
        assertEquals(StoneStatuesRuntime.State.FAILED, extraRuntime.snapshot().state());
        assertFalse(extraPack.getTextures().containsKey(extraFixture.key()));
        assertFalse(extraPack.getTextures().containsKey(extraKey));
    }

    @Test
    void partialPublicationCollisionRollsBackOnlyInsertedIdentities() throws Exception {
        ResourcePack pack = pack();
        StoneStatuesRuntime runtime = isolatedRuntime();
        StoneStatuesResourceExtension extension = new StoneStatuesResourceExtension(
                pack, runtime, () -> true
        );
        ProfilePreflight.Result preflight = preflight(temporary);
        Key firstKey = Key.parse(
                "bluemap_cobblemonstonestatues:generated/stone/partial-first"
        );
        Key collisionKey = Key.parse(
                "bluemap_cobblemonstonestatues:generated/gold/partial-collision"
        );
        Texture first = texture(firstKey, 0xff667788);
        Texture expectedCollision = texture(collisionKey, 0xff778899);
        Texture occupant = texture(collisionKey, 0xff8899aa);
        pack.getTextures().put(collisionKey, occupant);
        CompiledProfile profile = new CompiledProfile(
                extension.attempt(), preflight, Map.of(
                        MaterialTexturePlan.cacheKey(
                                Material.STONE, TEXTURE_ID, TINT
                        ), firstKey,
                        MaterialTexturePlan.cacheKey(
                                Material.GOLD_BLOCK, TEXTURE_ID, TINT
                        ), collisionKey
                )
        );
        Map<Key, Texture> generated = new LinkedHashMap<>();
        generated.put(firstKey, first);
        generated.put(collisionKey, expectedCollision);

        assertFalse(extension.stageForBake(profile, generated));
        assertEquals(StoneStatuesRuntime.State.FAILED, runtime.snapshot().state());
        assertFalse(pack.getTextures().containsKey(firstKey));
        assertSame(occupant, pack.getTextures().get(collisionKey));
        assertTrue(extension.collectUsedTextureKeys().isEmpty());
    }

    @Test
    void bakeFailuresRollbackOnlyTheOwnedTextureIdentity() throws Exception {
        ResourcePack retentionPack = pack();
        StoneStatuesRuntime retentionRuntime = isolatedRuntime();
        StoneStatuesResourceExtension retention = new StoneStatuesResourceExtension(
                retentionPack, retentionRuntime, () -> true
        );
        PendingFixture retentionFixture = fixture(retention, 0xff334455);
        assertTrue(retention.stageForBake(
                retentionFixture.profile(), retentionFixture.generated()
        ));
        Texture replacement = texture(retentionFixture.key(), 0xff778899);
        retentionPack.getTextures().put(retentionFixture.key(), replacement);

        retention.bake();
        assertEquals(StoneStatuesRuntime.State.FAILED,
                retentionRuntime.snapshot().state());
        assertSame(replacement, retentionPack.getTextures().get(retentionFixture.key()));

        ResourcePack probePack = pack();
        StoneStatuesRuntime probeRuntime = isolatedRuntime();
        StoneStatuesResourceExtension probeFailure = new StoneStatuesResourceExtension(
                probePack, probeRuntime, () -> false
        );
        PendingFixture probeFixture = fixture(probeFailure, 0xff445566);
        assertTrue(probeFailure.stageForBake(probeFixture.profile(), probeFixture.generated()));
        probeFailure.bake();
        assertEquals(StoneStatuesRuntime.State.FAILED, probeRuntime.snapshot().state());
        assertFalse(probePack.getTextures().containsKey(probeFixture.key()));
    }

    @Test
    void staleAttemptsNeverPublishOrDisableTheirReplacement() throws Exception {
        ResourcePack pack = pack();
        StoneStatuesRuntime runtime = isolatedRuntime();
        StoneStatuesResourceExtension extension = new StoneStatuesResourceExtension(
                pack, runtime, () -> true
        );
        PendingFixture neverPublished = fixture(extension, 0xff102030);
        StoneStatuesRuntime.Attempt replacement = runtime.beginAttempt(new Object());

        assertFalse(extension.stageForBake(
                neverPublished.profile(), neverPublished.generated()
        ));
        assertFalse(pack.getTextures().containsKey(neverPublished.key()));
        assertTrue(runtime.isCurrent(replacement));
        assertEquals(StoneStatuesRuntime.State.INACTIVE, runtime.snapshot().state());

        ResourcePack secondPack = pack();
        StoneStatuesRuntime secondRuntime = isolatedRuntime();
        StoneStatuesResourceExtension staged = new StoneStatuesResourceExtension(
                secondPack, secondRuntime, () -> true
        );
        PendingFixture stagedFixture = fixture(staged, 0xff203040);
        assertTrue(staged.stageForBake(stagedFixture.profile(), stagedFixture.generated()));
        StoneStatuesRuntime.Attempt current = secondRuntime.beginAttempt(new Object());
        staged.bake();
        assertFalse(secondPack.getTextures().containsKey(stagedFixture.key()));
        assertTrue(secondRuntime.isCurrent(current));
        assertEquals(StoneStatuesRuntime.State.INACTIVE,
                secondRuntime.snapshot().state());
    }

    @Test
    void repeatedLoadMakesRuntimeInactiveBeforeRemovingActiveTextures() throws Exception {
        ResourcePack pack = pack();
        StoneStatuesRuntime runtime = isolatedRuntime();
        StoneStatuesResourceExtension extension = new StoneStatuesResourceExtension(
                pack, runtime, () -> true
        );
        PendingFixture fixture = fixture(extension, 0xff506070);
        assertTrue(extension.stageForBake(fixture.profile(), fixture.generated()));
        extension.bake();
        assertEquals(StoneStatuesRuntime.State.ACTIVE, runtime.snapshot().state());

        extension.loadResources(List.of());

        assertEquals(StoneStatuesRuntime.State.INACTIVE, runtime.snapshot().state());
        assertEquals("exact-artifact-pair-missing", runtime.snapshot().detail());
        assertFalse(pack.getTextures().containsKey(fixture.key()));
    }

    private PendingFixture fixture(
            StoneStatuesResourceExtension extension, int color
    ) throws Exception {
        Key key = Key.parse("bluemap_cobblemonstonestatues:generated/stone/test");
        Texture texture = texture(key, color);
        ProfilePreflight.Result preflight = preflight(temporary);
        CompiledProfile profile = new CompiledProfile(
                extension.attempt(), preflight,
                Map.of(MaterialTexturePlan.cacheKey(Material.STONE, TEXTURE_ID, TINT), key)
        );
        return new PendingFixture(profile, key, texture, Map.of(key, texture));
    }

    static ProfilePreflight.Result preflight(Path directory) throws Exception {
        PoseState state = state();
        byte[] rawState = PoseStateCodec.encode(state);
        String stateSha = PoseStateCodec.sha256(rawState);
        PoseStateIdentity stateIdentity = PoseStateIdentity.from(
                stateSha, rawState.length, "/root", 1, TINT, rawState
        );
        QuadMultisetDigest.Vec3 zero = new QuadMultisetDigest.Vec3(0F, 0F, 0F);
        QuadMultisetDigest.Result verification = new QuadMultisetDigest.Result(
                "4".repeat(64), 1, 1, 0, TINT,
                new QuadMultisetDigest.Bounds(zero, zero)
        );
        PoseEntry fallback = new PoseEntry(
                "cobblemon:test", "Normal", "", PoseCatalog.FALLBACK_POSE, -1,
                null, 1F, ResolutionKind.DIRECT, ResolutionKind.DIRECT,
                MODEL_ID, "cobblemon:test", TEXTURE_ID, stateSha, verification
        );
        ModelIdentity model = new ModelIdentity(
                MODEL_ID, "cobblemon:bedrock/pokemon/models/test.geo.json",
                "assets/cobblemon/bedrock/pokemon/models/test.geo.json",
                "mod/cobblemon", 2, "1".repeat(64), "1.12.0", "geometry.test",
                1, 1, "2".repeat(64), 2, 1, 1, 0
        );
        TextureIdentity textureIdentity = new TextureIdentity(
                TEXTURE_ID, "assets/cobblemon/textures/pokemon/test.png",
                "mod/cobblemon", 1, "3".repeat(64), 1, 1
        );
        PoseCatalog catalog = new PoseCatalog(
                "5".repeat(64), "test-format-2", "6".repeat(64), "7".repeat(64),
                Map.of(), Map.of(fallback.choiceKey(), fallback),
                Map.of(MODEL_ID, model), Map.of(stateSha, stateIdentity),
                Map.of(TEXTURE_ID, textureIdentity), new Attestation(
                        1, 1, 1, 1, 1, 1,
                        "8".repeat(64), "9".repeat(64), "a".repeat(64),
                        "b".repeat(64), "c".repeat(64), "d".repeat(64),
                        1, 1, 1, 1,
                        List.of(new CodeSourceIdentity(
                                "cobblemon", "cobblemon.jar", 1, "e".repeat(64)
                        )),
                        List.of(new ResourcePackIdentity(
                                0, "mod/cobblemon", "f".repeat(64)
                        ))
                )
        );
        BakedGeometryTopology topology = topology();
        ProfilePreflight.VerifiedModel verified =
                new ProfilePreflight.VerifiedModel(model, topology);
        Path blobPath = directory.resolve("fixture-resource.bin");
        Files.write(blobPath, new byte[]{1});
        ResourceBlob blob = ResourceBlob.read(blobPath, 1);
        return new ProfilePreflight.Result(
                catalog, Map.of(MODEL_ID, verified), Map.of(TEXTURE_ID, blob),
                Map.of(ProfilePreflight.ReplayKey.from(fallback), verification)
        );
    }

    private static PoseState state() {
        return new PoseState("/root", TINT, List.of(new PoseState.Node(
                "/root", -1, 0, new PoseState.RuntimeFrame(
                        new PoseState.PositionMatrix(
                                1F, 0F, 0F, 0F,
                                0F, 1F, 0F, 0F,
                                0F, 0F, 1F, 0F,
                                0F, 0F, 0F, 1F
                        ),
                        new PoseState.CardinalNormals(
                                0F, -1F, 0F, 0F, 1F, 0F,
                                -1F, 0F, 0F, 0F, 0F, -1F,
                                1F, 0F, 0F, 0F, 0F, 1F
                        ),
                        new PoseState.MirrorZeroXNormals(
                                -0.0F, -1F, 0F, -0.0F, 1F, 0F,
                                -0.0F, 0F, -1F, -0.0F, 0F, 1F
                        )
                ), true, false
        )));
    }

    private static BakedGeometryTopology topology() {
        BakedGeometryTopology.Transform identity = new BakedGeometryTopology.Transform(
                new BedrockGeometry.Vec3(0F, 0F, 0F),
                new BedrockGeometry.Vec3(0F, 0F, 0F),
                new BedrockGeometry.Vec3(1F, 1F, 1F)
        );
        BakedGeometryTopology.Box box = new BakedGeometryTopology.Box(
                new BedrockGeometry.Vec3(0F, 0F, 0F),
                new BedrockGeometry.Vec3(1F, 1F, 1F), 0, 0, 0F, false
        );
        return new BakedGeometryTopology(
                "1.12.0", "geometry.test", 1, 1, 1, 1, 0,
                List.of(
                        new BakedGeometryTopology.Node(
                                "", "/", null, identity, List.of()
                        ),
                        new BakedGeometryTopology.Node(
                                "root", "/root", "/", identity, List.of(box)
                        )
                )
        );
    }

    static ResourcePack pack() {
        return new ResourcePack(new PackVersion(34, 0));
    }

    static StoneStatuesRuntime isolatedRuntime() {
        try {
            var constructor = StoneStatuesRuntime.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("cannot construct isolated runtime", exception);
        }
    }

    private static Texture texture(Key key, int color) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        return Texture.from(key, image);
    }

    private record PendingFixture(
            CompiledProfile profile,
            Key key,
            Texture texture,
            Map<Key, Texture> generated
    ) {
    }
}
