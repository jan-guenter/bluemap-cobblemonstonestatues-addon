/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseStateCodec;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Bounds;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Vec3;
import io.github.janguenter.stoneposeexporter.DeterministicBundleWriter.FileOperations;
import io.github.janguenter.stoneposeexporter.ExportRecords.Catalog;
import io.github.janguenter.stoneposeexporter.ExportRecords.CodeSource;
import io.github.janguenter.stoneposeexporter.ExportRecords.ModelResource;
import io.github.janguenter.stoneposeexporter.ExportRecords.Pose;
import io.github.janguenter.stoneposeexporter.ExportRecords.PoseStateBlob;
import io.github.janguenter.stoneposeexporter.ExportRecords.ResourcePack;
import io.github.janguenter.stoneposeexporter.ExportRecords.Texture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicBundleWriterTest {

    private static final String MODEL_ID = "cobblemon:test.geo";
    private static final String TEXTURE_ID = "cobblemon:textures/pokemon/test.png";
    private static final String PACK_ID = "mod/cobblemon";

    @TempDir
    Path temporary;

    @Test
    void unsupportedDirectoryFsyncCannotInvalidatePublishedAuthoritativeZip()
            throws IOException {
        DeterministicBundleWriter.Result result = DeterministicBundleWriter.write(
                temporary, catalog(), new TestOperations(false, false, true)
        );

        assertTrue(Files.isRegularFile(result.path()));
        assertEquals(Hashing.sha256(result.path()), result.sha256());
        assertEquals(Files.size(result.path()), result.size());
        assertFalse(Files.exists(result.path().resolveSibling(
                DeterministicBundleWriter.OUTPUT_NAME + ".sha256"
        )));
        assertNoTemporaries(result.path().getParent());
    }

    @Test
    void failedAtomicMovePreservesPriorZipAndCleansUnpublishedTemporary()
            throws IOException {
        Path directory = temporary.resolve("exports/atmons-stone-statues");
        Files.createDirectories(directory);
        Path target = directory.resolve(DeterministicBundleWriter.OUTPUT_NAME);
        byte[] previous = "previous-good-export".getBytes(StandardCharsets.US_ASCII);
        Files.write(target, previous);

        assertThrows(IOException.class, () -> DeterministicBundleWriter.write(
                temporary, catalog(), new TestOperations(false, true, false)
        ));

        assertArrayEquals(previous, Files.readAllBytes(target));
        assertNoTemporaries(directory);
    }

    @Test
    void prepublicationFsyncFailureCleansTemporaryAndPreservesPriorZip()
            throws IOException {
        Path directory = temporary.resolve("exports/atmons-stone-statues");
        Files.createDirectories(directory);
        Path target = directory.resolve(DeterministicBundleWriter.OUTPUT_NAME);
        byte[] previous = "previous-good-export".getBytes(StandardCharsets.US_ASCII);
        Files.write(target, previous);

        assertThrows(IOException.class, () -> DeterministicBundleWriter.write(
                temporary, catalog(), new TestOperations(true, false, false)
        ));

        assertArrayEquals(previous, Files.readAllBytes(target));
        assertNoTemporaries(directory);
    }

    @Test
    void finalGenerationGuardFailurePreservesPriorZipAndCleansTemporary()
            throws IOException {
        Path directory = temporary.resolve("exports/atmons-stone-statues");
        Files.createDirectories(directory);
        Path target = directory.resolve(DeterministicBundleWriter.OUTPUT_NAME);
        byte[] previous = "previous-good-export".getBytes(StandardCharsets.US_ASCII);
        Files.write(target, previous);

        assertThrows(IllegalStateException.class, () -> DeterministicBundleWriter.write(
                temporary, catalog(), new TestOperations(false, false, false),
                () -> {
                    throw new IllegalStateException("injected generation drift");
                }
        ));

        assertArrayEquals(previous, Files.readAllBytes(target));
        assertNoTemporaries(directory);
    }

    @Test
    void canonicalFormatTwoZipIsTimezoneIndependentAndContainsNoMeshBlobs()
            throws IOException {
        TimeZone previous = TimeZone.getDefault();
        DeterministicBundleWriter.Result first;
        DeterministicBundleWriter.Result second;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            first = DeterministicBundleWriter.write(
                    temporary.resolve("first"), catalog(),
                    new TestOperations(false, false, false)
            );
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            second = DeterministicBundleWriter.write(
                    temporary.resolve("second"), catalog(),
                    new TestOperations(false, false, false)
            );
        } finally {
            TimeZone.setDefault(previous);
        }

        assertEquals(first.sha256(), second.sha256());
        assertArrayEquals(Files.readAllBytes(first.path()), Files.readAllBytes(second.path()));
        PoseStateBlob state = catalog().poseStates().values().iterator().next();
        try (ZipFile zip = new ZipFile(first.path().toFile())) {
            List<String> names = zip.stream().map(java.util.zip.ZipEntry::getName).toList();
            assertEquals(List.of(
                    "manifest.json", "pose-states/" + state.sha256() + ".bin"
            ), names);
            zip.stream().forEach(entry -> {
                assertNull(entry.getExtra());
                assertNull(entry.getComment());
            });
            assertArrayEquals(state.raw(), zip.getInputStream(zip.getEntry(
                    "pose-states/" + state.sha256() + ".bin"
            )).readAllBytes());
            String manifest = new String(zip.getInputStream(
                    zip.getEntry("manifest.json")
            ).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(manifest.contains("\"format_version\": 2"));
            assertTrue(manifest.contains("\"pose_state_format_version\": 4"));
            assertTrue(manifest.contains("\"pose_states\""));
            assertFalse(manifest.contains("meshes/"));
        }
    }

    @Test
    void codeSourceMetadataSupportsExactPackBasenamesButRejectsPaths() {
        for (String name : List.of(
                "[1.21.1] SecurityCraft v1.10.2.1.jar",
                "L_Ender's Cataclysm 1.21.1-3.32.jar",
                "Super Factory Manager (SFM)-MC1.21.1-4.34.0.jar"
        )) {
            new CodeSource("example", name, 1L, "a".repeat(64));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new CodeSource("example", "../escape.jar", 1L, "a".repeat(64)));
    }

    @Test
    void resourcePackIdsAcceptExactNeoForgeFormsAndRejectTraversalOrControls() {
        for (String packId : List.of(
                "mod/cobblemon",
                "mod/dyenamicsandfriends:compat_packs/botanypots/",
                "moonlight:merged_pack",
                "KubeJS Virtual Resource Pack [Before Mods, assets]"
        )) {
            new ResourcePack(0, packId, "a".repeat(64));
        }
        for (String packId : List.of(
                "mod/../escape", "./relative", "mod/./pack", "bad\\pack",
                "bad\npack", "bad\u200bpack"
        )) {
            assertThrows(IllegalArgumentException.class, () ->
                    new ResourcePack(0, packId, "a".repeat(64)));
        }
    }

    @Test
    void manifestAllocationEstimateRejectsLargeMetadataBeforeGsonTreeBuild() {
        Catalog ordinary = catalog();
        String longForm = "f".repeat(4_096);
        List<Pose> profiles = new ArrayList<>();
        List<Pose> fallbacks = new ArrayList<>();
        Pose source = ordinary.profiles().getFirst();
        for (int index = 0; index < 2_000; index++) {
            String species = "cobblemon:test" + index;
            profiles.add(copyPose(source, species, longForm, "portrait", 0));
            fallbacks.add(copyPose(
                    source, species, longForm, ExportRecords.FALLBACK_POSE, -1
            ));
        }
        assertThrows(IllegalArgumentException.class, () -> new Catalog(
                profiles, fallbacks, ordinary.models(), ordinary.poseStates(),
                ordinary.textures(), ordinary.codeSources(), ordinary.resourcePacks(),
                ordinary.resourceGeneration(), ordinary.modelBindingGeneration(),
                ordinary.exporter()
        ));
        assertThrows(IllegalArgumentException.class, () -> copyPose(
                source, source.species(), "f".repeat(4_097), source.requestedPose(), 0
        ));
    }

    @Test
    void resolutionChannelsAndNullableEffectivePoseAreExplicit() {
        Pose source = catalog().profiles().getFirst();
        Pose mixed = new Pose(
                source.species(), source.form(), source.aspects(), source.requestedPose(),
                source.poseIndex(), null, source.baseScale(), "SUBSTITUTE", "DIRECT",
                "cobblemon:substitute.geo", "cobblemon:substitute",
                source.textureResourceId(), source.poseStateSha256(),
                source.verificationQuadMultisetSha256(), source.sourceQuadCount(),
                source.retainedQuadCount(),
                source.droppedDegenerateQuadCount(), source.bounds(), source.tintArgb()
        );
        assertEquals("SUBSTITUTE", mixed.geometryResolution());
        assertEquals("DIRECT", mixed.textureResolution());
        assertNull(mixed.effectivePose());
        assertThrows(IllegalArgumentException.class, () -> new Pose(
                source.species(), source.form(), source.aspects(), source.requestedPose(),
                source.poseIndex(), source.effectivePose(), source.baseScale(),
                "FALLBACK", "DIRECT", source.modelId(), source.poserId(),
                source.textureResourceId(), source.poseStateSha256(),
                source.verificationQuadMultisetSha256(), source.sourceQuadCount(),
                source.retainedQuadCount(),
                source.droppedDegenerateQuadCount(), source.bounds(), source.tintArgb()
        ));
    }

    @Test
    void fallbacksAreTheAuthoritativeCompleteChoiceRoster() {
        Catalog ordinary = catalog();
        Pose fallbackOnly = copyPose(
                ordinary.fallbacks().getFirst(), "cobblemon:fallback_only", "Normal",
                ExportRecords.FALLBACK_POSE, -1
        );
        Catalog extended = new Catalog(
                ordinary.profiles(),
                List.of(ordinary.fallbacks().getFirst(), fallbackOnly),
                ordinary.models(), ordinary.poseStates(), ordinary.textures(),
                ordinary.codeSources(), ordinary.resourcePacks(),
                ordinary.resourceGeneration(), ordinary.modelBindingGeneration(),
                ordinary.exporter()
        );

        var attestation = extended.manifest().getAsJsonObject("attestation");
        assertEquals(2, attestation.get("species_count").getAsInt());
        assertEquals(2, attestation.get("choice_count").getAsInt());
        assertEquals(3, attestation.get("pose_count").getAsInt());
    }

    private static Pose copyPose(
            Pose source, String species, String form, String requestedPose, int poseIndex
    ) {
        return new Pose(
                species, form, source.aspects(), requestedPose, poseIndex,
                source.effectivePose(), source.baseScale(), source.geometryResolution(),
                source.textureResolution(), source.modelId(), source.poserId(),
                source.textureResourceId(), source.poseStateSha256(),
                source.verificationQuadMultisetSha256(), source.sourceQuadCount(),
                source.retainedQuadCount(),
                source.droppedDegenerateQuadCount(), source.bounds(), source.tintArgb()
        );
    }

    private static void assertNoTemporaries(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            assertEquals(0L, entries.filter(path -> path.getFileName().toString()
                    .endsWith(".tmp")).count());
        }
    }

    private static Catalog catalog() {
        PoseState state = new PoseState("/root", 0xffffffff, List.of(new PoseState.Node(
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
                )), true, false
        )));
        byte[] stateRaw = PoseStateCodec.encode(state);
        String stateSha = PoseStateCodec.sha256(stateRaw);
        PoseStateBlob stateBlob = PoseStateBlob.from(stateSha, stateRaw);
        Bounds bounds = new Bounds(
                new Vec3(0F, 0F, 0F), new Vec3(1F, 1F, 1F)
        );
        ModelResource model = new ModelResource(
                MODEL_ID, "cobblemon:bedrock/pokemon/models/test.geo.json", PACK_ID,
                128, "1".repeat(64), "1.12.0", "geometry.test", 64, 64,
                "2".repeat(64), 1, 1, 1, 0
        );
        Texture texture = new Texture(
                TEXTURE_ID, PACK_ID, 4, "3".repeat(64), 1, 1
        );
        Pose profile = pose("portrait", 0, "portrait", stateSha, bounds);
        Pose fallback = pose(
                ExportRecords.FALLBACK_POSE, -1, "portrait", stateSha, bounds
        );
        CodeSource stone = new CodeSource(
                "cobblemonstonestatues", "stone.jar", 170_977L, "4".repeat(64)
        );
        CodeSource cobblemon = new CodeSource(
                "cobblemon", "cobblemon.jar", 128_748_941L, "5".repeat(64)
        );
        CodeSource exporter = new CodeSource(
                "atmons_stone_pose_exporter", "exporter.jar", 1L, "6".repeat(64)
        );
        return new Catalog(
                List.of(profile), List.of(fallback), Map.of(MODEL_ID, model),
                Map.of(stateSha, stateBlob), Map.of(TEXTURE_ID, texture),
                List.of(cobblemon, stone, exporter),
                List.of(new ResourcePack(0, PACK_ID, "7".repeat(64))),
                4L, 2L, exporter
        );
    }

    private static Pose pose(
            String requestedPose,
            int poseIndex,
            String effectivePose,
            String stateSha,
            Bounds bounds
    ) {
        return new Pose(
                "cobblemon:test", "Normal", "", requestedPose, poseIndex,
                effectivePose, 1F, "DIRECT", "DIRECT", MODEL_ID,
                "cobblemon:test", TEXTURE_ID, stateSha, "8".repeat(64),
                1, 1, 0, bounds, 0xffffffff
        );
    }

    private record TestOperations(
            boolean failFileFsync, boolean failMove, boolean unsupportedDirectoryFsync
    ) implements FileOperations {
        @Override
        public void forceFile(Path path) throws IOException {
            if (failFileFsync) {
                throw new IOException("injected file-fsync failure");
            }
        }

        @Override
        public void atomicReplace(Path source, Path target) throws IOException {
            if (failMove) {
                throw new IOException("injected atomic-move failure");
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void forceDirectory(Path directory) {
            if (unsupportedDirectoryFsync) {
                throw new UnsupportedOperationException("injected Windows behavior");
            }
        }
    }
}
