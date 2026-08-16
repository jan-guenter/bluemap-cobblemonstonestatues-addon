/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.catalog;

import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseStateCodec;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, fully-attested compact format-2 pose catalog. */
public record PoseCatalog(
        String bundleSha256,
        String catalogId,
        String stoneSha256,
        String cobblemonSha256,
        Map<String, PoseEntry> poses,
        Map<String, PoseEntry> fallbacks,
        Map<String, ModelIdentity> models,
        Map<String, PoseStateIdentity> poseStates,
        Map<String, TextureIdentity> textures,
        Attestation attestation
) {

    public static final String FALLBACK_POSE = "__atmons_impossible_pose__";

    public PoseCatalog {
        requireSha(bundleSha256, "bundleSha256");
        requireWire(catalogId, "catalogId");
        requireSha(stoneSha256, "stoneSha256");
        requireSha(cobblemonSha256, "cobblemonSha256");
        poses = immutableMap(poses);
        fallbacks = immutableMap(fallbacks);
        models = immutableMap(models);
        poseStates = immutableMap(poseStates);
        textures = immutableMap(textures);
        Objects.requireNonNull(attestation, "attestation");
        if (fallbacks.isEmpty() || models.isEmpty()
                || poseStates.isEmpty() || textures.isEmpty()) {
            throw new IllegalArgumentException("empty format-2 operator-local catalog");
        }
        verifyClosure(poses, fallbacks, models, poseStates, textures, attestation);
    }

    public PoseEntry resolve(StatueSelection selection) {
        PoseEntry exact = poses.get(selection.poseKey());
        return exact != null ? exact : fallbacks.get(choiceKey(selection));
    }

    private static String choiceKey(StatueSelection selection) {
        return selection.speciesId() + '\u001f' + selection.formId()
                + '\u001f' + selection.aspectsCsv();
    }

    public record PoseEntry(
            String speciesId,
            String formId,
            String aspectsCsv,
            String pose,
            int poseIndex,
            String effectivePose,
            float baseScale,
            ResolutionKind geometryResolution,
            ResolutionKind textureResolution,
            String modelId,
            String poserId,
            String textureResourceId,
            String poseStateSha256,
            QuadMultisetDigest.Result verification
    ) {
        public PoseEntry {
            requireResourceId(speciesId, "species id");
            requireSelectionText(formId, "form id");
            requireSelectionText(aspectsCsv, "aspects");
            requireSelectionText(pose, "pose");
            requireNullableNonblankText(effectivePose, "effective pose");
            Objects.requireNonNull(geometryResolution, "geometryResolution");
            Objects.requireNonNull(textureResolution, "textureResolution");
            requireResourceId(modelId, "model id");
            requireResourceId(poserId, "poser id");
            requireResourceId(textureResourceId, "texture resource id");
            requireSha(poseStateSha256, "pose-state sha256");
            Objects.requireNonNull(verification, "verification");
            if (pose.isBlank() || !Float.isFinite(baseScale)
                    || baseScale <= 0F || baseScale > 64F
                    || poseIndex < -1 || poseIndex > 50_000
                    || (poseIndex == -1) != FALLBACK_POSE.equals(pose)) {
                throw new IllegalArgumentException("invalid format-2 pose metadata");
            }
            if (!StatueSelection.normalizeAspects(aspectsCsv).equals(aspectsCsv)) {
                throw new IllegalArgumentException("aspects key is not canonical");
            }
        }

        public String key() {
            return choiceKey() + '\u001f' + pose;
        }

        public String choiceKey() {
            return speciesId + '\u001f' + formId + '\u001f' + aspectsCsv;
        }

        public int tintArgb() {
            return verification.tintArgb();
        }
    }

    public enum ResolutionKind {
        DIRECT,
        SUBSTITUTE
    }

    public record ModelIdentity(
            String modelId,
            String resourceId,
            String resourcePath,
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
        public ModelIdentity {
            requireResourceId(modelId, "model id");
            requireResourceId(resourceId, "model resource id");
            requireResource(resourcePath, ".geo.json");
            requirePackId(winningPackId);
            requireSelectionText(formatVersion, "GEO format version");
            requireSelectionText(geometryIdentifier, "GEO identifier");
            requireSha(sha256, "model sha256");
            requireSha(topologySha256, "topology sha256");
            if (!PoseCatalog.resourcePath(resourceId).equals(resourcePath)
                    || !resourceId.endsWith(".geo.json")
                    || !Set.of("1.12.0", "1.21.0").contains(formatVersion)
                    || geometryIdentifier.isBlank()
                    || size < 2 || size > FormatTwoBudgets.MAX_RESOURCE_BYTES
                    || textureWidth < 1 || textureWidth > 16_384
                    || textureHeight < 1 || textureHeight > 16_384
                    || topologyNodeCount < 1
                    || topologyNodeCount > FormatTwoBudgets.MAX_MODEL_TOPOLOGY_NODES
                    || boneCount < 1 || boneCount > FormatTwoBudgets.MAX_MODEL_BONES
                    || cubeCount < 0 || cubeCount > FormatTwoBudgets.MAX_MODEL_CUBES
                    || locatorCount < 0
                    || locatorCount > FormatTwoBudgets.MAX_MODEL_LOCATORS) {
                throw new IllegalArgumentException("model identity outside format-2 budget");
            }
        }
    }

    public record PoseStateIdentity(
            String sha256,
            int size,
            String selectedRootPath,
            int nodeCount,
            int tintArgb,
            byte[] raw,
            PoseState state
    ) {
        public PoseStateIdentity {
            requireSha(sha256, "pose-state sha256");
            requireSelectionText(selectedRootPath, "selected root path");
            Objects.requireNonNull(raw, "raw");
            Objects.requireNonNull(state, "state");
            if (size < 1 || size > PoseStateCodec.MAX_BYTES || raw.length != size
                    || !sha256.equals(PoseStateCodec.sha256(raw))
                    || !java.util.Arrays.equals(raw, PoseStateCodec.encode(state))
                    || !selectedRootPath.equals(state.selectedRootPath())
                    || nodeCount != state.nodes().size() || tintArgb != state.tintArgb()) {
                throw new IllegalArgumentException("pose-state identity differs from bytes");
            }
            raw = raw.clone();
        }

        public static PoseStateIdentity from(
                String sha256,
                int size,
                String selectedRootPath,
                int nodeCount,
                int tintArgb,
                byte[] raw
        ) {
            return new PoseStateIdentity(
                    sha256, size, selectedRootPath, nodeCount, tintArgb,
                    raw, PoseStateCodec.decode(raw)
            );
        }

        @Override
        public byte[] raw() {
            return raw.clone();
        }
    }

    public record TextureIdentity(
            String resourceId,
            String resourcePath,
            String winningPackId,
            int size,
            String sha256,
            int width,
            int height
    ) {
        public TextureIdentity {
            requireResourceId(resourceId, "texture resource id");
            requireResource(resourcePath, ".png");
            requirePackId(winningPackId);
            requireSha(sha256, "texture sha256");
            if (!PoseCatalog.resourcePath(resourceId).equals(resourcePath)
                    || !resourceId.endsWith(".png")
                    || size < 1 || size > FormatTwoBudgets.MAX_RESOURCE_BYTES
                    || width < 1 || width > 16_384 || height < 1 || height > 16_384
                    || (long) width * height > 16L * 1024L * 1024L) {
                throw new IllegalArgumentException("texture identity outside budget");
            }
        }
    }

    public record Attestation(
            int speciesCount,
            int choiceCount,
            int poseCount,
            int modelCount,
            int poseStateCount,
            int textureCount,
            String speciesRoot,
            String choicesRoot,
            String posesRoot,
            String modelsRoot,
            String poseStatesRoot,
            String texturesRoot,
            long resourceGenerationStart,
            long resourceGenerationEnd,
            long modelBindingGenerationStart,
            long modelBindingGenerationEnd,
            List<CodeSourceIdentity> codeSources,
            List<ResourcePackIdentity> resourcePacks
    ) {
        public Attestation {
            if (speciesCount < 1 || choiceCount < 1 || poseCount < 1
                    || modelCount < 1 || poseStateCount < 1 || textureCount < 1
                    || resourceGenerationStart < 1
                    || resourceGenerationStart != resourceGenerationEnd
                    || modelBindingGenerationStart < 1
                    || modelBindingGenerationStart != modelBindingGenerationEnd) {
                throw new IllegalArgumentException("incomplete format-2 exporter attestation");
            }
            for (String root : List.of(
                    speciesRoot, choicesRoot, posesRoot, modelsRoot,
                    poseStatesRoot, texturesRoot
            )) {
                requireSha(root, "attestation root");
            }
            codeSources = List.copyOf(codeSources);
            resourcePacks = List.copyOf(resourcePacks);
            if (codeSources.isEmpty() || resourcePacks.isEmpty()) {
                throw new IllegalArgumentException("attestation ledger is empty");
            }
        }
    }

    public record CodeSourceIdentity(String modId, String fileName, long size, String sha256) {
        public CodeSourceIdentity {
            if (modId == null || !modId.matches("[a-z0-9][a-z0-9._-]{0,127}")
                    || !safeLedgerBaseName(fileName)
                    || size < 1 || size > 2L * 1024L * 1024L * 1024L) {
                throw new IllegalArgumentException("invalid code source identity");
            }
            requireSha(sha256, "code source sha256");
        }
    }

    public record ResourcePackIdentity(int order, String packId, String fingerprint) {
        public ResourcePackIdentity {
            if (order < 0) {
                throw new IllegalArgumentException("invalid pack order");
            }
            requirePackId(packId);
            requireSha(fingerprint, "pack fingerprint");
        }
    }

    static void requireSha(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    static void requireResourceId(String value, String label) {
        if (value == null || value.length() > 4_096
                || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || value.contains("..")) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    static String resourcePath(String resourceId) {
        requireResourceId(resourceId, "resource id");
        int separator = resourceId.indexOf(':');
        return "assets/" + resourceId.substring(0, separator) + '/'
                + resourceId.substring(separator + 1);
    }

    static void requireResource(String value, String suffix) {
        if (value == null || !value.matches("assets/[a-z0-9_.-]+/[a-z0-9_./-]+")
                || !value.endsWith(suffix) || value.contains("..") || value.startsWith("/")) {
            throw new IllegalArgumentException("invalid external resource path");
        }
    }

    private static void verifyClosure(
            Map<String, PoseEntry> poses,
            Map<String, PoseEntry> fallbacks,
            Map<String, ModelIdentity> models,
            Map<String, PoseStateIdentity> states,
            Map<String, TextureIdentity> textures,
            Attestation attestation
    ) {
        Map<String, Set<Integer>> poseIndices = new HashMap<>();
        Map<String, RouteIdentity> routes = new HashMap<>();
        Set<String> namedChoices = new LinkedHashSet<>();
        Set<String> fallbackChoices = new HashSet<>();
        Set<String> species = new HashSet<>();
        Set<String> usedModels = new HashSet<>();
        Set<String> usedStates = new HashSet<>();
        Set<String> usedTextures = new HashSet<>();
        for (Map.Entry<String, PoseEntry> keyed : poses.entrySet()) {
            PoseEntry pose = keyed.getValue();
            if (!keyed.getKey().equals(pose.key())) {
                throw new IllegalArgumentException("pose map key differs from identity");
            }
            if (!poseIndices.computeIfAbsent(
                    pose.choiceKey(), ignored -> new HashSet<>()
            ).add(pose.poseIndex())) {
                throw new IllegalArgumentException("duplicate profile pose index");
            }
            namedChoices.add(pose.choiceKey());
            retainReferences(pose, models, states, textures,
                    usedModels, usedStates, usedTextures);
            verifyStableRoute(routes, pose);
        }
        poseIndices.values().forEach(indices -> {
            for (int expected = 0; expected < indices.size(); expected++) {
                if (!indices.contains(expected)) {
                    throw new IllegalArgumentException("noncanonical profile pose sequence");
                }
            }
        });
        for (Map.Entry<String, PoseEntry> keyed : fallbacks.entrySet()) {
            PoseEntry pose = keyed.getValue();
            if (!keyed.getKey().equals(pose.choiceKey())
                    || !fallbackChoices.add(pose.choiceKey())) {
                throw new IllegalArgumentException("fallback map key differs from identity");
            }
            species.add(pose.speciesId());
            retainReferences(pose, models, states, textures,
                    usedModels, usedStates, usedTextures);
            verifyStableRoute(routes, pose);
        }
        if (!fallbackChoices.containsAll(namedChoices)
                || !usedModels.equals(models.keySet())
                || !usedStates.equals(states.keySet())
                || !usedTextures.equals(textures.keySet())
                || attestation.speciesCount() != species.size()
                || attestation.choiceCount() != fallbackChoices.size()
                || attestation.poseCount() != poses.size() + fallbacks.size()
                || attestation.modelCount() != models.size()
                || attestation.poseStateCount() != states.size()
                || attestation.textureCount() != textures.size()) {
            throw new IllegalArgumentException("format-2 catalog closure is incomplete");
        }
        long resourceBytes = 0L;
        long structureUnits = 0L;
        for (ModelIdentity model : models.values()) {
            resourceBytes = Math.addExact(resourceBytes, model.size());
            structureUnits = Math.addExact(structureUnits, model.boneCount());
            structureUnits = Math.addExact(structureUnits, model.cubeCount());
            structureUnits = Math.addExact(structureUnits, model.locatorCount());
            structureUnits = Math.addExact(structureUnits, model.topologyNodeCount());
        }
        for (TextureIdentity texture : textures.values()) {
            resourceBytes = Math.addExact(resourceBytes, texture.size());
        }
        if (resourceBytes > FormatTwoBudgets.MAX_ACTIVE_RESOURCE_BYTES
                || structureUnits > FormatTwoBudgets.MAX_TOTAL_MODEL_STRUCTURE_UNITS) {
            throw new IllegalArgumentException("format-2 resource closure exceeds budget");
        }
        Set<TextureTint> materialRequests = new LinkedHashSet<>();
        poses.values().forEach(pose -> materialRequests.add(
                new TextureTint(pose.textureResourceId(), pose.tintArgb())
        ));
        fallbacks.values().forEach(pose -> materialRequests.add(
                new TextureTint(pose.textureResourceId(), pose.tintArgb())
        ));
        int materialVariants = Math.multiplyExact(
                materialRequests.size(), FormatTwoBudgets.MATERIAL_VARIANTS_PER_ROUTE
        );
        long materialPixels = 0L;
        for (TextureTint request : materialRequests) {
            TextureIdentity texture = textures.get(request.resourceId());
            long pixels = Math.multiplyExact(
                    (long) texture.width(), (long) texture.height()
            );
            materialPixels = Math.addExact(materialPixels, Math.multiplyExact(
                    pixels, FormatTwoBudgets.MATERIAL_VARIANTS_PER_ROUTE
            ));
        }
        if (materialVariants > FormatTwoBudgets.MAX_MATERIAL_VARIANTS
                || materialPixels > FormatTwoBudgets.MAX_MATERIAL_VARIANT_PIXELS) {
            throw new IllegalArgumentException("format-2 material closure exceeds budget");
        }
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
        Set<String> packIds = new HashSet<>();
        for (int index = 0; index < attestation.resourcePacks().size(); index++) {
            ResourcePackIdentity pack = attestation.resourcePacks().get(index);
            if (pack.order() != index || !packIds.add(pack.packId())) {
                throw new IllegalArgumentException("noncanonical resource-pack ledger");
            }
        }
        if (models.values().stream().anyMatch(model ->
                !packIds.contains(model.winningPackId()))
                || textures.values().stream().anyMatch(texture ->
                !packIds.contains(texture.winningPackId()))) {
            throw new IllegalArgumentException("winning source pack is absent from ledger");
        }
        Set<String> codeIds = new HashSet<>();
        if (attestation.codeSources().stream().anyMatch(source ->
                !codeIds.add(source.modId()))) {
            throw new IllegalArgumentException("duplicate code-source ledger key");
        }
    }

    private static void retainReferences(
            PoseEntry pose,
            Map<String, ModelIdentity> models,
            Map<String, PoseStateIdentity> states,
            Map<String, TextureIdentity> textures,
            Set<String> usedModels,
            Set<String> usedStates,
            Set<String> usedTextures
    ) {
        PoseStateIdentity state = states.get(pose.poseStateSha256());
        if (!models.containsKey(pose.modelId()) || state == null
                || !textures.containsKey(pose.textureResourceId())
                || state.tintArgb() != pose.tintArgb()) {
            throw new IllegalArgumentException("pose references absent format-2 closure");
        }
        usedModels.add(pose.modelId());
        usedStates.add(pose.poseStateSha256());
        usedTextures.add(pose.textureResourceId());
    }

    private static void verifyStableRoute(Map<String, RouteIdentity> routes, PoseEntry pose) {
        RouteIdentity route = RouteIdentity.from(pose);
        RouteIdentity previous = routes.putIfAbsent(pose.choiceKey(), route);
        if (previous != null && !previous.equals(route)) {
            throw new IllegalArgumentException("choice resolver route varies by requested pose");
        }
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        Objects.requireNonNull(source, "source");
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static void requireSelectionText(String value, String label) {
        if (value == null || value.length() > 4_096 || value.indexOf('\0') >= 0
                || value.indexOf('\u001f') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    private static void requireNullableNonblankText(String value, String label) {
        if (value != null) {
            requireSelectionText(value, label);
            if (value.isBlank()) {
                throw new IllegalArgumentException("invalid " + label);
            }
        }
    }

    private static void requireWire(String value, String label) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    private static void requirePackId(String value) {
        if (!safePackId(value)) {
            throw new IllegalArgumentException("invalid resource-pack identifier");
        }
    }

    static boolean safePackId(String value) {
        if (value == null || value.isBlank() || value.length() > 4_096
                || value.indexOf('\\') >= 0) {
            return false;
        }
        for (String segment : value.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
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

    static boolean safeLedgerBaseName(String value) {
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

    private record RouteIdentity(
            int baseScaleBits,
            ResolutionKind geometryResolution,
            ResolutionKind textureResolution,
            String modelId,
            String poserId,
            String textureResourceId
    ) {
        private static RouteIdentity from(PoseEntry pose) {
            return new RouteIdentity(
                    Float.floatToRawIntBits(pose.baseScale()),
                    pose.geometryResolution(), pose.textureResolution(),
                    pose.modelId(), pose.poserId(), pose.textureResourceId()
            );
        }
    }

    private record TextureTint(String resourceId, int tintArgb) {
    }
}
