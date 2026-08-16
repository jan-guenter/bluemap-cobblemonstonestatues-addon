/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.Attestation;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.CodeSourceIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ModelIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseEntry;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseStateIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ResolutionKind;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ResourcePackIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.TextureIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseStateCodec;
import io.github.janguenter.bluemap.cobblemonstonestatues.profile.ExactProfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Strict format-2 verifier; all external state bytes are retained before return. */
public final class PoseCatalogLoader {

    public static final String PATH_PROPERTY = "bluemapCobblemonStatues.poseBundle";
    public static final String SHA_PROPERTY = "bluemapCobblemonStatues.poseBundleSha256";
    public static final String PATH_ENVIRONMENT = "BLUEMAP_COBBLEMON_STATUES_POSE_BUNDLE";
    public static final String SHA_ENVIRONMENT =
            "BLUEMAP_COBBLEMON_STATUES_POSE_BUNDLE_SHA256";
    private static final String CATALOG_ID = ExactProfile.PROFILE_ID + "-hybrid";
    private static final long MAX_BUNDLE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_CODE_SOURCE_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_TOTAL_ENTRY_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_TOTAL_STATE_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_MANIFEST_BYTES = 64 * 1024 * 1024;
    private static final int MAX_POSES = 50_000;
    private static final int MAX_MODELS = 8_192;
    private static final int MAX_STATES = 50_000;
    private static final int MAX_TEXTURES = 8_192;
    private static final int MAX_CODE_SOURCES = 4_096;
    private static final int MAX_RESOURCE_PACKS = 1_024;
    private static final int LOCAL_FILE_HEADER = 0x04034B50;
    private static final int CENTRAL_DIRECTORY_HEADER = 0x02014B50;
    private static final int UTF8_FLAG = 1 << 11;
    private static final LocalDateTime CANONICAL_ZIP_TIME =
            LocalDateTime.of(1980, 2, 1, 0, 0);
    private static final Comparator<PoseEntry> POSE_ORDER =
            Comparator.comparing(PoseEntry::speciesId)
                    .thenComparing(PoseEntry::formId)
                    .thenComparing(PoseEntry::aspectsCsv)
                    .thenComparingInt(PoseEntry::poseIndex)
                    .thenComparing(PoseEntry::pose);

    private PoseCatalogLoader() {
    }

    public static PoseCatalog loadConfigured() throws IOException {
        requireFormatTwoReady();
        String configuredPath = configured(PATH_PROPERTY, PATH_ENVIRONMENT);
        String configuredSha = configured(SHA_PROPERTY, SHA_ENVIRONMENT);
        if (configuredPath == null || configuredSha == null) {
            throw new IllegalArgumentException("operator-local pose bundle is not configured");
        }
        return loadVerified(Path.of(configuredPath), configuredSha,
                ExactProfile.LIVE_SPECIES_COUNT);
    }

    public static PoseCatalog load(Path bundle, String expectedOuterSha) throws IOException {
        requireFormatTwoReady();
        return loadVerified(bundle, expectedOuterSha, ExactProfile.LIVE_SPECIES_COUNT);
    }

    static PoseCatalog loadVerified(
            Path bundle, String expectedOuterSha, int expectedSpeciesCount
    ) throws IOException {
        if (expectedSpeciesCount < 1) {
            throw new IllegalArgumentException("expected live species count must be positive");
        }
        PoseCatalog.requireSha(expectedOuterSha, "configured bundle sha256");
        if (!bundle.isAbsolute()) {
            throw new IllegalArgumentException("operator-local pose bundle path must be absolute");
        }
        Path real = bundle.toRealPath();
        long outerSize = Files.size(real);
        if (!Files.isRegularFile(real) || outerSize < 1 || outerSize > MAX_BUNDLE_BYTES
                || !expectedOuterSha.equals(digest(real))) {
            throw new IllegalArgumentException("operator-local pose bundle identity mismatch");
        }
        PoseCatalog result;
        try (ZipFile zip = new ZipFile(real.toFile(), StandardCharsets.UTF_8)) {
            List<String> zipNames = validateZipRoster(zip);
            validateLocalHeaders(real, zipNames);
            JsonObject root = readManifest(zip);
            requireExactKeys(root, List.of(
                    "format_version", "catalog_id", "minecraft_version",
                    "neoforge_version", "java_feature", "joml_version",
                    "pose_state_format_version", "quad_multiset_digest_version",
                    "stone_file_name", "stone_size", "stone_sha256",
                    "cobblemon_file_name", "cobblemon_size", "cobblemon_sha256",
                    "cobblemon_commit", "exporter_version", "exporter_file_name",
                    "exporter_size", "exporter_sha256", "profiles", "fallbacks",
                    "models", "pose_states", "textures", "attestation"
            ));
            ExporterIdentity exporter = verifyHeaderIdentity(root);
            Map<String, ModelIdentity> models = parseModels(
                    array(root.get("models"), "models")
            );
            Map<String, PoseStateIdentity> poseStates = parsePoseStates(
                    array(root.get("pose_states"), "pose states"), zip, zipNames
            );
            Map<String, TextureIdentity> textures = parseTextures(
                    array(root.get("textures"), "textures")
            );
            Map<String, PoseEntry> profiles = parsePoses(
                    array(root.get("profiles"), "profiles"), false
            );
            Map<String, PoseEntry> fallbacks = parsePoses(
                    array(root.get("fallbacks"), "fallbacks"), true
            );
            if (Math.addExact(profiles.size(), fallbacks.size()) > MAX_POSES) {
                throw new IllegalArgumentException("combined pose catalog exceeds budget");
            }
            Attestation attestation = verifyAttestation(
                    object(root.get("attestation"), "attestation"),
                    profiles, fallbacks, models, poseStates, textures,
                    exporter, expectedSpeciesCount
            );
            result = new PoseCatalog(
                    expectedOuterSha, string(root.get("catalog_id"), "catalog id"),
                    string(root.get("stone_sha256"), "stone sha"),
                    string(root.get("cobblemon_sha256"), "Cobblemon sha"),
                    profiles, fallbacks, models, poseStates, textures, attestation
            );
        }
        if (Files.size(real) != outerSize || !expectedOuterSha.equals(digest(real))) {
            throw new IllegalArgumentException("pose bundle changed during verification");
        }
        return result;
    }

    private static void requireFormatTwoReady() {
        if (!ExactProfile.FORMAT_2_READY) {
            throw new IllegalStateException("format-2 production loader is not ready");
        }
    }

    private static ExporterIdentity verifyHeaderIdentity(JsonObject root) {
        if (integer(root.get("format_version"), "format version") != 2
                || !CATALOG_ID.equals(string(root.get("catalog_id"), "catalog id"))
                || !ExactProfile.MINECRAFT_VERSION.equals(
                string(root.get("minecraft_version"), "Minecraft version")
        ) || !ExactProfile.NEOFORGE_VERSION.equals(
                string(root.get("neoforge_version"), "NeoForge version")
        ) || integer(root.get("java_feature"), "Java feature") != ExactProfile.JAVA_FEATURE
                || !"1.10.5".equals(string(root.get("joml_version"), "JOML version"))
                || integer(root.get("pose_state_format_version"),
                "pose-state format version") != PoseStateCodec.VERSION
                || integer(root.get("quad_multiset_digest_version"),
                "quad multiset digest version") != QuadMultisetDigest.VERSION
                || !ExactProfile.EXPORTER_VERSION.equals(
                string(root.get("exporter_version"), "exporter version")
        )) {
            throw new IllegalArgumentException("pose bundle environment identity mismatch");
        }
        String stoneFile = string(root.get("stone_file_name"), "Stone filename");
        String cobblemonFile = string(root.get("cobblemon_file_name"), "Cobblemon filename");
        String exporterFile = string(root.get("exporter_file_name"), "exporter filename");
        if (!PoseCatalog.safeLedgerBaseName(stoneFile)
                || !PoseCatalog.safeLedgerBaseName(cobblemonFile)
                || !PoseCatalog.safeLedgerBaseName(exporterFile)) {
            throw new IllegalArgumentException("unsafe artifact filename");
        }
        String stoneSha = string(root.get("stone_sha256"), "Stone sha");
        String cobblemonSha = string(root.get("cobblemon_sha256"), "Cobblemon sha");
        String exporterSha = string(root.get("exporter_sha256"), "exporter sha");
        PoseCatalog.requireSha(exporterSha, "exporter sha256");
        long exporterSize = positiveLong(
                root.get("exporter_size"), "exporter size", MAX_CODE_SOURCE_BYTES
        );
        if (positiveLong(root.get("stone_size"), "Stone size", MAX_CODE_SOURCE_BYTES)
                != ExactProfile.STONE_SIZE
                || positiveLong(root.get("cobblemon_size"), "Cobblemon size",
                MAX_CODE_SOURCE_BYTES) != ExactProfile.COBBLEMON_SIZE
                || !ExactProfile.STONE_SHA256.equals(stoneSha)
                || !ExactProfile.COBBLEMON_SHA256.equals(cobblemonSha)
                || exporterSize != ExactProfile.EXPORTER_SIZE
                || !ExactProfile.EXPORTER_SHA256.equals(exporterSha)
                || !ExactProfile.COBBLEMON_COMMIT.equals(
                string(root.get("cobblemon_commit"), "Cobblemon commit")
        )) {
            throw new IllegalArgumentException("pose bundle artifact identity mismatch");
        }
        return new ExporterIdentity(
                stoneFile, cobblemonFile, exporterFile, exporterSize, exporterSha
        );
    }

    private static Map<String, ModelIdentity> parseModels(JsonArray array) {
        if (array.isEmpty() || array.size() > MAX_MODELS) {
            throw new IllegalArgumentException("model catalog count outside budget");
        }
        Map<String, ModelIdentity> result = new LinkedHashMap<>();
        String previous = null;
        for (JsonElement element : array) {
            JsonObject value = object(element, "model");
            requireExactKeys(value, List.of(
                    "model_id", "resource_id", "resource_path", "winning_pack_id",
                    "size", "sha256", "format_version", "geometry_identifier",
                    "texture_width", "texture_height", "topology_sha256",
                    "topology_node_count", "bone_count", "cube_count", "locator_count"
            ));
            ModelIdentity identity = new ModelIdentity(
                    string(value.get("model_id"), "model id"),
                    string(value.get("resource_id"), "model resource id"),
                    string(value.get("resource_path"), "model resource path"),
                    string(value.get("winning_pack_id"), "model winning pack"),
                    integer(value.get("size"), "model size"),
                    string(value.get("sha256"), "model sha"),
                    string(value.get("format_version"), "model format version"),
                    string(value.get("geometry_identifier"), "geometry identifier"),
                    integer(value.get("texture_width"), "model texture width"),
                    integer(value.get("texture_height"), "model texture height"),
                    string(value.get("topology_sha256"), "model topology sha"),
                    integer(value.get("topology_node_count"), "topology node count"),
                    integer(value.get("bone_count"), "bone count"),
                    integer(value.get("cube_count"), "cube count"),
                    integer(value.get("locator_count"), "locator count")
            );
            if (previous != null && previous.compareTo(identity.modelId()) >= 0
                    || result.put(identity.modelId(), identity) != null) {
                throw new IllegalArgumentException("model array is not canonical");
            }
            previous = identity.modelId();
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<String, PoseStateIdentity> parsePoseStates(
            JsonArray array, ZipFile zip, List<String> zipNames
    ) throws IOException {
        if (array.isEmpty() || array.size() > MAX_STATES) {
            throw new IllegalArgumentException("pose-state catalog count outside budget");
        }
        Map<String, PoseStateIdentity> result = new LinkedHashMap<>();
        List<String> expectedZipNames = new ArrayList<>(array.size() + 1);
        expectedZipNames.add("manifest.json");
        String previous = null;
        long totalStateBytes = 0L;
        for (JsonElement element : array) {
            JsonObject value = object(element, "pose state");
            requireExactKeys(value, List.of(
                    "sha256", "size", "selected_root_path", "node_count", "tint_argb"
            ));
            String sha256 = string(value.get("sha256"), "pose-state sha");
            PoseCatalog.requireSha(sha256, "pose-state sha");
            int size = boundedPositiveInteger(
                    value.get("size"), "pose-state size", PoseStateCodec.MAX_BYTES
            );
            String zipPath = "pose-states/" + sha256 + ".bin";
            ZipEntry entry = zip.getEntry(zipPath);
            if (entry == null || entry.getSize() != size) {
                throw new IllegalArgumentException("pose-state entry identity mismatch");
            }
            totalStateBytes = Math.addExact(totalStateBytes, size);
            if (totalStateBytes > MAX_TOTAL_STATE_BYTES) {
                throw new IllegalArgumentException("pose-state closure exceeds byte budget");
            }
            byte[] raw = readEntry(zip, entry, size);
            PoseStateIdentity identity = PoseStateIdentity.from(
                    sha256, size,
                    string(value.get("selected_root_path"), "selected root path"),
                    integer(value.get("node_count"), "pose-state node count"),
                    integer(value.get("tint_argb"), "pose-state tint"), raw
            );
            if (previous != null && previous.compareTo(sha256) >= 0
                    || result.put(sha256, identity) != null) {
                throw new IllegalArgumentException("pose-state array is not canonical");
            }
            previous = sha256;
            expectedZipNames.add(zipPath);
        }
        if (!expectedZipNames.equals(zipNames)) {
            throw new IllegalArgumentException("pose-state ZIP roster differs from manifest");
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<String, TextureIdentity> parseTextures(JsonArray array) {
        if (array.isEmpty() || array.size() > MAX_TEXTURES) {
            throw new IllegalArgumentException("texture catalog count outside budget");
        }
        Map<String, TextureIdentity> result = new LinkedHashMap<>();
        String previous = null;
        for (JsonElement element : array) {
            JsonObject value = object(element, "texture");
            requireExactKeys(value, List.of(
                    "resource_id", "resource_path", "winning_pack_id",
                    "size", "sha256", "width", "height"
            ));
            TextureIdentity identity = new TextureIdentity(
                    string(value.get("resource_id"), "texture resource id"),
                    string(value.get("resource_path"), "texture resource path"),
                    string(value.get("winning_pack_id"), "texture winning pack"),
                    integer(value.get("size"), "texture size"),
                    string(value.get("sha256"), "texture sha"),
                    integer(value.get("width"), "texture width"),
                    integer(value.get("height"), "texture height")
            );
            if (previous != null && previous.compareTo(identity.resourceId()) >= 0
                    || result.put(identity.resourceId(), identity) != null) {
                throw new IllegalArgumentException("texture array is not canonical");
            }
            previous = identity.resourceId();
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<String, PoseEntry> parsePoses(JsonArray array, boolean fallback) {
        if (array.isEmpty() || array.size() > MAX_POSES) {
            throw new IllegalArgumentException("pose catalog count outside budget");
        }
        Map<String, PoseEntry> result = new LinkedHashMap<>();
        PoseEntry previous = null;
        for (JsonElement element : array) {
            JsonObject value = object(element, fallback ? "fallback" : "profile");
            requireExactKeys(value, List.of(
                    "species", "form", "aspects", "pose", "pose_index",
                    "effective_pose", "base_scale", "geometry_resolution",
                    "texture_resolution", "model_id", "poser_id",
                    "texture_resource_id", "pose_state_sha256",
                    "verification_quad_multiset_sha256", "source_quad_count",
                    "retained_quad_count", "dropped_degenerate_quad_count",
                    "minimum", "maximum", "tint_argb"
            ));
            QuadMultisetDigest.Bounds bounds = new QuadMultisetDigest.Bounds(
                    vector(value.get("minimum"), "minimum"),
                    vector(value.get("maximum"), "maximum")
            );
            QuadMultisetDigest.Result verification = new QuadMultisetDigest.Result(
                    string(value.get("verification_quad_multiset_sha256"),
                            "quad multiset sha"),
                    integer(value.get("source_quad_count"), "source quad count"),
                    integer(value.get("retained_quad_count"), "retained quad count"),
                    integer(value.get("dropped_degenerate_quad_count"),
                            "dropped quad count"),
                    integer(value.get("tint_argb"), "pose tint"), bounds
            );
            PoseEntry entry = new PoseEntry(
                    string(value.get("species"), "species"),
                    string(value.get("form"), "form"),
                    string(value.get("aspects"), "aspects"),
                    string(value.get("pose"), "requested pose"),
                    integer(value.get("pose_index"), "pose index"),
                    nullableString(value.get("effective_pose"), "effective pose"),
                    finiteFloat(value.get("base_scale"), "base scale"),
                    resolution(value.get("geometry_resolution"), "geometry resolution"),
                    resolution(value.get("texture_resolution"), "texture resolution"),
                    string(value.get("model_id"), "model id"),
                    string(value.get("poser_id"), "poser id"),
                    string(value.get("texture_resource_id"), "texture resource id"),
                    string(value.get("pose_state_sha256"), "pose-state sha"),
                    verification
            );
            if (fallback != (entry.poseIndex() == -1)
                    || previous != null && POSE_ORDER.compare(previous, entry) >= 0) {
                throw new IllegalArgumentException("pose array is not canonical");
            }
            String key = fallback ? entry.choiceKey() : entry.key();
            if (result.put(key, entry) != null) {
                throw new IllegalArgumentException("duplicate pose identity");
            }
            previous = entry;
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Attestation verifyAttestation(
            JsonObject value,
            Map<String, PoseEntry> profiles,
            Map<String, PoseEntry> fallbacks,
            Map<String, ModelIdentity> models,
            Map<String, PoseStateIdentity> poseStates,
            Map<String, TextureIdentity> textures,
            ExporterIdentity exporter,
            int expectedSpeciesCount
    ) {
        requireExactKeys(value, List.of(
                "species_count", "choice_count", "pose_count", "model_count",
                "pose_state_count", "texture_count", "species_root", "choices_root",
                "poses_root", "models_root", "pose_states_root", "textures_root",
                "resource_generation_start", "resource_generation_end",
                "model_binding_generation_start", "model_binding_generation_end",
                "code_sources", "resource_packs"
        ));
        List<CodeSourceIdentity> codeSources = parseCodeSources(
                array(value.get("code_sources"), "code sources")
        );
        List<ResourcePackIdentity> resourcePacks = parseResourcePacks(
                array(value.get("resource_packs"), "resource packs")
        );
        long resourceStart = positiveLong(
                value.get("resource_generation_start"), "resource generation start",
                Long.MAX_VALUE
        );
        long resourceEnd = positiveLong(
                value.get("resource_generation_end"), "resource generation end",
                Long.MAX_VALUE
        );
        long bindingStart = positiveLong(
                value.get("model_binding_generation_start"), "binding generation start",
                Long.MAX_VALUE
        );
        long bindingEnd = positiveLong(
                value.get("model_binding_generation_end"), "binding generation end",
                Long.MAX_VALUE
        );
        Set<String> species = new HashSet<>();
        fallbacks.values().forEach(entry -> species.add(entry.speciesId()));
        String speciesRoot = string(value.get("species_root"), "species root");
        String choicesRoot = string(value.get("choices_root"), "choices root");
        String posesRoot = string(value.get("poses_root"), "poses root");
        String modelsRoot = string(value.get("models_root"), "models root");
        String poseStatesRoot = string(value.get("pose_states_root"), "pose-states root");
        String texturesRoot = string(value.get("textures_root"), "textures root");
        if (integer(value.get("species_count"), "species count") != species.size()
                || species.size() != expectedSpeciesCount
                || integer(value.get("choice_count"), "choice count") != fallbacks.size()
                || integer(value.get("pose_count"), "pose count")
                != profiles.size() + fallbacks.size()
                || integer(value.get("model_count"), "model count") != models.size()
                || integer(value.get("pose_state_count"), "pose-state count")
                != poseStates.size()
                || integer(value.get("texture_count"), "texture count") != textures.size()
                || resourceStart != resourceEnd || bindingStart != bindingEnd
                || !CatalogRoots.species(fallbacks).equals(speciesRoot)
                || !CatalogRoots.choices(fallbacks).equals(choicesRoot)
                || !CatalogRoots.poses(profiles, fallbacks).equals(posesRoot)
                || !CatalogRoots.models(models).equals(modelsRoot)
                || !CatalogRoots.poseStates(poseStates).equals(poseStatesRoot)
                || !CatalogRoots.textures(textures).equals(texturesRoot)) {
            throw new IllegalArgumentException("format-2 completeness attestation mismatch");
        }
        verifyCodeSourceIdentity(codeSources, "cobblemonstonestatues",
                ExactProfile.STONE_SIZE, ExactProfile.STONE_SHA256, exporter.stoneFileName());
        verifyCodeSourceIdentity(codeSources, "cobblemon",
                ExactProfile.COBBLEMON_SIZE, ExactProfile.COBBLEMON_SHA256,
                exporter.cobblemonFileName());
        verifyCodeSourceIdentity(codeSources, ExactProfile.EXPORTER_MOD_ID,
                exporter.size(), exporter.sha256(), exporter.fileName());
        return new Attestation(
                species.size(), fallbacks.size(), profiles.size() + fallbacks.size(),
                models.size(), poseStates.size(), textures.size(), speciesRoot, choicesRoot,
                posesRoot, modelsRoot, poseStatesRoot, texturesRoot,
                resourceStart, resourceEnd, bindingStart, bindingEnd,
                codeSources, resourcePacks
        );
    }

    private static List<CodeSourceIdentity> parseCodeSources(JsonArray array) {
        if (array.isEmpty() || array.size() > MAX_CODE_SOURCES) {
            throw new IllegalArgumentException("code-source ledger outside budget");
        }
        List<CodeSourceIdentity> result = new ArrayList<>(array.size());
        Set<String> modIds = new HashSet<>();
        for (JsonElement element : array) {
            JsonObject value = object(element, "code source");
            requireExactKeys(value, List.of("mod_id", "file_name", "size", "sha256"));
            CodeSourceIdentity identity = new CodeSourceIdentity(
                    string(value.get("mod_id"), "code-source mod id"),
                    string(value.get("file_name"), "code-source filename"),
                    positiveLong(value.get("size"), "code-source size",
                            MAX_CODE_SOURCE_BYTES),
                    string(value.get("sha256"), "code-source sha")
            );
            if (!modIds.add(identity.modId())) {
                throw new IllegalArgumentException("duplicate code-source ledger key");
            }
            result.add(identity);
        }
        return List.copyOf(result);
    }

    private static List<ResourcePackIdentity> parseResourcePacks(JsonArray array) {
        if (array.isEmpty() || array.size() > MAX_RESOURCE_PACKS) {
            throw new IllegalArgumentException("resource-pack ledger outside budget");
        }
        List<ResourcePackIdentity> result = new ArrayList<>(array.size());
        Set<String> packIds = new HashSet<>();
        for (int expectedOrder = 0; expectedOrder < array.size(); expectedOrder++) {
            JsonObject value = object(array.get(expectedOrder), "resource pack");
            requireExactKeys(value, List.of("order", "pack_id", "fingerprint"));
            ResourcePackIdentity identity = new ResourcePackIdentity(
                    integer(value.get("order"), "pack order"),
                    string(value.get("pack_id"), "pack id"),
                    string(value.get("fingerprint"), "pack fingerprint")
            );
            if (identity.order() != expectedOrder || !packIds.add(identity.packId())) {
                throw new IllegalArgumentException("resource-pack ledger key/order mismatch");
            }
            result.add(identity);
        }
        return List.copyOf(result);
    }

    private static List<String> validateZipRoster(ZipFile zip) {
        if (zip.getComment() != null && !zip.getComment().isEmpty()) {
            throw new IllegalArgumentException("pose bundle ZIP comment is not canonical");
        }
        List<String> names = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        long totalBytes = 0L;
        int count = 0;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            long size = entry.getSize();
            long compressedSize = entry.getCompressedSize();
            if (++count > MAX_STATES + 1 || entry.isDirectory() || !unique.add(name)
                    || name.startsWith("/") || name.contains("..") || name.contains("\\")
                    || (!"manifest.json".equals(name)
                    && !name.matches("pose-states/[0-9a-f]{64}\\.bin"))
                    || entry.getMethod() != ZipEntry.STORED || size < 1
                    || compressedSize != size || entry.getComment() != null
                    || entry.getExtra() != null && entry.getExtra().length != 0
                    || !CANONICAL_ZIP_TIME.equals(entry.getTimeLocal())) {
                throw new IllegalArgumentException("pose bundle ZIP roster is unsafe");
            }
            totalBytes = Math.addExact(totalBytes, size);
            if (totalBytes > MAX_TOTAL_ENTRY_BYTES) {
                throw new IllegalArgumentException("pose bundle ZIP exceeds byte budget");
            }
            names.add(name);
        }
        if (names.isEmpty() || !"manifest.json".equals(names.getFirst())) {
            throw new IllegalArgumentException("pose bundle manifest is missing or reordered");
        }
        for (int index = 2; index < names.size(); index++) {
            if (names.get(index - 1).compareTo(names.get(index)) >= 0) {
                throw new IllegalArgumentException("pose-state ZIP entries are not sorted");
            }
        }
        return List.copyOf(names);
    }

    private static void validateLocalHeaders(Path bundle, List<String> expected)
            throws IOException {
        List<String> localNames = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        try (RandomAccessFile input = new RandomAccessFile(bundle.toFile(), "r")) {
            while (input.getFilePointer() + Integer.BYTES <= input.length()) {
                int signature = readLittleInt(input);
                if (signature == CENTRAL_DIRECTORY_HEADER) {
                    break;
                }
                if (signature != LOCAL_FILE_HEADER) {
                    throw new IllegalArgumentException("noncanonical ZIP local header");
                }
                readLittleShort(input);
                int flags = readLittleShort(input);
                int method = readLittleShort(input);
                readLittleShort(input);
                readLittleShort(input);
                readLittleInt(input);
                long compressed = Integer.toUnsignedLong(readLittleInt(input));
                long uncompressed = Integer.toUnsignedLong(readLittleInt(input));
                int nameLength = readLittleShort(input);
                int extraLength = readLittleShort(input);
                if ((flags & ~UTF8_FLAG) != 0 || method != ZipEntry.STORED
                        || compressed != uncompressed || compressed < 1
                        || nameLength < 1 || nameLength > 128 || extraLength != 0) {
                    throw new IllegalArgumentException("unsafe ZIP flags or local sizes");
                }
                byte[] nameBytes = new byte[nameLength];
                input.readFully(nameBytes);
                String name = decodeUtf8(nameBytes);
                if (!unique.add(name) || compressed > input.length() - input.getFilePointer()) {
                    throw new IllegalArgumentException("invalid ZIP local roster");
                }
                localNames.add(name);
                input.seek(input.getFilePointer() + compressed);
            }
        }
        if (!localNames.equals(expected)) {
            throw new IllegalArgumentException("ZIP local/central roster mismatch");
        }
    }

    private static JsonObject readManifest(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("manifest.json");
        if (entry == null || entry.getSize() < 1 || entry.getSize() > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("pose bundle manifest is invalid");
        }
        byte[] raw = readEntry(zip, entry, Math.toIntExact(entry.getSize()));
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString();
            return object(JsonParser.parseString(json), "manifest root");
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("manifest is not canonical UTF-8", exception);
        }
    }

    private static ResolutionKind resolution(JsonElement element, String label) {
        try {
            return ResolutionKind.valueOf(string(element, label));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid " + label, exception);
        }
    }

    private static void verifyCodeSourceIdentity(
            List<CodeSourceIdentity> identities,
            String modId,
            long size,
            String sha256,
            String fileName
    ) {
        CodeSourceIdentity found = identities.stream()
                .filter(identity -> modId.equals(identity.modId()))
                .findFirst().orElseThrow(() ->
                        new IllegalArgumentException("required code source is absent"));
        if (found.size() != size || !found.sha256().equals(sha256)
                || fileName != null && !found.fileName().equals(fileName)) {
            throw new IllegalArgumentException("required code-source identity mismatch");
        }
    }

    private static String configured(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static QuadMultisetDigest.Vec3 vector(JsonElement element, String label) {
        JsonArray values = array(element, label);
        if (values.size() != 3) {
            throw new IllegalArgumentException(label + " must have three values");
        }
        return new QuadMultisetDigest.Vec3(
                finiteFloat(values.get(0), label + " x"),
                finiteFloat(values.get(1), label + " y"),
                finiteFloat(values.get(2), label + " z")
        );
    }

    private static float finiteFloat(JsonElement element, String label) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(label + " must be numeric");
        }
        float value = element.getAsFloat();
        if (!Float.isFinite(value) || Math.abs(value) > 1_000_000F) {
            throw new IllegalArgumentException(label + " is outside the float budget");
        }
        return value;
    }

    private static int integer(JsonElement element, String label) {
        long value = exactLong(element, label);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " must be an integer");
        }
        return (int) value;
    }

    private static int boundedPositiveInteger(
            JsonElement element, String label, int maximum
    ) {
        long value = positiveLong(element, label, maximum);
        return Math.toIntExact(value);
    }

    private static long positiveLong(JsonElement element, String label, long maximum) {
        long value = exactLong(element, label);
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException(label + " outside budget");
        }
        return value;
    }

    private static long exactLong(JsonElement element, String label) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(label + " must be numeric");
        }
        try {
            return new BigDecimal(element.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be an exact integer", exception);
        }
    }

    private static String nullableString(JsonElement element, String label) {
        if (element == null) {
            throw new IllegalArgumentException(label + " is absent");
        }
        return element.isJsonNull() ? null : string(element, label);
    }

    private static String string(JsonElement element, String label) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(label + " must be a string");
        }
        String value = element.getAsString();
        if (value.length() > 4_096 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(label + " is outside budget");
        }
        return value;
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonElement element, String label) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(label + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static void requireExactKeys(JsonObject object, List<String> expected) {
        List<String> actual = new ArrayList<>(object.keySet());
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "catalog object fields changed or reordered: " + actual
            );
        }
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry, int expected)
            throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] raw = input.readNBytes(expected + 1);
            if (raw.length != expected || input.read() != -1) {
                throw new IllegalArgumentException("ZIP entry size changed while reading");
            }
            return raw;
        }
    }

    private static int readLittleInt(RandomAccessFile input) throws IOException {
        return Integer.reverseBytes(input.readInt());
    }

    private static int readLittleShort(RandomAccessFile input) throws IOException {
        return Short.toUnsignedInt(Short.reverseBytes(input.readShort()));
    }

    private static String decodeUtf8(byte[] raw) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("ZIP name is not UTF-8", exception);
        }
    }

    static String digest(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static String digest(byte[] raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record ExporterIdentity(
            String stoneFileName,
            String cobblemonFileName,
            String fileName,
            long size,
            String sha256
    ) {
    }
}
