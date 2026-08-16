/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseStateCodec;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Bounds;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Canonical format-2 schema records used only by the operator-local exporter. */
final class ExportRecords {

    static final String FALLBACK_POSE = "__atmons_impossible_pose__";
    private static final int MAX_TEXT_LENGTH = 4_096;
    private static final int MAX_QUADS = 50_000;

    private ExportRecords() {
    }

    record Pose(
            String species,
            String form,
            String aspects,
            String requestedPose,
            int poseIndex,
            String effectivePose,
            float baseScale,
            String geometryResolution,
            String textureResolution,
            String modelId,
            String poserId,
            String textureResourceId,
            String poseStateSha256,
            String verificationQuadMultisetSha256,
            int sourceQuadCount,
            int retainedQuadCount,
            int droppedDegenerateQuadCount,
            Bounds bounds,
            int tintArgb
    ) {
        Pose {
            requireResourceId(species, "species");
            requireText(form, "form");
            requireText(aspects, "aspects");
            requireText(requestedPose, "requested pose");
            requireNullableNonblankText(effectivePose, "effective pose");
            requireResolution(geometryResolution, "geometry resolution");
            requireResolution(textureResolution, "texture resolution");
            requireResourceId(modelId, "model");
            requireResourceId(poserId, "poser");
            requireResourceId(textureResourceId, "texture");
            requireSha(poseStateSha256, "pose state");
            requireSha(verificationQuadMultisetSha256, "verification quad multiset");
            Objects.requireNonNull(bounds, "bounds");
            if (requestedPose.isBlank()
                    || !Float.isFinite(baseScale) || baseScale <= 0F || baseScale > 64F
                    || poseIndex < -1 || poseIndex > MAX_QUADS
                    || sourceQuadCount < 1 || sourceQuadCount > MAX_QUADS
                    || retainedQuadCount < 1 || retainedQuadCount > MAX_QUADS
                    || droppedDegenerateQuadCount < 0
                    || Math.addExact(retainedQuadCount, droppedDegenerateQuadCount)
                    != sourceQuadCount) {
                throw new IllegalArgumentException("invalid exported pose metadata");
            }
            requireOrderedBounds(bounds);
            boolean fallback = poseIndex == -1;
            if (fallback != FALLBACK_POSE.equals(requestedPose)) {
                throw new IllegalArgumentException("invalid exported fallback identity");
            }
        }

        String choiceKey() {
            return species + '\u001f' + form + '\u001f' + aspects;
        }

        String poseKey() {
            return choiceKey() + '\u001f' + requestedPose;
        }

        List<String> rootRow(String kind) {
            List<String> row = new ArrayList<>(27);
            Collections.addAll(
                    row,
                    kind, species, form, aspects, requestedPose,
                    Integer.toString(poseIndex), effectivePose == null ? "0" : "1",
                    effectivePose == null ? "" : effectivePose,
                    Float.toHexString(baseScale), geometryResolution, textureResolution,
                    modelId, poserId, textureResourceId,
                    poseStateSha256, verificationQuadMultisetSha256,
                    Integer.toString(sourceQuadCount),
                    Integer.toString(retainedQuadCount),
                    Integer.toString(droppedDegenerateQuadCount)
            );
            row.addAll(boundsRow(bounds));
            row.add(Integer.toUnsignedString(tintArgb));
            return List.copyOf(row);
        }

        JsonObject json() {
            JsonObject value = new JsonObject();
            value.addProperty("species", species);
            value.addProperty("form", form);
            value.addProperty("aspects", aspects);
            value.addProperty("pose", requestedPose);
            value.addProperty("pose_index", poseIndex);
            if (effectivePose == null) {
                value.add("effective_pose", JsonNull.INSTANCE);
            } else {
                value.addProperty("effective_pose", effectivePose);
            }
            value.addProperty("base_scale", baseScale);
            value.addProperty("geometry_resolution", geometryResolution);
            value.addProperty("texture_resolution", textureResolution);
            value.addProperty("model_id", modelId);
            value.addProperty("poser_id", poserId);
            value.addProperty("texture_resource_id", textureResourceId);
            value.addProperty("pose_state_sha256", poseStateSha256);
            value.addProperty(
                    "verification_quad_multiset_sha256", verificationQuadMultisetSha256
            );
            value.addProperty("source_quad_count", sourceQuadCount);
            value.addProperty("retained_quad_count", retainedQuadCount);
            value.addProperty("dropped_degenerate_quad_count", droppedDegenerateQuadCount);
            value.add("minimum", vector(bounds.minimum()));
            value.add("maximum", vector(bounds.maximum()));
            value.addProperty("tint_argb", tintArgb);
            return value;
        }
    }

    record PoseStateBlob(
            String sha256,
            byte[] raw,
            String selectedRootPath,
            int nodeCount,
            int tintArgb
    ) {
        PoseStateBlob {
            requireSha(sha256, "pose state");
            Objects.requireNonNull(raw, "raw");
            requireText(selectedRootPath, "selected root path");
            if (raw.length < 1 || raw.length > PoseStateCodec.MAX_BYTES
                    || !sha256.equals(PoseStateCodec.sha256(raw))) {
                throw new IllegalArgumentException("invalid compact pose-state blob");
            }
            PoseState decoded = PoseStateCodec.decode(raw);
            if (!selectedRootPath.equals(decoded.selectedRootPath())
                    || nodeCount != decoded.nodes().size() || tintArgb != decoded.tintArgb()) {
                throw new IllegalArgumentException("pose-state summary differs from bytes");
            }
            raw = raw.clone();
        }

        static PoseStateBlob from(String sha256, byte[] raw) {
            PoseState decoded = PoseStateCodec.decode(raw);
            return new PoseStateBlob(
                    sha256, raw, decoded.selectedRootPath(),
                    decoded.nodes().size(), decoded.tintArgb()
            );
        }

        List<String> rootRow() {
            return List.of(
                    sha256, Integer.toString(raw.length), selectedRootPath,
                    Integer.toString(nodeCount), Integer.toUnsignedString(tintArgb)
            );
        }

        JsonObject json() {
            JsonObject value = new JsonObject();
            value.addProperty("sha256", sha256);
            value.addProperty("size", raw.length);
            value.addProperty("selected_root_path", selectedRootPath);
            value.addProperty("node_count", nodeCount);
            value.addProperty("tint_argb", tintArgb);
            return value;
        }

        @Override
        public byte[] raw() {
            return raw.clone();
        }

        byte[] verifiedRawForWrite() {
            if (!sha256.equals(PoseStateCodec.sha256(raw))) {
                throw new IllegalStateException("retained pose-state bytes changed");
            }
            PoseState decoded = PoseStateCodec.decode(raw);
            if (!selectedRootPath.equals(decoded.selectedRootPath())
                    || nodeCount != decoded.nodes().size()
                    || tintArgb != decoded.tintArgb()) {
                throw new IllegalStateException("retained pose-state summary changed");
            }
            return raw;
        }
    }

    record ModelResource(
            String modelId,
            String resourceId,
            String winningPackId,
            int size,
            String sha256,
            String formatVersion,
            String geometryIdentifier,
            int textureWidth,
            int textureHeight,
            String topologySha256,
            int topologyNodeCount,
            int boneCount,
            int cubeCount,
            int locatorCount
    ) {
        ModelResource {
            requireResourceId(modelId, "model");
            requireResourceId(resourceId, "model resource");
            requireText(winningPackId, "winning pack");
            requireText(formatVersion, "GEO format version");
            requireText(geometryIdentifier, "GEO identifier");
            requireSha(sha256, "model resource");
            requireSha(topologySha256, "model topology");
            if (winningPackId.isBlank() || size < 2
                    || size > FormatTwoBudgets.MAX_RESOURCE_BYTES
                    || !MetadataValidation.safePackId(winningPackId)
                    || !resourceId.endsWith(".geo.json")
                    || !Set.of("1.12.0", "1.21.0").contains(formatVersion)
                    || geometryIdentifier.isBlank()
                    || textureWidth < 1 || textureWidth > 16_384
                    || textureHeight < 1 || textureHeight > 16_384
                    || topologyNodeCount < 1
                    || topologyNodeCount > FormatTwoBudgets.MAX_MODEL_TOPOLOGY_NODES
                    || boneCount < 1 || boneCount > FormatTwoBudgets.MAX_MODEL_BONES
                    || cubeCount < 0 || cubeCount > FormatTwoBudgets.MAX_MODEL_CUBES
                    || locatorCount < 0
                    || locatorCount > FormatTwoBudgets.MAX_MODEL_LOCATORS) {
                throw new IllegalArgumentException("invalid winning model resource metadata");
            }
        }

        List<String> rootRow() {
            return List.of(
                    modelId, resourceId, resourcePath(resourceId), winningPackId,
                    Integer.toString(size), sha256, formatVersion, geometryIdentifier,
                    Integer.toString(textureWidth), Integer.toString(textureHeight),
                    topologySha256, Integer.toString(topologyNodeCount),
                    Integer.toString(boneCount), Integer.toString(cubeCount),
                    Integer.toString(locatorCount)
            );
        }

        JsonObject json() {
            JsonObject value = new JsonObject();
            value.addProperty("model_id", modelId);
            value.addProperty("resource_id", resourceId);
            value.addProperty("resource_path", resourcePath(resourceId));
            value.addProperty("winning_pack_id", winningPackId);
            value.addProperty("size", size);
            value.addProperty("sha256", sha256);
            value.addProperty("format_version", formatVersion);
            value.addProperty("geometry_identifier", geometryIdentifier);
            value.addProperty("texture_width", textureWidth);
            value.addProperty("texture_height", textureHeight);
            value.addProperty("topology_sha256", topologySha256);
            value.addProperty("topology_node_count", topologyNodeCount);
            value.addProperty("bone_count", boneCount);
            value.addProperty("cube_count", cubeCount);
            value.addProperty("locator_count", locatorCount);
            return value;
        }
    }

    record Texture(
            String resourceId,
            String winningPackId,
            int size,
            String sha256,
            int width,
            int height
    ) {
        Texture {
            requireResourceId(resourceId, "texture");
            requireText(winningPackId, "winning pack");
            requireSha(sha256, "texture");
            if (winningPackId.isBlank() || !MetadataValidation.safePackId(winningPackId)
                    || !resourceId.endsWith(".png")
                    || size < 1 || size > FormatTwoBudgets.MAX_RESOURCE_BYTES
                    || width < 1 || width > 16_384 || height < 1 || height > 16_384
                    || (long) width * height > 16L * 1024L * 1024L) {
                throw new IllegalArgumentException("invalid texture metadata");
            }
        }

        List<String> rootRow() {
            return List.of(
                    resourceId, resourcePath(resourceId), winningPackId,
                    Integer.toString(size), sha256,
                    Integer.toString(width), Integer.toString(height)
            );
        }

        JsonObject json() {
            JsonObject value = new JsonObject();
            value.addProperty("resource_id", resourceId);
            value.addProperty("resource_path", resourcePath(resourceId));
            value.addProperty("winning_pack_id", winningPackId);
            value.addProperty("size", size);
            value.addProperty("sha256", sha256);
            value.addProperty("width", width);
            value.addProperty("height", height);
            return value;
        }
    }

    record CodeSource(String modId, String fileName, long size, String sha256) {
        CodeSource {
            if (modId == null || !modId.matches("[a-z0-9][a-z0-9._-]{0,127}")
                    || !safeBaseName(fileName) || size < 1
                    || size > 2L * 1024L * 1024L * 1024L
                    || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid code-source metadata");
            }
        }

        JsonObject json() {
            JsonObject value = new JsonObject();
            value.addProperty("mod_id", modId);
            value.addProperty("file_name", fileName);
            value.addProperty("size", size);
            value.addProperty("sha256", sha256);
            return value;
        }

        private static boolean safeBaseName(String value) {
            if (value == null || value.isBlank() || value.length() > 512
                    || ".".equals(value) || "..".equals(value)
                    || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
                return false;
            }
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                if (Character.isISOControl(codePoint)
                        || Character.getType(codePoint) == Character.FORMAT) {
                    return false;
                }
                offset += Character.charCount(codePoint);
            }
            return true;
        }
    }

    record ResourcePack(int order, String packId, String fingerprint) {
        ResourcePack {
            requireText(packId, "resource pack");
            requireSha(fingerprint, "resource-pack fingerprint");
            if (order < 0 || packId.isBlank()
                    || !MetadataValidation.safePackId(packId)) {
                throw new IllegalArgumentException("invalid resource-pack metadata");
            }
        }

        JsonObject json() {
            JsonObject value = new JsonObject();
            value.addProperty("order", order);
            value.addProperty("pack_id", packId);
            value.addProperty("fingerprint", fingerprint);
            return value;
        }
    }

    record Catalog(
            List<Pose> profiles,
            List<Pose> fallbacks,
            Map<String, ModelResource> models,
            Map<String, PoseStateBlob> poseStates,
            Map<String, Texture> textures,
            List<CodeSource> codeSources,
            List<ResourcePack> resourcePacks,
            long resourceGeneration,
            long modelBindingGeneration,
            CodeSource exporter
    ) {
        private static final int MAX_CATALOG_POSES = 50_000;
        private static final int MAX_CATALOG_MODELS = 8_192;
        private static final int MAX_CATALOG_STATES = 50_000;
        private static final int MAX_CATALOG_TEXTURES = 8_192;
        private static final int MAX_CODE_SOURCES = 4_096;
        private static final int MAX_RESOURCE_PACKS = 1_024;
        private static final long MAX_MANIFEST_ESTIMATE = 48L * 1024L * 1024L;

        Catalog {
            profiles = profiles.stream().sorted(poseOrder()).toList();
            fallbacks = fallbacks.stream().sorted(poseOrder()).toList();
            models = immutableMap(models);
            poseStates = immutableMap(poseStates);
            textures = immutableMap(textures);
            codeSources = List.copyOf(codeSources);
            resourcePacks = List.copyOf(resourcePacks);
            Objects.requireNonNull(exporter, "exporter");
            if (profiles.isEmpty() || fallbacks.isEmpty()
                    || Math.addExact(profiles.size(), fallbacks.size()) > MAX_CATALOG_POSES
                    || models.isEmpty() || models.size() > MAX_CATALOG_MODELS
                    || poseStates.isEmpty() || poseStates.size() > MAX_CATALOG_STATES
                    || textures.isEmpty() || textures.size() > MAX_CATALOG_TEXTURES
                    || codeSources.isEmpty() || codeSources.size() > MAX_CODE_SOURCES
                    || resourcePacks.isEmpty() || resourcePacks.size() > MAX_RESOURCE_PACKS
                    || resourceGeneration < 1 || modelBindingGeneration < 1) {
                throw new IllegalArgumentException("export catalog exceeds metadata budget");
            }
            verifyClosure(profiles, fallbacks, models, poseStates, textures);
            verifyCodeSources(codeSources, exporter);
            verifyResourcePacks(resourcePacks);
            verifyMapKeys(models, poseStates, textures);
            verifyWinningPacks(models, textures, resourcePacks);
            verifyResourceBudgets(models, textures);
            verifyMaterialBudgets(profiles, fallbacks, textures);
            if (estimatedManifestBytes(
                    profiles, fallbacks, models, poseStates, textures,
                    codeSources, resourcePacks
            ) > MAX_MANIFEST_ESTIMATE) {
                throw new IllegalArgumentException("export manifest exceeds allocation budget");
            }
        }

        JsonObject manifest() {
            JsonObject root = new JsonObject();
            root.addProperty("format_version", 2);
            root.addProperty("catalog_id",
                    "atmons-1.2.0-stone-statues-1.1-cobblemon-1.7.3-hybrid");
            root.addProperty("minecraft_version", "1.21.1");
            root.addProperty("neoforge_version", "21.1.248");
            root.addProperty("java_feature", 21);
            root.addProperty("joml_version", "1.10.5");
            root.addProperty("pose_state_format_version", PoseStateCodec.VERSION);
            root.addProperty("quad_multiset_digest_version", 1);
            CodeSource stone = source("cobblemonstonestatues");
            CodeSource cobblemon = source("cobblemon");
            addArtifact(root, "stone", stone);
            addArtifact(root, "cobblemon", cobblemon);
            root.addProperty("cobblemon_commit",
                    "37264aff0f6c90c9f9a79bbdfc712bd11929a38c");
            root.addProperty("exporter_version", "0.1.0-alpha.1");
            addArtifact(root, "exporter", exporter);
            root.add("profiles", array(profiles.stream().map(Pose::json).toList()));
            root.add("fallbacks", array(fallbacks.stream().map(Pose::json).toList()));
            root.add("models", array(models.values().stream()
                    .map(ModelResource::json).toList()));
            root.add("pose_states", array(poseStates.values().stream()
                    .map(PoseStateBlob::json).toList()));
            root.add("textures", array(textures.values().stream()
                    .map(Texture::json).toList()));
            root.add("attestation", attestation());
            return root;
        }

        private JsonObject attestation() {
            List<List<String>> speciesRows = fallbacks.stream()
                    .map(pose -> List.of(pose.species())).distinct().toList();
            List<List<String>> choiceRows = fallbacks.stream()
                    .map(pose -> List.of(pose.choiceKey())).distinct().toList();
            List<List<String>> poseRows = new ArrayList<>();
            profiles.forEach(pose -> poseRows.add(pose.rootRow("profile")));
            fallbacks.forEach(pose -> poseRows.add(pose.rootRow("fallback")));
            JsonObject value = new JsonObject();
            value.addProperty("species_count", speciesRows.size());
            value.addProperty("choice_count", choiceRows.size());
            value.addProperty("pose_count", poseRows.size());
            value.addProperty("model_count", models.size());
            value.addProperty("pose_state_count", poseStates.size());
            value.addProperty("texture_count", textures.size());
            value.addProperty("species_root", CatalogRoots.root("species", speciesRows));
            value.addProperty("choices_root", CatalogRoots.root("choices", choiceRows));
            value.addProperty("poses_root", CatalogRoots.root("poses-v2", poseRows));
            value.addProperty("models_root", CatalogRoots.root("models-v2",
                    models.values().stream().map(ModelResource::rootRow).toList()));
            value.addProperty("pose_states_root", CatalogRoots.root("pose-states-v2",
                    poseStates.values().stream().map(PoseStateBlob::rootRow).toList()));
            value.addProperty("textures_root", CatalogRoots.root("textures",
                    textures.values().stream().map(Texture::rootRow).toList()));
            value.addProperty("resource_generation_start", resourceGeneration);
            value.addProperty("resource_generation_end", resourceGeneration);
            value.addProperty("model_binding_generation_start", modelBindingGeneration);
            value.addProperty("model_binding_generation_end", modelBindingGeneration);
            value.add("code_sources", array(codeSources.stream()
                    .map(CodeSource::json).toList()));
            value.add("resource_packs", array(resourcePacks.stream()
                    .map(ResourcePack::json).toList()));
            return value;
        }

        private CodeSource source(String modId) {
            return codeSources.stream().filter(source -> modId.equals(source.modId()))
                    .findFirst().orElseThrow();
        }

        private static void addArtifact(JsonObject target, String prefix, CodeSource source) {
            target.addProperty(prefix + "_file_name", source.fileName());
            target.addProperty(prefix + "_size", source.size());
            target.addProperty(prefix + "_sha256", source.sha256());
        }

        private static void verifyClosure(
                List<Pose> profiles,
                List<Pose> fallbacks,
                Map<String, ModelResource> models,
                Map<String, PoseStateBlob> states,
                Map<String, Texture> textures
        ) {
            Set<String> choices = new LinkedHashSet<>();
            Map<String, Integer> nextPoseIndex = new HashMap<>();
            Map<String, RouteIdentity> routes = new HashMap<>();
            Set<String> poseKeys = new HashSet<>();
            Set<String> usedModels = new HashSet<>();
            Set<String> usedStates = new HashSet<>();
            Set<String> usedTextures = new HashSet<>();
            for (Pose pose : profiles) {
                choices.add(pose.choiceKey());
                int expected = nextPoseIndex.getOrDefault(pose.choiceKey(), 0);
                if (pose.poseIndex() != expected || !poseKeys.add(pose.poseKey())) {
                    throw new IllegalArgumentException("noncanonical profile pose sequence");
                }
                nextPoseIndex.put(pose.choiceKey(), expected + 1);
                verifyPoseReferences(pose, models, states, textures);
                verifyStableRoute(routes, pose);
                usedModels.add(pose.modelId());
                usedStates.add(pose.poseStateSha256());
                usedTextures.add(pose.textureResourceId());
            }
            Set<String> fallbackChoices = new HashSet<>();
            for (Pose pose : fallbacks) {
                if (!fallbackChoices.add(pose.choiceKey())) {
                    throw new IllegalArgumentException("duplicate choice fallback");
                }
                verifyPoseReferences(pose, models, states, textures);
                verifyStableRoute(routes, pose);
                usedModels.add(pose.modelId());
                usedStates.add(pose.poseStateSha256());
                usedTextures.add(pose.textureResourceId());
            }
            if (!fallbackChoices.containsAll(choices)
                    || !usedModels.equals(models.keySet())
                    || !usedStates.equals(states.keySet())
                    || !usedTextures.equals(textures.keySet())) {
                throw new IllegalArgumentException("format-2 export closure is incomplete");
            }
        }

        private static void verifyPoseReferences(
                Pose pose,
                Map<String, ModelResource> models,
                Map<String, PoseStateBlob> states,
                Map<String, Texture> textures
        ) {
            ModelResource model = models.get(pose.modelId());
            PoseStateBlob state = states.get(pose.poseStateSha256());
            Texture texture = textures.get(pose.textureResourceId());
            if (model == null || state == null || texture == null
                    || state.tintArgb() != pose.tintArgb()) {
                throw new IllegalArgumentException("pose references absent format-2 closure");
            }
        }

        private static void verifyCodeSources(List<CodeSource> values, CodeSource exporter) {
            Set<String> ids = new HashSet<>();
            for (CodeSource value : values) {
                if (!ids.add(value.modId())) {
                    throw new IllegalArgumentException("duplicate code-source mod ID");
                }
            }
            if (!values.contains(exporter) || !ids.contains("cobblemon")
                    || !ids.contains("cobblemonstonestatues")) {
                throw new IllegalArgumentException("required code-source closure is absent");
            }
        }

        private static void verifyMapKeys(
                Map<String, ModelResource> models,
                Map<String, PoseStateBlob> states,
                Map<String, Texture> textures
        ) {
            models.forEach((key, value) -> {
                if (!key.equals(value.modelId())) {
                    throw new IllegalArgumentException("model map key differs from identity");
                }
            });
            states.forEach((key, value) -> {
                if (!key.equals(value.sha256())) {
                    throw new IllegalArgumentException("pose-state map key differs from identity");
                }
            });
            textures.forEach((key, value) -> {
                if (!key.equals(value.resourceId())) {
                    throw new IllegalArgumentException("texture map key differs from identity");
                }
            });
        }

        private static void verifyWinningPacks(
                Map<String, ModelResource> models,
                Map<String, Texture> textures,
                List<ResourcePack> resourcePacks
        ) {
            Set<String> packs = resourcePacks.stream()
                    .map(ResourcePack::packId).collect(java.util.stream.Collectors.toSet());
            if (models.values().stream().anyMatch(model ->
                    !packs.contains(model.winningPackId()))
                    || textures.values().stream().anyMatch(texture ->
                    !packs.contains(texture.winningPackId()))) {
                throw new IllegalArgumentException("winning source pack is absent from ledger");
            }
        }

        private static void verifyResourceBudgets(
                Map<String, ModelResource> models,
                Map<String, Texture> textures
        ) {
            long resourceBytes = 0L;
            long structureUnits = 0L;
            for (ModelResource model : models.values()) {
                resourceBytes = Math.addExact(resourceBytes, model.size());
                structureUnits = Math.addExact(structureUnits, model.boneCount());
                structureUnits = Math.addExact(structureUnits, model.cubeCount());
                structureUnits = Math.addExact(structureUnits, model.locatorCount());
                structureUnits = Math.addExact(
                        structureUnits, model.topologyNodeCount()
                );
            }
            for (Texture texture : textures.values()) {
                resourceBytes = Math.addExact(resourceBytes, texture.size());
            }
            if (resourceBytes > FormatTwoBudgets.MAX_ACTIVE_RESOURCE_BYTES
                    || structureUnits
                    > FormatTwoBudgets.MAX_TOTAL_MODEL_STRUCTURE_UNITS) {
                throw new IllegalArgumentException(
                        "export resource closure exceeds exact-profile budget"
                );
            }
        }

        static MaterialBudget verifyMaterialBudgets(
                List<Pose> profiles,
                List<Pose> fallbacks,
                Map<String, Texture> textures
        ) {
            Set<TextureTint> requests = new LinkedHashSet<>();
            for (Pose pose : concat(profiles, fallbacks)) {
                requests.add(new TextureTint(pose.textureResourceId(), pose.tintArgb()));
            }
            int variants = Math.multiplyExact(
                    requests.size(), FormatTwoBudgets.MATERIAL_VARIANTS_PER_ROUTE
            );
            if (variants > FormatTwoBudgets.MAX_MATERIAL_VARIANTS) {
                throw new IllegalArgumentException("export material routes exceed budget");
            }
            long pixels = 0L;
            for (TextureTint request : requests) {
                Texture texture = Objects.requireNonNull(
                        textures.get(request.resourceId()), "material source texture"
                );
                long sourcePixels = Math.multiplyExact(
                        (long) texture.width(), (long) texture.height()
                );
                pixels = Math.addExact(pixels, Math.multiplyExact(
                        sourcePixels, FormatTwoBudgets.MATERIAL_VARIANTS_PER_ROUTE
                ));
            }
            if (pixels > FormatTwoBudgets.MAX_MATERIAL_VARIANT_PIXELS) {
                throw new IllegalArgumentException("export material pixels exceed budget");
            }
            return new MaterialBudget(variants, pixels);
        }

        private static void verifyStableRoute(
                Map<String, RouteIdentity> routes, Pose pose
        ) {
            RouteIdentity route = RouteIdentity.from(pose);
            RouteIdentity prior = routes.putIfAbsent(pose.choiceKey(), route);
            if (prior != null && !prior.equals(route)) {
                throw new IllegalArgumentException("choice resolver route varies by requested pose");
            }
        }

        private static void verifyResourcePacks(List<ResourcePack> values) {
            Set<String> ids = new HashSet<>();
            for (int index = 0; index < values.size(); index++) {
                ResourcePack value = values.get(index);
                if (value.order() != index || !ids.add(value.packId())) {
                    throw new IllegalArgumentException("noncanonical resource-pack ledger");
                }
            }
        }

        private static long estimatedManifestBytes(
                List<Pose> profiles,
                List<Pose> fallbacks,
                Map<String, ModelResource> models,
                Map<String, PoseStateBlob> states,
                Map<String, Texture> textures,
                List<CodeSource> codeSources,
                List<ResourcePack> resourcePacks
        ) {
            long bytes = 16_384L;
            for (Pose pose : concat(profiles, fallbacks)) {
                bytes = addEstimate(bytes, 1_536L, pose.species(), pose.form(),
                        pose.aspects(), pose.requestedPose(), nullable(pose.effectivePose()),
                        pose.geometryResolution(), pose.textureResolution(), pose.modelId(),
                        pose.poserId(), pose.textureResourceId(), pose.poseStateSha256(),
                        pose.verificationQuadMultisetSha256());
            }
            for (ModelResource model : models.values()) {
                bytes = addEstimate(bytes, 1_536L, model.modelId(), model.resourceId(),
                        resourcePath(model.resourceId()), model.winningPackId(), model.sha256(),
                        model.formatVersion(), model.geometryIdentifier(),
                        model.topologySha256());
            }
            for (PoseStateBlob state : states.values()) {
                bytes = addEstimate(bytes, 768L, state.sha256(), state.selectedRootPath());
            }
            for (Texture texture : textures.values()) {
                bytes = addEstimate(bytes, 1_024L, texture.resourceId(),
                        resourcePath(texture.resourceId()), texture.winningPackId(),
                        texture.sha256());
            }
            for (CodeSource source : codeSources) {
                bytes = addEstimate(bytes, 1_024L, source.modId(), source.fileName(),
                        source.sha256());
            }
            for (ResourcePack pack : resourcePacks) {
                bytes = addEstimate(bytes, 1_024L, pack.packId(), pack.fingerprint());
            }
            return bytes;
        }

        private static List<Pose> concat(List<Pose> first, List<Pose> second) {
            List<Pose> result = new ArrayList<>(Math.addExact(first.size(), second.size()));
            result.addAll(first);
            result.addAll(second);
            return result;
        }

        private static long addEstimate(long current, long fixed, String... values) {
            long result = Math.addExact(current, fixed);
            for (String value : values) {
                // Six bytes per UTF-16 code unit safely covers JSON escaping and UTF-8.
                result = Math.addExact(result, Math.multiplyExact(6L, value.length()));
            }
            return result;
        }

        private record TextureTint(String resourceId, int tintArgb) {
        }

        record MaterialBudget(int variants, long pixels) {
        }
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        Map<String, T> sorted = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    private static Comparator<Pose> poseOrder() {
        return Comparator.comparing(Pose::species).thenComparing(Pose::form)
                .thenComparing(Pose::aspects).thenComparingInt(Pose::poseIndex)
                .thenComparing(Pose::requestedPose);
    }

    private static List<String> boundsRow(Bounds bounds) {
        return List.of(
                coordinate(bounds.minimum().x()), coordinate(bounds.minimum().y()),
                coordinate(bounds.minimum().z()), coordinate(bounds.maximum().x()),
                coordinate(bounds.maximum().y()), coordinate(bounds.maximum().z())
        );
    }

    private static JsonArray vector(Vec3 value) {
        JsonArray values = new JsonArray();
        values.add(value.x());
        values.add(value.y());
        values.add(value.z());
        return values;
    }

    private static JsonArray array(List<JsonObject> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static String coordinate(float value) {
        return Float.toHexString(value);
    }

    private static void requireOrderedBounds(Bounds bounds) {
        Vec3 minimum = bounds.minimum();
        Vec3 maximum = bounds.maximum();
        if (minimum == null || maximum == null
                || minimum.x() > maximum.x() || minimum.y() > maximum.y()
                || minimum.z() > maximum.z()) {
            throw new IllegalArgumentException("invalid verification mesh bounds");
        }
    }

    static String resourcePath(String resourceId) {
        int separator = resourceId.indexOf(':');
        if (separator < 1) {
            throw new IllegalArgumentException("invalid resource id");
        }
        return "assets/" + resourceId.substring(0, separator) + '/'
                + resourceId.substring(separator + 1);
    }

    private static void requireText(String value, String label) {
        if (value == null || value.length() > MAX_TEXT_LENGTH
                || value.indexOf('\0') >= 0 || value.indexOf('\u001f') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("invalid " + label + " metadata");
        }
    }

    private static void requireNullableNonblankText(String value, String label) {
        if (value != null) {
            requireText(value, label);
            if (value.isBlank()) {
                throw new IllegalArgumentException("blank " + label + " metadata");
            }
        }
    }

    private static void requireResourceId(String value, String label) {
        if (value == null || value.length() > MAX_TEXT_LENGTH
                || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || value.contains("..")) {
            throw new IllegalArgumentException("invalid " + label + " resource ID");
        }
    }

    private static void requireSha(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid " + label + " SHA-256");
        }
    }

    private static void requireResolution(String value, String label) {
        if (!"DIRECT".equals(value) && !"SUBSTITUTE".equals(value)) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    private record RouteIdentity(
            int baseScaleBits,
            String geometryResolution,
            String textureResolution,
            String modelId,
            String poserId,
            String textureResourceId
    ) {
        private static RouteIdentity from(Pose pose) {
            return new RouteIdentity(
                    Float.floatToRawIntBits(pose.baseScale()),
                    pose.geometryResolution(), pose.textureResolution(),
                    pose.modelId(), pose.poserId(), pose.textureResourceId()
            );
        }
    }
}
