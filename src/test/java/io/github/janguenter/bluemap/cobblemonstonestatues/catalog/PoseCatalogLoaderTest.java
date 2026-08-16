/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.catalog;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.CodeSourceIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ModelIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseEntry;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseStateIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ResolutionKind;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.TextureIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseStateCodec;
import io.github.janguenter.bluemap.cobblemonstonestatues.profile.ExactProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoseCatalogLoaderTest {

    private static final LocalDateTime ZIP_TIME = LocalDateTime.of(1980, 2, 1, 0, 0);

    @TempDir
    Path temporary;

    @Test
    void loadsStrictFormatTwoWithNullableFallbackOnlyChoice() throws IOException {
        Path bundle = writeBundle(root -> { }, StateEntries.EXACT, Map.of());
        PoseCatalog catalog = PoseCatalogLoader.loadVerified(
                bundle, PoseCatalogLoader.digest(bundle), 2
        );

        assertEquals(1, catalog.poses().size());
        assertEquals(2, catalog.fallbacks().size());
        assertEquals(2, catalog.attestation().speciesCount());
        assertEquals(2, catalog.attestation().choiceCount());
        assertEquals(2, catalog.poseStates().size());
        assertNotNull(catalog.resolve(selection("cobblemon:alpha", "standing")));
        PoseEntry fallbackOnly = catalog.resolve(selection("cobblemon:beta", "anything"));
        assertNotNull(fallbackOnly);
        assertEquals(PoseCatalog.FALLBACK_POSE, fallbackOnly.pose());
        assertNull(fallbackOnly.effectivePose());

        PoseStateIdentity retained = catalog.poseStates().values().iterator().next();
        byte[] first = retained.raw();
        first[0] ^= 0x55;
        assertFalse(java.util.Arrays.equals(first, retained.raw()));

        PoseState source = sampleState("/constructor-clone", 0xffffffff);
        byte[] raw = PoseStateCodec.encode(source);
        PoseStateIdentity copied = PoseStateIdentity.from(
                PoseStateCodec.sha256(raw), raw.length, source.selectedRootPath(),
                source.nodes().size(), source.tintArgb(), raw
        );
        raw[0] ^= 0x55;
        assertFalse(java.util.Arrays.equals(raw, copied.raw()));
    }

    @Test
    void publicEntryPointReachesTheProductionVerifier() throws IOException {
        Path bundle = writeBundle(root -> { }, StateEntries.EXACT, Map.of());
        String sha256 = PoseCatalogLoader.digest(bundle);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                PoseCatalogLoader.load(bundle, sha256));
        assertEquals("format-2 completeness attestation mismatch", failure.getMessage());
    }

    @Test
    void rejectsAllSupersededExporterIdentities() throws IOException {
        for (ExporterIdentity identity : List.of(
                new ExporterIdentity(
                        93_116L,
                        "ef3ed365a4480c2b9e8706459181c1bce99022f24147702388c7ee477d02b90c"
                ),
                new ExporterIdentity(
                        278_192L,
                        "5e742b85dfb18c0cc3c09348b13bfae3855c0b180957ee751e6b75128bd228ef"
                ),
                new ExporterIdentity(
                        278_438L,
                        "49760c9bf86c202a0b4499b0e4fbf84c21818d573d7b6f7aa88247c6d8cb3fe7"
                ),
                new ExporterIdentity(
                        278_486L,
                        "9b41f38e105ce94a8eb56179e9a5c939f953c84d171df1bc2001bdc0650f9728"
                ),
                new ExporterIdentity(
                        281_461L,
                        "6c3590d3f3bb23689b95d88afcd677c3c50cbe876f0de90cdf7ee63d11f4fe38"
                ),
                new ExporterIdentity(
                        285_871L,
                        "9dbac38dda2e5ddadb5d75cad2b612a5112d4598478964bcc59d827c0ad40e08"
                ),
                new ExporterIdentity(
                        285_905L,
                        "e775fa414d218e89a8d21ab89ff47417cca6627452459629520d90f9e1b197ae"
                )
        )) {
            Path bundle = writeBundle(root -> {
                root.addProperty("exporter_size", identity.size());
                root.addProperty("exporter_sha256", identity.sha256());
                JsonObject exporter = root.getAsJsonObject("attestation")
                        .getAsJsonArray("code_sources").get(2).getAsJsonObject();
                exporter.addProperty("size", identity.size());
                exporter.addProperty("sha256", identity.sha256());
            }, StateEntries.EXACT, Map.of());

            assertThrows(IllegalArgumentException.class, () ->
                    PoseCatalogLoader.loadVerified(
                            bundle, PoseCatalogLoader.digest(bundle), 2
                    ));
        }
    }

    @Test
    void rejectsFormatOneAndAnyMeshPayload() throws IOException {
        Path formatOne = writeBundle(
                root -> root.addProperty("format_version", 1),
                StateEntries.EXACT, Map.of()
        );
        assertThrows(IllegalArgumentException.class, () -> PoseCatalogLoader.loadVerified(
                formatOne, PoseCatalogLoader.digest(formatOne), 2
        ));

        Path poseStateFormatThree = writeBundle(
                root -> root.addProperty("pose_state_format_version", 3),
                StateEntries.EXACT, Map.of()
        );
        assertThrows(IllegalArgumentException.class, () -> PoseCatalogLoader.loadVerified(
                poseStateFormatThree, PoseCatalogLoader.digest(poseStateFormatThree), 2
        ));

        Path withMesh = writeBundle(root -> { }, StateEntries.EXACT, Map.of(
                "meshes/" + "a".repeat(64) + ".bin", new byte[]{1}
        ));
        assertThrows(IllegalArgumentException.class, () -> PoseCatalogLoader.loadVerified(
                withMesh, PoseCatalogLoader.digest(withMesh), 2
        ));
    }

    @Test
    void rejectsMissingOrCorruptedOrOrphanPoseStateEntries() throws IOException {
        for (StateEntries mode : List.of(StateEntries.MISSING, StateEntries.CORRUPTED)) {
            Path bundle = writeBundle(root -> { }, mode, Map.of());
            assertThrows(IllegalArgumentException.class, () ->
                    PoseCatalogLoader.loadVerified(
                            bundle, PoseCatalogLoader.digest(bundle), 2
                    ));
        }
        String orphan = "pose-states/" + "f".repeat(64) + ".bin";
        Path bundle = writeBundle(root -> { }, StateEntries.EXACT, Map.of(
                orphan, PoseStateCodec.encode(sampleState("/orphan", 0xffffffff))
        ));
        assertThrows(IllegalArgumentException.class, () -> PoseCatalogLoader.loadVerified(
                bundle, PoseCatalogLoader.digest(bundle), 2
        ));
    }

    @Test
    void rejectsBadRootsGenerationsOrderingAndPartialLiveCensus() throws IOException {
        List<Consumer<JsonObject>> corruptions = List.of(
                root -> root.getAsJsonObject("attestation")
                        .addProperty("models_root", "0".repeat(64)),
                root -> root.getAsJsonObject("attestation")
                        .addProperty("resource_generation_end", 8),
                root -> {
                    JsonArray fallbacks = root.getAsJsonArray("fallbacks");
                    var first = fallbacks.get(0);
                    fallbacks.set(0, fallbacks.get(1));
                    fallbacks.set(1, first);
                }
        );
        for (Consumer<JsonObject> corruption : corruptions) {
            Path bundle = writeBundle(corruption, StateEntries.EXACT, Map.of());
            assertThrows(IllegalArgumentException.class, () ->
                    PoseCatalogLoader.loadVerified(
                            bundle, PoseCatalogLoader.digest(bundle), 2
                    ));
        }

        Path partial = writeBundle(root -> { }, StateEntries.EXACT, Map.of());
        assertThrows(IllegalArgumentException.class, () -> PoseCatalogLoader.loadVerified(
                partial, PoseCatalogLoader.digest(partial), ExactProfile.LIVE_SPECIES_COUNT
        ));
    }

    @Test
    void rejectsWrongOuterIdentityAndHeaderLedgerFilenameDrift() throws IOException {
        Path bundle = writeBundle(root -> { }, StateEntries.EXACT, Map.of());
        assertThrows(IllegalArgumentException.class, () -> PoseCatalogLoader.loadVerified(
                bundle, "0".repeat(64), 2
        ));

        Path mismatch = writeBundle(root -> root.addProperty(
                "stone_file_name", "[1.21.1] Stone Statue (exact).jar"
        ), StateEntries.EXACT, Map.of());
        assertThrows(IllegalArgumentException.class, () -> PoseCatalogLoader.loadVerified(
                mismatch, PoseCatalogLoader.digest(mismatch), 2
        ));
    }

    @Test
    void acceptsHeaderBasenamesFromTheExactLedgerContract() throws IOException {
        String fileName = "[1.21.1] Stone’s Statue (exact).jar";
        Path bundle = writeBundle(root -> {
            root.addProperty("stone_file_name", fileName);
            root.getAsJsonObject("attestation").getAsJsonArray("code_sources")
                    .get(0).getAsJsonObject().addProperty("file_name", fileName);
        }, StateEntries.EXACT, Map.of());

        assertNotNull(PoseCatalogLoader.loadVerified(
                bundle, PoseCatalogLoader.digest(bundle), 2
        ));
    }

    @Test
    void rejectsNoncanonicalZipOrderMethodAndTime() throws IOException {
        for (ZipVariant variant : ZipVariant.values()) {
            Path bundle = writeNoncanonicalBundle(variant);
            assertThrows(IllegalArgumentException.class, () ->
                    PoseCatalogLoader.loadVerified(
                            bundle, PoseCatalogLoader.digest(bundle), 2
                    ));
        }
    }

    @Test
    void resourcePackLedgerAcceptsExactIdsAndRejectsUnsafeSegments() {
        for (String value : List.of(
                "mod/cobblemon",
                "mod/dyenamicsandfriends:compat_packs/botanypots/",
                "moonlight:merged_pack",
                "KubeJS Virtual Resource Pack [Before Mods, assets]"
        )) {
            assertTrue(PoseCatalog.safePackId(value));
        }
        for (String value : List.of(
                "bad\\pack", "../escape", "root/./file", "root/../file",
                "bad\npack", "bad\u200Dpack"
        )) {
            assertFalse(PoseCatalog.safePackId(value));
        }
    }

    @Test
    void codeSourceLedgerAcceptsExactPackBasenamesButRejectsPathsAndControls() {
        for (String name : List.of(
                "[1.21.1] SecurityCraft v1.10.2.1.jar",
                "L_Ender's Cataclysm 1.21.1-3.32.jar",
                "Super Factory Manager (SFM)-MC1.21.1-4.34.0.jar"
        )) {
            new CodeSourceIdentity("example", name, 1L, "a".repeat(64));
        }
        assertThrows(IllegalArgumentException.class, () -> new CodeSourceIdentity(
                "bad:id", "example.jar", 1L, "a".repeat(64)
        ));
        assertThrows(IllegalArgumentException.class, () -> new CodeSourceIdentity(
                "example", "../escape.jar", 1L, "a".repeat(64)
        ));
    }

    private Path writeBundle(
            Consumer<JsonObject> mutation,
            StateEntries stateEntries,
            Map<String, byte[]> extras
    ) throws IOException {
        Fixture fixture = fixture();
        byte[] manifestRaw = manifestRaw(fixture, mutation);
        Path bundle = temporary.resolve("bundle-" + Files.list(temporary).count() + ".zip");
        try (OutputStream output = Files.newOutputStream(bundle);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            writeStored(zip, "manifest.json", manifestRaw);
            List<Map.Entry<String, byte[]>> states = new ArrayList<>(
                    fixture.stateBytes().entrySet()
            );
            if (stateEntries == StateEntries.MISSING) {
                states.removeLast();
            }
            for (int index = 0; index < states.size(); index++) {
                Map.Entry<String, byte[]> state = states.get(index);
                byte[] raw = state.getValue().clone();
                if (stateEntries == StateEntries.CORRUPTED && index == 0) {
                    raw[0] ^= 0x55;
                }
                writeStored(zip, "pose-states/" + state.getKey() + ".bin", raw);
            }
            for (Map.Entry<String, byte[]> extra : new TreeMap<>(extras).entrySet()) {
                writeStored(zip, extra.getKey(), extra.getValue());
            }
        }
        return bundle;
    }

    private Path writeNoncanonicalBundle(ZipVariant variant) throws IOException {
        Fixture fixture = fixture();
        byte[] manifest = manifestRaw(fixture, root -> { });
        List<Map.Entry<String, byte[]>> states = new ArrayList<>(
                fixture.stateBytes().entrySet()
        );
        Path bundle = temporary.resolve("noncanonical-" + variant + ".zip");
        try (OutputStream output = Files.newOutputStream(bundle);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            if (variant == ZipVariant.ORDER) {
                Map.Entry<String, byte[]> first = states.removeFirst();
                writeStored(zip, "pose-states/" + first.getKey() + ".bin", first.getValue());
            }
            if (variant == ZipVariant.METHOD) {
                writeDeflated(zip, "manifest.json", manifest);
            } else if (variant == ZipVariant.TIME) {
                writeStored(zip, "manifest.json", manifest, ZIP_TIME.plusDays(1));
            } else {
                writeStored(zip, "manifest.json", manifest);
            }
            for (Map.Entry<String, byte[]> state : states) {
                writeStored(zip, "pose-states/" + state.getKey() + ".bin", state.getValue());
            }
        }
        return bundle;
    }

    private static byte[] manifestRaw(
            Fixture fixture, Consumer<JsonObject> mutation
    ) {
        JsonObject manifest = fixture.manifest();
        mutation.accept(manifest);
        return (new GsonBuilder().disableHtmlEscaping().serializeNulls()
                .setPrettyPrinting().create().toJson(manifest) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static Fixture fixture() {
        int tint = 0xffffffff;
        PoseStateIdentity alphaState = stateIdentity(sampleState("/alpha", tint));
        PoseStateIdentity betaState = stateIdentity(sampleState("/beta", tint));
        Map<String, PoseStateIdentity> states = sorted(Map.of(
                alphaState.sha256(), alphaState,
                betaState.sha256(), betaState
        ));
        Map<String, byte[]> stateBytes = new LinkedHashMap<>();
        states.forEach((sha256, state) -> stateBytes.put(sha256, state.raw()));

        ModelIdentity model = new ModelIdentity(
                "cobblemon:test.geo", "cobblemon:bedrock/pokemon/models/test.geo.json",
                "assets/cobblemon/bedrock/pokemon/models/test.geo.json", "mod/cobblemon",
                2, "a".repeat(64), "1.12.0", "geometry.test", 64, 64,
                "b".repeat(64), 3, 2, 1, 0
        );
        TextureIdentity texture = new TextureIdentity(
                "cobblemon:textures/pokemon/test.png",
                "assets/cobblemon/textures/pokemon/test.png", "mod/cobblemon",
                8, "c".repeat(64), 2, 2
        );
        Map<String, ModelIdentity> models = Map.of(model.modelId(), model);
        Map<String, TextureIdentity> textures = Map.of(texture.resourceId(), texture);

        PoseEntry profile = pose(
                "cobblemon:alpha", "standing", 0, "standing",
                alphaState.sha256(), texture.resourceId(), tint, "1".repeat(64)
        );
        PoseEntry alphaFallback = pose(
                "cobblemon:alpha", PoseCatalog.FALLBACK_POSE, -1, "standing",
                alphaState.sha256(), texture.resourceId(), tint, "1".repeat(64)
        );
        PoseEntry betaFallback = pose(
                "cobblemon:beta", PoseCatalog.FALLBACK_POSE, -1, null,
                betaState.sha256(), texture.resourceId(), tint, "2".repeat(64)
        );
        Map<String, PoseEntry> profiles = new LinkedHashMap<>();
        profiles.put(profile.key(), profile);
        Map<String, PoseEntry> fallbacks = new LinkedHashMap<>();
        fallbacks.put(alphaFallback.choiceKey(), alphaFallback);
        fallbacks.put(betaFallback.choiceKey(), betaFallback);
        return new Fixture(
                profiles, fallbacks, models, states, textures, stateBytes
        );
    }

    private static PoseEntry pose(
            String species,
            String requestedPose,
            int poseIndex,
            String effectivePose,
            String stateSha,
            String textureId,
            int tint,
            String verificationSha
    ) {
        return new PoseEntry(
                species, "Normal", "", requestedPose, poseIndex, effectivePose, 1F,
                ResolutionKind.DIRECT, ResolutionKind.DIRECT,
                "cobblemon:test.geo", "cobblemon:test", textureId, stateSha,
                new QuadMultisetDigest.Result(
                        verificationSha, 1, 1, 0, tint,
                        new QuadMultisetDigest.Bounds(
                                new QuadMultisetDigest.Vec3(0F, 0F, 0F),
                                new QuadMultisetDigest.Vec3(1F, 1F, 1F)
                        )
                )
        );
    }

    private static PoseState sampleState(String root, int tint) {
        return new PoseState(root, tint, List.of(
                new PoseState.Node(root, -1, 0, null, true, false)
        ));
    }

    private static PoseStateIdentity stateIdentity(PoseState state) {
        byte[] raw = PoseStateCodec.encode(state);
        return PoseStateIdentity.from(
                PoseStateCodec.sha256(raw), raw.length, state.selectedRootPath(),
                state.nodes().size(), state.tintArgb(), raw
        );
    }

    private static StatueSelection selection(String species, String pose) {
        return new StatueSelection(
                species, "Normal", "", pose,
                StatueSelection.Scale.NORMAL, StatueSelection.Material.STONE, 0, 0L
        );
    }

    private static <T> Map<String, T> sorted(Map<String, T> input) {
        return new LinkedHashMap<>(new TreeMap<>(input));
    }

    private static void writeStored(ZipOutputStream zip, String name, byte[] raw)
            throws IOException {
        writeStored(zip, name, raw, ZIP_TIME);
    }

    private static void writeStored(
            ZipOutputStream zip, String name, byte[] raw, LocalDateTime time
    ) throws IOException {
        CRC32 checksum = new CRC32();
        checksum.update(raw);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(raw.length);
        entry.setCompressedSize(raw.length);
        entry.setCrc(checksum.getValue());
        entry.setTimeLocal(time);
        entry.setExtra(null);
        entry.setComment(null);
        zip.putNextEntry(entry);
        zip.write(raw);
        zip.closeEntry();
    }

    private static void writeDeflated(ZipOutputStream zip, String name, byte[] raw)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.DEFLATED);
        entry.setTimeLocal(ZIP_TIME);
        entry.setExtra(null);
        entry.setComment(null);
        zip.putNextEntry(entry);
        zip.write(raw);
        zip.closeEntry();
    }

    private enum StateEntries {
        EXACT,
        MISSING,
        CORRUPTED
    }

    private enum ZipVariant {
        ORDER,
        METHOD,
        TIME
    }

    private record ExporterIdentity(long size, String sha256) {
    }

    private record Fixture(
            Map<String, PoseEntry> profiles,
            Map<String, PoseEntry> fallbacks,
            Map<String, ModelIdentity> models,
            Map<String, PoseStateIdentity> states,
            Map<String, TextureIdentity> textures,
            Map<String, byte[]> stateBytes
    ) {
        JsonObject manifest() {
            JsonObject root = new JsonObject();
            root.addProperty("format_version", 2);
            root.addProperty("catalog_id", ExactProfile.PROFILE_ID + "-hybrid");
            root.addProperty("minecraft_version", ExactProfile.MINECRAFT_VERSION);
            root.addProperty("neoforge_version", ExactProfile.NEOFORGE_VERSION);
            root.addProperty("java_feature", ExactProfile.JAVA_FEATURE);
            root.addProperty("joml_version", "1.10.5");
            root.addProperty(
                    "pose_state_format_version", PoseStateCodec.VERSION
            );
            root.addProperty("quad_multiset_digest_version", 1);
            root.addProperty("stone_file_name", "cobblemonstonestatues-neoforge-1.1.jar");
            root.addProperty("stone_size", ExactProfile.STONE_SIZE);
            root.addProperty("stone_sha256", ExactProfile.STONE_SHA256);
            root.addProperty(
                    "cobblemon_file_name", "Cobblemon-neoforge-1.7.3+1.21.1.jar"
            );
            root.addProperty("cobblemon_size", ExactProfile.COBBLEMON_SIZE);
            root.addProperty("cobblemon_sha256", ExactProfile.COBBLEMON_SHA256);
            root.addProperty("cobblemon_commit", ExactProfile.COBBLEMON_COMMIT);
            root.addProperty("exporter_version", ExactProfile.EXPORTER_VERSION);
            root.addProperty(
                    "exporter_file_name", "atmons-stone-pose-exporter-0.1.0-alpha.1.jar"
            );
            root.addProperty("exporter_size", ExactProfile.EXPORTER_SIZE);
            root.addProperty("exporter_sha256", ExactProfile.EXPORTER_SHA256);
            root.add("profiles", poses(profiles.values()));
            root.add("fallbacks", poses(fallbacks.values()));
            root.add("models", modelArray(models.values()));
            root.add("pose_states", stateArray(states.values()));
            root.add("textures", textureArray(textures.values()));
            root.add("attestation", attestation());
            return root;
        }

        private JsonObject attestation() {
            JsonObject value = new JsonObject();
            value.addProperty("species_count", 2);
            value.addProperty("choice_count", fallbacks.size());
            value.addProperty("pose_count", profiles.size() + fallbacks.size());
            value.addProperty("model_count", models.size());
            value.addProperty("pose_state_count", states.size());
            value.addProperty("texture_count", textures.size());
            value.addProperty("species_root", CatalogRoots.species(fallbacks));
            value.addProperty("choices_root", CatalogRoots.choices(fallbacks));
            value.addProperty("poses_root", CatalogRoots.poses(profiles, fallbacks));
            value.addProperty("models_root", CatalogRoots.models(models));
            value.addProperty("pose_states_root", CatalogRoots.poseStates(states));
            value.addProperty("textures_root", CatalogRoots.textures(textures));
            value.addProperty("resource_generation_start", 7);
            value.addProperty("resource_generation_end", 7);
            value.addProperty("model_binding_generation_start", 11);
            value.addProperty("model_binding_generation_end", 11);
            value.add("code_sources", codeSources());
            value.add("resource_packs", resourcePacks());
            return value;
        }
    }

    private static JsonArray poses(Iterable<PoseEntry> entries) {
        JsonArray result = new JsonArray();
        for (PoseEntry entry : entries) {
            JsonObject value = new JsonObject();
            value.addProperty("species", entry.speciesId());
            value.addProperty("form", entry.formId());
            value.addProperty("aspects", entry.aspectsCsv());
            value.addProperty("pose", entry.pose());
            value.addProperty("pose_index", entry.poseIndex());
            if (entry.effectivePose() == null) {
                value.add("effective_pose", JsonNull.INSTANCE);
            } else {
                value.addProperty("effective_pose", entry.effectivePose());
            }
            value.addProperty("base_scale", entry.baseScale());
            value.addProperty("geometry_resolution", entry.geometryResolution().name());
            value.addProperty("texture_resolution", entry.textureResolution().name());
            value.addProperty("model_id", entry.modelId());
            value.addProperty("poser_id", entry.poserId());
            value.addProperty("texture_resource_id", entry.textureResourceId());
            value.addProperty("pose_state_sha256", entry.poseStateSha256());
            value.addProperty(
                    "verification_quad_multiset_sha256", entry.verification().sha256()
            );
            value.addProperty("source_quad_count", entry.verification().sourceQuadCount());
            value.addProperty(
                    "retained_quad_count", entry.verification().retainedQuadCount()
            );
            value.addProperty(
                    "dropped_degenerate_quad_count",
                    entry.verification().droppedDegenerateQuadCount()
            );
            value.add("minimum", vector(entry.verification().bounds().minimum()));
            value.add("maximum", vector(entry.verification().bounds().maximum()));
            value.addProperty("tint_argb", entry.tintArgb());
            result.add(value);
        }
        return result;
    }

    private static JsonArray modelArray(Iterable<ModelIdentity> entries) {
        JsonArray result = new JsonArray();
        for (ModelIdentity entry : entries) {
            JsonObject value = new JsonObject();
            value.addProperty("model_id", entry.modelId());
            value.addProperty("resource_id", entry.resourceId());
            value.addProperty("resource_path", entry.resourcePath());
            value.addProperty("winning_pack_id", entry.winningPackId());
            value.addProperty("size", entry.size());
            value.addProperty("sha256", entry.sha256());
            value.addProperty("format_version", entry.formatVersion());
            value.addProperty("geometry_identifier", entry.geometryIdentifier());
            value.addProperty("texture_width", entry.textureWidth());
            value.addProperty("texture_height", entry.textureHeight());
            value.addProperty("topology_sha256", entry.topologySha256());
            value.addProperty("topology_node_count", entry.topologyNodeCount());
            value.addProperty("bone_count", entry.boneCount());
            value.addProperty("cube_count", entry.cubeCount());
            value.addProperty("locator_count", entry.locatorCount());
            result.add(value);
        }
        return result;
    }

    private static JsonArray stateArray(Iterable<PoseStateIdentity> entries) {
        JsonArray result = new JsonArray();
        for (PoseStateIdentity entry : entries) {
            JsonObject value = new JsonObject();
            value.addProperty("sha256", entry.sha256());
            value.addProperty("size", entry.size());
            value.addProperty("selected_root_path", entry.selectedRootPath());
            value.addProperty("node_count", entry.nodeCount());
            value.addProperty("tint_argb", entry.tintArgb());
            result.add(value);
        }
        return result;
    }

    private static JsonArray textureArray(Iterable<TextureIdentity> entries) {
        JsonArray result = new JsonArray();
        for (TextureIdentity entry : entries) {
            JsonObject value = new JsonObject();
            value.addProperty("resource_id", entry.resourceId());
            value.addProperty("resource_path", entry.resourcePath());
            value.addProperty("winning_pack_id", entry.winningPackId());
            value.addProperty("size", entry.size());
            value.addProperty("sha256", entry.sha256());
            value.addProperty("width", entry.width());
            value.addProperty("height", entry.height());
            result.add(value);
        }
        return result;
    }

    private static JsonArray codeSources() {
        JsonArray result = new JsonArray();
        result.add(codeSource(
                "cobblemonstonestatues", "cobblemonstonestatues-neoforge-1.1.jar",
                ExactProfile.STONE_SIZE, ExactProfile.STONE_SHA256
        ));
        result.add(codeSource(
                "cobblemon", "Cobblemon-neoforge-1.7.3+1.21.1.jar",
                ExactProfile.COBBLEMON_SIZE, ExactProfile.COBBLEMON_SHA256
        ));
        result.add(codeSource(
                ExactProfile.EXPORTER_MOD_ID,
                "atmons-stone-pose-exporter-0.1.0-alpha.1.jar",
                ExactProfile.EXPORTER_SIZE, ExactProfile.EXPORTER_SHA256
        ));
        return result;
    }

    private static JsonObject codeSource(
            String modId, String fileName, long size, String sha256
    ) {
        JsonObject value = new JsonObject();
        value.addProperty("mod_id", modId);
        value.addProperty("file_name", fileName);
        value.addProperty("size", size);
        value.addProperty("sha256", sha256);
        return value;
    }

    private static JsonArray resourcePacks() {
        JsonArray result = new JsonArray();
        JsonObject value = new JsonObject();
        value.addProperty("order", 0);
        value.addProperty("pack_id", "mod/cobblemon");
        value.addProperty("fingerprint", "d".repeat(64));
        result.add(value);
        return result;
    }

    private static JsonArray vector(QuadMultisetDigest.Vec3 source) {
        JsonArray value = new JsonArray();
        value.add(source.x());
        value.add(source.y());
        value.add(source.z());
        return value;
    }
}
