/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import com.cobblemon.mod.common.CobblemonBuildDetails;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import com.jorgaomc.cobblemonstonestatues.client.render.DynamicStoneTextureCache;
import com.jorgaomc.cobblemonstonestatues.compat.CobblemonStoneBridgeImpl;
import com.jorgaomc.cobblemonstonestatues.data.PokemonChoice;
import com.jorgaomc.cobblemonstonestatues.data.StatueMaterial;
import com.jorgaomc.cobblemonstonestatues.data.StatueScale;
import com.jorgaomc.cobblemonstonestatues.data.StatueSelection;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BakedGeometryTopology;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BedrockGeometry;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BedrockGeometryParser;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.CleanRoomGeometryCompiler;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.GeometryTopologyDigest;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Bounds;
import io.github.janguenter.stoneposeexporter.ExportRecords.Catalog;
import io.github.janguenter.stoneposeexporter.ExportRecords.CodeSource;
import io.github.janguenter.stoneposeexporter.ExportRecords.ModelResource;
import io.github.janguenter.stoneposeexporter.ExportRecords.Pose;
import io.github.janguenter.stoneposeexporter.ExportRecords.Texture;
import io.github.janguenter.stoneposeexporter.EnvironmentSnapshotJob.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.loading.FMLLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded render-thread state machine over Stone's exact public client bridge. */
final class StonePoseExportEngine {

    private static final String STONE_SHA =
            "0fece8e5a988b660f2b608a316f88e2572290835fec1acf67d78329133e92f09";
    private static final long STONE_SIZE = 170_977L;
    private static final String COBBLEMON_SHA =
            "962d75df4fb649d94863a7a7d130d4d2b3de4da9b3cae4c44b1ce90f37ec0ed5";
    private static final long COBBLEMON_SIZE = 128_748_941L;
    private static final String COBBLEMON_COMMIT =
            "37264aff0f6c90c9f9a79bbdfc712bd11929a38c";
    private static final String SENTINEL_POSE = "__atmons_impossible_pose__";
    private static final String AMBIGUOUS_FALLBACK_POSE =
            "__bridge_fallback_output_ambiguous__";
    private static final int LIGHT_SENTINEL = LightTexture.pack(7, 11);
    private static final int OVERLAY_SENTINEL = OverlayTexture.NO_OVERLAY;
    private static final int MAX_CHOICES = 4_096;
    private static final int EXPECTED_LIVE_SPECIES = 1_027;
    private static final int MAX_POSES_PER_CHOICE = 256;
    private static final int MAX_WORK_ITEMS = 50_000;
    private static final int MAX_ITEMS_PER_TICK = 4;
    private static final long SNAPSHOT_BYTES_PER_TICK = 4L * 1024L * 1024L;

    private final Minecraft minecraft;
    private final long generation;
    private final long modelBindingGeneration;
    private final List<PokemonChoice> choices;
    private final Deque<WorkItem> work = new ArrayDeque<>();
    private final List<Pose> profiles = new ArrayList<>();
    private final List<Pose> fallbacks = new ArrayList<>();
    private final PoseStateStore poseStates = new PoseStateStore();
    private final Map<String, ResolvedModelResource> models = new LinkedHashMap<>();
    private final Map<String, Texture> textures = new LinkedHashMap<>();
    private final ResourceAdmissionBudget resourceAdmission =
            new ResourceAdmissionBudget();
    private final Set<String> choiceKeys = new HashSet<>();
    private EnvironmentSnapshotJob snapshotJob;
    private Snapshot startSnapshot;
    private String startCensusFingerprint;
    private Phase phase = Phase.START_SNAPSHOT;
    private int completed;

    StonePoseExportEngine(Minecraft minecraft, long generation) throws IOException {
        this.minecraft = minecraft;
        this.generation = generation;
        requireRenderThreadAndConnection();
        verifyEnvironment();
        modelBindingGeneration = ModelResourceBindings.generation();
        choices = enumerateChoices();
        // Clear once before the guarded start snapshot so every dynamic Stone texture used by
        // this export is rebuilt from the same source closure. Never clear at export finish.
        DynamicStoneTextureCache.INSTANCE.clear();
        snapshotJob = new EnvironmentSnapshotJob(minecraft);
    }

    Progress advance() throws IOException {
        requireRenderThreadAndConnection();
        requireBindingGeneration();
        if (phase == Phase.START_SNAPSHOT) {
            if (!snapshotJob.advance(SNAPSHOT_BYTES_PER_TICK)) {
                return progress(false, null);
            }
            startSnapshot = snapshotJob.result();
            verifyRequiredSources(startSnapshot.codeSources());
            snapshotJob.close();
            snapshotJob = null;
            phase = Phase.CAPTURE;
        }
        for (int item = 0; item < MAX_ITEMS_PER_TICK && !work.isEmpty(); item++) {
            capture(work.removeFirst());
            completed++;
        }
        if (!work.isEmpty()) {
            return progress(false, null);
        }
        if (phase == Phase.CAPTURE) {
            snapshotJob = new EnvironmentSnapshotJob(minecraft);
            phase = Phase.END_SNAPSHOT;
        }
        if (!snapshotJob.advance(SNAPSHOT_BYTES_PER_TICK)) {
            return progress(false, null);
        }
        Snapshot endSnapshot = snapshotJob.result();
        snapshotJob.close();
        snapshotJob = null;
        if (!startSnapshot.equals(endSnapshot)
                || PokemonSpecies.count() != EXPECTED_LIVE_SPECIES
                || !startCensusFingerprint.equals(censusFingerprint(
                CobblemonStoneBridgeImpl.INSTANCE.getAllPokemonChoices()
                ))) {
            throw new IllegalArgumentException("export environment changed before finish");
        }
        requireBindingGeneration();
        Catalog catalog = finishCatalog();
        phase = Phase.COMPLETE;
        return progress(true, catalog);
    }

    void cancel() {
        if (snapshotJob != null) {
            try {
                snapshotJob.close();
            } catch (IOException ignored) {
                // Cancellation remains fail-closed; no output is published.
            }
            snapshotJob = null;
        }
        phase = Phase.COMPLETE;
    }

    private Progress progress(boolean complete, Catalog catalog) {
        return new Progress(complete, completed, completed + work.size(), catalog);
    }

    private List<PokemonChoice> enumerateChoices() {
        List<PokemonChoice> bridgeChoices = new ArrayList<>(
                CobblemonStoneBridgeImpl.INSTANCE.getAllPokemonChoices()
        );
        if (bridgeChoices.isEmpty() || bridgeChoices.size() > MAX_CHOICES
                || PokemonSpecies.count() != EXPECTED_LIVE_SPECIES) {
            throw new IllegalArgumentException("Stone GUI choice census outside budget");
        }
        List<PokemonChoice> sorted = bridgeChoices.stream().sorted(
                Comparator.comparing(PokemonChoice::speciesId)
                        .thenComparing(PokemonChoice::formId)
                        .thenComparing(choice -> normalizeAspects(choice.aspectsCsv()))
        ).toList();
        Set<String> representedSpecies = new HashSet<>();
        List<List<String>> censusRows = new ArrayList<>();
        for (PokemonChoice choice : sorted) {
            String aspects = normalizeAspects(choice.aspectsCsv());
            String key = choice.speciesId() + '\u001f' + choice.formId() + '\u001f' + aspects;
            if (!choiceKeys.add(key)) {
                throw new IllegalArgumentException("duplicate canonical Stone GUI choice");
            }
            Species species = PokemonSpecies.getByIdentifier(
                    ResourceLocation.parse(choice.speciesId())
            );
            if (species == null) {
                throw new IllegalArgumentException("Stone GUI choice lacks live species");
            }
            representedSpecies.add(species.getResourceIdentifier().toString());
            FormData expectedForm = choice.formId().isBlank()
                    ? species.getForm(parseAspects(aspects))
                    : species.getFormByName(choice.formId());
            if (expectedForm == null || !expectedForm.getName().equals(choice.formId())) {
                throw new IllegalArgumentException("Stone GUI choice/form correlation failed");
            }
            List<String> poses = CobblemonStoneBridgeImpl.INSTANCE
                    .getAnimationOrPoseNames(choice.speciesId(), aspects);
            PoseNameCensus.requireValid(
                    poses, MAX_POSES_PER_CHOICE,
                    Set.of(SENTINEL_POSE, AMBIGUOUS_FALLBACK_POSE),
                    "Stone pose-name census is incomplete"
            );
            censusRows.add(List.of("choice", choice.speciesId(), choice.formId(), aspects));
            for (int index = 0; index < poses.size(); index++) {
                work.addLast(new WorkItem(choice, aspects, poses.get(index), index));
                censusRows.add(List.of(
                        "pose", choice.speciesId(), choice.formId(), aspects,
                        Integer.toString(index), poses.get(index)
                ));
            }
            work.addLast(new WorkItem(choice, aspects, SENTINEL_POSE, -1));
            if (work.size() > MAX_WORK_ITEMS) {
                throw new IllegalArgumentException("Stone pose export exceeds work budget");
            }
        }
        if (representedSpecies.size() != EXPECTED_LIVE_SPECIES) {
            throw new IllegalArgumentException("Stone GUI choices omit live species");
        }
        startCensusFingerprint = CatalogRoots.root("live-stone-census", censusRows);
        return List.copyOf(sorted);
    }

    private void capture(WorkItem item) throws IOException {
        try {
            captureUnchecked(item);
        } catch (IOException exception) {
            throw new IOException(item.diagnostic() + " failed", exception);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    item.diagnostic() + " failed: " + exception.getMessage(), exception
            );
        }
    }

    private void captureUnchecked(WorkItem item) throws IOException {
        Resolution resolution = resolve(item);
        Texture texture = requireTexture(resolution.baseTexture());
        if (!texture.resourceId().equals(resolution.baseTexture().toString())) {
            throw new IllegalArgumentException("texture closure identity mismatch");
        }
        ResolvedModelResource modelResource = requireModelResource(resolution);
        ResourceLocation dynamicTexture = DynamicStoneTextureCache.INSTANCE
                .getOrCreate(resolution.baseTexture(), StatueMaterial.STONE);
        RenderedCapture rendered = render(item, resolution, dynamicTexture);
        CapturedMesh first = rendered.mesh();
        QuadMultisetDigest.Result quadIdentity = first.multisetDigest();
        Bounds bounds = first.bounds();
        BakedGeometryTopology.BoundGeometry bound =
                modelResource.topology.bind(rendered.poseState());
        CleanRoomGeometryCompiler.Compilation replay = CleanRoomGeometryCompiler.compile(
                bound, rendered.poseState().tintArgb()
        );
        if (!quadIdentity.equals(replay.identity())) {
            throw new IllegalArgumentException(
                    "clean-room GEO replay differs from Stone bridge: bridge="
                            + quadIdentity + ", replay=" + replay.identity()
            );
        }
        String poseStateSha256 = poseStates.retain(rendered.poseState());
        String effective = rendered.effectivePose();
        Pose pose = new Pose(
                item.choice().speciesId(), item.choice().formId(), item.aspects(),
                item.requestedPose(), item.poseIndex(), effective,
                resolution.baseScale(), resolution.geometryResolution().name(),
                resolution.textureResolution().name(), resolution.modelId().toString(),
                resolution.poserId().toString(), resolution.baseTexture().toString(),
                poseStateSha256, quadIdentity.sha256(),
                first.quads().size() + first.droppedDegenerateQuads(),
                first.quads().size(),
                first.droppedDegenerateQuads(), bounds, first.tintArgb()
        );
        if (item.poseIndex() < 0) {
            fallbacks.add(pose);
        } else {
            profiles.add(pose);
        }
    }

    private Resolution resolve(WorkItem item) {
        ResourceLocation speciesId = ResourceLocation.parse(item.choice().speciesId());
        Species species = PokemonSpecies.getByIdentifier(speciesId);
        Set<String> aspects = parseAspects(item.aspects());
        FormData form = item.choice().formId().isBlank()
                ? species.getForm(aspects) : species.getFormByName(item.choice().formId());
        FloatingState state = new FloatingState();
        state.setCurrentAspects(new LinkedHashSet<>(aspects));
        VaryingModelRepository repository = VaryingModelRepository.INSTANCE;
        VaryingRenderableResolver resolver = repository.getVariations().get(speciesId);
        ResourceLocation fallbackId = repository.getFallback();
        VaryingRenderableResolver fallback = repository.getVariations().get(fallbackId);
        if (fallback == null) {
            throw new IllegalArgumentException("missing exact substitute resolver");
        }

        ResourceLocation model;
        ResourceLocation poser;
        VaryingRenderableResolver geometryResolver;
        PosableModel geometryModel;
        ResolutionKind geometryResolution;
        if (resolver == null) {
            geometryResolution = ResolutionKind.SUBSTITUTE;
            geometryResolver = fallback;
            geometryModel = verifyPoserRoute(fallback, state);
            model = fallback.getResolvedModel(state);
            poser = fallback.getResolvedPoser(state);
        } else {
            PosableModel directPoserModel = tryDirectPoserRoute(resolver, state);
            if (directPoserModel != null) {
                directPoserModel.setDefault();
                ResourceLocation directModel = resolver.getResolvedModel(state);
                ResourceLocation directPoser = resolver.getResolvedPoser(state);
                if (directPoser == null || directModel == null
                        || !repository.getPosers().containsKey(directPoser)
                        || !resolver.getModels().containsKey(directModel)) {
                    throw new IllegalArgumentException(
                            "successful direct poser route has incomplete effective IDs"
                    );
                }
                geometryResolution = ResolutionKind.DIRECT;
                geometryResolver = resolver;
                geometryModel = directPoserModel;
                model = directModel;
                poser = directPoser;
            } else {
                geometryResolution = ResolutionKind.SUBSTITUTE;
                geometryResolver = fallback;
                geometryModel = verifyPoserRoute(fallback, state);
                model = fallback.getResolvedModel(state);
                poser = fallback.getResolvedPoser(state);
            }
        }
        if (model == null || poser == null || !repository.getPosers().containsKey(poser)
                || !fallbackOrDirectModels(
                geometryResolution, resolver, fallback
        ).containsKey(model)) {
            throw new IllegalArgumentException("effective geometry resolver is incomplete");
        }

        ResourceLocation texture;
        ResolutionKind textureResolution;
        if (resolver == null) {
            texture = fallback.getTexture(state);
            textureResolution = ResolutionKind.SUBSTITUTE;
        } else {
            try {
                texture = resolver.getTexture(state);
                textureResolution = ResolutionKind.DIRECT;
            } catch (IllegalStateException exception) {
                texture = fallback.getTexture(state);
                textureResolution = ResolutionKind.SUBSTITUTE;
            }
        }
        if (texture == null) {
            throw new IllegalArgumentException("effective texture resolver is incomplete");
        }
        StatueSelection selection = selection(item);
        ResourceLocation bridgeTexture = CobblemonStoneBridgeImpl.INSTANCE
                .getBaseTexture(selection);
        if (!texture.equals(bridgeTexture)) {
            throw new IllegalArgumentException("Stone bridge texture resolver drift");
        }
        return new Resolution(
                geometryResolver, geometryModel, speciesId, Set.copyOf(aspects),
                model, poser, bridgeTexture, form.getBaseScale(),
                geometryResolution, textureResolution
        );
    }

    private static PosableModel tryDirectPoserRoute(
            VaryingRenderableResolver resolver, FloatingState state
    ) {
        try {
            return resolver.getPoser(state);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private static PosableModel verifyPoserRoute(
            VaryingRenderableResolver resolver, FloatingState state
    ) {
        PosableModel model = resolver.getPoser(state);
        if (model == null) {
            throw new IllegalStateException("effective poser route returned null");
        }
        model.setDefault();
        return model;
    }

    private static Map<ResourceLocation, ?> fallbackOrDirectModels(
            ResolutionKind resolution,
            VaryingRenderableResolver direct,
            VaryingRenderableResolver fallback
    ) {
        return (resolution == ResolutionKind.DIRECT ? direct : fallback).getModels();
    }

    private RenderedCapture render(
            WorkItem item, Resolution resolution, ResourceLocation dynamicTexture
    ) {
        RenderType expected = RenderType.entityCutout(dynamicTexture);
        CaptureBuffer buffer = new CaptureBuffer(expected, LIGHT_SENTINEL, OVERLAY_SENTINEL);
        PoseStack stack = new PoseStack();
        PoseCaptureHook.Request request = new PoseCaptureHook.Request(
                resolution.geometryModel(), resolution.geometryResolver(),
                resolution.modelId(), resolution.poserId(), resolution.speciesId(),
                resolution.aspects(), dynamicTexture, resolution.baseScale(),
                stack, buffer, LIGHT_SENTINEL, OVERLAY_SENTINEL
        );
        PoseCaptureHook.Observation observation = PoseCaptureHook.capture(request, () ->
                CobblemonStoneBridgeImpl.INSTANCE.renderStonePokemon(
                        selection(item), dynamicTexture, stack, buffer,
                        LIGHT_SENTINEL, OVERLAY_SENTINEL
                ));
        CapturedMesh result = buffer.finish();
        PoseState poseState = observation.finish(result.tintArgb());
        AbstractTexture registered = minecraft.getTextureManager().getTexture(
                dynamicTexture
        );
        if (!(registered instanceof DynamicTexture)) {
            throw new IllegalArgumentException("Stone dynamic texture is not registered");
        }
        return new RenderedCapture(result, poseState, observation.effectivePose());
    }

    private Texture requireTexture(ResourceLocation baseTexture) throws IOException {
        try {
            return textures.computeIfAbsent(baseTexture.toString(), ignored -> {
                try {
                    return captureTexture(baseTexture);
                } catch (IOException exception) {
                    throw new ExportIoRuntimeException(exception);
                }
            });
        } catch (ExportIoRuntimeException exception) {
            throw exception.getCause();
        }
    }

    private ResolvedModelResource requireModelResource(Resolution resolution)
            throws IOException {
        Bone wrapper = resolution.geometryResolver().getModels().get(resolution.modelId());
        if (wrapper == null) {
            throw new IllegalArgumentException("effective baked model wrapper is absent");
        }
        requireBindingGeneration();
        ModelResourceBindings.Binding binding = ModelResourceBindings.requireWinner(
                resolution.modelId(), wrapper
        );
        String key = resolution.modelId().toString();
        ResolvedModelResource cached = models.get(key);
        if (cached != null) {
            if (!cached.binding.equals(binding) || cached.wrapper != wrapper) {
                throw new IllegalArgumentException("winning GEO binding changed during export");
            }
            return cached;
        }

        Resource resource = minecraft.getResourceManager().getResource(binding.physicalId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "winning GEO resource is absent"
                ));
        if (!binding.packId().equals(resource.sourcePackId())) {
            throw new IllegalArgumentException("winning GEO source pack changed after reload");
        }
        resourceAdmission.requireResourceCapacity(Math.toIntExact(binding.size()));
        byte[] raw;
        try (InputStream input = resource.open()) {
            raw = input.readNBytes(BedrockGeometryParser.MAX_BYTES + 1);
        }
        if (raw.length < 2 || raw.length > BedrockGeometryParser.MAX_BYTES
                || binding.size() != raw.length
                || !binding.sha256().equals(Hashing.sha256(raw))) {
            throw new IllegalArgumentException("winning GEO bytes differ from baked bytes");
        }
        requireBindingGeneration();
        BedrockGeometry geometry = BedrockGeometryParser.parse(raw);
        BakedGeometryTopology topology = BakedGeometryTopology.bake(geometry);
        String topologySha256 = GeometryTopologyDigest.sha256(topology);
        ModelResource metadata = new ModelResource(
                key, binding.physicalId().toString(), binding.packId(), raw.length,
                binding.sha256(), geometry.formatVersion(), geometry.identifier(),
                geometry.textureWidth(), geometry.textureHeight(), topologySha256,
                topology.nodes().size(), topology.sourceBoneCount(),
                topology.sourceCubeCount(), topology.sourceLocatorCount()
        );
        resourceAdmission.admitModel(
                raw.length, topology.sourceBoneCount(), topology.sourceCubeCount(),
                topology.sourceLocatorCount(), topology.nodes().size()
        );
        ResolvedModelResource result = new ResolvedModelResource(
                metadata, topology, binding, wrapper, raw
        );
        if (models.putIfAbsent(key, result) != null) {
            throw new IllegalArgumentException("duplicate winning GEO model identity");
        }
        requireBindingGeneration();
        return result;
    }

    private Texture captureTexture(ResourceLocation texture) throws IOException {
        ResourceManager manager = minecraft.getResourceManager();
        Resource resource = manager.getResource(texture).orElseThrow(() ->
                new IllegalArgumentException("winning source texture is absent"));
        byte[] raw;
        try (InputStream input = resource.open()) {
            raw = input.readNBytes(FormatTwoBudgets.MAX_RESOURCE_BYTES + 1);
        }
        if (raw.length < 1 || raw.length > FormatTwoBudgets.MAX_RESOURCE_BYTES) {
            throw new IllegalArgumentException("winning source texture outside budget");
        }
        int expectedWidth = pngDimension(raw, 16);
        int expectedHeight = pngDimension(raw, 20);
        if (expectedWidth < 1 || expectedWidth > 16_384
                || expectedHeight < 1 || expectedHeight > 16_384
                || (long) expectedWidth * expectedHeight > 16L * 1024L * 1024L) {
            throw new IllegalArgumentException("winning source texture dimensions outside budget");
        }
        resourceAdmission.requireResourceCapacity(raw.length);
        int width;
        int height;
        // NativeImage.read(byte[]) copies the complete PNG onto LWJGL's small
        // thread-local MemoryStack. Exact-profile PNGs can exceed that fixed stack even
        // though both Java heap and the bounded resource closure have ample capacity.
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(raw))) {
            width = image.getWidth();
            height = image.getHeight();
        }
        if (width != expectedWidth || height != expectedHeight) {
            throw new IllegalArgumentException("winning PNG header/decode dimensions differ");
        }
        Texture result = new Texture(
                texture.toString(), resource.sourcePackId(), raw.length,
                Hashing.sha256(raw), width, height
        );
        resourceAdmission.admitTexture(raw.length);
        return result;
    }

    private Catalog finishCatalog() {
        if (profiles.isEmpty() || fallbacks.size() != choices.size()
                || textures.isEmpty() || models.isEmpty() || poseStates.isEmpty()) {
            throw new IllegalArgumentException("export closure is incomplete");
        }
        requireBindingGeneration();
        Map<String, ModelResource> modelRecords = new LinkedHashMap<>();
        for (Map.Entry<String, ResolvedModelResource> entry : models.entrySet()) {
            ResolvedModelResource resource = entry.getValue();
            ModelResourceBindings.Binding current = ModelResourceBindings.requireWinner(
                    ResourceLocation.parse(entry.getKey()), resource.wrapper
            );
            if (!current.equals(resource.binding)
                    || resource.raw.length != resource.metadata.size()
                    || !Hashing.sha256(resource.raw).equals(resource.metadata.sha256())
                    || !GeometryTopologyDigest.sha256(resource.topology)
                    .equals(resource.metadata.topologySha256())) {
                throw new IllegalArgumentException("retained winning GEO closure drifted");
            }
            modelRecords.put(entry.getKey(), resource.metadata);
        }
        requireBindingGeneration();
        return new Catalog(
                profiles.stream().sorted(poseOrder()).toList(),
                fallbacks.stream().sorted(poseOrder()).toList(),
                sortedMap(modelRecords), poseStates.snapshot(), sortedMap(textures),
                startSnapshot.codeSources(), startSnapshot.resourcePacks(), generation,
                modelBindingGeneration,
                startSnapshot.codeSources().stream().filter(source ->
                        StonePoseExporterMod.MOD_ID.equals(source.modId()))
                        .findFirst().orElseThrow()
        );
    }

    void requirePublicationReady() {
        if (phase != Phase.COMPLETE) {
            throw new IllegalStateException("export catalog is not complete");
        }
        requireBindingGeneration();
    }

    private void requireBindingGeneration() {
        if (ModelResourceBindings.generation() != modelBindingGeneration) {
            throw new IllegalStateException("model-resource binding generation changed");
        }
    }

    private static void verifyRequiredSources(List<CodeSource> sources) {
        verifySource(sources, "cobblemonstonestatues", STONE_SIZE, STONE_SHA);
        verifySource(sources, "cobblemon", COBBLEMON_SIZE, COBBLEMON_SHA);
        if (!"1.7.3".equals(CobblemonBuildDetails.VERSION)
                || !COBBLEMON_COMMIT.equals(CobblemonBuildDetails.GIT_COMMIT)) {
            throw new IllegalArgumentException("Cobblemon embedded build identity drift");
        }
        if (sources.stream().noneMatch(source ->
                StonePoseExporterMod.MOD_ID.equals(source.modId()))) {
            throw new IllegalArgumentException("exporter code source is absent");
        }
    }

    private void verifyEnvironment() {
        if (!"1.21.1".equals(net.minecraft.SharedConstants.getCurrentVersion().getName())
                || !"21.1.248".equals(FMLLoader.versionInfo().neoForgeVersion())
                || Runtime.version().feature() != 21) {
            throw new IllegalArgumentException("exporter environment identity mismatch");
        }
    }

    private void requireRenderThreadAndConnection() {
        if (!minecraft.isSameThread() || minecraft.level == null
                || minecraft.player == null || minecraft.getConnection() == null) {
            throw new IllegalStateException("export requires connected render thread");
        }
    }

    private static StatueSelection selection(WorkItem item) {
        return new StatueSelection(
                item.choice().speciesId(), item.choice().formId(), item.aspects(),
                item.requestedPose(), StatueScale.NORMAL, StatueMaterial.STONE, 0, 0L
        );
    }

    private static Comparator<Pose> poseOrder() {
        return Comparator.comparing(Pose::species).thenComparing(Pose::form)
                .thenComparing(Pose::aspects).thenComparingInt(Pose::poseIndex);
    }

    private static <T> Map<String, T> sortedMap(Map<String, T> source) {
        Map<String, T> result = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static Set<String> parseAspects(String aspects) {
        return aspects.isBlank() ? Set.of()
                : new LinkedHashSet<>(List.of(aspects.split(",")));
    }

    private static String normalizeAspects(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(source.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
    }

    private static void verifySource(
            List<CodeSource> sources, String modId, long size, String sha256
    ) {
        CodeSource source = sources.stream().filter(value -> modId.equals(value.modId()))
                .findFirst().orElseThrow();
        if (source.size() != size || !source.sha256().equals(sha256)) {
            throw new IllegalArgumentException("required loaded mod artifact drift");
        }
    }

    private static String censusFingerprint(List<PokemonChoice> source) {
        List<List<String>> rows = new ArrayList<>();
        Set<String> choices = new HashSet<>();
        for (PokemonChoice choice : source.stream().sorted(
                Comparator.comparing(PokemonChoice::speciesId)
                        .thenComparing(PokemonChoice::formId)
                        .thenComparing(value -> normalizeAspects(value.aspectsCsv()))
        ).toList()) {
            String aspects = normalizeAspects(choice.aspectsCsv());
            String choiceKey = choice.speciesId() + '\u001f' + choice.formId()
                    + '\u001f' + aspects;
            if (!choices.add(choiceKey)) {
                throw new IllegalArgumentException("duplicate live Stone GUI choice");
            }
            List<String> poses = CobblemonStoneBridgeImpl.INSTANCE
                    .getAnimationOrPoseNames(choice.speciesId(), aspects);
            PoseNameCensus.requireValid(
                    poses, MAX_POSES_PER_CHOICE,
                    Set.of(SENTINEL_POSE, AMBIGUOUS_FALLBACK_POSE),
                    "live Stone pose census changed"
            );
            rows.add(List.of("choice", choice.speciesId(), choice.formId(), aspects));
            for (int index = 0; index < poses.size(); index++) {
                rows.add(List.of(
                        "pose", choice.speciesId(), choice.formId(), aspects,
                        Integer.toString(index), poses.get(index)
                ));
            }
        }
        return CatalogRoots.root("live-stone-census", rows);
    }

    private static int pngDimension(byte[] raw, int offset) {
        byte[] signature = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        if (raw.length < 24 || !java.util.Arrays.equals(
                signature, java.util.Arrays.copyOf(raw, signature.length)
        ) || ByteBuffer.wrap(raw, 8, 8).getInt() != 13
                || raw[12] != 'I' || raw[13] != 'H' || raw[14] != 'D' || raw[15] != 'R') {
            throw new IllegalArgumentException("winning source texture is not canonical PNG");
        }
        return ByteBuffer.wrap(raw, offset, Integer.BYTES).getInt();
    }

    record Progress(boolean complete, int completed, int total, Catalog catalog) {
    }

    private record WorkItem(
            PokemonChoice choice,
            String aspects,
            String requestedPose,
            int poseIndex
    ) {
        String choiceKey() {
            return choice.speciesId() + '\u001f' + choice.formId() + '\u001f' + aspects;
        }

        String diagnostic() {
            return "Stone work item species=" + choice.speciesId()
                    + ", form=" + choice.formId()
                    + ", aspects=" + (aspects.isEmpty() ? "<empty>" : aspects)
                    + ", pose=" + requestedPose + ", poseIndex=" + poseIndex;
        }
    }

    private record Resolution(
            VaryingRenderableResolver geometryResolver,
            PosableModel geometryModel,
            ResourceLocation speciesId,
            Set<String> aspects,
            ResourceLocation modelId,
            ResourceLocation poserId,
            ResourceLocation baseTexture,
            float baseScale,
            ResolutionKind geometryResolution,
            ResolutionKind textureResolution
    ) {
        private Resolution {
            aspects = Set.copyOf(aspects);
        }
    }

    private record RenderedCapture(
            CapturedMesh mesh,
            PoseState poseState,
            String effectivePose
    ) {
    }

    private static final class ResolvedModelResource {

        private final ModelResource metadata;
        private final BakedGeometryTopology topology;
        private final ModelResourceBindings.Binding binding;
        private final Bone wrapper;
        private final byte[] raw;

        private ResolvedModelResource(
                ModelResource metadata,
                BakedGeometryTopology topology,
                ModelResourceBindings.Binding binding,
                Bone wrapper,
                byte[] raw
        ) {
            this.metadata = metadata;
            this.topology = topology;
            this.binding = binding;
            this.wrapper = wrapper;
            this.raw = raw.clone();
        }
    }

    private enum ResolutionKind {
        DIRECT,
        SUBSTITUTE
    }

    private static final class ExportIoRuntimeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ExportIoRuntimeException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }

    private enum Phase {
        START_SNAPSHOT,
        CAPTURE,
        END_SNAPSHOT,
        COMPLETE
    }
}
