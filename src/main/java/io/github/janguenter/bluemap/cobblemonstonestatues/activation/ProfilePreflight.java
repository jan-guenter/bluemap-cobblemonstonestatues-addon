/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.activation;

import de.bluecolored.bluemap.core.util.Key;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ModelIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseEntry;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseStateIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.TextureIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BakedGeometryTopology;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BedrockGeometry;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.BedrockGeometryParser;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.CleanRoomGeometryCompiler;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.GeometryTopologyDigest;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Full immutable resource/topology/replay preflight for one format-2 catalog. */
public final class ProfilePreflight {

    public static final int MAX_SOURCE_QUADS_PER_POSE = 4_096;
    private static final Key PREFLIGHT_TEXTURE =
            Key.parse("bluemap_cobblemonstonestatues:preflight");

    private ProfilePreflight() {
    }

    public static Result verify(
            PoseCatalog catalog,
            Map<String, ResourceBlob> rawModels,
            Map<String, ResourceBlob> rawTextures
    ) {
        Objects.requireNonNull(catalog, "catalog");
        requireExactKeys(rawModels, catalog.models().keySet(), "model byte closure");
        requireExactKeys(rawTextures, catalog.textures().keySet(), "texture byte closure");
        long encodedBytes = 0L;
        for (ResourceBlob blob : rawModels.values()) {
            encodedBytes = Math.addExact(encodedBytes, blob.size());
        }
        for (ResourceBlob blob : rawTextures.values()) {
            encodedBytes = Math.addExact(encodedBytes, blob.size());
        }
        if (encodedBytes > FormatTwoBudgets.MAX_ACTIVE_RESOURCE_BYTES) {
            throw new IllegalArgumentException("active encoded resources exceed budget");
        }

        Map<String, VerifiedModel> models = new LinkedHashMap<>();
        long structureUnits = 0L;
        for (ModelIdentity identity : catalog.models().values()) {
            ResourceBlob blob = Objects.requireNonNull(
                    rawModels.get(identity.modelId()), "model bytes"
            );
            if (blob.size() != identity.size()
                    || !identity.sha256().equals(blob.sha256())) {
                throw new IllegalArgumentException("verified model bytes differ from catalog");
            }
            byte[] raw = read(blob);
            BedrockGeometry geometry = BedrockGeometryParser.parse(raw);
            BakedGeometryTopology topology = BakedGeometryTopology.bake(geometry);
            if (!identity.formatVersion().equals(geometry.formatVersion())
                    || !identity.geometryIdentifier().equals(geometry.identifier())
                    || identity.textureWidth() != geometry.textureWidth()
                    || identity.textureHeight() != geometry.textureHeight()
                    || identity.boneCount() != geometry.bones().size()
                    || identity.cubeCount() != geometry.cubeCount()
                    || identity.locatorCount() != geometry.locatorCount()
                    || identity.topologyNodeCount() != topology.nodes().size()
                    || !identity.topologySha256().equals(
                    GeometryTopologyDigest.sha256(topology)
            )) {
                throw new IllegalArgumentException("model metadata/topology attestation mismatch");
            }
            structureUnits = Math.addExact(structureUnits, geometry.bones().size());
            structureUnits = Math.addExact(structureUnits, geometry.cubeCount());
            structureUnits = Math.addExact(structureUnits, geometry.locatorCount());
            structureUnits = Math.addExact(structureUnits, topology.nodes().size());
            if (structureUnits > FormatTwoBudgets.MAX_TOTAL_MODEL_STRUCTURE_UNITS) {
                throw new IllegalArgumentException("actual model structure exceeds budget");
            }
            models.put(identity.modelId(), new VerifiedModel(identity, topology));
        }

        Map<String, ResourceBlob> textures = new LinkedHashMap<>();
        for (TextureIdentity identity : catalog.textures().values()) {
            ResourceBlob blob = Objects.requireNonNull(
                    rawTextures.get(identity.resourceId()), "texture bytes"
            );
            if (blob.size() != identity.size()
                    || !identity.sha256().equals(blob.sha256())) {
                throw new IllegalArgumentException("verified texture bytes differ from catalog");
            }
            verifyPngHeader(blob, identity);
            textures.put(identity.resourceId(), blob);
        }

        Map<ReplayKey, QuadMultisetDigest.Result> expectations = new LinkedHashMap<>();
        List<PoseEntry> allPoses = new ArrayList<>(
                catalog.poses().size() + catalog.fallbacks().size()
        );
        allPoses.addAll(catalog.poses().values());
        allPoses.addAll(catalog.fallbacks().values());
        for (PoseEntry pose : allPoses) {
            ReplayKey key = ReplayKey.from(pose);
            QuadMultisetDigest.Result previous = expectations.putIfAbsent(
                    key, pose.verification()
            );
            if (previous != null && !previous.equals(pose.verification())) {
                throw new IllegalArgumentException("one replay pair has conflicting identities");
            }
        }
        for (Map.Entry<ReplayKey, QuadMultisetDigest.Result> entry
                : expectations.entrySet()) {
            VerifiedModel model = models.get(entry.getKey().modelId());
            PoseStateIdentity state = catalog.poseStates().get(entry.getKey().poseStateSha256());
            Geometry geometry = compile(model, state, entry.getValue());
            // Exercise every production StatueModel invariant, then discard the preflight mesh.
            geometry.withTexture(PREFLIGHT_TEXTURE);
        }
        return new Result(catalog, models, textures, expectations);
    }

    static Geometry compile(
            VerifiedModel model,
            PoseStateIdentity state,
            QuadMultisetDigest.Result expected
    ) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(expected, "expected");
        if (expected.sourceQuadCount() > MAX_SOURCE_QUADS_PER_POSE
                || expected.tintArgb() != state.tintArgb()) {
            throw new IllegalArgumentException("production replay identity exceeds profile cap");
        }
        BakedGeometryTopology.BoundGeometry bound = model.topology().bind(state.state());
        CleanRoomGeometryCompiler.Compilation compiled =
                CleanRoomGeometryCompiler.compile(bound, state.tintArgb());
        if (!expected.equals(compiled.identity())) {
            throw new IllegalArgumentException("production replay differs from client digest");
        }
        return Geometry.from(compiled);
    }

    private static void requireExactKeys(
            Map<String, ResourceBlob> values, Set<String> expected, String label
    ) {
        Objects.requireNonNull(values, label);
        if (!new HashSet<>(values.keySet()).equals(expected)) {
            throw new IllegalArgumentException(label + " is incomplete");
        }
    }

    private static byte[] read(ResourceBlob blob) {
        try {
            return blob.openStream().readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory resource read failed", exception);
        }
    }

    private static void verifyPngHeader(ResourceBlob blob, TextureIdentity identity) {
        ByteBuffer header = blob.readOnlyBuffer();
        byte[] signature = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        if (header.remaining() < 24) {
            throw new IllegalArgumentException("texture PNG header is truncated");
        }
        for (int index = 0; index < signature.length; index++) {
            if (header.get(index) != signature[index]) {
                throw new IllegalArgumentException("texture PNG signature mismatch");
            }
        }
        if (header.getInt(8) != 13 || header.getInt(12) != 0x49484452
                || header.getInt(16) != identity.width()
                || header.getInt(20) != identity.height()) {
            throw new IllegalArgumentException("texture PNG dimensions differ from catalog");
        }
    }

    public record Result(
            PoseCatalog catalog,
            Map<String, VerifiedModel> models,
            Map<String, ResourceBlob> textures,
            Map<ReplayKey, QuadMultisetDigest.Result> expectations
    ) {
        public Result {
            Objects.requireNonNull(catalog, "catalog");
            models = Collections.unmodifiableMap(new LinkedHashMap<>(models));
            textures = Collections.unmodifiableMap(new LinkedHashMap<>(textures));
            expectations = Collections.unmodifiableMap(new LinkedHashMap<>(expectations));
            if (models.isEmpty() || textures.isEmpty() || expectations.isEmpty()) {
                throw new IllegalArgumentException("empty preflight result");
            }
        }
    }

    public record ReplayKey(String modelId, String poseStateSha256) {
        public ReplayKey {
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(poseStateSha256, "poseStateSha256");
        }

        public static ReplayKey from(PoseEntry pose) {
            return new ReplayKey(pose.modelId(), pose.poseStateSha256());
        }
    }

    public record VerifiedModel(
            ModelIdentity identity,
            BakedGeometryTopology topology
    ) {
        public VerifiedModel {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(topology, "topology");
        }
    }

    /** Texture-neutral immutable geometry cached across Stone and Gold material variants. */
    public record Geometry(List<StatueModel.Quad> quads, StatueModel.Bounds bounds) {
        public Geometry {
            quads = List.copyOf(quads);
            Objects.requireNonNull(bounds, "bounds");
            if (quads.isEmpty() || quads.size() > MAX_SOURCE_QUADS_PER_POSE) {
                throw new IllegalArgumentException("replay geometry outside production cap");
            }
        }

        static Geometry from(CleanRoomGeometryCompiler.Compilation compilation) {
            List<StatueModel.Quad> quads = compilation.quads().stream()
                    .map(ProfilePreflight::quad).toList();
            QuadMultisetDigest.Bounds sourceBounds = compilation.identity().bounds();
            return new Geometry(quads, new StatueModel.Bounds(
                    vector(sourceBounds.minimum()), vector(sourceBounds.maximum())
            ));
        }

        public StatueModel withTexture(Key texture) {
            return new StatueModel(texture, quads, bounds);
        }

        public int weight() {
            return quads.size();
        }
    }

    private static StatueModel.Quad quad(QuadMultisetDigest.Quad value) {
        List<QuadMultisetDigest.Vertex> vertices = value.vertices();
        return new StatueModel.Quad(
                vertex(vertices.get(0)), vertex(vertices.get(1)),
                vertex(vertices.get(2)), vertex(vertices.get(3)),
                vector(value.storedNormal())
        );
    }

    private static StatueModel.Vertex vertex(QuadMultisetDigest.Vertex value) {
        return new StatueModel.Vertex(vector(value.position()), value.u(), value.v());
    }

    private static StatueModel.Vec3 vector(QuadMultisetDigest.Vec3 value) {
        return new StatueModel.Vec3(value.x(), value.y(), value.z());
    }
}
